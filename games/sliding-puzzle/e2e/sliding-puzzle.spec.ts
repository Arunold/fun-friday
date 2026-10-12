import { APIRequestContext } from '@playwright/test';

import { expect, joinRoom, peek, setUpRoom, startGame, test, waitForPhase } from '../../../e2e/fixtures';

/** `debugState()` of the game. */
interface PuzzleState {
  size: number;
  start: number[];
  scrambleDistance: number;
  timeLimitSeconds: number;
  players: { id: string; name: string; board: number[]; moves: number; solved: boolean }[];
}

const SIZE = 3;

async function state(request: APIRequestContext, code: string): Promise<PuzzleState> {
  return (await peek<PuzzleState>(request, code)).state!;
}

async function boardOf(request: APIRequestContext, code: string, name: string): Promise<number[]> {
  return (await state(request, code)).players.find((p) => p.name === name)!.board;
}

/** The board after moving `tile` (next to the gap) into the gap. */
function applyMove(cells: readonly number[], tile: number): number[] {
  const next = [...cells];
  const gap = next.indexOf(0);
  const index = next.indexOf(tile);
  next[gap] = tile;
  next[index] = 0;
  return next;
}

/** Cells next to the gap. */
function neighbours(gap: number): number[] {
  const row = Math.floor(gap / SIZE);
  const column = gap % SIZE;
  return [
    row > 0 ? gap - SIZE : -1,
    row < SIZE - 1 ? gap + SIZE : -1,
    column > 0 ? gap - 1 : -1,
    column < SIZE - 1 ? gap + 1 : -1,
  ].filter((cell) => cell >= 0);
}

/** The arrow key that moves `tile` (next to the gap) into the gap: ← for the tile right of it, … */
function arrowFor(cells: readonly number[], tile: number): string {
  const gap = cells.indexOf(0);
  const index = cells.indexOf(tile);
  if (index === gap + 1) {
    return 'ArrowLeft';
  }
  if (index === gap - 1) {
    return 'ArrowRight';
  }
  return index === gap + SIZE ? 'ArrowUp' : 'ArrowDown';
}

/** The tile an arrow key moves into the gap (← the one right of the gap, …), `null` at an edge. */
function tileForKey(cells: readonly number[], key: string): number | null {
  const gap = cells.indexOf(0);
  const row = Math.floor(gap / SIZE);
  const column = gap % SIZE;
  switch (key) {
    case 'ArrowLeft':
      return column < SIZE - 1 ? cells[gap + 1] : null;
    case 'ArrowRight':
      return column > 0 ? cells[gap - 1] : null;
    case 'ArrowUp':
      return row < SIZE - 1 ? cells[gap + SIZE] : null;
    default:
      return row > 0 ? cells[gap - SIZE] : null;
  }
}

/** An optimal solution (IDA* with the Manhattan distance): the tiles to move into the gap. */
function solve(cells: readonly number[]): number[] {
  const board = [...cells];
  let gap = board.indexOf(0);
  const distance = () =>
    board.reduce((sum, tile, i) => {
      if (tile === 0) {
        return sum;
      }
      const home = tile - 1;
      return sum + Math.abs(Math.floor(i / SIZE) - Math.floor(home / SIZE)) + Math.abs((i % SIZE) - (home % SIZE));
    }, 0);
  const path: number[] = [];
  const search = (moves: number, bound: number, previous: number): number | 'found' => {
    const h = distance();
    if (moves + h > bound) {
      return moves + h;
    }
    if (h === 0) {
      return 'found';
    }
    let next = Infinity;
    for (const cell of neighbours(gap)) {
      if (cell === previous) {
        continue;
      }
      const tile = board[cell];
      const from = gap;
      board[from] = tile;
      board[cell] = 0;
      gap = cell;
      path.push(tile);
      const result = search(moves + 1, bound, from);
      if (result === 'found') {
        return result;
      }
      next = Math.min(next, result);
      path.pop();
      board[cell] = tile;
      board[from] = 0;
      gap = from;
    }
    return next;
  };
  for (let bound = distance(); ;) {
    const result = search(0, bound, -1);
    if (result === 'found') {
      return path;
    }
    bound = result;
  }
}

