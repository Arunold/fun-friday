import { APIRequestContext, Locator } from '@playwright/test';

import { Player, expect, peek, setUpRoom, startGame, test, waitForPhase } from '../../../e2e/fixtures';

/** What `MemoryMatchLogic.debugState()` exposes in the e2e profile. */
interface MemoryMatchState {
  faces: string[];
  /** Index pairs of the cards nobody has found yet. */
  pairs: number[][];
  activePlayerId?: string;
  activePlayerName?: string;
  step: 'FIRST' | 'SECOND' | 'RESOLVING' | 'OVER';
}

async function secret(request: APIRequestContext, code: string): Promise<MemoryMatchState> {
  const { state } = await peek<MemoryMatchState>(request, code);
  return state!;
}

function card(player: Player, index: number): Locator {
  return player.page.getByTestId(`card-${index}`);
}

/** Flips two cards: the second click waits until the first card is face up. */
async function flipTwo(player: Player, [first, second]: number[]): Promise<void> {
  await card(player, first).click();
  await expect(card(player, first)).toHaveAttribute('data-state', 'UP');
  await card(player, second).click();
}

test.describe('Memory Match', () => {
  test('a full game: a pair goes again, a miss passes the turn, the board is cleared', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const byName: Record<string, Player> = { Ann: ann, Bob: bob };
    const code = await setUpRoom([ann, bob], 'memory-match', { rounds: '1', board: '4×4' });
    await startGame(ann, request, code);

    // Ann joined first, so she starts; Bob watches her move.
    await expect(ann.page.getByTestId('your-turn')).toBeVisible();
    await expect(bob.page.getByTestId('turn-banner')).toContainText("Ann's turn");
    await expect(bob.page.locator('[data-state="HIDDEN"]')).toHaveCount(16);

    // A pair stays up in Ann's name — and it's her move again.
    let state = await secret(request, code);
    expect(state.activePlayerName).toBe('Ann');
    const pair = state.pairs[0];
    await flipTwo(ann, pair);
    for (const index of pair) {
      await expect(card(bob, index)).toHaveAttribute('data-state', 'MATCHED');
    }
    await expect(ann.page.getByTestId('seat-Ann')).toContainText('1');
    await expect(ann.page.getByTestId('your-turn')).toBeVisible();
    await expect(ann.page.getByTestId('pairs-left')).toContainText('7 / 8');

    // A miss stays visible for a moment, then flips back and Bob moves.
    state = await secret(request, code);
    const miss = [state.pairs[0][0], state.pairs[1][0]];
    await flipTwo(ann, miss);
    await expect(card(bob, miss[1])).toHaveAttribute('data-state', 'UP');
    await expect(bob.page.getByTestId('your-turn')).toBeVisible();
    await expect(ann.page.getByTestId('turn-banner')).toContainText("Bob's turn");
    await expect(card(ann, miss[0])).toHaveAttribute('data-state', 'HIDDEN');
    await expect(card(ann, miss[1])).toHaveAttribute('data-state', 'HIDDEN');

    // Whoever is on the move finds a pair until the board is cleared.
    state = await secret(request, code);
    while (state.pairs.length > 0) {
      const mover = byName[state.activePlayerName!];
      const remaining = state.pairs.length;
      await flipTwo(mover, state.pairs[0]);
      if (remaining === 1) {
        break;
      }
      await expect.poll(async () => (state = await secret(request, code)).pairs.length).toBe(remaining - 1);
    }

    await expect(bob.page.getByTestId('reveal')).toContainText('Board cleared! Bob found the most pairs (7)');
    await waitForPhase(request, code, 'FINISHED');
    await expect(bob.page.getByTestId('results-headline')).toContainText('You won');
    await expect(ann.page.getByTestId('results-headline')).toContainText('Bob wins');
    await expect(ann.page.locator('ff-results')).toContainText('Pair collector');
  });

  test('a player who is not on the move cannot flip', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'memory-match', { rounds: '1', board: '4×4' });
    await startGame(ann, request, code);
    await expect(bob.page.getByTestId('turn-banner')).toContainText("Ann's turn");

    await expect(card(bob, 0)).toHaveAttribute('aria-disabled', 'true');
    await card(bob, 0).click({ force: true });

    await expect(bob.page.getByTestId('turn-hint')).toContainText('Wait for your turn');
    await expect(card(bob, 0)).toHaveAttribute('data-state', 'HIDDEN');
    await expect(card(ann, 0)).toHaveAttribute('data-state', 'HIDDEN');
    const state = await secret(request, code);
    expect(state.step).toBe('FIRST');
    expect(state.activePlayerName).toBe('Ann');
  });

  test('cards can be flipped with the keyboard', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'memory-match', { rounds: '1', board: '4×4' });
    await startGame(ann, request, code);
    await expect(ann.page.getByTestId('your-turn')).toBeVisible();

    // Arrow keys move across the 4×4 grid; Enter flips the focused card.
    await card(ann, 0).focus();
    await ann.page.keyboard.press('ArrowRight');
    await ann.page.keyboard.press('ArrowDown');
    await expect(card(ann, 5)).toBeFocused();
    await ann.page.keyboard.press('Enter');

    await expect(card(bob, 5)).toHaveAttribute('data-state', 'UP');
    await expect(ann.page.getByTestId('turn-hint')).toContainText('Now find its twin');
  });
});
