import { ArenaEffects } from './arena-effects';
import { LabelLayout } from './arena-labels';
import { ArenaTheme, SnakePalette, css, paletteOf } from './arena-theme';
import { Point, Pose, VECTORS, cellCenter, pointAt } from './motion';
import { Bounds, Frame, PowerType, ShrinkSchedule, SnakeFrame } from './snake-arena.types';

/** Everything one picture needs. */
export interface Scene {
  cols: number;
  rows: number;
  current: Frame;
  /** Where to draw each living snake right now (see `poseAt`). */
  poses: ReadonlyMap<string, Pose>;
  /** `performance.now()`. */
  now: number;
  /** This browser's player (`null` for spectators). */
  me: string | null;
  colors: ReadonlyMap<string, string>;
  names: ReadonlyMap<string, string>;
  /** Show the "YOU" tag (round start or just respawned). */
  youTag: boolean;
  shrink: ShrinkSchedule | null;
  reducedMotion: boolean;
}

const FOOD = '🍎';
const ICONS: Record<PowerType, string> = { BOOST: '⚡', GHOST: '👻', MAGNET: '🧲' };
const HALOS: Record<PowerType, string> = { BOOST: '255, 212, 59', GHOST: '190, 200, 255', MAGNET: '255, 107, 129' };
const MAGNET_RADIUS = 5;
const WALL_MOVE_MS = 450;
const SPAWN_POP_MS = 300;

/**
 * Draws the arena on a canvas: a checkerboard floor, hazard-striped walls that slide in, bobbing
 * food, pulsing power-ups and the snakes as chains of rounded segments in their players' colours,
 * gliding between frames, with eyes that look where they go. Shapes are drawn in cell units, text
 * in CSS pixels; the backing store follows `devicePixelRatio` so everything stays crisp.
 */
export class ArenaRenderer {
  readonly effects = new ArenaEffects();
  private readonly ctx: CanvasRenderingContext2D | null;
  private width = 0;
  private height = 0;
  private dpr = 1;
  private cell = 1;
  private cols = 1;
  private floorCache: HTMLCanvasElement | null = null;
  private readonly sprites = new Map<string, HTMLCanvasElement>();
  private readonly palettes = new Map<string, SnakePalette>();
  private readonly spawnedAt = new Map<string, number>();
  private readonly foodBornAt = new Map<number, number>();
  private wall: { from: Bounds; to: Bounds; start: number } | null = null;

  constructor(
    private readonly canvas: HTMLCanvasElement,
    private theme: ArenaTheme,
  ) {
    this.ctx = canvas.getContext('2d');
  }

  /** The device pixel ratio the backing store was sized for. */
  get pixelRatio(): number {
    return this.dpr;
  }

  /** Sizes the backing store for a board of `width` × `height` CSS pixels. */
  resize(width: number, height: number, dpr: number, cols: number): void {
    if (width === this.width && height === this.height && dpr === this.dpr && cols === this.cols) {
      return;
    }
    this.width = width;
    this.height = height;
    this.dpr = dpr;
    this.cols = cols;
    this.cell = width / cols;
    this.canvas.width = Math.max(1, Math.round(width * dpr));
    this.canvas.height = Math.max(1, Math.round(height * dpr));
    this.floorCache = null;
    this.sprites.clear();
  }

