/**
 * The Pictionary view as sent by `PictionaryLogic.view()` (see PictionaryView.java), plus the
 * drawing events of the room stream. Fields that are `null` on the server are omitted from the
 * JSON (or arrive as `null`), hence optional here.
 */

export type Tool = 'pen' | 'eraser' | 'fill';

/** A stroke (`x1, y1, x2, y2, …` on the logical 1000×750 canvas) or a fill (one point). */
export interface OpView {
  id: number;
  tool: Tool;
  color: string;
  /** Brush diameter in logical pixels (0 for fills). */
  size: number;
  points: number[];
}

/** The authoritative drawing log of a turn. */
export interface DrawingView {
  turn: number;
  /** Increases with every change; events carry the version they produce. */
  version: number;
  /** Steps that can be undone / redone. */
  undo: number;
  redo: number;
  ops: OpView[];
}

/** Stream event `stroke`: a batch that starts or continues stroke `op.id`. */
export interface StrokeEvent {
  turn: number;
  version: number;
  undo: number;
  redo: number;
  op: OpView;
}

/** Stream event `edit` (fill, undo, redo, clear): reset, then remove, then add. */
export interface EditEvent {
  turn: number;
  version: number;
  undo: number;
  redo: number;
  reset?: boolean | null;
  remove?: number | null;
  add?: OpView[] | null;
}

/** Private event `close`: the guess was nearly right. */
export interface CloseEvent {
  text: string;
}

export type WordLevel = 'EASY' | 'MEDIUM' | 'HARD';

export interface ChoiceView {
  word: string;
  level: WordLevel;
}

/** GUESS: wrong (public) · CLOSE: near miss (only its author sees it) · CORRECT: never shows the word. */
export type FeedKind = 'GUESS' | 'CLOSE' | 'CORRECT';

export interface FeedEntry {
  id: number;
  playerId: string;
  name: string;
  kind: FeedKind;
  text?: string | null;
}

export interface CorrectGuess {
  playerId: string;
  name: string;
  /** Milliseconds from the start of the drawing phase. */
  ms: number;
  /** Revealed only: points awarded (0 when the turn was voided). */
  points?: number | null;
}

export type EndReason = 'ALL_GUESSED' | 'TIME_UP' | 'DRAWER_LEFT' | 'DRAWER_OFFLINE' | 'NO_GUESSERS';

export interface PictionaryView {
  /** Sequential turn number in the game (also on every drawing event). */
  turn: number;
  phase: 'CHOOSING' | 'DRAWING';
  revealed: boolean;
  drawerId: string;
  drawerName: string;
  /** Drawer only, while choosing. */
  choices?: ChoiceView[] | null;
  /** Drawer, players who guessed it, everybody once revealed. */
  word?: string | null;
  /** While drawing: hidden letters as `_`, spaces and hyphens kept. */
  mask?: string | null;
  /** Guessers only (absent for the drawer and spectators). */
  guessed?: boolean | null;
  guessesLeft?: number | null;
  feed: FeedEntry[];
  correct: CorrectGuess[];
  /** Players who guess this turn. */
  guessers: number;
  drawing: DrawingView;
  endReason?: EndReason | null;
  drawerPoints?: number | null;
}
