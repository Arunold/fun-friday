/**
 * Mirror of the server's `BackseatDriversView` records. `null` fields are omitted from the JSON, so
 * they are optional here.
 */

/** Everything a seat can hold; the navigator is listed as a "control" too. */
export type Control = 'NAVIGATOR' | 'STEER_LEFT' | 'STEER_RIGHT' | 'GAS' | 'BRAKE' | 'HORN' | 'WIPERS' | 'HEADLIGHTS';

/** Order of the server's enum: bit `1 << index` in the frames' pressed masks. */
export const CONTROLS: readonly Control[] = [
  'NAVIGATOR',
  'STEER_LEFT',
  'STEER_RIGHT',
  'GAS',
  'BRAKE',
  'HORN',
  'WIPERS',
  'HEADLIGHTS',
];

export type LevelStatus = 'DRIVING' | 'PARKED' | 'CRASHED' | 'TIME_UP';
export type SignalKind = 'LEFT' | 'RIGHT' | 'STRAIGHT' | 'STOP' | 'HONK' | 'WIPE' | 'LIGHTS';
export type Variation = 'classic' | 'rush-hour' | 'silent-navigator';

/** What the server sends this browser. */
export interface BackseatDriversView {
  /** `LEVEL` while a level runs (and during its reveal), `VOTE` for the MVP vote. */
  mode: 'LEVEL' | 'VOTE';
  variation: Variation;
  shuffleSeconds: number;
  /** Finished levels so far. */
  levels: LevelResult[];
  level?: LevelView;
  vote?: VoteView;
}

export interface LevelResult {
  number: number;
  name: string;
  status: Exclude<LevelStatus, 'DRIVING'>;
  secondsLeft: number;
  coffees: number;
  cones: number;
  score: number;
}

export interface LevelView {
  number: number;
  name: string;
  status: LevelStatus;
  /** Increases with every (re)assignment of the seats. */
  rolesVersion: number;
  /** Navigator first; `FrameView.p` follows this order. */
  seats: SeatView[];
  /** The viewer's controls: empty without a seat, missing for spectators. */
  myControls?: Control[];
  navigator: boolean;
  /** Epoch ms of the next SEAT SHUFFLE (0 once the level is over). */
  nextShuffleAt: number;
  shuffleWarning: boolean;
  frame?: FrameView;
  /** The area around the bus (drivers and spectators while driving). */
  window?: AreaView;
  /** The whole map (the navigator while driving, everyone once the level is over). */
  map?: AreaView;
  /** The bus's path `[x·100, y·100]` once the level is over. */
  trail?: number[][];
  signal?: SignalView;
  result?: LevelResult;
}

export interface SeatView {
  playerId: string;
  controls: Control[];
}

/** One simulation tick (stream event `frame`). */
export interface FrameView {
  /** Level number. */
  l: number;
  /** Tick (restarts every level). */
  t: number;
  /** Bus `[x·1000, y·1000, heading·1000 (rad, 0 = east, π/2 = south), speed·1000 (tiles/s)]`. */
  b: number[];
  /** Pressed-control bits per seat, in seat order. */
  p: number[];
  /** Effect bits, see {@link Effect}. */
  e: number;
  /** Cones hit. */
  c: number;
  /** Coffee cups collected. */
  k: number;
  /** Bit `i` set = flock `i` is crossing. */
  g: number;
  /** Roles version the pressed bits refer to. */
  r: number;
}

/** A rectangle of the map (stream event `window`, or the whole map). */
export interface AreaView {
  l: number;
  v: number;
  x0: number;
  y0: number;
  w: number;
  h: number;
  /** Ground characters per row; `?` = unknown (outside the drivers' view). */
  rows: string[];
  cones: number[][];
  coffees: number[][];
  geese: GooseView[];
}

export interface GooseView {
  i: number;
  cells: number[][];
}

/** A silent-navigator signal (stream event `signal`). */
export interface SignalView {
  id: number;
  kind: SignalKind;
  /** Epoch ms. */
  at: number;
}

export interface VoteView {
  revealed: boolean;
  candidates: string[];
  votesCast: number;
  voters: number;
  voted: string[];
  myVote?: string;
  tally?: Record<string, number>;
  winners?: string[];
}

/** Effect bits of {@link FrameView.e}. */
export const Effect = {
  BLUR: 1,
  DARK: 2,
  HONK: 4,
  LIGHTS: 8,
  BUMP: 16,
  SCRAPE: 32,
  WIPE: 64,
  RAIN: 128,
  TUNNEL: 256,
  GEESE: 512,
  OFFROAD: 1024,
} as const;

/** Length of a simulation tick. */
export const TICK_MS = 50;

/** Map characters (see the README legend). */
export const Ground = {
  GRASS: '.',
  BUILDING: '#',
  TREE: 'T',
  WATER: 'w',
  ROAD: 'r',
  RAIN: '~',
  TUNNEL: 't',
  PARKING: 'P',
  CEO_CAR: 'E',
  UNKNOWN: '?',
} as const;
