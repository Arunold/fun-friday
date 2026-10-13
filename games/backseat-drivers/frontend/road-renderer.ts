import { AreaView, Effect, FrameView, Ground } from './backseat-drivers.types';
import { BusPose } from './bus-feed';
import {
  ART,
  CellBox,
  CellLookup,
  flockLayout,
  forEachCeoCar,
  noise,
  paintBeams,
  paintBus,
  paintCeoCar,
  paintCoffee,
  paintCone,
  paintEdges,
  paintGoose,
  paintGround,
  paintHonk,
} from './paint';

/** The fog is clear up to this many tiles from the bus… */
export const FOG_INNER = 4.6;
/**
 * …and opaque from here. The server's window holds the cells within 7.5 tiles of the bus's cell, so
 * even with the bus at the edge of its cell and a frame of interpolation lag no unknown cell shows.
 */
export const FOG_OUTER = 6.1;
/** Tiles across the short side of the canvas. */
const VIEW_TILES = FOG_OUTER * 2 + 0.4;

/** Everything one picture of the road needs. */
export interface RoadScene {
  /** `performance.now()`. */
  now: number;
  pose: BusPose;
  frame: FrameView | null;
  area: AreaView | null;
  /** Somebody presses the brake (brake lights). */
  brake: boolean;
  reducedMotion: boolean;
}

/** Something flying off the road for a moment: a hit cone, scattered geese, "+5 s". */
interface Flyer {
  kind: 'cone' | 'goose' | 'text';
  x: number;
  y: number;
  vx: number;
  vy: number;
  born: number;
  life: number;
  text?: string;
  color?: string;
}

/** The ground character of an area's cell, `?` outside it. */
export function cellOf(area: AreaView | null, x: number, y: number): string {
  if (!area) {
    return Ground.UNKNOWN;
  }
  return area.rows[y - area.y0]?.[x - area.x0] ?? Ground.UNKNOWN;
}

/**
 * Draws the drivers' view: the road around the bus (north up, the bus in the middle) with a radial
 * fog, rain blur and streaks, tunnel darkness with headlight beams, the wipers, honks and bumps.
 * All drawing happens in device pixels.
 */
export class RoadRenderer {
  private readonly ctx: CanvasRenderingContext2D | null;
  private readonly world = document.createElement('canvas');
  private readonly shade = document.createElement('canvas');
  private readonly since = new Map<number, number>();
  private effects = 0;
  private flyers: Flyer[] = [];
  private knownLevel = -1;
  private knownVersion = -1;
  private knownCones = new Set<string>();
  private knownCoffees = new Set<string>();
  private knownGeese = new Map<number, number[][]>();

  constructor(
    canvas: HTMLCanvasElement,
    private readonly font: string,
  ) {
    this.ctx = canvas.getContext('2d');
  }

  /** Clears the canvas (nothing to show yet). */
  clear(): void {
    const ctx = this.ctx;
    if (ctx) {
      ctx.fillStyle = ART.fog;
      ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    }
  }

