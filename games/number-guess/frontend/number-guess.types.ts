/**
 * The Number Guess view as sent by `NumberGuessLogic.view()` (see NumberGuessView.java).
 * Fields that are `null` on the server are omitted from the JSON, hence optional here.
 */
export type Hint = 'HIGHER' | 'LOWER' | 'CORRECT';

export interface GuessView {
  value: number;
  /** HIGHER: the secret number is higher than this guess. */
  hint: Hint;
}

export interface PlayerStatus {
  playerId: string;
  guessesUsed: number;
  /** Exact hit or out of guesses. */
  done: boolean;
  /** Revealed only: whether they hit the number. */
  exact?: boolean;
  /** Revealed only: every guess with its hint. */
  guesses?: GuessView[];
  /** Revealed only: their closest guess. */
  best?: number;
}

export interface NumberGuessView {
  min: number;
  max: number;
  attempts: number;
  revealed: boolean;
  /** Revealed only. */
  target?: number;
  /** Absent for spectators. */
  myGuesses?: GuessView[];
  myStatus?: 'GUESSING' | 'EXACT' | 'OUT_OF_GUESSES';
  players: PlayerStatus[];
}
