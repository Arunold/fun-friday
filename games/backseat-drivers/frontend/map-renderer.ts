import { AreaView, FrameView, Ground, LevelStatus } from './backseat-drivers.types';
import { BusPose } from './bus-feed';
import { FOG_OUTER } from './road-renderer';
import {
  ART,
  CellLookup,
  flockLayout,
  forEachCeoCar,
  paintBus,
  paintCeoCar,
  paintCoffee,
  paintCone,
  paintEdges,
  paintGoose,
  paintGround,
} from './paint';

/** Hazards closer than this (tiles) to the bus are highlighted for the navigator. */
const UPCOMING = 9;

/** Everything one picture of the map needs. */
export interface MapScene {
  /** `performance.now()`. */
  now: number;
  map: AreaView;
  pose: BusPose | null;
  frame: FrameView | null;
  /** The bus's path `[x·100, y·100]` (level over). */
  trail: number[][] | null;
  status: LevelStatus;
  reducedMotion: boolean;
  /** Device pixels per CSS pixel (markers keep a minimum on-screen size). */
  dpr: number;
}

/**
 * Draws the whole map scaled to fit: the static ground is painted once into an offscreen layer;
 * hazards, highlights of what's coming up, the drivers' circle of sight, the bus and (once the
 * level is over) its trail are drawn on top every frame.
 */
export class MapRenderer {
  private readonly ctx: CanvasRenderingContext2D | null;
  private readonly base = document.createElement('canvas');
  private baseKey = '';

  constructor(
    canvas: HTMLCanvasElement,
    private readonly font: string,
  ) {
    this.ctx = canvas.getContext('2d');
  }

  render(scene: MapScene): void {
    const ctx = this.ctx;
    const width = ctx?.canvas.width ?? 0;
    const height = ctx?.canvas.height ?? 0;
    const { map } = scene;
    if (!ctx || !width || !height || !map.w || !map.h) {
      return;
    }
    const pad = Math.round(Math.min(width, height) * 0.02);
    const s = Math.min((width - 2 * pad) / map.w, (height - 2 * pad) / map.h);
    const ox = Math.round((width - map.w * s) / 2);
    const oy = Math.round((height - map.h * s) / 2);
    const key = `${map.l}:${map.w}x${map.h}:${width}x${height}:${map.rows.length}`;
    if (key !== this.baseKey) {
      this.baseKey = key;
      this.paintBase(map, s, ox, oy, width, height);
    }
    ctx.fillStyle = ART.fog;
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(this.base, 0, 0);

    const sx = (x: number) => ox + x * s;
    const sy = (y: number) => oy + y * s;
    const pose = scene.pose;
    const pulse = scene.reducedMotion ? 0.5 : 0.5 + 0.5 * Math.sin(scene.now / 220);
    const upcoming = (x: number, y: number) =>
      !!pose && scene.status === 'DRIVING' && Math.hypot(x + 0.5 - pose.x, y + 0.5 - pose.y) < UPCOMING;
    const ring = (x: number, y: number, color: string) => {
      ctx.strokeStyle = color;
      ctx.lineWidth = Math.max(1.5, s * 0.14);
      ctx.globalAlpha = 0.45 + 0.5 * pulse;
      ctx.beginPath();
      ctx.arc(sx(x + 0.5), sy(y + 0.5), s * (0.75 + 0.2 * pulse), 0, Math.PI * 2);
      ctx.stroke();
      ctx.globalAlpha = 1;
    };
    const mark = Math.max(s, 9 * scene.dpr);
    for (const [x, y] of map.cones) {
      if (upcoming(x, y)) {
        ring(x, y, '#ffb35c');
      }
      paintCone(ctx, sx(x + 0.5), sy(y + 0.5), mark);
    }
    for (const [x, y] of map.coffees) {
      if (upcoming(x, y)) {
        ring(x, y, '#ffe08a');
      }
      paintCoffee(ctx, sx(x + 0.5), sy(y + 0.5), mark, pulse);
    }
    const crossing = scene.frame?.g ?? 0;
    for (const flock of map.geese) {
      const layout = flockLayout(flock.cells);
      const along = Math.atan2(layout.dy, layout.dx);
      const active = (crossing & (1 << flock.i)) !== 0;
      for (const [x, y] of flock.cells) {
        if (upcoming(x, y)) {
          ring(x, y, active ? '#ff6b6b' : '#fff3c4');
        }
        if (active) {
          paintGoose(ctx, sx(x + 0.5), sy(y + 0.5), mark, along);
        }
      }
      if (!active) {
        paintGoose(ctx, sx(layout.waitX), sy(layout.waitY), mark, along);
      }
    }
    this.paintCeoWarnings(ctx, map, s, sx, sy, pose, pulse, scene.status);
    if (scene.trail?.length) {
      this.paintTrail(ctx, scene.trail, s, sx, sy, scene.status);
    }
    if (pose) {
      const bx = sx(pose.x);
      const by = sy(pose.y);
      if (scene.status === 'DRIVING') {
        ctx.save();
        ctx.setLineDash([Math.max(3, s * 0.5), Math.max(3, s * 0.4)]);
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.4)';
        ctx.lineWidth = Math.max(1, s * 0.1);
        ctx.beginPath();
        ctx.arc(bx, by, FOG_OUTER * s, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
        ctx.strokeStyle = ART.bus;
        ctx.globalAlpha = 0.35 + 0.4 * (1 - pulse);
        ctx.lineWidth = Math.max(2, s * 0.18);
        ctx.beginPath();
        ctx.arc(bx, by, mark * (1.3 + 0.5 * pulse), 0, Math.PI * 2);
        ctx.stroke();
        ctx.globalAlpha = 1;
      }
      paintBus(ctx, bx, by, pose.heading, Math.max(s * 1.25, 14 * scene.dpr), {
        lights: false,
        brake: false,
        reverse: false,
      });
    }
  }