  /**
   * A new frame replaced `before`: crashes burst, (re)spawns pop, eaten food sparkles, walls start
   * sliding and "+5"s float up.
   */
  frameArrived(
    before: Frame | null,
    after: Frame,
    scene: Pick<Scene, 'cols' | 'colors' | 'me' | 'reducedMotion' | 'now'>,
  ): void {
    const { cols, now, reducedMotion } = scene;
    if (!before || before.round !== after.round) {
      this.effects.clear();
      this.wall = null;
      this.spawnedAt.clear();
      this.foodBornAt.clear();
      after.snakes.forEach((snake) => this.spawnedAt.set(snake.id, now));
      return;
    }
    const old = new Map(before.snakes.map((snake) => [snake.id, snake]));
    for (const snake of after.snakes) {
      const was = old.get(snake.id);
      const palette = this.palette(scene.colors.get(snake.id));
      if (was?.alive && !snake.alive) {
        this.effects.crash(was.body, cols, palette, now, reducedMotion);
      } else if (snake.alive && !was?.alive) {
        this.spawnedAt.set(snake.id, now);
        const head = cellCenter(snake.body[0], cols);
        this.effects.pop(head.x, head.y, palette.head, now, reducedMotion, 1.6);
      } else if (snake.alive && was && snake.id === scene.me && snake.points - was.points === 1) {
        const head = cellCenter(snake.body[0], cols);
        this.effects.float(head.x, head.y, '+1', '#ffffff', now);
      }
      old.delete(snake.id);
    }
    for (const gone of old.values()) {
      if (gone.alive) {
        this.effects.crash(gone.body, cols, this.palette(scene.colors.get(gone.id)), now, reducedMotion);
      }
    }
    this.creditFloaters(before, after, cols, now);
    this.trackFood(before, after, cols, now, reducedMotion);
    if (!sameBounds(before.bounds, after.bounds)) {
      this.wall = { from: this.displayedBounds(now) ?? before.bounds, to: after.bounds, start: now };
    }
  }

  /** Draws one picture. */
  render(scene: Scene): void {
    const ctx = this.ctx;
    if (!ctx || this.width <= 0 || this.height <= 0) {
      return;
    }
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    ctx.drawImage(this.floor(scene.cols, scene.rows), 0, 0);

    this.useCells(ctx);
    this.drawWalls(ctx, scene);
    this.drawFood(ctx, scene);
    this.drawPowerUps(ctx, scene);
    const alive = scene.current.snakes.filter((snake) => snake.alive && scene.poses.has(snake.id));
    const poseOf = (snake: SnakeFrame) => scene.poses.get(snake.id) as Pose;
    for (const snake of alive) {
      if (snake.power === 'MAGNET') {
        this.drawMagnet(ctx, scene, snake, poseOf(snake));
      }
    }
    this.effects.drawFades(ctx, scene.cols, scene.now);
    const ordered = [...alive.filter((s) => s.id !== scene.me), ...alive.filter((s) => s.id === scene.me)];
    for (const snake of ordered) {
      this.drawSnake(ctx, scene, snake, poseOf(snake));
    }
    this.effects.drawBursts(ctx, scene.now);

    this.usePixels(ctx);
    // Mine first, so the "YOU" pill always gets its spot.
    const solid = new Set(alive.flatMap((snake) => snake.body));
    const labels = new LabelLayout(this.cell, this.width, this.height, scene.cols, solid);
    for (const snake of [...ordered].reverse()) {
      this.drawLabel(ctx, scene, snake, poseOf(snake), labels);
    }
    this.effects.drawFloaters(ctx, this.cell, this.theme.font, scene.now, scene.reducedMotion);
  }

  // ------------------------------------------------------------------ layers

  private drawWalls(ctx: CanvasRenderingContext2D, scene: Scene): void {
    const bounds = this.displayedBounds(scene.now) ?? scene.current.bounds;
    const { cols, rows } = scene;
    const full = bounds.x0 <= 0 && bounds.y0 <= 0 && bounds.x1 >= cols - 1 && bounds.y1 >= rows - 1;
    const warning = this.shrinkWarning(scene);
    if (!full) {
      const { hazard, hazardStripe } = this.theme;
      ctx.save();
      ctx.beginPath();
      ctx.rect(0, 0, cols, rows);
      ctx.rect(bounds.x0, bounds.y0, bounds.x1 - bounds.x0 + 1, bounds.y1 - bounds.y0 + 1);
      ctx.clip('evenodd');
      ctx.fillStyle = css(hazard, 0.16);
      ctx.fillRect(0, 0, cols, rows);
      ctx.strokeStyle = css(hazardStripe, 0.3);
      ctx.lineWidth = 0.24;
      const spacing = 0.8;
      const shift = scene.reducedMotion ? 0 : ((scene.now / 900) % 1) * spacing;
      ctx.beginPath();
      for (let k = -rows; k < cols + rows; k += spacing) {
        ctx.moveTo(k + shift, 0);
        ctx.lineTo(k + shift + rows, rows);
      }
      ctx.stroke();
      ctx.restore();
      ctx.strokeStyle = css(hazardStripe, 0.85);
      ctx.lineWidth = 0.09;
      ctx.strokeRect(bounds.x0, bounds.y0, bounds.x1 - bounds.x0 + 1, bounds.y1 - bounds.y0 + 1);
    }
    if (warning > 0) {
      const b = scene.current.bounds;
      const pulse = scene.reducedMotion ? 0.6 : 0.5 + 0.5 * Math.sin(scene.now / 85);
      ctx.save();
      ctx.beginPath();
      ctx.rect(b.x0, b.y0, b.x1 - b.x0 + 1, b.y1 - b.y0 + 1);
      ctx.rect(b.x0 + 1, b.y0 + 1, b.x1 - b.x0 - 1, b.y1 - b.y0 - 1);
      ctx.clip('evenodd');
      ctx.fillStyle = css(this.theme.hazard, (0.12 + 0.22 * pulse) * warning);
      ctx.fillRect(b.x0, b.y0, b.x1 - b.x0 + 1, b.y1 - b.y0 + 1);
      ctx.restore();
    }
  }

