/**
 * The Reaction Showdown view as sent by `ReactionShowdownLogic.view()` (see ReactionShowdownView.java).
 * Fields that are `null` on the server are omitted from the JSON, hence optional here.
 */
export type SignalState = 'WAITING' | 'FAKE' | 'GO' | 'RESULT';

/** The decoy colours that may flash before GO (only sent while one is on screen). */
export type FakeOut = 'HOLD' | 'STEADY' | 'FREEZE';

/** The viewer's own reaction to the current signal. */
export interface MyStatus {
  /** Tapped validly after GO. */
  tapped: boolean;
  /** Accepted reaction time (valid taps only). */
  ms?: number;
  /** Jumped the gun on this signal. */
  falseStart: boolean;
  /** That false start was on a fake-out. */
  fake: boolean;
  /** Points from the finished signals of this round. */
  roundPoints: number;
}

/** A player's status for the current signal (times arrive with the signal's result). */
export interface PlayerStatus {
  playerId: string;
  tapped: boolean;
  falseStart: boolean;
}

/** One player's outcome on a finished signal. */
export interface ResultEntry {
  playerId: string;
  /** Valid taps only. */
  ms?: number;
  /** 1-based rank among valid taps; ties share a rank. */
  rank?: number;
  falseStart: boolean;
  fake: boolean;
  /** Negative for a false start. */
  points: number;
}

/** The outcome of one finished signal. */
export interface SignalResult {
  signalId: number;
  /** 1-based number within the round. */
  signal: number;
  /** A decoy flashed before this signal's GO. */
  fakeOut: boolean;
  fastestPlayerId?: string;
  fastestMs?: number;
  /** Valid taps by rank, then misses, then false starts. */
  entries: ResultEntry[];
}

/** One line of the round table (reveal only). */
export interface TableRow {
  playerId: string;
  rank: number;
  bestMs?: number;
  avgMs?: number;
  points: number;
  falseStarts: number;
}

export interface ReactionShowdownView {
  /** 1-based number of the current signal within the round. */
  signal: number;
  signals: number;
  /** Unique id of the current signal; taps quote it. */
  signalId: number;
  state: SignalState;
  /** Only while `state` is FAKE. */
  fake?: FakeOut;
  fakeouts: boolean;
  revealed: boolean;
  /** Absent for spectators. */
  me?: MyStatus;
  players: PlayerStatus[];
  /** Finished signals of this round, oldest first. */
  results: SignalResult[];
  /** Revealed only. */
  table?: TableRow[];
}

/** Payload of the `tap` action: `ms` only when this browser rendered GO before the tap. */
export interface TapPayload {
  signal: number;
  ms?: number;
}
