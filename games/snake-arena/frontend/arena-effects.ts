import { SnakePalette, css, mix } from './arena-theme';
import { cellCenter } from './motion';

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  born: number;
  life: number;
  size: number;
  color: string;
}

interface Ring {
  x: number;
  y: number;
  born: number;
  life: number;
  radius: number;
  color: string;
}

interface Floater {
  x: number;
  y: number;
  text: string;
  born: number;
  life: number;
  color: string;
}

interface Fade {
  path: readonly number[];
  palette: SnakePalette;
  born: number;
  life: number;
}

const WHITE = { r: 255, g: 255, b: 255 };

/**
 * Short-lived decorations on the arena: crash bursts, shock rings, fading bodies and floating
 * "+5"s. Positions are in cell units; everything expires on its own. With reduced motion only
 * the calm parts remain (fading bodies and static "+5"s).
 */
export class ArenaEffects {
  private particles: Particle[] = [];
  private rings: Ring[] = [];
  private floaters: Floater[] = [];
  private fades: Fade[] = [];

  /** A snake crashed: its body fades out and bursts into sparks. */
  crash(body: readonly number[], cols: number, palette: SnakePalette, now: number, reducedMotion: boolean): void {
    if (!body.length) {
      return;
    }
    this.fades.push({ path: body, palette, born: now, life: reducedMotion ? 200 : 420 });
    if (reducedMotion) {
      return;
    }
    const head = cellCenter(body[0], cols);
    this.rings.push({ ...head, born: now, life: 480, radius: 2.4, color: css(mix(palette.base, WHITE, 0.4), 0.9) });
    const step = Math.max(1, Math.floor(body.length / 36));
    const light = css(mix(palette.base, WHITE, 0.55));
    for (let i = 0; i < body.length; i += step) {
      const at = cellCenter(body[i], cols);
      for (let k = 0; k < 2; k++) {
        const angle = Math.random() * Math.PI * 2;
        const speed = 1.2 + Math.random() * 3.2;
        this.particles.push({
          x: at.x,
          y: at.y,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          born: now,
          life: 480 + Math.random() * 420,
          size: 0.09 + Math.random() * 0.14,
          color: k === 0 ? palette.body : light,
        });
      }
    }
  }

  /** A ring where something appeared or was eaten. */
  pop(x: number, y: number, color: string, now: number, reducedMotion: boolean, radius = 0.9): void {
    if (!reducedMotion) {
      this.rings.push({ x, y, born: now, life: 320, radius, color });
    }
  }

  /** A rising label such as "+5". */
  float(x: number, y: number, text: string, color: string, now: number): void {
    this.floaters.push({ x, y, text, born: now, life: 1100, color });
  }

  /** Bodies of crashed snakes, fading out (cell units). */
  drawFades(ctx: CanvasRenderingContext2D, cols: number, now: number): void {
    this.fades = this.fades.filter((fade) => now - fade.born < fade.life);
    for (const fade of this.fades) {
      const t = progress(now, fade.born, fade.life);
      ctx.globalAlpha = 0.65 * (1 - t);
      ctx.fillStyle = fade.palette.body;
      for (const cell of fade.path) {
        const at = cellCenter(cell, cols);
        ctx.beginPath();
        ctx.arc(at.x, at.y, 0.38 * (1 + 0.35 * t), 0, Math.PI * 2);
        ctx.fill();
      }
    }
    ctx.globalAlpha = 1;
  }

  /** Sparks and rings (cell units). */
  drawBursts(ctx: CanvasRenderingContext2D, now: number): void {
    this.particles = this.particles.filter((p) => now - p.born < p.life);
    for (const p of this.particles) {
      const age = Math.max(0, now - p.born) / 1000;
      const t = progress(now, p.born, p.life);
      const travel = (1 - Math.exp(-age * 3.2)) / 3.2; // decelerating
      ctx.globalAlpha = 1 - t;
      ctx.fillStyle = p.color;
      ctx.beginPath();
      ctx.arc(p.x + p.vx * travel, p.y + p.vy * travel + 0.6 * age * age, p.size * (1 - 0.5 * t), 0, Math.PI * 2);
      ctx.fill();
    }
    this.rings = this.rings.filter((ring) => now - ring.born < ring.life);
    for (const ring of this.rings) {
      const t = progress(now, ring.born, ring.life);
      const eased = 1 - Math.pow(1 - t, 3);
      ctx.globalAlpha = 1 - t;
      ctx.strokeStyle = ring.color;
      ctx.lineWidth = 0.12 * (1 - t) + 0.03;
      ctx.beginPath();
      ctx.arc(ring.x, ring.y, 0.2 + ring.radius * eased, 0, Math.PI * 2);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
  }

  /** Floating labels (pixel space: `cell` CSS pixels per cell). */
  drawFloaters(ctx: CanvasRenderingContext2D, cell: number, font: string, now: number, reducedMotion: boolean): void {
    this.floaters = this.floaters.filter((f) => now - f.born < f.life);
    if (!this.floaters.length) {
      return;
    }
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = `800 ${Math.round(Math.max(12, cell * 0.75))}px ${font}`;
    ctx.lineJoin = 'round';
    for (const f of this.floaters) {
      const t = progress(now, f.born, f.life);
      const rise = reducedMotion ? 0 : 1.1 * (1 - Math.pow(1 - t, 2));
      const x = f.x * cell;
      const y = (f.y - 0.7 - rise) * cell;
      ctx.globalAlpha = t < 0.75 ? 1 : 1 - (t - 0.75) / 0.25;
      ctx.lineWidth = 4;
      ctx.strokeStyle = 'rgba(10, 8, 16, 0.75)';
      ctx.strokeText(f.text, x, y);
      ctx.fillStyle = f.color;
      ctx.fillText(f.text, x, y);
    }
    ctx.globalAlpha = 1;
  }

  /** Drops everything (new round). */
  clear(): void {
    this.particles = [];
    this.rings = [];
    this.floaters = [];
    this.fades = [];
  }
}

/** How far through its life an effect is: 0–1, never below 0 even if born a moment "later". */
function progress(now: number, born: number, life: number): number {
  return Math.min(1, Math.max(0, (now - born) / life));
}