  private drawFood(ctx: CanvasRenderingContext2D, scene: Scene): void {
    const sprite = this.sprite(FOOD);
    const { cols, now, reducedMotion } = scene;
    for (const cell of scene.current.food) {
      const x = (cell % cols) + 0.5;
      const y = Math.floor(cell / cols) + 0.5;
      const bob = reducedMotion ? 0 : Math.sin(now / 420 + cell * 1.7) * 0.06;
      const born = this.foodBornAt.get(cell);
      const grow = born === undefined || reducedMotion ? 1 : easeOutBack(Math.min(1, (now - born) / 260));
      const size = 0.84 * grow;
      ctx.globalAlpha = 0.28;
      ctx.fillStyle = '#000000';
      ctx.beginPath();
      ctx.ellipse(x, y + 0.34, 0.26 * grow, 0.08 * grow, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.drawImage(sprite, x - size / 2, y - size / 2 + bob - 0.04, size, size);
    }
  }

  private drawPowerUps(ctx: CanvasRenderingContext2D, scene: Scene): void {
    const { cols, now, reducedMotion } = scene;
    for (const item of scene.current.powerUps) {
      const x = (item.cell % cols) + 0.5;
      const y = Math.floor(item.cell / cols) + 0.5;
      const pulse = reducedMotion ? 0.5 : 0.5 + 0.5 * Math.sin(now / 260 + item.cell);
      const ending = item.until - scene.current.tick <= 30;
      ctx.globalAlpha = ending && !reducedMotion ? 0.45 + 0.55 * (0.5 + 0.5 * Math.sin(now / 70)) : 1;
      const radius = 0.85 + 0.25 * pulse;
      const halo = ctx.createRadialGradient(x, y, 0.1, x, y, radius);
      halo.addColorStop(0, `rgba(${HALOS[item.type]}, ${0.45 + 0.2 * pulse})`);
      halo.addColorStop(1, `rgba(${HALOS[item.type]}, 0)`);
      ctx.fillStyle = halo;
      ctx.beginPath();
      ctx.arc(x, y, radius, 0, Math.PI * 2);
      ctx.fill();
      const size = 0.92 + 0.06 * pulse;
      ctx.drawImage(this.sprite(ICONS[item.type]), x - size / 2, y - size / 2, size, size);
      ctx.globalAlpha = 1;
    }
  }

  private drawMagnet(ctx: CanvasRenderingContext2D, scene: Scene, snake: SnakeFrame, pose: Pose): void {
    const head = pointAt(pose.points, pose.head);
    const palette = this.palette(scene.colors.get(snake.id));
    ctx.save();
    ctx.fillStyle = css(palette.base, 0.06);
    ctx.strokeStyle = css(palette.base, 0.45);
    ctx.lineWidth = 0.07;
    ctx.setLineDash([0.45, 0.35]);
    ctx.lineDashOffset = scene.reducedMotion ? 0 : -scene.now / 400;
    ctx.beginPath();
    ctx.arc(head.x, head.y, MAGNET_RADIUS, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    ctx.restore();
  }

  private drawSnake(ctx: CanvasRenderingContext2D, scene: Scene, snake: SnakeFrame, pose: Pose): void {
    const { now } = scene;
    const palette = this.palette(scene.colors.get(snake.id));
    const mine = snake.id === scene.me;
    const born = this.spawnedAt.get(snake.id);
    const pop = born === undefined || scene.reducedMotion ? 1 : easeOutBack(Math.min(1, (now - born) / SPAWN_POP_MS));
    const head = pointAt(pose.points, pose.head);
    const vector = VECTORS[pose.look];

    ctx.save();
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    if (snake.power === 'GHOST') {
      ctx.globalAlpha = 0.42 + (scene.reducedMotion ? 0 : 0.08 * Math.sin(now / 160));
    }
    if (snake.power === 'BOOST' && !scene.reducedMotion) {
      this.drawSpeedLines(ctx, pose.points, pose.head, palette);
    }
    // Rim (with a soft glow around my own snake), then the body as one rounded tube.
    this.trace(ctx, pose.points, pose.head, pose.tail);
    if (mine) {
      ctx.shadowColor = palette.glow;
      ctx.shadowBlur = 16 * this.dpr;
    }
    ctx.strokeStyle = mine ? palette.ring : palette.outline;
    ctx.lineWidth = 0.92 * pop;
    ctx.stroke();
    ctx.shadowBlur = 0;
    ctx.shadowColor = 'transparent';
    ctx.strokeStyle = palette.body;
    ctx.lineWidth = 0.7 * pop;
    ctx.stroke();

    // Rounded segments sliding along the body, darker towards the tail, each with a small sheen.
    const segments = Math.floor(pose.tail - pose.head + 1e-6);
    for (let j = segments; j >= 1; j--) {
      const at = pointAt(pose.points, pose.head + j);
      const fromTail = segments - j;
      const taper = fromTail === 0 ? 0.6 : fromTail === 1 ? 0.75 : fromTail === 2 ? 0.88 : 1;
      const radius = 0.4 * taper * pop;
      const shade = Math.min(
        palette.shades.length - 1,
        Math.floor((j / Math.max(1, segments)) * palette.shades.length),
      );
      ctx.fillStyle = palette.shades[shade];
      ctx.beginPath();
      ctx.arc(at.x, at.y, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = palette.sheen;
      ctx.beginPath();
      ctx.arc(at.x - radius * 0.32, at.y - radius * 0.32, radius * 0.34, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.fillStyle = palette.head;
    ctx.beginPath();
    ctx.arc(head.x, head.y, 0.47 * pop, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = mine ? 'rgba(255, 255, 255, 0.95)' : palette.outline;
    ctx.lineWidth = mine ? 0.08 : 0.06;
    ctx.beginPath();
    ctx.arc(head.x, head.y, (mine ? 0.55 : 0.47) * pop, 0, Math.PI * 2);
    ctx.stroke();
    this.drawEyes(ctx, head.x, head.y, vector, pop);
    ctx.restore();
  }

  private drawEyes(ctx: CanvasRenderingContext2D, x: number, y: number, v: Point, scale: number): void {
    const side = { x: -v.y, y: v.x };
    for (const s of [-1, 1]) {
      const ex = x + v.x * 0.13 * scale + side.x * 0.21 * s * scale;
      const ey = y + v.y * 0.13 * scale + side.y * 0.21 * s * scale;
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.arc(ex, ey, 0.135 * scale, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#1b1820';
      ctx.beginPath();
      ctx.arc(ex + v.x * 0.055 * scale, ey + v.y * 0.055 * scale, 0.07 * scale, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  /** Two fading streaks along the body behind a boosted head. */
  private drawSpeedLines(
    ctx: CanvasRenderingContext2D,
    points: readonly Point[],
    head: number,
    palette: SnakePalette,
  ): void {
    const steps = 10;
    for (const side of [-1, 1]) {
      for (let i = 0; i < steps; i++) {
        const d0 = head + 0.4 + i * 0.3;
        const d1 = d0 + 0.3;
        const a = pointAt(points, d0);
        const b = pointAt(points, d1);
        const nx = -(b.y - a.y);
        const ny = b.x - a.x;
        const length = Math.hypot(nx, ny) || 1;
        const ox = (nx / length) * 0.55 * side;
        const oy = (ny / length) * 0.55 * side;
        ctx.strokeStyle = css(palette.base, 0.55 * (1 - i / steps));
        ctx.lineWidth = 0.09;
        ctx.beginPath();
        ctx.moveTo(a.x + ox, a.y + oy);
        ctx.lineTo(b.x + ox, b.y + oy);
        ctx.stroke();
      }
    }
  }

  /**
   * A name next to another snake's head, or a "YOU" pill on mine at the start of a round and after
   * a respawn; `labels` decides where (or whether) it fits.
   */
  private drawLabel(
    ctx: CanvasRenderingContext2D,
    scene: Scene,
    snake: SnakeFrame,
    pose: Pose,
    labels: LabelLayout,
  ): void {
    const mine = snake.id === scene.me;
    const name = scene.names.get(snake.id);
    if ((mine && !scene.youTag) || (!mine && !name)) {
      return;
    }
    const head = pointAt(pose.points, pose.head);
    const text = mine ? 'YOU' : shorten(name ?? '', 14);
    ctx.font = `${mine ? 800 : 600} 11px ${this.theme.font}`;
    const width = ctx.measureText(text).width + (mine ? 14 : 0);
    const spot = labels.place(head.x * this.cell, head.y * this.cell, pose.look, width, mine ? 17 : 13, mine);
    if (!spot) {
      return;
    }
    const { cx, cy } = spot;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    if (mine) {
      const bounce = scene.reducedMotion ? 0 : Math.sin(scene.now / 180) * 1.5;
      ctx.fillStyle = this.palette(scene.colors.get(snake.id)).body;
      roundRect(ctx, cx - width / 2, cy - 8.5 + bounce, width, 17, 8.5);
      ctx.fill();
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.9)';
      ctx.lineWidth = 1.5;
      ctx.stroke();
      ctx.fillStyle = '#ffffff';
      ctx.fillText(text, cx, cy + 0.5 + bounce);
      return;
    }
    ctx.lineJoin = 'round';
    ctx.lineWidth = 3;
    ctx.strokeStyle = this.theme.shadow;
    ctx.globalAlpha = snake.power === 'GHOST' ? 0.45 : 0.85;
    ctx.strokeText(text, cx, cy);
    ctx.fillStyle = this.theme.text;
    ctx.fillText(text, cx, cy);
    ctx.globalAlpha = 1;
  }

  // ------------------------------------------------------------------ frame bookkeeping

  private creditFloaters(before: Frame, after: Frame, cols: number, now: number): void {
    const seen = before.feed.at(-1)?.id ?? 0;
    for (const entry of after.feed) {
      if (entry.id <= seen || entry.kind !== 'BODY') {
        continue;
      }
      const owner = after.snakes.find((snake) => snake.id === entry.a);
      if (owner?.alive && owner.body.length) {
        const head = cellCenter(owner.body[0], cols);
        this.effects.float(head.x, head.y, '+5', '#6fdc8c', now);
      }
    }
  }

  private trackFood(before: Frame, after: Frame, cols: number, now: number, reducedMotion: boolean): void {
    const remaining = new Set(after.food);
    const heads = new Set(after.snakes.filter((s) => s.alive).map((s) => s.body[0]));
    for (const cell of before.food) {
      if (!remaining.has(cell) && heads.has(cell)) {
        this.effects.pop(
          (cell % cols) + 0.5,
          Math.floor(cell / cols) + 0.5,
          'rgba(255, 120, 110, 0.9)',
          now,
          reducedMotion,
        );
      }
    }
    const old = new Set(before.food);
    for (const cell of after.food) {
      if (!old.has(cell) && !this.foodBornAt.has(cell)) {
        this.foodBornAt.set(cell, now);
      }
    }
    for (const cell of [...this.foodBornAt.keys()]) {
      if (!remaining.has(cell) || now - (this.foodBornAt.get(cell) ?? 0) > 1000) {
        this.foodBornAt.delete(cell);
      }
    }
  }

  /** The walls while they slide in, `null` when they rest. */
  private displayedBounds(now: number): Bounds | null {
    const wall = this.wall;
    if (!wall) {
      return null;
    }
    const t = Math.min(1, Math.max(0, (now - wall.start) / WALL_MOVE_MS));
    if (t >= 1) {
      this.wall = null;
      return null;
    }
    const e = 1 - Math.pow(1 - t, 3);
    const lerp = (a: number, b: number) => a + (b - a) * e;
    return {
      x0: lerp(wall.from.x0, wall.to.x0),
      y0: lerp(wall.from.y0, wall.to.y0),
      x1: lerp(wall.from.x1, wall.to.x1),
      y1: lerp(wall.from.y1, wall.to.y1),
    };
  }

  /** 0–1: how urgently the next wall move is coming (the last second before it). */
  private shrinkWarning(scene: Scene): number {
    const shrink = scene.shrink;
    const b = scene.current.bounds;
    if (!shrink) {
      return 0;
    }
    const canShrink = b.x1 - b.x0 + 1 - 2 >= shrink.minWidth || b.y1 - b.y0 + 1 - 2 >= shrink.minHeight;
    if (!canShrink) {
      return 0;
    }
    const tick = scene.current.tick;
    const next =
      tick < shrink.from
        ? shrink.from
        : shrink.from + (Math.floor((tick - shrink.from) / shrink.every) + 1) * shrink.every;
    const left = next - tick;
    return left > 0 && left <= 10 ? 1 - (left - 1) / 10 : 0;
  }

  // ------------------------------------------------------------------ helpers

  private useCells(ctx: CanvasRenderingContext2D): void {
    const scale = this.dpr * this.cell;
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
  }

  private usePixels(ctx: CanvasRenderingContext2D): void {
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
  }

  /** A path from distance `from` to `to` along the points. */
  private trace(ctx: CanvasRenderingContext2D, points: readonly Point[], from: number, to: number): void {
    const start = pointAt(points, from);
    ctx.beginPath();
    ctx.moveTo(start.x, start.y);
    for (let i = Math.ceil(from + 1e-6); i < to; i++) {
      const at = pointAt(points, i);
      ctx.lineTo(at.x, at.y);
    }
    const end = pointAt(points, to);
    ctx.lineTo(end.x, end.y);
  }

  private palette(color: string | undefined): SnakePalette {
    const key = color ?? '';
    let palette = this.palettes.get(key);
    if (!palette) {
      palette = paletteOf(key);
      this.palettes.set(key, palette);
    }
    return palette;
  }

  /** The checkerboard floor at device resolution, drawn once per size. */
  private floor(cols: number, rows: number): HTMLCanvasElement {
    if (this.floorCache) {
      return this.floorCache;
    }
    const floor = document.createElement('canvas');
    floor.width = this.canvas.width;
    floor.height = this.canvas.height;
    const ctx = floor.getContext('2d');
    if (ctx) {
      ctx.fillStyle = this.theme.floor;
      ctx.fillRect(0, 0, floor.width, floor.height);
      ctx.fillStyle = this.theme.floorAlt;
      const size = floor.width / cols;
      for (let y = 0; y < rows; y++) {
        for (let x = y % 2; x < cols; x += 2) {
          const left = Math.round(x * size);
          const top = Math.round(y * size);
          ctx.fillRect(left, top, Math.round((x + 1) * size) - left, Math.round((y + 1) * size) - top);
        }
      }
    }
    this.floorCache = floor;
    return floor;
  }

  /** An emoji pre-rendered at the current cell size. */
  private sprite(emoji: string): HTMLCanvasElement {
    let sprite = this.sprites.get(emoji);
    if (!sprite) {
      sprite = document.createElement('canvas');
      const size = Math.max(8, Math.ceil(this.cell * this.dpr * 1.1));
      sprite.width = size;
      sprite.height = size;
      const ctx = sprite.getContext('2d');
      if (ctx) {
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.font = `${Math.round(size * 0.82)}px "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif`;
        ctx.fillText(emoji, size / 2, size / 2 + size * 0.05);
      }
      this.sprites.set(emoji, sprite);
    }
    return sprite;
  }
}

function shorten(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

function sameBounds(a: Bounds, b: Bounds): boolean {
  return a.x0 === b.x0 && a.y0 === b.y0 && a.x1 === b.x1 && a.y1 === b.y1;
}

/** Overshooting ease for pop-ins; `t` is clamped to 0–1 and the result never drops below 0. */
function easeOutBack(t: number): number {
  const c = 1.70158;
  const x = Math.min(1, Math.max(0, t));
  return Math.max(0, 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2));
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}
