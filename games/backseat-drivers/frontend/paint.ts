import { Ground } from './backseat-drivers.types';

/** Ground character of a cell, `?` when unknown. */
export type CellLookup = (x: number, y: number) => string;

/** Game art colours (tokens don't fit grass and asphalt). */
export const ART = {
  fog: '#0f0d16',
  grass: '#33603a',
  grassDark: '#2b5432',
  grassTuft: '#27492d',
  building: '#474d66',
  buildingTop: '#4f5675',
  buildingEdge: '#737c9e',
  skylight: '#8fb4d9',
  treeShadow: 'rgba(0, 0, 0, 0.28)',
  tree: '#1f4a2a',
  treeLight: '#2e6b3b',
  water: '#245780',
  waterLight: '#4f86b4',
  road: '#3a3b43',
  rain: '#333d4d',
  puddle: 'rgba(120, 170, 230, 0.22)',
  tunnel: '#24252c',
  tunnelWall: '#7a7d8a',
  parking: '#383c48',
  parkingLine: 'rgba(235, 238, 245, 0.75)',
  goal: '#6fdc8c',
  kerb: 'rgba(214, 218, 228, 0.8)',
  bus: '#f7c12d',
  busDark: '#b9850c',
  busRoof: '#ffd65c',
  glass: '#1d2638',
  ceo: '#e3354f',
  ceoDark: '#8e1529',
  cone: '#ff8a1f',
  coneBase: '#2a2a2e',
  cup: '#f5f0e6',
  coffee: '#6b4226',
  goose: '#f7f6f0',
  gooseShade: '#cfcfc6',
  beak: '#ffa21c',
  trail: '#ffd54a',
} as const;

/** Road surfaces the bus drives on at full speed (the CEO's car stands on road too). */
export function isPaved(c: string): boolean {
  return c === Ground.ROAD || c === Ground.RAIN || c === Ground.TUNNEL || c === Ground.PARKING || c === Ground.CEO_CAR;
}

/** Deterministic noise in [0, 1) per cell, for texture that doesn't flicker. */
export function noise(x: number, y: number, salt = 0): number {
  let h = Math.imul(x + 0x9e37 + salt * 0x3c6e, 0x85ebca6b) ^ Math.imul(y + 0x7f4a, 0xc2b2ae35);
  h ^= h >>> 15;
  h = Math.imul(h, 0x27d4eb2f);
  h ^= h >>> 13;
  return (h >>> 0) / 4294967296;
}

/** A cell's rectangle in device pixels (edges rounded so neighbours never show seams). */
export interface CellBox {
  left: number;
  top: number;
  width: number;
  height: number;
}

/** Below this cell size (device pixels) small details would only look like noise. */
const DETAIL_MIN = 16;