  render(scene: RoadScene): void {
    const ctx = this.ctx;
    const width = ctx?.canvas.width ?? 0;
    const height = ctx?.canvas.height ?? 0;
    if (!ctx || !width || !height) {
      return;
    }
    const s = Math.min(width, height) / VIEW_TILES;
    const effects = scene.frame?.e ?? 0;
    this.trackEffects(effects, scene.now);
    this.trackArea(scene);
    let cx = width / 2;
    let cy = height / 2;
    if (effects & Effect.BUMP && !scene.reducedMotion) {
      cx += (Math.random() - 0.5) * s * 0.14;
      cy += (Math.random() - 0.5) * s * 0.14;
    }
    ctx.fillStyle = ART.fog;
    ctx.fillRect(0, 0, width, height);

    const blur = (effects & Effect.BLUR) !== 0;
    const target = blur ? this.layer(this.world, width, height) : ctx;
    if (target) {
      this.drawWorld(target, scene, s, cx, cy);
      if (blur) {
        ctx.save();
        ctx.filter = `blur(${Math.max(2, Math.round(s * 0.17))}px)`;
        ctx.drawImage(this.world, 0, 0);
        ctx.restore();
        ctx.fillStyle = 'rgba(190, 210, 235, 0.1)';
        ctx.fillRect(0, 0, width, height);
      }
    }
    const { pose } = scene;
    if (effects & Effect.LIGHTS && !(effects & Effect.TUNNEL)) {
      ctx.save();
      ctx.globalCompositeOperation = 'lighter';
      paintBeams(ctx, cx, cy, pose.heading, s, 0.55);
      ctx.restore();
    }
    if (effects & Effect.RAIN) {
      this.drawRain(ctx, width, height, s, scene, blur);
    }
    if (effects & Effect.TUNNEL) {
      this.drawDarkness(ctx, width, height, s, cx, cy, pose.heading, effects);
    }
    if (effects & Effect.OFFROAD && Math.abs(pose.speed) > 0.3 && !scene.reducedMotion) {
      this.drawDust(ctx, cx, cy, pose, s, scene.now);
    }
    paintBus(ctx, cx, cy, pose.heading, s, {
      lights: (effects & Effect.LIGHTS) !== 0,
      brake: scene.brake,
      reverse: pose.speed < -0.05,
    });
    if (effects & Effect.HONK) {
      const phase = scene.reducedMotion ? 0.3 : ((scene.now - (this.since.get(Effect.HONK) ?? scene.now)) % 600) / 600;
      paintHonk(ctx, cx, cy, pose.heading, s, phase);
    }
    if (effects & Effect.SCRAPE && !scene.reducedMotion) {
      this.drawSparks(ctx, cx, cy, s);
    }
    this.drawFlyers(ctx, scene, s, cx, cy);
    if (effects & Effect.WIPE) {
      this.drawWiper(ctx, width, height, s, scene);
    }
    const fog = ctx.createRadialGradient(cx, cy, FOG_INNER * s, cx, cy, FOG_OUTER * s);
    fog.addColorStop(0, 'rgba(15, 13, 22, 0)');
    fog.addColorStop(1, 'rgba(15, 13, 22, 1)');
    ctx.fillStyle = fog;
    ctx.fillRect(0, 0, width, height);
  }

  // ------------------------------------------------------------------ the world

