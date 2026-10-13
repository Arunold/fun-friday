import type { APIRequestContext } from '@playwright/test';

import { expect, joinRoom, peek, setUpRoom, startGame, test, waitForPhase } from '../../../e2e/fixtures';

type Dir = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT';

/** `SnakeArenaLogic.debugState()`. */
interface SnakeArenaState {
  round: number;
  tick: number;
  cols: number;
  rows: number;
  snakes: { id: string; name: string; alive: boolean; dir: Dir; x?: number; y?: number; length: number }[];
}

async function arena(request: APIRequestContext, code: string): Promise<SnakeArenaState> {
  return (await peek<SnakeArenaState>(request, code)).state!;
}

async function snakeOf(request: APIRequestContext, code: string, name: string) {
  return (await arena(request, code)).snakes.find((snake) => snake.name === name)!;
}

test.describe('Snake Arena', () => {
  test('arrow keys and WASD steer; everyone sees the arena and the leaderboard', async ({ players, request }) => {
    const [ann, bob, watcher] = await players('Ann', 'Bob', 'Watcher');
    const code = await setUpRoom([ann, bob], 'snake-arena', { rounds: '1' });
    await joinRoom(watcher, code, { spectator: true });
    await startGame(ann, request, code);

    for (const person of [ann, bob, watcher]) {
      await expect(person.page.getByTestId('arena-canvas')).toBeVisible();
      const leaderboard = person.page.getByTestId('leaderboard');
      await expect(leaderboard.locator('[data-player="Ann"]')).toBeVisible();
      await expect(leaderboard.locator('[data-player="Bob"]')).toBeVisible();
    }
    await expect(watcher.page.locator('ff-snake-panel .controls')).toHaveCount(0);
    await expect(ann.page.locator('ff-snake-panel .controls')).toBeVisible();

    // Both start heading across the arena (Ann → right, Bob → left); up is a valid turn for both.
    expect((await snakeOf(request, code, 'Ann')).dir).toBe('RIGHT');
    expect((await snakeOf(request, code, 'Bob')).dir).toBe('LEFT');
    await ann.page.keyboard.press('ArrowUp');
    await bob.page.keyboard.press('w');

    await expect.poll(async () => (await snakeOf(request, code, 'Ann')).dir).toBe('UP');
    await expect.poll(async () => (await snakeOf(request, code, 'Bob')).dir).toBe('UP');
    await expect(ann.page.getByTestId('arena-canvas')).toHaveAttribute('data-alive', /\d/);
  });

  test('a full game: last snake standing ends when Ann hits a wall and Bob takes the podium', async ({
    players,
    request,
  }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'snake-arena', {
      rounds: '1',
      variation: 'Last snake standing',
      arena: 'Large',
    });
    await startGame(ann, request, code);
    await expect(ann.page.getByTestId('arena-canvas')).toBeVisible();
    await expect(ann.page.getByTestId('leaderboard').locator('[data-player="Ann"]')).toBeVisible();

    // Turn Ann towards the nearest wall across her heading (Bob needs seconds to reach his).
    const state = await arena(request, code);
    const me = state.snakes.find((snake) => snake.name === 'Ann')!;
    const across =
      me.dir === 'LEFT' || me.dir === 'RIGHT'
        ? me.y! < state.rows / 2
          ? 'ArrowUp'
          : 'ArrowDown'
        : me.x! < state.cols / 2
          ? 'ArrowLeft'
          : 'ArrowRight';
    await ann.page.keyboard.press(across);

    // Ann crashes: only Bob is left, so the round ends at once and the reveal names him.
    await expect(bob.page.getByTestId('reveal')).toContainText('Bob survived!');
    await expect(ann.page.getByTestId('reveal')).toContainText('Bob survived!');
    await expect(ann.page.getByTestId('round-results')).toContainText('Bob');
    expect((await snakeOf(request, code, 'Ann')).alive).toBe(false);

    await waitForPhase(request, code, 'FINISHED');
    await expect(bob.page.getByTestId('results-headline')).toContainText('You won');
    await expect(ann.page.getByTestId('results-headline')).toContainText('Bob wins');
    await expect(ann.page.locator('.place[data-rank="1"]')).toContainText('Bob');
    await expect(ann.page.locator('ff-results')).toContainText('Survivor');
  });
});
