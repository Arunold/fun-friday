/**
 * The Speed Typing view as sent by `SpeedTypingLogic.view()` (see SpeedTypingView.java).
 * Fields that are `null` on the server are omitted from the JSON, hence optional here.
 */
export type Difficulty = 'SHORT' | 'MEDIUM' | 'LONG';

/** The viewer's own race. `typed` is only ever sent to its owner (restores the input on refresh). */
export interface MyRace {
  typed: string;
  /** Characters typed in total, including corrected ones. */
  keystrokes: number;
  /** Characters that were wrong when typed. */
  errors: number;
  /** Server time of the first keystroke. */
  startedAt?: number;
  /** Correct prefix in percent (100 once finished). */
  progress: number;
  finished: boolean;
  /** Finished only. */
  rank?: number;
  /** Finished or revealed only. */
  wpm?: number;
  /** Finished or revealed only, whole percent rounded down. */
  accuracy?: number;
  /** Finished only: from the start of the round to the finish. */
  timeMs?: number;
  /** Finished below the minimum accuracy: points halved. */
  penalized: boolean;
}

/** A player's lane on the race track. */
export interface Lane {
  playerId: string;
  progress: number;
  finished: boolean;
  /** Finished only. */
  rank?: number;
  /** Finished only. */
  wpm?: number;
}

/** A row of the final table (revealed only). */
export interface ResultRow {
  playerId: string;
  finished: boolean;
  rank?: number;
  progress: number;
  /** Absent if they never typed. */
  wpm?: number;
  /** Absent if they never typed. */
  accuracy?: number;
  /** Finished only. */
  timeMs?: number;
  penalized: boolean;
}

export interface SpeedTypingView {
  text: string;
  difficulty: Difficulty;
  /** Percent; finishers below it get half points. */
  minAccuracy: number;
  /** Server time when the round ends. */
  endsAt: number;
  revealed: boolean;
  /** Absent for spectators. */
  me?: MyRace;
  lanes: Lane[];
  /** Revealed only, best first. */
  results?: ResultRow[];
}

/** Payload of the `progress` action (`SpeedTypingLogic.Progress`). */
export interface ProgressPayload {
  typed: string;
  keystrokes: number;
  errors: number;
}