  private drawWorld(ctx: CanvasRenderingContext2D, scene: RoadScene, s: number, cx: number, cy: number): void {
    const { pose, area, now } = scene;
    const cell: CellLookup = (x, y) => cellOf(area, x, y);
    const reach = FOG_OUTER + 1.2;
    const x0 = Math.floor(pose.x - reach);
    const x1 = Math.floor(pose.x + reach);
    const y0 = Math.floor(pose.y - reach);
    const y1 = Math.floor(pose.y + reach);
    const px = (x: number) => Math.round(cx + (x - pose.x) * s);
    const py = (y: number) => Math.round(cy + (y - pose.y) * s);
    const box = (x: number, y: number): CellBox => ({
      left: px(x),
      top: py(y),
      width: px(x + 1) - px(x),
      height: py(y + 1) - py(y),
    });
    const cars: { x: number; y: number }[] = [];
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        const c = cell(x, y);
        if (c !== Ground.UNKNOWN) {
          paintGround(ctx, c, x, y, box(x, y));
          if (c === Ground.CEO_CAR) {
            cars.push({ x, y });
          }
        }
      }
    }
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        const c = cell(x, y);
        if (c !== Ground.UNKNOWN) {
          paintEdges(ctx, c, x, y, box(x, y), cell);
        }
      }
    }
    const sx = (x: number) => cx + (x - pose.x) * s;
    const sy = (y: number) => cy + (y - pose.y) * s;
    forEachCeoCar(cars, cell, (x, y, angle, length) => paintCeoCar(ctx, sx(x), sy(y), s, angle, length));
    if (!area) {
      return;
    }
    for (const [x, y] of area.cones) {
      paintCone(ctx, sx(x + 0.5), sy(y + 0.5), s);
    }
    const glow = scene.reducedMotion ? 0.5 : 0.5 + 0.5 * Math.sin(now / 280);
    for (const [x, y] of area.coffees) {
      paintCoffee(ctx, sx(x + 0.5), sy(y + 0.5), s, glow);
    }
    const crossing = scene.frame?.g ?? 0;
    for (const flock of area.geese) {
      const layout = flockLayout(flock.cells);
      const along = Math.atan2(layout.dy, layout.dx);
      if (crossing & (1 << flock.i)) {
        flock.cells.forEach(([x, y], i) => {
          const wobble = scene.reducedMotion ? 0 : Math.sin(now / 130 + i * 1.7);
          const shift = wobble * 0.12;
          paintGoose(
            ctx,
            sx(x + 0.5 + layout.dx * shift),
            sy(y + 0.5 + layout.dy * shift),
            s,
            along + (i % 2 ? Math.PI : 0) + wobble * 0.25,
          );
        });
      } else {
        const count = Math.min(3, flock.cells.length);
        for (let i = 0; i < count; i++) {
          const spread = (i - (count - 1) / 2) * 0.42;
          paintGoose(
            ctx,
            sx(layout.waitX + layout.dy * spread),
            sy(layout.waitY + layout.dx * spread),
            s * 0.85,
            along,
          );
        }
      }
    }
  }

  // ------------------------------------------------------------------ weather, light, wipers

  private drawRain(
    ctx: CanvasRenderingContext2D,
    w: number,
    h: number,
    s: number,
    scene: RoadScene,
    blur: boolean,
  ): void {
    const t = scene.reducedMotion ? 0 : scene.now;
    const count = Math.round(((w * h) / (s * s)) * 0.7);
    ctx.save();
    ctx.strokeStyle = 'rgba(196, 220, 255, 0.38)';
    ctx.lineWidth = Math.max(1, s * 0.03);
    ctx.lineCap = 'round';
    ctx.beginPath();
    for (let i = 0; i < count; i++) {
      const speed = 0.8 + noise(i, 1) * 0.5;
      const x = (noise(i, 2) * w + t * s * 0.004) % w;
      const y = (noise(i, 3) * h + t * s * 0.016 * speed) % h;
      ctx.moveTo(x, y);
      ctx.lineTo(x + s * 0.07, y + s * 0.34);
    }
    ctx.stroke();
    if (blur) {
      const seed = Math.floor(t / 2500);
      ctx.fillStyle = 'rgba(220, 235, 255, 0.07)';
      ctx.strokeStyle = 'rgba(230, 240, 255, 0.28)';
      ctx.lineWidth = Math.max(1, s * 0.025);
      for (let i = 0; i < 18; i++) {
        const r = s * (0.07 + noise(i, seed, 4) * 0.14);
        ctx.beginPath();
        ctx.arc(noise(i, seed, 5) * w, noise(i, seed, 6) * h, r, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
      }
    }
    ctx.restore();
  }

  /** A dark tunnel: almost nothing visible; with headlights a cone of light ahead. */
  private drawDarkness(
    ctx: CanvasRenderingContext2D,
    w: number,
    h: number,
    s: number,
    cx: number,
    cy: number,
    heading: number,
    effects: number,
  ): void {
    const shade = this.layer(this.shade, w, h);
    if (!shade) {
      return;
    }
    const dark = (effects & Effect.DARK) !== 0;
    shade.fillStyle = dark ? 'rgba(4, 4, 9, 0.97)' : 'rgba(4, 4, 9, 0.84)';
    shade.fillRect(0, 0, w, h);
    shade.globalCompositeOperation = 'destination-out';
    const sight = (dark ? 1.5 : 1.25) * s;
    const glow = shade.createRadialGradient(cx, cy, sight * 0.3, cx, cy, sight);
    glow.addColorStop(0, 'rgba(0, 0, 0, 1)');
    glow.addColorStop(1, 'rgba(0, 0, 0, 0)');
    shade.fillStyle = glow;
    shade.beginPath();
    shade.arc(cx, cy, sight, 0, Math.PI * 2);
    shade.fill();
    if (!dark) {
      const ox = cx + Math.cos(heading) * s * 0.6;
      const oy = cy + Math.sin(heading) * s * 0.6;
      const reach = s * 4.8;
      const beam = shade.createRadialGradient(ox, oy, 0, ox, oy, reach);
      beam.addColorStop(0, 'rgba(0, 0, 0, 1)');
      beam.addColorStop(0.65, 'rgba(0, 0, 0, 0.85)');
      beam.addColorStop(1, 'rgba(0, 0, 0, 0)');
      shade.fillStyle = beam;
      shade.beginPath();
      shade.moveTo(ox, oy);
      shade.arc(ox, oy, reach, heading - 0.45, heading + 0.45);
      shade.closePath();
      shade.fill();
    }
    shade.globalCompositeOperation = 'source-over';
    ctx.drawImage(this.shade, 0, 0);
    if (!dark) {
      ctx.save();
      ctx.globalCompositeOperation = 'lighter';
      paintBeams(ctx, cx, cy, heading, s, 0.35);
      ctx.restore();
    }
  }

  /** One sweep of the wipers across the screen. */
  private drawWiper(ctx: CanvasRenderingContext2D, w: number, h: number, s: number, scene: RoadScene): void {
    const start = this.since.get(Effect.WIPE) ?? scene.now;
    const p = scene.reducedMotion ? 0.5 : Math.min(1, (scene.now - start) / 450);
    const angle = -Math.PI + 0.35 + p * (Math.PI - 0.7);
    const pivotX = w / 2;
    const pivotY = h + s * 0.4;
    const length = h * 0.98;
    ctx.save();
    ctx.lineCap = 'round';
    ctx.strokeStyle = 'rgba(16, 16, 22, 0.88)';
    ctx.lineWidth = Math.max(4, s * 0.13);
    ctx.beginPath();
    ctx.moveTo(pivotX, pivotY);
    ctx.lineTo(pivotX + Math.cos(angle) * length, pivotY + Math.sin(angle) * length);
    ctx.stroke();
    ctx.strokeStyle = 'rgba(150, 150, 165, 0.6)';
    ctx.lineWidth = Math.max(1, s * 0.03);
    ctx.stroke();
    ctx.restore();
  }

  private drawSparks(ctx: CanvasRenderingContext2D, cx: number, cy: number, s: number): void {
    ctx.fillStyle = '#ffb347';
    for (let i = 0; i < 7; i++) {
      const a = Math.random() * Math.PI * 2;
      const r = s * (0.45 + Math.random() * 0.35);
      ctx.fillRect(cx + Math.cos(a) * r, cy + Math.sin(a) * r, Math.max(1.5, s * 0.05), Math.max(1.5, s * 0.05));
    }
  }

  private drawDust(ctx: CanvasRenderingContext2D, cx: number, cy: number, pose: BusPose, s: number, now: number): void {
    const back = pose.speed >= 0 ? pose.heading + Math.PI : pose.heading;
    ctx.fillStyle = 'rgba(170, 140, 90, 0.22)';
    for (let i = 0; i < 4; i++) {
      const d = s * (0.75 + ((now / 90 + i * 0.37) % 1) * 0.6);
      const side = (i % 2 ? 1 : -1) * s * 0.25;
      ctx.beginPath();
      ctx.arc(
        cx + Math.cos(back) * d - Math.sin(back) * side,
        cy + Math.sin(back) * d + Math.cos(back) * side,
        s * (0.14 + 0.1 * ((i * 7) % 3)),
        0,
        Math.PI * 2,
      );
      ctx.fill();
    }
  }

  // ------------------------------------------------------------------ cones, coffee and geese leaving

  /** Notices cones hit, coffee drunk and geese scattered near the bus, for a short animation. */
  private trackArea(scene: RoadScene): void {
    const area = scene.area;
    if (!area) {
      return;
    }
    if (area.l !== this.knownLevel) {
      this.knownLevel = area.l;
      this.flyers = [];
    } else if (area.v !== this.knownVersion) {
      const { pose, now } = scene;
      const near = (x: number, y: number, range: number) => Math.hypot(x + 0.5 - pose.x, y + 0.5 - pose.y) < range;
      const cones = new Set(area.cones.map(key));
      for (const gone of [...this.knownCones].filter((k) => !cones.has(k)).map(unkey)) {
        if (near(gone[0], gone[1], 2)) {
          const spin = Math.random() < 0.5 ? -1 : 1;
          this.fly(
            'cone',
            gone[0] + 0.5,
            gone[1] + 0.5,
            Math.cos(pose.heading) * 2 + spin,
            Math.sin(pose.heading) * 2,
            now,
          );
          this.fly('text', gone[0] + 0.5, gone[1] - 0.1, 0, -1, now, '−5 s', '#ff9d5c');
        }
      }
      const coffees = new Set(area.coffees.map(key));
      for (const gone of [...this.knownCoffees].filter((k) => !coffees.has(k)).map(unkey)) {
        if (near(gone[0], gone[1], 2)) {
          this.fly('text', gone[0] + 0.5, gone[1] - 0.1, 0, -1, now, '+5 s', '#6fdc8c');
        }
      }
      const geese = new Set(area.geese.map((flock) => flock.i));
      for (const [i, cells] of this.knownGeese) {
        if (!geese.has(i) && cells.some(([x, y]) => near(x, y, 6.5))) {
          cells.slice(0, 4).forEach(([x, y], n) => {
            const a = Math.atan2(y + 0.5 - pose.y, x + 0.5 - pose.x) + (n - 1.5) * 0.4;
            this.fly('goose', x + 0.5, y + 0.5, Math.cos(a) * 3, Math.sin(a) * 3, now);
          });
        }
      }
    }
    this.knownVersion = area.v;
    this.knownCones = new Set(area.cones.map(key));
    this.knownCoffees = new Set(area.coffees.map(key));
    this.knownGeese = new Map(area.geese.map((flock) => [flock.i, flock.cells]));
  }

  private fly(
    kind: Flyer['kind'],
    x: number,
    y: number,
    vx: number,
    vy: number,
    now: number,
    text?: string,
    color?: string,
  ): void {
    this.flyers.push({ kind, x, y, vx, vy, born: now, life: kind === 'goose' ? 1100 : 900, text, color });
  }

  private drawFlyers(ctx: CanvasRenderingContext2D, scene: RoadScene, s: number, cx: number, cy: number): void {
    const { now, pose } = scene;
    this.flyers = this.flyers.filter((f) => now - f.born < f.life);
    for (const f of this.flyers) {
      const age = (now - f.born) / 1000;
      const p = (now - f.born) / f.life;
      const move = scene.reducedMotion ? 0 : age;
      const x = cx + (f.x + f.vx * move - pose.x) * s;
      const y = cy + (f.y + f.vy * move - pose.y) * s;
      ctx.save();
      ctx.globalAlpha = 1 - p;
      if (f.kind === 'text') {
        ctx.font = `800 ${Math.round(s * 0.42)}px ${this.font}`;
        ctx.textAlign = 'center';
        ctx.lineWidth = Math.max(2, s * 0.08);
        ctx.strokeStyle = 'rgba(10, 10, 16, 0.85)';
        ctx.strokeText(f.text ?? '', x, y);
        ctx.fillStyle = f.color ?? '#ffffff';
        ctx.fillText(f.text ?? '', x, y);
      } else if (f.kind === 'cone') {
        ctx.translate(x, y);
        ctx.rotate(move * 9);
        ctx.scale(1 + p * 0.6, 1 + p * 0.6);
        paintCone(ctx, 0, 0, s);
      } else {
        paintGoose(ctx, x, y, s * (1 + p * 0.5), Math.atan2(f.vy, f.vx));
      }
      ctx.restore();
    }
  }

  // ------------------------------------------------------------------ helpers

  private trackEffects(effects: number, now: number): void {
    const started = effects & ~this.effects;
    for (const bit of [Effect.HONK, Effect.WIPE]) {
      if (started & bit) {
        this.since.set(bit, now);
      }
    }
    this.effects = effects;
  }

  /** An offscreen layer of the canvas's size, cleared. */
  private layer(canvas: HTMLCanvasElement, w: number, h: number): CanvasRenderingContext2D | null {
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }
    const ctx = canvas.getContext('2d');
    ctx?.clearRect(0, 0, w, h);
    return ctx;
  }
}

function key(cell: number[]): string {
  return `${cell[0]},${cell[1]}`;
}

function unkey(text: string): number[] {
  return text.split(',').map(Number);
}
