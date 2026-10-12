import { expect, joinRoom, peek, setUpRoom, startGame, test, waitForPhase } from '../../../e2e/fixtures';
import type { APIRequestContext, Page } from '@playwright/test';

interface WordScrambleState {
  word: string;
  scramble: string;
}

async function guess(page: Page, text: string): Promise<void> {
  await page.getByTestId('guess-input').fill(text);
  await page.getByTestId('guess-input').press('Enter');
}

/** Waits until the given round is being played and returns its secret word. */
async function roundState(request: APIRequestContext, code: string, round: number): Promise<WordScrambleState> {
  await expect
    .poll(async () => {
      const room = await peek(request, code);
      return room.phase === 'PLAYING' ? room.round : -1;
    })
    .toBe(round);
  return (await peek<WordScrambleState>(request, code)).state!;
}

test.describe('Word Scramble', () => {
  test('a full game: a miss, solves in order, reveal and podium', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'word-scramble', { rounds: '3' });
    await startGame(ann, request, code);

    // Round 1: Ann misses once, then solves first; Bob solves second.
    let state = await roundState(request, code, 1);
    await expect(ann.page.getByTestId('tiles').locator('.tile')).toHaveCount(state.word.length);
    await guess(ann.page, state.scramble);
    await expect(ann.page.getByTestId('feedback')).toContainText('Not quite');
    await expect(ann.page.getByTestId('misses')).toContainText(state.scramble);
    await expect(bob.page.getByTestId('misses')).toHaveCount(0);

    await guess(ann.page, state.word.toLowerCase());
    await expect(ann.page.getByTestId('solved')).toContainText('Solved! #1');
    await expect(bob.page.getByTestId('solved-chip')).toContainText('Ann');
    await expect(bob.page.getByTestId('guess-input')).toBeEnabled();

    await guess(bob.page, state.word);
    await expect(bob.page.getByTestId('reveal')).toContainText(`The word was ${state.word}`);
    await expect(ann.page.getByTestId('results')).toContainText(/🥇[\s\S]*Ann[\s\S]*🥈[\s\S]*Bob/);

    // Rounds 2 and 3: Ann is always first.
    for (const round of [2, 3]) {
      state = await roundState(request, code, round);
      await guess(ann.page, state.word);
      await expect(ann.page.getByTestId('solved')).toContainText('Solved! #1');
      await guess(bob.page, state.word);
    }

    // Podium: Ann wins with three first solves.
    await waitForPhase(request, code, 'FINISHED');
    await expect(ann.page.getByTestId('results-headline')).toContainText('You won');
    await expect(bob.page.getByTestId('results-headline')).toContainText('Ann wins');
    await expect(ann.page.locator('ff-results')).toContainText('Wordsmith');
    await expect(ann.page.locator('ff-results')).toContainText('Speed reader');
  });

  test('a wrong guess shows feedback and allows a retry', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'word-scramble');
    await startGame(ann, request, code);
    const state = await roundState(request, code, 1);

    // Not an anagram of the tiles: refused with an explanation, nothing counted.
    await guess(ann.page, 'zz');
    await expect(ann.page.getByTestId('feedback')).toContainText(`The word has ${state.word.length} letters`);
    await expect(ann.page.getByTestId('misses')).toHaveCount(0);

    // A wrong anagram: "Not quite", listed for Ann only, and she can try again.
    await guess(ann.page, state.scramble);
    await expect(ann.page.getByTestId('feedback')).toContainText('Not quite');
    await expect(ann.page.getByTestId('misses').locator('li')).toHaveCount(1);
    await expect(ann.page.getByTestId('guess-input')).toBeEnabled();
    await expect(ann.page.getByTestId('guess-input')).toBeFocused();

    await guess(ann.page, state.word);
    await expect(ann.page.getByTestId('solved')).toContainText('Solved! #1');
    await expect(ann.page.getByTestId('tiles')).toHaveClass(/flipped/);
    await expect(bob.page.getByTestId('tiles')).not.toHaveClass(/flipped/);
  });

  test('hints reveal letters in place, spectators only watch', async ({ players, request }) => {
    const [ann, bob, sam] = await players('Ann', 'Bob', 'Sam');
    const code = await setUpRoom([ann, bob], 'word-scramble');
    await joinRoom(sam, code, { spectator: true });
    await startGame(ann, request, code);
    const state = await roundState(request, code, 1);

    await ann.page.getByTestId('hint').click();
    await expect(ann.page.getByTestId('hint-slots').locator('.slot').first()).toHaveText(state.word[0]);
    await expect(bob.page.getByTestId('hint-slots').locator('.slot.filled')).toHaveCount(0);

    await expect(sam.page.getByTestId('tiles').locator('.tile')).toHaveCount(state.word.length);
    await expect(sam.page.getByTestId('guess-input')).toHaveCount(0);
    await sam.page.getByTestId('shuffle').click();
    await expect(sam.page.getByTestId('tiles').locator('.tile')).toHaveCount(state.word.length);
  });
});