test.describe('Sliding Puzzle', () => {
  test('a full game: same scramble, optimistic solve with clicks and arrow keys, reveal and podium', async ({
    players,
    request,
  }) => {
    const [ann, bob, cleo] = await players('Ann', 'Bob', 'Cleo');
    const code = await setUpRoom([ann, bob], 'sliding-puzzle', { rounds: '1', size: '3×3' });
    await startGame(ann, request, code);

    // Everyone gets the same scramble, 60 s on Auto for a 3×3 board (BUG-05).
    const start = await state(request, code);
    expect(start.timeLimitSeconds).toBe(60);
    expect(start.players.map((p) => p.board)).toEqual([start.start, start.start]);
    await expect(ann.page.getByTestId('tile-8')).toBeVisible();

    // Bob makes a few moves; Ann sees his progress, never his board.
    let bobCells = start.start;
    for (let i = 0; i < 3; i++) {
      const tile = bobCells[neighbours(bobCells.indexOf(0))[0]];
      await bob.page.getByTestId(`tile-${tile}`).click();
      bobCells = applyMove(bobCells, tile);
    }
    await expect(bob.page.getByTestId('moves')).toHaveText('3');
    await expect(ann.page.getByTestId('opponents').locator('[data-player="Bob"]')).toContainText('3 moves');
    await expect.poll(() => boardOf(request, code, 'Bob')).toEqual(bobCells);

    // A latecomer watches: leader board and starting scramble, nothing to click.
    await joinRoom(cleo, code);
    await expect(cleo.page.getByTestId('leaderboard')).toContainText('Bob');
    await expect(cleo.page.getByTestId('start-board')).toBeVisible();
    await expect(cleo.page.getByTestId('board')).toHaveCount(0);

    // Ann solves, alternating clicks and arrow keys; every move shows up at once.
    let annCells = start.start;
    for (const [i, tile] of solve(annCells).entries()) {
      if (i % 2 === 0) {
        await ann.page.getByTestId(`tile-${tile}`).click();
      } else {
        await ann.page.keyboard.press(arrowFor(annCells, tile));
      }
      annCells = applyMove(annCells, tile);
    }
    await expect(ann.page.getByTestId('solved')).toContainText('1st place');
    await expect(ann.page.getByTestId('my-progress')).toHaveText('100%');
    await expect(bob.page.getByTestId('opponents').locator('[data-player="Ann"]')).toContainText('1st');
    await expect(cleo.page.getByTestId('leaderboard').locator('[data-player="Ann"]')).toContainText('1st');

    // Bob solves as well: the round ends early and the reveal lists both.
    for (const tile of solve(bobCells)) {
      await bob.page.getByTestId(`tile-${tile}`).click();
    }
    await expect(bob.page.getByTestId('reveal')).toContainText('Ann solved it first');
    await expect(ann.page.getByTestId('results').locator('[data-player="Bob"]')).toContainText('2nd');

    // Podium: Ann wins.
    await waitForPhase(request, code, 'FINISHED');
    await expect(ann.page.getByTestId('results-headline')).toContainText('You won');
    await expect(bob.page.getByTestId('results-headline')).toContainText('Ann wins');
    await expect(ann.page.locator('ff-results')).toContainText('Speed solver');
  });

  test('arrow keys and WASD move the tile next to the gap into it', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'sliding-puzzle', { rounds: '1', size: '3×3' });
    await startGame(ann, request, code);
    let cells = await boardOf(request, code, 'Ann');
    await expect(ann.page.getByTestId('board')).toBeVisible();

    // ← moves the tile right of the gap to the left (→ the tile left of it).
    let gap = cells.indexOf(0);
    const [key, tile] = gap % SIZE < SIZE - 1 ? ['ArrowLeft', cells[gap + 1]] : ['ArrowRight', cells[gap - 1]];
    await ann.page.keyboard.press(key);
    await expect(ann.page.getByTestId(`tile-${tile}`)).toHaveAttribute('data-cell', String(gap));
    await expect(ann.page.getByTestId('moves')).toHaveText('1');
    cells = applyMove(cells, tile);

    // W moves the tile below the gap up (S the tile above it down).
    gap = cells.indexOf(0);
    const [letter, other] = gap < SIZE * (SIZE - 1) ? ['w', cells[gap + SIZE]] : ['s', cells[gap - SIZE]];
    await ann.page.keyboard.press(letter);
    await expect(ann.page.getByTestId(`tile-${other}`)).toHaveAttribute('data-cell', String(gap));
    cells = applyMove(cells, other);

    // A key toward an edge with no tile beyond the gap does nothing.
    gap = cells.indexOf(0);
    const blocked = gap % SIZE === 0 ? 'ArrowRight' : gap % SIZE === SIZE - 1 ? 'ArrowLeft' : null;
    if (blocked) {
      await ann.page.keyboard.press(blocked);
    }
    await expect(ann.page.getByTestId('moves')).toHaveText('2');
    await expect.poll(() => boardOf(request, code, 'Ann')).toEqual(cells);

    // A fast burst (no waiting for the server): every move shows at once and the server ends up with
    // exactly the same board (IMP-28: optimistic moves).
    let moves = 2;
    const burst = [
      'ArrowLeft',
      'ArrowUp',
      'ArrowLeft',
      'ArrowDown',
      'ArrowRight',
      'ArrowUp',
      'ArrowRight',
      'ArrowDown',
    ];
    for (const next of [...burst, ...burst, ...burst]) {
      const pushed = tileForKey(cells, next);
      await ann.page.keyboard.press(next);
      if (pushed !== null) {
        cells = applyMove(cells, pushed);
        moves++;
      }
      if (cells.every((t, i) => t === (i + 1) % (SIZE * SIZE))) {
        break; // solved by accident: the board locks
      }
    }
    await expect(ann.page.getByTestId('moves')).toHaveText(String(moves));
    await expect.poll(() => boardOf(request, code, 'Ann')).toEqual(cells);
    for (const [cell, t] of cells.entries()) {
      if (t !== 0) {
        await expect(ann.page.getByTestId(`tile-${t}`)).toHaveAttribute('data-cell', String(cell));
      }
    }
  });

  test('a click slides every tile between it and the gap; tiles out of line stay put', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'sliding-puzzle', { rounds: '1', size: '3×3' });
    await startGame(ann, request, code);
    let cells = await boardOf(request, code, 'Ann');
    await expect(ann.page.getByTestId('board')).toBeVisible();

    // From the centre no tile is two cells away: step aside first.
    let moved = 0;
    if (cells.indexOf(0) === 4) {
      const tile = cells[3];
      await ann.page.getByTestId(`tile-${tile}`).click();
      cells = applyMove(cells, tile);
      moved = 1;
      await expect(ann.page.getByTestId('moves')).toHaveText('1');
    }
    const gap = cells.indexOf(0);
    // The cell two steps from the gap: across its row, or (from a middle column) across its column.
    const column = gap % SIZE;
    const far = column !== 1 ? gap - column + (SIZE - 1 - column) : gap < SIZE ? gap + 2 * SIZE : gap - 2 * SIZE;
    const between = (gap + far) / 2;
    const [farTile, middleTile] = [cells[far], cells[between]];

    // A tile in neither the gap's row nor its column only wiggles.
    const outOfLine = cells.findIndex(
      (t, i) => t !== 0 && i % SIZE !== gap % SIZE && Math.floor(i / SIZE) !== Math.floor(gap / SIZE),
    );
    await ann.page.getByTestId(`tile-${cells[outOfLine]}`).click();
    await expect(ann.page.getByTestId('moves')).toHaveText(String(moved));

    await ann.page.getByTestId(`tile-${farTile}`).click();
    await expect(ann.page.getByTestId(`tile-${farTile}`)).toHaveAttribute('data-cell', String(between));
    await expect(ann.page.getByTestId(`tile-${middleTile}`)).toHaveAttribute('data-cell', String(gap));
    await expect(ann.page.getByTestId('moves')).toHaveText(String(moved + 2));
    await expect
      .poll(async () => (await state(request, code)).players.find((p) => p.name === 'Ann')!.moves)
      .toBe(moved + 2);
  });
});
