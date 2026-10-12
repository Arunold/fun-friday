import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { injectGameApi } from '@ff/sdk';

import { CanvasRenderer, parseHex } from './canvas-renderer';
import {
  BATCH_MS,
  BrushSize,
  CANVAS_HEIGHT,
  CANVAS_WIDTH,
  ConfirmedDrawing,
  ERASER_FACTOR,
  EventResult,
  MAX_BATCH_POINTS,
  MAX_STROKE_POINTS,
  MIN_POINT_DISTANCE,
  Op,
  PALETTE,
  PENDING_TIMEOUT_MS,
  PendingOps,
  SIZES,
  strokeTool,
} from './drawing';
import { DrawingView, EditEvent, PictionaryView, StrokeEvent, Tool } from './pictionary.types';

/** The stroke being drawn: all its points plus those not sent yet. */
interface LiveStroke {
  op: Op;
  unsent: number[];
  pointerId: number;
}

interface BoardStats {
  ops: number;
  version: number;
  undo: number;
  redo: number;
}

/** Undo/redo at most this often (key repeat would exceed the action rate limit). */
const HISTORY_INTERVAL_MS = 120;
/** Ask the server for the whole drawing at most this often after a missed event. */
const SYNC_INTERVAL_MS = 2000;

/**
 * The drawing board: a 4:3 canvas scaled to fit, the drawer's tools and keyboard shortcuts.
 *
 * Everyone renders the server's log (snapshot + `stroke`/`edit` stream events in version order).
 * The drawer additionally sees their own strokes immediately and streams them in batches every
 * {@link BATCH_MS} ms; undo, redo and clear are applied by the server and come back as events.
 * Overlays (word choice, results) are projected over the paper.
 */
@Component({
  selector: 'ff-sketch-board',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, MatTooltipModule],
  templateUrl: './sketch-board.component.html',
  styleUrl: './sketch-board.component.scss',
  host: { '(document:keydown)': 'onKey($event)' },
})
export class SketchBoard {
  /** The drawing from the latest snapshot. */
  readonly drawing = input.required<DrawingView>();
  /** This browser draws this turn (shows the tools). */
  readonly drawer = input(false);
  /** Drawing is possible right now (drawing phase of a running turn). */
  readonly canDraw = input(false);

  private readonly api = injectGameApi<PictionaryView>();
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly stage = viewChild.required<ElementRef<HTMLElement>>('stage');

