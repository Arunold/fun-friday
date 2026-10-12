import { DrawingView, EditEvent, OpView, StrokeEvent, Tool } from './pictionary.types';

/** Logical canvas; every client renders the log at exactly this size, so fills come out the same. */
export const CANVAS_WIDTH = 1000;
export const CANVAS_HEIGHT = 750;
/** The paper colour; the eraser paints it. */
export const PAPER = '#ffffff';
/** A stroke batch goes out at most this often while drawing. */
export const BATCH_MS = 80;
/** Mirrors `Drawing.MAX_BATCH_POINTS` on the server. */
export const MAX_BATCH_POINTS = 256;
/** Long strokes continue as a new stroke well before the server limit (2000 points). */
export const MAX_STROKE_POINTS = 1500;
/** Points closer than this to the previous one (logical px) are skipped to keep payloads small. */
export const MIN_POINT_DISTANCE = 2;
/** A finished local operation the server never echoed is dropped after this long. */
export const PENDING_TIMEOUT_MS = 3000;
/** The eraser is this much wider than the brush. */
export const ERASER_FACTOR = 2.5;

export interface Swatch {
  name: string;
  value: string;
}

/** The drawing palette (content colours for the paper, not UI colours). */
export const PALETTE: readonly Swatch[] = [
  { name: 'Black', value: '#1f1f1f' },
  { name: 'Grey', value: '#8a8a8a' },
  { name: 'White', value: '#ffffff' },
  { name: 'Brown', value: '#7b4a2d' },
  { name: 'Red', value: '#e53935' },
  { name: 'Orange', value: '#fb8c00' },
  { name: 'Yellow', value: '#fdd835' },
  { name: 'Green', value: '#43a047' },
  { name: 'Teal', value: '#00acc1' },
  { name: 'Blue', value: '#1e63d6' },
  { name: 'Purple', value: '#8e24aa' },
  { name: 'Pink', value: '#f06292' },
];

export interface BrushSize {
  name: string;
  /** Diameter in logical pixels. */
  value: number;
}

export const SIZES: readonly BrushSize[] = [
  { name: 'Small', value: 4 },
  { name: 'Medium', value: 10 },
  { name: 'Large', value: 24 },
];

/** A drawing operation; stroke points grow in place while a stroke streams in. */
export type Op = OpView;

export function copyOp(op: OpView): Op {
  return { id: op.id, tool: op.tool, color: op.color, size: op.size, points: [...op.points] };
}

/** How a stream event fitted the local log. */
export type EventResult = 'applied' | 'skipped' | 'gap';

/**
 * The drawing as confirmed by the server (BUG-18): replaced by every snapshot (or sync reply) that
 * is at least as new, advanced by stream events in version order. An event that skips versions is
 * applied best-effort and marks the log stale until the next full state arrives.
 */
export class ConfirmedDrawing {
  turn = -1;
  version = 0;
  undo = 0;
  redo = 0;
  ops: Op[] = [];
  stale = false;

  /** Adopts a full state; returns whether anything changed. */
  load(view: DrawingView): boolean {
    if (view.turn === this.turn && (view.version < this.version || (view.version === this.version && !this.stale))) {
      return false;
    }
    this.turn = view.turn;
    this.version = view.version;
    this.undo = view.undo;
    this.redo = view.redo;
    this.ops = view.ops.map(copyOp);
    this.stale = false;
    return true;
  }

  applyStroke(event: StrokeEvent): EventResult {
    const result = this.check(event);
    if (result !== 'skipped') {
      const op = this.find(event.op.id);
      if (op) {
        op.points.push(...event.op.points);
      } else {
        this.ops.push(copyOp(event.op));
      }
      this.advance(event, result);
    }
    return result;
  }

  applyEdit(event: EditEvent): EventResult {
    const result = this.check(event);
    if (result !== 'skipped') {
      if (event.reset) {
        this.ops = [];
      }
      if (event.remove != null) {
        this.ops = this.ops.filter((op) => op.id !== event.remove);
      }
      for (const op of event.add ?? []) {
        if (!this.find(op.id)) {
          this.ops.push(copyOp(op));
        }
      }
      this.advance(event, result);
    }
    return result;
  }

  find(id: number): Op | undefined {
    for (let i = this.ops.length - 1; i >= 0; i--) {
      if (this.ops[i].id === id) {
        return this.ops[i];
      }
    }
    return undefined;
  }

  private check(event: { turn: number; version: number }): EventResult {
    if (event.turn !== this.turn || event.version <= this.version) {
      return 'skipped';
    }
    return event.version === this.version + 1 ? 'applied' : 'gap';
  }

  private advance(event: { version: number; undo: number; redo: number }, result: EventResult): void {
    this.stale ||= result === 'gap';
    this.version = event.version;
    this.undo = event.undo;
    this.redo = event.redo;
  }
}

interface PendingOp {
  op: Op;
  finished: boolean;
  /** The server has (part of) it. */
  seen: boolean;
  finishedAt: number;
}

/**
 * The drawer's own operations that the server hasn't fully echoed yet, so the drawer sees every
 * stroke immediately. Undo, redo and clear are not predicted: they round-trip through the server.
 */
export class PendingOps {
  private items: PendingOp[] = [];

  add(op: Op): void {
    this.items.push({ op, finished: false, seen: false, finishedAt: 0 });
  }

  finish(op: Op, now: number): void {
    const item = this.items.find((candidate) => candidate.op === op);
    if (item) {
      item.finished = true;
      item.finishedAt = now;
    }
  }

  clear(): void {
    this.items = [];
  }

  /** Drops what the server confirmed completely, undid meanwhile, or never got. */
  reconcile(confirmed: ConfirmedDrawing, now: number): void {
    this.items = this.items.filter((item) => {
      const echo = confirmed.find(item.op.id);
      if (echo) {
        item.seen = true;
        return !(item.finished && echo.points.length >= item.op.points.length);
      }
      if (item.seen) {
        return false;
      }
      return !(item.finished && now - item.finishedAt > PENDING_TIMEOUT_MS);
    });
  }

  /** Operations the server doesn't know about yet (they count as undoable steps already). */
  unseen(): number {
    return this.items.filter((item) => !item.seen).length;
  }

  /** The confirmed operations, with local (more complete) versions and not-yet-known ones added. */
  merge(confirmed: readonly Op[]): readonly Op[] {
    if (!this.items.length) {
      return confirmed;
    }
    const local = new Map(this.items.map((item) => [item.op.id, item.op]));
    const merged = confirmed.map((op) => {
      const mine = local.get(op.id);
      if (!mine) {
        return op;
      }
      local.delete(op.id);
      return mine.points.length >= op.points.length ? mine : op;
    });
    for (const item of this.items) {
      if (local.has(item.op.id)) {
        merged.push(item.op);
      }
    }
    return merged;
  }
}

/** The tool a stroke is drawn with (fills are separate operations). */
export function strokeTool(tool: Tool): Tool {
  return tool === 'eraser' ? 'eraser' : 'pen';
}
