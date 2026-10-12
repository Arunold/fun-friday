/**
 * Pure board helpers for the client (they mirror Board.java), plus the reconciliation of
 * optimistic local moves with the server's board.
 *
 * Boards are cells in reading order; tiles are numbered 1 … n²-1 and 0 is the gap.
 */

export type Cells = readonly number[];

/** Direction a tile moves: `left` moves the tile right of the gap into it, and so on. */
export type Direction = 'left' | 'right' | 'up' | 'down';

export const GAP = 0;

/**
 * The single-tile moves of a push on `tile` (each one cell into the gap, in the order they
 * happen), or `null` when the tile isn't in the gap's row or column.
 */
export function pushPath(cells: Cells, size: number, tile: number): number[] | null {
  const index = cells.indexOf(tile);
  const gap = cells.indexOf(GAP);
  if (tile === GAP || index < 0 || gap < 0) {
    return null;
  }
  const sameRow = Math.floor(index / size) === Math.floor(gap / size);
  const sameColumn = index % size === gap % size;
  if (!sameRow && !sameColumn) {
    return null;
  }
  const step = sameRow ? Math.sign(index - gap) : Math.sign(index - gap) * size;
  const path: number[] = [];
  for (let cell = gap + step; ; cell += step) {
    path.push(cells[cell]);
    if (cell === index) {
      return path;
    }
  }
}

/** Applies single-tile moves; `null` when a tile isn't next to the gap at its turn. */
export function applyMoves(cells: Cells, size: number, tiles: readonly number[]): number[] | null {
  const next = [...cells];
  let gap = next.indexOf(GAP);
  for (const tile of tiles) {
    const index = next.indexOf(tile);
    if (index < 0 || tile === GAP || !adjacent(index, gap, size)) {
      return null;
    }
    next[gap] = tile;
    next[index] = GAP;
    gap = index;
  }
  return next;
}

function adjacent(a: number, b: number, size: number): boolean {
  return Math.abs(Math.floor(a / size) - Math.floor(b / size)) + Math.abs((a % size) - (b % size)) === 1;
}

/**
 * The tile an arrow key moves into the gap: ← moves the tile right of the gap to the left, ↑ the
 * tile below the gap up, and so on. `null` when there is no such tile (the gap is at that edge).
 */
export function tileForDirection(cells: Cells, size: number, direction: Direction): number | null {
  const gap = cells.indexOf(GAP);
  const row = Math.floor(gap / size);
  const column = gap % size;
  switch (direction) {
    case 'left':
      return column < size - 1 ? cells[gap + 1] : null;
    case 'right':
      return column > 0 ? cells[gap - 1] : null;
    case 'up':
      return row < size - 1 ? cells[gap + size] : null;
    case 'down':
      return row > 0 ? cells[gap - size] : null;
  }
}

/** Tiles at their home cell (the gap doesn't count). */
export function tilesInPlace(cells: Cells): number {
  return cells.reduce((count, tile, index) => (tile === index + 1 ? count + 1 : count), 0);
}

export function isSolved(cells: Cells): boolean {
  return cells.length > 1 && tilesInPlace(cells) === cells.length - 1;
}

/**
 * What the player sees: the last server board (`base`, with the server's move count) plus the
 * moves made locally since then that no server view contains yet.
 */
export interface LocalBoard {
  round: number;
  base: Cells;
  baseMoves: number;
  /** Single-tile moves applied optimistically on top of `base`. */
  pending: readonly number[];
  cells: Cells;
}

/** A local board that equals the server's. */
export function adopt(round: number, board: Cells, moves: number): LocalBoard {
  return { round, base: board, baseMoves: moves, pending: [], cells: board };
}

/**
 * Merges a server view into the local board (IMP-28: optimistic moves).
 *
 * The server counts every tile it moves, so `moves` tells how many of our moves the view already
 * contains. The rest are replayed on top of the server's board; when that fails (a move was
 * rejected or the boards diverged) the server's board wins. Unchanged cells keep their array, so
 * nothing re-renders.
 */
export function reconcile(
  previous: LocalBoard | null,
  round: number,
  size: number,
  board: Cells,
  moves: number,
  forceAdopt: boolean,
): LocalBoard {
  if (!previous || forceAdopt || previous.round !== round) {
    return adopt(round, board, moves);
  }
  const unconfirmed = previous.baseMoves + previous.pending.length - moves;
  if (unconfirmed <= 0) {
    return adopt(round, sameCells(previous.cells, board) ? previous.cells : board, moves);
  }
  if (unconfirmed > previous.pending.length) {
    return previous; // older than the board we already have
  }
  const pending = previous.pending.slice(previous.pending.length - unconfirmed);
  const cells = applyMoves(board, size, pending);
  if (!cells) {
    return adopt(round, board, moves);
  }
  return {
    round,
    base: board,
    baseMoves: moves,
    pending,
    cells: sameCells(previous.cells, cells) ? previous.cells : cells,
  };
}

function sameCells(a: Cells, b: Cells): boolean {
  return a.length === b.length && a.every((tile, index) => tile === b[index]);
}

/** `42.3 s` under a minute, `2:05` above (like the server's headline). */
export function formatSolveTime(ms: number): string {
  if (ms < 60_000) {
    return `${(ms / 1000).toFixed(1)} s`;
  }
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/** 1st, 2nd, 3rd, 4th … */
export function ordinal(rank: number): string {
  const tens = rank % 100;
  if (tens >= 11 && tens <= 13) {
    return `${rank}th`;
  }
  switch (rank % 10) {
    case 1:
      return `${rank}st`;
    case 2:
      return `${rank}nd`;
    case 3:
      return `${rank}rd`;
    default:
      return `${rank}th`;
  }
}