/** Paints the ground of cell (x, y). */
export function paintGround(ctx: CanvasRenderingContext2D, c: string, x: number, y: number, box: CellBox): void {
  const { left, top, width: w, height: h } = box;
  const detail = w >= DETAIL_MIN;
  switch (c) {
    case Ground.GRASS:
    case Ground.TREE:
      ctx.fillStyle = noise(x, y) < 0.5 ? ART.grass : ART.grassDark;
      ctx.fillRect(left, top, w, h);
      if (c === Ground.TREE) {
        paintTree(ctx, left + w / 2, top + h / 2, w, noise(x, y, 1));
      } else if (detail && noise(x, y, 2) > 0.72) {
        ctx.fillStyle = ART.grassTuft;
        const tx = left + w * (0.2 + 0.5 * noise(x, y, 3));
        const ty = top + h * (0.2 + 0.5 * noise(x, y, 4));
        ctx.fillRect(tx, ty, Math.max(1, w * 0.06), h * 0.14);
        ctx.fillRect(tx + w * 0.1, ty + h * 0.04, Math.max(1, w * 0.06), h * 0.11);
      }
      return;
    case Ground.BUILDING: {
      ctx.fillStyle = ART.building;
      ctx.fillRect(left, top, w, h);
      const roof = detail ? noise(x, y, 5) : 0.5;
      if (roof > 0.9) {
        ctx.fillStyle = ART.buildingTop; // a rooftop unit
        ctx.fillRect(left + w * 0.25, top + h * 0.3, w * 0.4, h * 0.32);
      } else if (roof < 0.04) {
        ctx.fillStyle = ART.skylight;
        ctx.globalAlpha = 0.32;
        ctx.fillRect(left + w * 0.3, top + h * 0.3, w * 0.4, h * 0.4);
        ctx.globalAlpha = 1;
      }
      return;
    }
    case Ground.WATER: {
      ctx.fillStyle = ART.water;
      ctx.fillRect(left, top, w, h);
      ctx.strokeStyle = ART.waterLight;
      ctx.lineWidth = Math.max(1, w * 0.05);
      ctx.beginPath();
      const wy = top + h * (0.3 + 0.4 * noise(x, y, 6));
      ctx.moveTo(left + w * 0.2, wy);
      ctx.quadraticCurveTo(left + w * 0.35, wy - h * 0.1, left + w * 0.5, wy);
      ctx.quadraticCurveTo(left + w * 0.65, wy + h * 0.1, left + w * 0.8, wy);
      ctx.stroke();
      return;
    }
    case Ground.RAIN:
      ctx.fillStyle = ART.rain;
      ctx.fillRect(left, top, w, h);
      if (detail && noise(x, y, 7) > 0.55) {
        ctx.fillStyle = ART.puddle;
        ctx.beginPath();
        ctx.ellipse(
          left + w * (0.3 + 0.4 * noise(x, y, 8)),
          top + h * (0.3 + 0.4 * noise(x, y, 9)),
          w * 0.22,
          h * 0.12,
          0,
          0,
          Math.PI * 2,
        );
        ctx.fill();
      }
      return;
    case Ground.TUNNEL:
      ctx.fillStyle = ART.tunnel;
      ctx.fillRect(left, top, w, h);
      return;
    case Ground.PARKING:
      ctx.fillStyle = ART.parking;
      ctx.fillRect(left, top, w, h);
      return;
    default:
      ctx.fillStyle = ART.road;
      ctx.fillRect(left, top, w, h);
  }
}

