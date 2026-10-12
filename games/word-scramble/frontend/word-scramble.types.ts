/**
 * The Word Scramble view as sent by `WordScrambleLogic.view()` (see WordScrambleView.java).
 * Fields that are `null` on the server are omitted from the JSON, hence optional here.
 */

/** A letter revealed by a hint, in its place. */
export interface HintLetter {
  /** Index in the word, from 0. */
  position: number;
  letter: string;
}

/** The viewer's own progress (players only). */
export interface MyStatus {
  solved: boolean;
  /** Solve order, 1 = first (solved only). */
  rank?: number;
  /** Time from the round start to the solve (solved only). */
  timeMillis?: number;
  misses: number;
  /** The latest wrong guesses, most recent first. */
  recentMisses: string[];
  hintsUsed: number;
  hints: HintLetter[];
}

/** A player's progress as everybody sees it — never their guesses. */
export interface PlayerStatus {
  playerId: string;
  solved: boolean;
  rank?: number;
  timeMillis?: number;
  /** Revealed only. */
  hintsUsed?: number;
  /** Revealed only. */
  misses?: number;
}

export interface WordScrambleView {
  /** The scrambled letters everyone sees. */
  scramble: string;
  length: number;
  revealed: boolean;
  /** The answer — revealed only. */
  word?: string;
  /** Hints a player may take this round (0 when hints are off). */
  maxHints: number;
  /** Wrong guesses allowed per player this round. */
  maxMisses: number;
  /** Absent for spectators. */
  me?: MyStatus;
  players: PlayerStatus[];
}
