import { CANVAS_HEIGHT, CANVAS_WIDTH, Op, PAPER } from './drawing';

/** A fill spreads over pixels at most this far (per channel) from the clicked colour. */
const FILL_TOLERANCE = 64;
/** Anti-aliased edge pixels up to this far are painted too, so fills don't leave a halo. */
const EDGE_TOLERANCE = 160;

interface Rendered {
  id: number;
  tool: string;
  color: string;
  size: number;
  count: number;
}

/**
 * Paints a drawing log onto a canvas of the logical size. Appended points and operations are drawn
 * incrementally; any other change (undo, clear, re-sync) repaints the whole log in order, so the
 * picture only depends on the log — the same on every screen (BUG-18).
 */
export class CanvasRenderer {
  private rendered: Rendered[] = [];

  constructor(private readonly ctx: CanvasRenderingContext2D) {
    this.reset();
  }

  render(ops: readonly Op[]): void {
    if (!this.canAppend(ops)) {
      this.reset();
    }
    for (let i = 0; i < ops.length; i++) {
      const op = ops[i];
      const done = this.rendered[i];
      if (!done) {
        this.draw(op, 0);
        this.rendered.push({ id: op.id, tool: op.tool, color: op.color, size: op.size, count: op.points.length });
      } else if (op.points.length > done.count) {
        this.draw(op, done.count);
        done.count = op.points.length;
      }
    }
  }

  /** The packed colour at a logical point (see {@link packColor}). */
  colorAt(x: number, y: number): number {
    const [r, g, b] = this.ctx.getImageData(x, y, 1, 1).data;
    return packColor(r, g, b);
  }

  /** Only the newest stroke may grow; everything rendered before must be unchanged. */
  private canAppend(ops: readonly Op[]): boolean {
    if (ops.length < this.rendered.length) {
      return false;
    }
    const last = this.rendered.length - 1;
    for (let i = 0; i <= last; i++) {
      const done = this.rendered[i];
      const op = ops[i];
      if (done.id !== op.id || done.tool !== op.tool || done.color !== op.color || done.size !== op.size) {
        return false;
      }
      const grew = op.points.length !== done.count;
      if (op.points.length < done.count || (grew && (i !== last || op.tool === 'fill'))) {
        return false;
      }
    }
    return true;
  }

  private reset(): void {
    this.ctx.fillStyle = PAPER;
    this.ctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    this.rendered = [];
  }

  private draw(op: Op, from: number): void {
    if (op.tool === 'fill') {
      this.fill(op);
    } else {
      this.stroke(op, from);
    }
  }

  /** Draws a stroke from point index {@code from} (a flat-array index) on, joined to the last one. */
  private stroke(op: Op, from: number): void {
    const ctx = this.ctx;
    const points = op.points;
    const color = op.tool === 'eraser' ? PAPER : op.color;
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = op.size;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    if (points.length === 2) {
      ctx.beginPath();
      ctx.arc(points[0], points[1], op.size / 2, 0, Math.PI * 2);
      ctx.fill();
      return;
    }
    const start = Math.max(0, from - 2);
    ctx.beginPath();
    ctx.moveTo(points[start], points[start + 1]);
    for (let i = start + 2; i < points.length; i += 2) {
      ctx.lineTo(points[i], points[i + 1]);
    }
    ctx.stroke();
  }

  private fill(op: Op): void {
    const x = Math.min(CANVAS_WIDTH - 1, Math.max(0, Math.round(op.points[0])));
    const y = Math.min(CANVAS_HEIGHT - 1, Math.max(0, Math.round(op.points[1])));
    const image = this.ctx.getImageData(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    floodFill(image, x, y, parseHex(op.color));
    this.ctx.putImageData(image, 0, 0);
  }
}

/** Packs RGB like the canvas' little-endian RGBA bytes read as one 32-bit integer (opaque). */
export function packColor(r: number, g: number, b: number): number {
  return ((255 << 24) | (b << 16) | (g << 8) | r) >>> 0;
}

/** `#rrggbb` → packed colour. */
export function parseHex(hex: string): number {
  const value = Number.parseInt(hex.slice(1), 16);
  return packColor((value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff);
}

/**
 * Scanline flood fill with a colour tolerance, then a one-pixel growth into anti-aliased edge
 * pixels. Integer-only and deterministic: the same pixels in give the same pixels out.
 */
export function floodFill(image: ImageData, x: number, y: number, fill: number): void {
  const { width, height } = image;
  const pixels = new Uint32Array(image.data.buffer, image.data.byteOffset, width * height);
  const start = y * width + x;
  const target = pixels[start];
  if (target === fill) {
    return;
  }
  const tr = target & 0xff;
  const tg = (target >>> 8) & 0xff;
  const tb = (target >>> 16) & 0xff;
  const near = (pixel: number, tolerance: number) =>
    Math.abs((pixel & 0xff) - tr) <= tolerance &&
    Math.abs(((pixel >>> 8) & 0xff) - tg) <= tolerance &&
    Math.abs(((pixel >>> 16) & 0xff) - tb) <= tolerance;

  const area = new Uint8Array(width * height);
  const seeds = [start];
  const pushRuns = (from: number, to: number) => {
    let inRun = false;
    for (let i = from; i <= to; i++) {
      const open = !area[i] && near(pixels[i], FILL_TOLERANCE);
      if (open && !inRun) {
        seeds.push(i);
      }
      inRun = open;
    }
  };
  while (seeds.length) {
    const seed = seeds.pop()!;
    if (area[seed]) {
      continue;
    }
    const rowStart = seed - (seed % width);
    let left = seed;
    while (left > rowStart && !area[left - 1] && near(pixels[left - 1], FILL_TOLERANCE)) {
      left--;
    }
    let right = seed;
    while (right < rowStart + width - 1 && !area[right + 1] && near(pixels[right + 1], FILL_TOLERANCE)) {
      right++;
    }
    area.fill(1, left, right + 1);
    if (rowStart > 0) {
      pushRuns(left - width, right - width);
    }
    if (rowStart + width < width * height) {
      pushRuns(left + width, right + width);
    }
  }

  for (let i = 0; i < area.length; i++) {
    if (area[i]) {
      pixels[i] = fill;
    }
  }
  for (let i = 0; i < area.length; i++) {
    if (!area[i]) {
      continue;
    }
    const column = i % width;
    for (const n of [column > 0 ? i - 1 : -1, column < width - 1 ? i + 1 : -1, i - width, i + width]) {
      if (n >= 0 && n < area.length && !area[n] && near(pixels[n], EDGE_TOLERANCE)) {
        pixels[n] = fill;
      }
    }
  }
}