function paintTree(ctx: CanvasRenderingContext2D, cx: number, cy: number, s: number, n: number): void {
  const r = s * (0.38 + n * 0.08);
  ctx.fillStyle = ART.treeShadow;
  ctx.beginPath();
  ctx.arc(cx + s * 0.07, cy + s * 0.09, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = ART.tree;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = ART.treeLight;
  ctx.beginPath();
  ctx.arc(cx - r * 0.25, cy - r * 0.25, r * 0.55, 0, Math.PI * 2);
  ctx.fill();
}

/**
 * Paints the borders of cell (x, y): kerbs where a road meets anything else, concrete walls along
 * tunnels, parking bays, and light edges on building blocks.
 */
export function paintEdges(
  ctx: CanvasRenderingContext2D,
  c: string,
  x: number,
  y: number,
  box: CellBox,
  cell: CellLookup,
): void {
  const { left, top, width: w, height: h } = box;
  if (c === Ground.BUILDING) {
    ctx.fillStyle = ART.buildingEdge;
    paintSides(ctx, box, Math.max(1, w * 0.06), 0, x, y, cell, (n) => n !== Ground.BUILDING && n !== Ground.UNKNOWN);
    return;
  }
  if (!isPaved(c)) {
    return;
  }
  const tunnel = c === Ground.TUNNEL;
  ctx.fillStyle = tunnel ? ART.tunnelWall : ART.kerb;
  const thickness = tunnel ? Math.max(2, w * 0.14) : Math.max(1, w * 0.05);
  paintSides(ctx, box, thickness, tunnel ? 0 : w * 0.05, x, y, cell, (n) => n !== Ground.UNKNOWN && !isPaved(n));
  if (c === Ground.PARKING) {
    ctx.fillStyle = ART.parkingLine;
    const line = Math.max(1, w * 0.05);
    ctx.fillRect(left, top + h * 0.12, line, h * 0.76);
  }
}

/** Paints a band of `thickness` along every side of the box whose neighbour passes `test`. */
function paintSides(
  ctx: CanvasRenderingContext2D,
  box: CellBox,
  thickness: number,
  inset: number,
  x: number,
  y: number,
  cell: CellLookup,
  test: (neighbour: string) => boolean,
): void {
  const { left, top, width: w, height: h } = box;
  if (test(cell(x, y - 1))) {
    ctx.fillRect(left, top + inset, w, thickness);
  }
  if (test(cell(x, y + 1))) {
    ctx.fillRect(left, top + h - inset - thickness, w, thickness);
  }
  if (test(cell(x - 1, y))) {
    ctx.fillRect(left + inset, top, thickness, h);
  }
  if (test(cell(x + 1, y))) {
    ctx.fillRect(left + w - inset - thickness, top, thickness, h);
  }
}

/** A traffic cone seen from above. */
export function paintCone(ctx: CanvasRenderingContext2D, cx: number, cy: number, s: number): void {
  const r = s * 0.19;
  ctx.fillStyle = 'rgba(0, 0, 0, 0.3)';
  ctx.beginPath();
  ctx.arc(cx + r * 0.3, cy + r * 0.4, r * 1.3, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = ART.coneBase;
  ctx.beginPath();
  ctx.roundRect(cx - r * 1.25, cy - r * 1.25, r * 2.5, r * 2.5, r * 0.45);
  ctx.fill();
  ctx.fillStyle = ART.cone;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = '#ffffff';
  ctx.lineWidth = r * 0.32;
  ctx.beginPath();
  ctx.arc(cx, cy, r * 0.58, 0, Math.PI * 2);
  ctx.stroke();
  ctx.fillStyle = '#ffc48a';
  ctx.beginPath();
  ctx.arc(cx, cy, r * 0.2, 0, Math.PI * 2);
  ctx.fill();
}

/** A cup of coffee seen from above, with a soft glow (it's a pick-up). */
export function paintCoffee(ctx: CanvasRenderingContext2D, cx: number, cy: number, s: number, glow: number): void {
  const r = s * 0.18;
  const halo = ctx.createRadialGradient(cx, cy, r * 0.5, cx, cy, r * 2.6);
  halo.addColorStop(0, `rgba(255, 214, 120, ${0.35 + 0.2 * glow})`);
  halo.addColorStop(1, 'rgba(255, 214, 120, 0)');
  ctx.fillStyle = halo;
  ctx.beginPath();
  ctx.arc(cx, cy, r * 2.6, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = ART.cup;
  ctx.lineWidth = r * 0.28;
  ctx.beginPath();
  ctx.arc(cx + r * 1.05, cy, r * 0.42, -Math.PI / 2, Math.PI / 2);
  ctx.stroke();
  ctx.fillStyle = ART.cup;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = ART.coffee;
  ctx.beginPath();
  ctx.arc(cx, cy, r * 0.72, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = 'rgba(255, 236, 210, 0.45)';
  ctx.beginPath();
  ctx.arc(cx - r * 0.22, cy - r * 0.22, r * 0.24, 0, Math.PI * 2);
  ctx.fill();
}

/** A goose seen from above, facing `angle`. */
export function paintGoose(ctx: CanvasRenderingContext2D, cx: number, cy: number, s: number, angle: number): void {
  const l = s * 0.48;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate(angle);
  ctx.fillStyle = 'rgba(0, 0, 0, 0.25)';
  ctx.beginPath();
  ctx.ellipse(l * 0.08, l * 0.1, l * 0.6, l * 0.36, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = ART.goose;
  ctx.strokeStyle = ART.gooseShade;
  ctx.lineWidth = Math.max(1, l * 0.06);
  ctx.beginPath();
  ctx.ellipse(-l * 0.05, 0, l * 0.55, l * 0.32, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(l * 0.3, 0);
  ctx.lineTo(l * 0.72, 0);
  ctx.lineWidth = l * 0.16;
  ctx.strokeStyle = ART.goose;
  ctx.stroke();
  ctx.fillStyle = ART.goose;
  ctx.beginPath();
  ctx.arc(l * 0.76, 0, l * 0.15, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = ART.beak;
  ctx.beginPath();
  ctx.moveTo(l * 0.86, -l * 0.07);
  ctx.lineTo(l * 1.08, 0);
  ctx.lineTo(l * 0.86, l * 0.07);
  ctx.closePath();
  ctx.fill();
  ctx.restore();
}

/** The CEO's red sports car, `cells` tiles long, facing `angle`. */
export function paintCeoCar(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  s: number,
  angle: number,
  cells: number,
): void {
  const l = s * (cells > 1 ? 1.78 : 0.9);
  const w = s * 0.66;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate(angle);
  ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
  ctx.beginPath();
  ctx.roundRect(-l / 2 + s * 0.05, -w / 2 + s * 0.07, l, w, w * 0.4);
  ctx.fill();
  ctx.fillStyle = ART.ceo;
  ctx.strokeStyle = ART.ceoDark;
  ctx.lineWidth = Math.max(1, s * 0.035);
  ctx.beginPath();
  ctx.roundRect(-l / 2, -w / 2, l, w, w * 0.4);
  ctx.fill();
  ctx.stroke();
  ctx.fillStyle = 'rgba(255, 255, 255, 0.85)'; // racing stripes
  ctx.fillRect(-l / 2 + s * 0.04, -w * 0.12, l - s * 0.08, w * 0.07);
  ctx.fillRect(-l / 2 + s * 0.04, w * 0.05, l - s * 0.08, w * 0.07);
  ctx.fillStyle = ART.glass;
  ctx.beginPath();
  ctx.roundRect(l * 0.04, -w * 0.36, l * 0.16, w * 0.72, w * 0.12);
  ctx.fill();
  ctx.beginPath();
  ctx.roundRect(-l * 0.28, -w * 0.32, l * 0.1, w * 0.64, w * 0.1);
  ctx.fill();
  ctx.fillStyle = ART.ceoDark;
  ctx.fillRect(-l / 2 + s * 0.02, -w * 0.42, s * 0.07, w * 0.84); // spoiler
  ctx.fillStyle = '#fff3b0';
  ctx.fillRect(l / 2 - s * 0.06, -w * 0.4, s * 0.05, w * 0.18);
  ctx.fillRect(l / 2 - s * 0.06, w * 0.22, s * 0.05, w * 0.18);
  ctx.restore();
}

/** What the bus shows besides its position. */
export interface BusLook {
  lights: boolean;
  brake: boolean;
  reverse: boolean;
}

/** The shuttle bus seen from above: yellow body, windows, roof hatch, lamps. */
export function paintBus(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  heading: number,
  s: number,
  look: BusLook,
): void {
  const l = s * 1.32;
  const w = s * 0.74;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate(heading);
  ctx.fillStyle = 'rgba(0, 0, 0, 0.38)';
  ctx.beginPath();
  ctx.roundRect(-l / 2 + s * 0.06, -w / 2 + s * 0.09, l, w, w * 0.26);
  ctx.fill();
  ctx.fillStyle = ART.bus;
  ctx.strokeStyle = ART.busDark;
  ctx.lineWidth = Math.max(1, s * 0.035);
  ctx.beginPath();
  ctx.roundRect(-l / 2, -w / 2, l, w, w * 0.26);
  ctx.fill();
  ctx.stroke();
  ctx.fillStyle = ART.glass;
  ctx.beginPath();
  ctx.roundRect(l / 2 - s * 0.27, -w / 2 + s * 0.07, s * 0.17, w - s * 0.14, s * 0.05); // windscreen
  ctx.fill();
  ctx.beginPath();
  ctx.roundRect(-l / 2 + s * 0.05, -w / 2 + s * 0.13, s * 0.07, w - s * 0.26, s * 0.03);
  ctx.fill();
  for (let i = 0; i < 4; i++) {
    const wx = -l / 2 + s * 0.2 + i * s * 0.21;
    ctx.fillRect(wx, -w / 2 + s * 0.035, s * 0.15, s * 0.07);
    ctx.fillRect(wx, w / 2 - s * 0.105, s * 0.15, s * 0.07);
  }
  ctx.fillStyle = ART.busRoof;
  ctx.beginPath();
  ctx.roundRect(-l / 2 + s * 0.17, -w / 2 + s * 0.14, l - s * 0.5, w - s * 0.28, s * 0.06);
  ctx.fill();
  ctx.fillStyle = 'rgba(236, 241, 248, 0.9)';
  ctx.fillRect(-s * 0.16, -s * 0.09, s * 0.22, s * 0.18); // roof hatch
  ctx.fillStyle = look.lights ? '#fffbe0' : '#e8e0b0';
  for (const side of [-1, 1]) {
    ctx.beginPath();
    ctx.arc(l / 2 - s * 0.04, side * (w / 2 - s * 0.11), s * 0.05, 0, Math.PI * 2);
    ctx.fill();
  }
  for (const side of [-1, 1]) {
    ctx.fillStyle = look.brake ? '#ff3030' : '#7e1d1d';
    ctx.fillRect(-l / 2 - s * 0.01, side * (w / 2 - s * 0.12) - s * 0.045, s * 0.05, s * 0.09);
    if (look.brake) {
      ctx.fillStyle = 'rgba(255, 48, 48, 0.35)';
      ctx.beginPath();
      ctx.arc(-l / 2, side * (w / 2 - s * 0.12), s * 0.16, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  if (look.reverse) {
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(-l / 2 - s * 0.01, -s * 0.06, s * 0.04, s * 0.12);
  }
  ctx.restore();
}

/** Headlight beams in front of the bus (drawn under it). */
export function paintBeams(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  heading: number,
  s: number,
  strength: number,
): void {
  const reach = s * 3.4;
  for (const side of [-1, 1]) {
    const ox = cx + Math.cos(heading) * s * 0.62 - Math.sin(heading) * side * s * 0.25;
    const oy = cy + Math.sin(heading) * s * 0.62 + Math.cos(heading) * side * s * 0.25;
    const beam = ctx.createRadialGradient(ox, oy, 0, ox, oy, reach);
    beam.addColorStop(0, `rgba(255, 246, 200, ${0.5 * strength})`);
    beam.addColorStop(1, 'rgba(255, 246, 200, 0)');
    ctx.fillStyle = beam;
    ctx.beginPath();
    ctx.moveTo(ox, oy);
    ctx.arc(ox, oy, reach, heading - 0.32 + side * 0.06, heading + 0.32 + side * 0.06);
    ctx.closePath();
    ctx.fill();
  }
}

/** Sound rings in front of the bus while the horn sounds; `phase` runs from 0 to 1. */
export function paintHonk(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  heading: number,
  s: number,
  phase: number,
): void {
  ctx.save();
  ctx.strokeStyle = '#ffffff';
  ctx.lineCap = 'round';
  for (let i = 0; i < 3; i++) {
    const p = (phase + i / 3) % 1;
    ctx.globalAlpha = (1 - p) * 0.85;
    ctx.lineWidth = Math.max(1.5, s * 0.06);
    ctx.beginPath();
    ctx.arc(cx, cy, s * (0.9 + p * 1.6), heading - 0.6, heading + 0.6);
    ctx.stroke();
  }
  ctx.restore();
}

/** Visits the CEO's cars among the known cells: a pair of neighbouring `E` cells is one car. */
export function forEachCeoCar(
  cells: { x: number; y: number }[],
  cell: CellLookup,
  visit: (cx: number, cy: number, angle: number, length: number) => void,
): void {
  for (const { x, y } of cells) {
    if (cell(x - 1, y) === Ground.CEO_CAR || cell(x, y - 1) === Ground.CEO_CAR) {
      continue; // drawn with its left or upper half
    }
    if (cell(x + 1, y) === Ground.CEO_CAR) {
      visit(x + 1, y + 0.5, 0, 2);
    } else if (cell(x, y + 1) === Ground.CEO_CAR) {
      visit(x + 0.5, y + 1, Math.PI / 2, 2);
    } else {
      visit(x + 0.5, y + 0.5, 0, 1);
    }
  }
}

/** Direction of a flock's line of cells (geese cross along it) and where they wait. */
export function flockLayout(cells: number[][]): { dx: number; dy: number; waitX: number; waitY: number } {
  const vertical = cells.every((c) => c[0] === cells[0][0]) && cells.length > 1;
  const first = cells.reduce(
    (best, c) => (vertical ? (c[1] < best[1] ? c : best) : c[0] < best[0] ? c : best),
    cells[0],
  );
  const dx = vertical ? 0 : 1;
  const dy = vertical ? 1 : 0;
  return { dx, dy, waitX: first[0] + 0.5 - dx * 0.9, waitY: first[1] + 0.5 - dy * 0.9 };
}
