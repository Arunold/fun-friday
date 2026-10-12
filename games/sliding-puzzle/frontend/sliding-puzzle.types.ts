/**
 * The Sliding Puzzle view as sent by `SlidingPuzzleLogic.view()` (see SlidingPuzzleView.java).
 * Fields that are `null` on the server are omitted from the JSON, hence optional here.
 */

/** A player's progress — never their board. */
export interface PlayerStatus {
  playerId: string;
  /** Tiles at their home cell. */
  inPlace: number;
  /** `inPlace` as a percentage of all tiles. */
  progress: number;
  /** Tiles moved (a push that slides three tiles counts three). */
  moves: number;
  finished: boolean;
  /** Finishing position (solvers only). */
  rank?: number;
  /** Solve time in ms (solvers only). */
  timeMs?: number;
}

export interface SlidingPuzzleView {
  /** A new round number means a new puzzle. */
  round: number;
  /** Cells per row: 3, 4 or 5. */
  size: number;
  revealed: boolean;
  /** The round's starting scramble in reading order, 0 = gap (the same for everyone). */
  start: number[];
  /** My board (absent for spectators). */
  board?: number[];
  /** My moves (absent for spectators). */
  moves?: number;
  finished?: boolean;
  rank?: number;
  timeMs?: number;
  /** Everyone's progress, in join order. */
  players: PlayerStatus[];
}