  /** The ground, the CEO's cars and the parking sign, painted once per level and size. */
  private paintBase(map: AreaView, s: number, ox: number, oy: number, width: number, height: number): void {
    this.base.width = width;
    this.base.height = height;
    const ctx = this.base.getContext('2d');
    if (!ctx) {
      return;
    }
    ctx.clearRect(0, 0, width, height);
    const cell: CellLookup = (x, y) => map.rows[y]?.[x] ?? Ground.UNKNOWN;
    const px = (x: number) => Math.round(ox + x * s);
    const py = (y: number) => Math.round(oy + y * s);
    const box = (x: number, y: number) => ({
      left: px(x),
      top: py(y),
      width: px(x + 1) - px(x),
      height: py(y + 1) - py(y),
    });
    const cars: { x: number; y: number }[] = [];
    const lot = { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity };
    for (let y = 0; y < map.h; y++) {
      for (let x = 0; x < map.w; x++) {
        const c = cell(x, y);
        paintGround(ctx, c, x, y, box(x, y));
        if (c === Ground.CEO_CAR) {
          cars.push({ x, y });
        } else if (c === Ground.PARKING) {
          lot.x0 = Math.min(lot.x0, x);
          lot.y0 = Math.min(lot.y0, y);
          lot.x1 = Math.max(lot.x1, x + 1);
          lot.y1 = Math.max(lot.y1, y + 1);
        }
      }
    }
    for (let y = 0; y < map.h; y++) {
      for (let x = 0; x < map.w; x++) {
        paintEdges(ctx, cell(x, y), x, y, box(x, y), cell);
      }
    }
    for (let y = 0; y < map.h; y++) {
      for (let x = 0; x < map.w; x++) {
        const c = cell(x, y);
        if (c === Ground.RAIN || c === Ground.TUNNEL) {
          this.paintZone(ctx, c, box(x, y));
        } else if (c === Ground.BUILDING) {
          const b = box(x, y);
          ctx.fillStyle = 'rgba(15, 13, 22, 0.32)'; // calmer blocks: the roads are what matters here
          ctx.fillRect(b.left, b.top, b.width, b.height);
        }
      }
    }
    forEachCeoCar(cars, cell, (x, y, angle, length) => paintCeoCar(ctx, ox + x * s, oy + y * s, s, angle, length));
    if (lot.x1 > lot.x0) {
      const size = Math.min(lot.x1 - lot.x0, lot.y1 - lot.y0, 4) * s * 0.7;
      const cx = ox + ((lot.x0 + lot.x1) / 2) * s;
      const cy = oy + ((lot.y0 + lot.y1) / 2) * s;
      ctx.fillStyle = 'rgba(111, 220, 140, 0.16)';
      ctx.fillRect(px(lot.x0), py(lot.y0), px(lot.x1) - px(lot.x0), py(lot.y1) - py(lot.y0));
      ctx.strokeStyle = ART.goal;
      ctx.lineWidth = Math.max(2, s * 0.16);
      ctx.strokeRect(px(lot.x0), py(lot.y0), px(lot.x1) - px(lot.x0), py(lot.y1) - py(lot.y0));
      ctx.fillStyle = '#2f6fdc';
      ctx.beginPath();
      ctx.roundRect(cx - size / 2, cy - size / 2, size, size, size * 0.18);
      ctx.fill();
      ctx.fillStyle = '#ffffff';
      ctx.font = `800 ${Math.round(size * 0.72)}px ${this.font}`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('P', cx, cy + size * 0.04);
    }
  }

