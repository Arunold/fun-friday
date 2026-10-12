/**
 * The Memory Match view as sent by `MemoryMatchLogic.view()` (see MemoryMatchView.java).
 * Fields that are `null` on the server are omitted from the JSON, hence optional here.
 */
export type CardState = 'HIDDEN' | 'UP' | 'MATCHED';

export interface CardView {
  /** Position on the board: row × cols + column. */
  index: number;
  state: CardState;
  /** The face — only for UP and MATCHED cards (face-down cards never carry it). */
  emoji?: string;
  /** Who found the pair (MATCHED cards only). */
  matchedBy?: string;
}

export interface PlayerProgress {
  playerId: string;
  /** Pairs found this round. */
  pairs: number;
  /** Points from pairs this round (the most-pairs bonus is added at the reveal). */
  points: number;
  /** After the reveal: whether they found the most pairs this round. */
  mostPairs?: boolean;
}

/** MATCH: the player goes again; TIMEOUT: no flip in time; AWAY: disconnected or left mid-move. */
export type MoveKind = 'MATCH' | 'MISS' | 'TIMEOUT' | 'AWAY';

export interface MoveResult {
  /** Grows with every result, to tell a new result from a re-sent one. */
  seq: number;
  kind: MoveKind;
  playerId: string;
  cards: number[];
  /** Points earned (MATCH only). */
  points: number;
  /** Pairs in a row including this one (MATCH only). */
  streak: number;
}

export interface MemoryMatchView {
  rows: number;
  cols: number;
  cards: CardView[];
  /** Whose move it is; absent once the round is over. */
  activePlayerId?: string;
  /** Server time (epoch ms) when the current move times out; absent while a miss is shown. */
  moveDeadline?: number;
  moveTimeMs: number;
  /** A miss is being shown before the cards flip back (no flips allowed). */
  resolving: boolean;
  pairsLeft: number;
  pairsTotal: number;
  /** Pairs the active player found in a row on this move streak. */
  streak: number;
  /** The round's order of play. */
  players: PlayerProgress[];
  /** Latest move result; absent at the start of a round. */
  last?: MoveResult;
  /** The round is over and every card is shown. */
  revealed: boolean;
}
