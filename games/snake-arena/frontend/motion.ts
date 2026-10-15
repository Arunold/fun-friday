import { Bounds, Dir, SnakeFrame } from './snake-arena.types';

/** A point in cell units; cell centres sit at `+0.5`. */
export interface Point {
  x: number;
  y: number;
}

/**
 * A snake ready to draw: a path of points (head first) and the fractional distances of the head and
 * the tail along it, plus where its eyes look. Between two frames the head glides forward along the
 * new cells while the tail retracts along the old ones (a growing snake's tail stays put); past the
 * newest frame the head runs on along the moves the snake is expected to make.
 */
export interface Pose {
  points: readonly Point[];
  head: number;
  tail: number;
  look: Dir;
}

/** One cell step per heading. */
export const VECTORS: Readonly<Record<Dir, Point>> = {
  UP: { x: 0, y: -1 },
  DOWN: { x: 0, y: 1 },
  LEFT: { x: -1, y: 0 },
  RIGHT: { x: 1, y: 0 },
};

/** Everything {@link poseAt} needs to place one snake at a moment. */
export interface Motion {
  cols: number;
  /** The snake in the frame before (`undefined`: it wasn't on the board). */
  previous: SnakeFrame | undefined;
  previousTick: number;
  current: SnakeFrame;
  currentTick: number;
  /** The moment to draw, in (fractional) ticks of the round. */
  at: number;
  /** Headings of the moves after the current frame, one per cell (empty: the snake stays put). */
  ahead: readonly Dir[];
  /** The last tick of a boost (two cells per tick up to and including it; `-1`: no boost). */
  boostUntil: number;
  /** Cells with food: a snake that eats there grows instead of pulling its tail along. */
  food: ReadonlySet<number>;
  /** The walls: a head running on ahead of the newest frame stops in front of them. */
  bounds?: Bounds;
  /** Where the eyes look (default: where the head is going). */
  look?: Dir;
}

const centres = new WeakMap<readonly number[], readonly Point[]>();

/** The centre of a cell. */
export function cellCenter(cell: number, cols: number): Point {
  return { x: (cell % cols) + 0.5, y: Math.floor(cell / cols) + 0.5 };
}

/**
 * How many cells a snake moves from the frame of tick `from` to moment `at` (fractional ticks):
 * two per tick up to and including tick `boostUntil`, one after.
 */
export function cellsMoved(from: number, at: number, boostUntil: number): number {
  const ticks = Math.max(0, at - from);
  return ticks + Math.max(0, Math.min(ticks, boostUntil - from));
}

/** The centres of a body's cells (cached per frame). */
function centresOf(body: readonly number[], cols: number): readonly Point[] {
  let points = centres.get(body);
  if (!points) {
    points = body.map((cell) => cellCenter(cell, cols));
    centres.set(body, points);
  }
  return points;
}

/** Places a snake at moment `m.at`: between its two newest frames, or ahead of the newest one. */
export function poseAt(m: Motion): Pose {
  const body = m.current.body;
  const points = centresOf(body, m.cols);
  if (m.at <= m.currentTick || !m.ahead.length) {
    const span = m.currentTick - m.previousTick;
    const progress = m.previous && span > 0 ? Math.min(1, Math.max(0, (m.at - m.previousTick) / span)) : 1;
    return {
      ...between(m.previous ? centresOf(m.previous.body, m.cols) : null, points, progress),
      look: m.look ?? m.current.dir,
    };
  }
  // Ahead of the newest frame: the head runs on along the expected moves — never into a wall, the
  // crash is the server's to tell — and the tail follows, except over food (the snake will grow).
  const path: Point[] = [];
  let point = points[0];
  const wanted = Math.min(m.ahead.length, cellsMoved(m.currentTick, m.at, m.boostUntil));
  for (let i = 0; i < Math.ceil(wanted - 1e-6); i++) {
    const step = VECTORS[m.ahead[i]];
    const next = { x: point.x + step.x, y: point.y + step.y };
    if (m.bounds && !inside(next, m.bounds)) {
      break;
    }
    point = next;
    path.unshift(point);
  }
  const cells = Math.min(wanted, path.length);
  const steps = path.length;
  let retract = 0;
  for (let i = 0; i < steps; i++) {
    const at = path[steps - 1 - i];
    const x = at.x - 0.5;
    const eats = x >= 0 && x < m.cols && m.food.has((at.y - 0.5) * m.cols + x);
    retract += eats ? 0 : Math.min(1, cells - i);
  }
  const head = steps - cells;
  const tail = steps + Math.max(0, body.length - 1 - retract);
  return { points: path.concat(points), head, tail: Math.max(head, tail), look: m.look ?? m.ahead[0] };
}

/**
 * The same pose with its head moved by (`dx`, `dy`) cells and joined straight to the body at the
 * first corner behind it — how a correction is eased out instead of making the head jump.
 */
export function shiftHead(pose: Pose, dx: number, dy: number): Pose {
  const from = pointAt(pose.points, pose.head);
  const joint = Math.min(pose.points.length - 1, Math.floor(pose.head + 1e-6) + 1);
  if (joint <= pose.head || pose.tail <= pose.head) {
    return pose;
  }
  const head = { x: from.x + dx, y: from.y + dy };
  return {
    points: [head, ...pose.points.slice(joint)],
    head: 0,
    tail: Math.max(0, 1 + pose.tail - joint),
    look: pose.look,
  };
}

/** Whether a cell centre lies inside the walls. */
function inside(point: Point, bounds: Bounds): boolean {
  const x = point.x - 0.5;
  const y = point.y - 0.5;
  return x >= bounds.x0 && x <= bounds.x1 && y >= bounds.y0 && y <= bounds.y1;
}

/** Interpolates a body from `previous` (progress 0) to `current` (progress 1). */
function between(previous: readonly Point[] | null, current: readonly Point[], progress: number): Omit<Pose, 'look'> {
  const still = { points: current, head: 0, tail: Math.max(0, current.length - 1) };
  if (!previous?.length || !current.length) {
    return still;
  }
  // The old head is where the new body continues: 0 (no move), 1 or 2 (boost) cells back.
  const moved = current.findIndex((point) => point.x === previous[0].x && point.y === previous[0].y);
  if (moved < 0 || moved > 4) {
    return still; // respawned or teleported
  }
  const points = moved === 0 ? previous : current.slice(0, moved).concat(previous);
  const head = (1 - progress) * moved;
  const tail = previous.length - 1 + moved + (current.length - 1 - (previous.length - 1 + moved)) * progress;
  return { points, head, tail: Math.max(head, tail) };
}

/** The point at distance `d` along a path. */
export function pointAt(points: readonly Point[], d: number): Point {
  const last = points.length - 1;
  const clamped = Math.min(last, Math.max(0, d));
  const i = Math.floor(clamped);
  const f = clamped - i;
  const a = points[i];
  const b = points[Math.min(last, i + 1)];
  return { x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f };
}