  protected readonly palette = PALETTE;
  protected readonly sizes = SIZES;
  protected readonly tool = signal<Tool>('pen');
  protected readonly color = signal(PALETTE[0].value);
  protected readonly size = signal(SIZES[1].value);
  /** Size of the paper in CSS pixels (largest 4:3 box that fits). */
  protected readonly box = signal({ width: 0, height: 0 });
  protected readonly stats = signal<BoardStats>(
    { ops: 0, version: 0, undo: 0, redo: 0 },
    { equal: (a, b) => a.ops === b.ops && a.version === b.version && a.undo === b.undo && a.redo === b.redo },
  );
  protected readonly active = computed(() => this.drawer() && this.canDraw());
  /** A brush-sized ring as the cursor while drawing. */
  protected readonly cursor = computed(() => {
    if (!this.active()) {
      return 'default';
    }
    if (this.tool() === 'fill') {
      return 'crosshair';
    }
    const scale = this.box().width / CANVAS_WIDTH;
    const diameter = Math.max(4, Math.round(this.brushSize() * scale));
    const side = diameter + 4;
    const c = side / 2;
    const ring = (stroke: string, width: number) =>
      `<circle cx='${c}' cy='${c}' r='${diameter / 2}' fill='none' stroke='${stroke}' stroke-width='${width}'/>`;
    const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='${side}' height='${side}'>${ring('black', 2)}${ring('white', 1)}</svg>`;
    return `url("data:image/svg+xml,${encodeURIComponent(svg)}") ${c} ${c}, crosshair`;
  });

  private readonly confirmed = new ConfirmedDrawing();
  private readonly pending = new PendingOps();
  private renderer: CanvasRenderer | null = null;
  private frame = 0;
  private live: LiveStroke | null = null;
  private flushTimer: ReturnType<typeof setInterval> | undefined;
  private nextId = Math.floor(Math.random() * 1_000_000) + 1;
  private lastSync = -SYNC_INTERVAL_MS;
  private lastHistory = -HISTORY_INTERVAL_MS;

  constructor() {
    effect(() => {
      const view = this.drawing();
      untracked(() => this.onSnapshot(view));
    });
    effect(() => {
      if (!this.active()) {
        untracked(() => this.endStroke());
      }
    });
    this.api.stream$.pipe(takeUntilDestroyed()).subscribe((message) => {
      if (message.type === 'stroke') {
        this.afterEvent(this.confirmed.applyStroke(message.payload as StrokeEvent));
      } else if (message.type === 'edit') {
        this.afterEvent(this.confirmed.applyEdit(message.payload as EditEvent));
      }
    });
    this.api.events$.pipe(takeUntilDestroyed()).subscribe((event) => {
      if (event.type === 'sync' && this.confirmed.load(event.payload as DrawingView)) {
        this.refresh();
      }
    });
    afterNextRender(() => {
      const context = this.canvas().nativeElement.getContext('2d', { willReadFrequently: true });
      if (context) {
        this.renderer = new CanvasRenderer(context);
      }
      this.observeSize();
      this.refresh();
    });
    this.destroyRef.onDestroy(() => {
      cancelAnimationFrame(this.frame);
      clearInterval(this.flushTimer);
    });
  }

  // ------------------------------------------------------------------ tools

  protected pickTool(tool: Tool): void {
    this.tool.set(tool);
  }

  protected pickColor(color: string): void {
    this.color.set(color);
    if (this.tool() === 'eraser') {
      this.tool.set('pen');
    }
  }

  protected pickSize(size: BrushSize): void {
    this.size.set(size.value);
    if (this.tool() === 'fill') {
      this.tool.set('pen');
    }
  }

  /** Diameter of a size preview dot in CSS pixels. */
  protected dot(size: BrushSize): number {
    return Math.round(6 + size.value * 0.6);
  }

  protected undo(): void {
    if (this.canEdit() && this.stats().undo > 0 && this.historyAllowed()) {
      this.api.sendFast('undo', {});
    }
  }

  protected redo(): void {
    if (this.canEdit() && this.stats().redo > 0 && this.historyAllowed()) {
      this.api.sendFast('redo', {});
    }
  }

  protected clear(): void {
    if (this.canEdit() && this.stats().ops > 0) {
      this.api.sendFast('clear', {});
    }
  }

  protected onKey(event: KeyboardEvent): void {
    if (!this.active() || isTyping(event.target)) {
      return;
    }
    const key = event.key.toLowerCase();
    const modifier = event.ctrlKey || event.metaKey;
    if (modifier && key === 'z' && !event.shiftKey) {
      event.preventDefault();
      this.undo();
    } else if (modifier && (key === 'y' || (key === 'z' && event.shiftKey))) {
      event.preventDefault();
      this.redo();
    } else if (!modifier && !event.altKey) {
      const tools: Record<string, Tool> = { b: 'pen', e: 'eraser', f: 'fill' };
      const size = SIZES[Number(key) - 1];
      if (tools[key]) {
        this.tool.set(tools[key]);
      } else if (size) {
        this.pickSize(size);
      }
    }
  }

  // ------------------------------------------------------------------ pointer input

  protected onPointerDown(event: PointerEvent): void {
    if (!this.active() || event.button !== 0 || this.live) {
      return;
    }
    event.preventDefault();
    const point = this.toLogical(event);
    if (this.tool() === 'fill') {
      this.fillAt(Math.min(point.x, CANVAS_WIDTH - 1), Math.min(point.y, CANVAS_HEIGHT - 1));
      return;
    }
    this.canvas().nativeElement.setPointerCapture(event.pointerId);
    const op: Op = {
      id: this.nextId++,
      tool: strokeTool(this.tool()),
      color: this.color(),
      size: this.brushSize(),
      points: [point.x, point.y],
    };
    this.live = { op, unsent: [point.x, point.y], pointerId: event.pointerId };
    this.pending.add(op);
    clearInterval(this.flushTimer);
    this.flushTimer = setInterval(() => this.flush(), BATCH_MS);
    this.refresh();
  }

  protected onPointerMove(event: PointerEvent): void {
    if (!this.live || event.pointerId !== this.live.pointerId) {
      return;
    }
    const events = event.getCoalescedEvents?.() ?? [];
    for (const each of events.length ? events : [event]) {
      this.addPoint(this.toLogical(each));
    }
    this.refresh();
  }

  protected onPointerUp(event: PointerEvent): void {
    if (this.live && event.pointerId === this.live.pointerId) {
      this.endStroke();
    }
  }

  private addPoint(point: { x: number; y: number }): void {
    const live = this.live!;
    const points = live.op.points;
    const lastX = points[points.length - 2];
    const lastY = points[points.length - 1];
    const dx = point.x - lastX;
    const dy = point.y - lastY;
    if (dx * dx + dy * dy < MIN_POINT_DISTANCE * MIN_POINT_DISTANCE) {
      return;
    }
    if (points.length / 2 >= MAX_STROKE_POINTS) {
      // Long scribble: carry on as a new stroke from the last point (the server caps strokes).
      this.flush();
      this.pending.finish(live.op, performance.now());
      live.op = { ...live.op, id: this.nextId++, points: [lastX, lastY] };
      live.unsent = [lastX, lastY];
      this.pending.add(live.op);
    }
    live.op.points.push(point.x, point.y);
    live.unsent.push(point.x, point.y);
    if (live.unsent.length >= MAX_BATCH_POINTS * 2) {
      this.flush();
    }
  }

  private flush(): void {
    const live = this.live;
    if (live?.unsent.length) {
      const { id, tool, color, size } = live.op;
      this.api.sendFast('stroke', { id, tool, color, size, points: live.unsent });
      live.unsent = [];
    }
  }

  private endStroke(): void {
    const live = this.live;
    if (!live) {
      return;
    }
    this.flush();
    clearInterval(this.flushTimer);
    this.pending.finish(live.op, performance.now());
    this.live = null;
    this.refresh();
    setTimeout(() => this.refresh(), PENDING_TIMEOUT_MS + 100);
  }

  private fillAt(x: number, y: number): void {
    if (this.renderer?.colorAt(x, y) === parseHex(this.color())) {
      return; // nothing would change
    }
    const op: Op = { id: this.nextId++, tool: 'fill', color: this.color(), size: 0, points: [x, y] };
    this.pending.add(op);
    this.pending.finish(op, performance.now());
    this.api.sendFast('fill', { id: op.id, color: op.color, x, y });
    this.refresh();
    setTimeout(() => this.refresh(), PENDING_TIMEOUT_MS + 100);
  }

  private toLogical(event: PointerEvent): { x: number; y: number } {
    const rect = this.canvas().nativeElement.getBoundingClientRect();
    const x = Math.round(((event.clientX - rect.left) / Math.max(1, rect.width)) * CANVAS_WIDTH);
    const y = Math.round(((event.clientY - rect.top) / Math.max(1, rect.height)) * CANVAS_HEIGHT);
    return { x: clamp(x, 0, CANVAS_WIDTH), y: clamp(y, 0, CANVAS_HEIGHT) };
  }

  private brushSize(): number {
    return this.tool() === 'eraser' ? Math.round(this.size() * ERASER_FACTOR) : this.size();
  }

  private canEdit(): boolean {
    return this.active() && !this.live;
  }

  private historyAllowed(): boolean {
    const now = performance.now();
    if (now - this.lastHistory < HISTORY_INTERVAL_MS) {
      return false;
    }
    this.lastHistory = now;
    return true;
  }

  // ------------------------------------------------------------------ sync & rendering

  private onSnapshot(view: DrawingView): void {
    if (view.turn !== this.confirmed.turn) {
      this.live = null;
      clearInterval(this.flushTimer);
      this.pending.clear();
    }
    if (this.confirmed.load(view)) {
      this.refresh();
    }
  }

  private afterEvent(result: EventResult): void {
    if (result === 'skipped') {
      return;
    }
    if (this.confirmed.stale) {
      this.requestSync();
    }
    this.refresh();
  }

  /** After a missed event: players ask for the whole drawing; spectators wait for the next snapshot. */
  private requestSync(): void {
    const now = performance.now();
    if (this.api.isPlaying() && now - this.lastSync >= SYNC_INTERVAL_MS) {
      this.lastSync = now;
      this.api.sendFast('sync', {});
    }
  }

  private displayOps(): readonly Op[] {
    return this.drawer() ? this.pending.merge(this.confirmed.ops) : this.confirmed.ops;
  }

  private refresh(): void {
    this.pending.reconcile(this.confirmed, performance.now());
    const unseen = this.pending.unseen();
    this.stats.set({
      ops: this.displayOps().length,
      version: this.confirmed.version,
      undo: this.confirmed.undo + unseen,
      redo: unseen ? 0 : this.confirmed.redo,
    });
    if (!this.frame) {
      this.frame = requestAnimationFrame(() => {
        this.frame = 0;
        this.renderer?.render(this.displayOps());
      });
    }
  }

  private observeSize(): void {
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect;
      const fit = Math.floor(Math.min(width, (height * CANVAS_WIDTH) / CANVAS_HEIGHT));
      this.box.set({ width: Math.max(0, fit), height: Math.max(0, Math.floor((fit * CANVAS_HEIGHT) / CANVAS_WIDTH)) });
    });
    observer.observe(this.stage().nativeElement);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function isTyping(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable ||
      target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.tagName === 'SELECT')
  );
}
