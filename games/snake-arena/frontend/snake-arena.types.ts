/**
 * The Snake Arena view as sent by `SnakeArenaLogic.view()` (see SnakeArenaView.java) and the
 * `frame` stream event. Fields that are `null` on the server are omitted from the JSON, hence
 * optional here. Cells are numbered `y * cols + x`; times are ticks of `tickMs` since the round
 * started.
 */
export type Dir = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT';
export type PowerType = 'BOOST' | 'GHOST' | 'MAGNET';
export type ArenaSize = 'SMALL' | 'MEDIUM' | 'LARGE';
export type Variation = 'classic' | 'shrinking' | 'last-standing';
/** BODY: `b` ran into `a` (who earned +5) · HEAD: head-on · WALL · SELF · LEFT: `a` left the game. */
export type FeedKind = 'BODY' | 'HEAD' | 'WALL' | 'SELF' | 'LEFT';

export interface SnakeFrame {
  /** The player's id. */
  id: string;
  /** Cells head first; empty while crashed. */
  body: number[];
  dir: Dir;
  alive: boolean;
  /** Tick of the respawn (crashed snakes that come back). */
  respawnAt?: number;
  power?: PowerType;
  /** The last tick of the power-up. */
  powerUntil?: number;
  /** Round points so far. */
  points: number;
  length: number;
}

export interface PowerUpFrame {
  cell: number;
  type: PowerType;
  /** The tick at which it vanishes. */
  until: number;
}

/** The playable rectangle (inclusive); everything outside is wall. */
export interface Bounds {
  x0: number;
  y0: number;
  x1: number;
  y1: number;
}

export interface FeedEntry {
  id: number;
  kind: FeedKind;
  a: string;
  b?: string;
}

/** The arena after a tick: streamed as `frame` on every tick and included in the view. */
export interface Frame {
  round: number;
  tick: number;
  snakes: SnakeFrame[];
  food: number[];
  powerUps: PowerUpFrame[];
  bounds: Bounds;
  /** The last crashes, oldest first. */
  feed: FeedEntry[];
}

/** Shrinking arena: the walls move in at `from`, `from + every`, … down to `minWidth` × `minHeight`. */
export interface ShrinkSchedule {
  from: number;
  every: number;
  minWidth: number;
  minHeight: number;
}

export interface RosterEntry {
  id: string;
  name: string;
  color: string;
}

export interface ResultRow {
  playerId: string;
  points: number;
  food: number;
  credits: number;
  crashes: number;
  maxLength: number;
  longest: boolean;
  survivor: boolean;
}

export interface SnakeArenaView {
  cols: number;
  rows: number;
  arena: ArenaSize;
  variation: Variation;
  powers: boolean;
  tickMs: number;
  /** Ticks at the start of a round during which nothing moves. */
  startDelay: number;
  /** Ticks from a crash to the respawn; 0 when snakes don't come back. */
  respawnTicks: number;
  shrink?: ShrinkSchedule;
  /** Everyone who started the round (names stay known after someone leaves). */
  roster: RosterEntry[];
  frame: Frame;
  /** Revealed only, best first. */
  results?: ResultRow[];
}