  /** Rain is hatched, tunnels get a dashed roof outline: easy to spot on the overview. */
  private paintZone(
    ctx: CanvasRenderingContext2D,
    c: string,
    b: { left: number; top: number; width: number; height: number },
  ): void {
    ctx.save();
    ctx.beginPath();
    ctx.rect(b.left, b.top, b.width, b.height);
    ctx.clip();
    if (c === Ground.RAIN) {
      ctx.strokeStyle = 'rgba(150, 195, 255, 0.45)';
      ctx.lineWidth = Math.max(1, b.width * 0.08);
      ctx.beginPath();
      for (let i = -1; i <= 1; i++) {
        ctx.moveTo(b.left + b.width * (0.15 + i * 0.5), b.top);
        ctx.lineTo(b.left + b.width * (0.15 + i * 0.5) + b.width * 0.35, b.top + b.height);
      }
      ctx.stroke();
    } else {
      ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
      ctx.fillRect(b.left, b.top, b.width, b.height);
      ctx.fillStyle = 'rgba(255, 214, 102, 0.55)';
      const dot = Math.max(1, b.width * 0.12);
      ctx.fillRect(b.left + b.width / 2 - dot / 2, b.top + b.height / 2 - dot / 2, dot, dot);
    }
    ctx.restore();
  }

  private paintCeoWarnings(
    ctx: CanvasRenderingContext2D,
    map: AreaView,
    s: number,
    sx: (x: number) => number,
    sy: (y: number) => number,
    pose: BusPose | null,
    pulse: number,
    status: LevelStatus,
  ): void {
    for (let y = 0; y < map.h; y++) {
      const row = map.rows[y];
      for (let x = row.indexOf(Ground.CEO_CAR); x >= 0; x = row.indexOf(Ground.CEO_CAR, x + 1)) {
        const near = !!pose && status === 'DRIVING' && Math.hypot(x + 0.5 - pose.x, y + 0.5 - pose.y) < 6;
        ctx.strokeStyle = `rgba(255, 82, 102, ${near ? 0.5 + 0.5 * pulse : 0.55})`;
        ctx.lineWidth = Math.max(1.5, s * (near ? 0.2 : 0.1));
        ctx.strokeRect(sx(x) + 1, sy(y) + 1, s - 2, s - 2);
      }
    }
  }

  private paintTrail(
    ctx: CanvasRenderingContext2D,
    trail: number[][],
    s: number,
    sx: (x: number) => number,
    sy: (y: number) => number,
    status: LevelStatus,
  ): void {
    const points = trail.map(([x, y]) => [sx(x / 100), sy(y / 100)]);
    ctx.save();
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(points[0][0], points[0][1]);
    for (const [x, y] of points.slice(1)) {
      ctx.lineTo(x, y);
    }
    ctx.strokeStyle = 'rgba(10, 10, 16, 0.7)';
    ctx.lineWidth = Math.max(4, s * 0.42);
    ctx.stroke();
    ctx.strokeStyle = ART.trail;
    ctx.lineWidth = Math.max(2, s * 0.22);
    ctx.stroke();
    const [startX, startY] = points[0];
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.arc(startX, startY, Math.max(3, s * 0.35), 0, Math.PI * 2);
    ctx.fill();
    const [endX, endY] = points[points.length - 1];
    const badge = Math.max(9, s * 0.8);
    ctx.fillStyle = status === 'PARKED' ? '#3fbf6a' : status === 'CRASHED' ? '#e3354f' : '#f0a020';
    ctx.beginPath();
    ctx.arc(endX, endY - badge * 1.4, badge, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#ffffff';
    ctx.font = `800 ${Math.round(badge * 1.1)}px ${this.font}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(status === 'PARKED' ? '✓' : status === 'CRASHED' ? '!' : '⏱', endX, endY - badge * 1.4 + badge * 0.05);
    ctx.restore();
  }
}
