import type { APIRequestContext } from '@playwright/test';

import { type Player, expect, joinRoom, peek, setUpRoom, startGame, test, waitForPhase } from '../../../e2e/fixtures';

/** What `TriviaBlitzLogic.debugState()` returns. */
interface TriviaState {
  correct: number;
  answer: string;
  question: string;
}

/** Waits until `round` is being played and its question is on every listed screen. */
async function question(request: APIRequestContext, code: string, round: number, screens: Player[]) {
  let state: TriviaState | undefined;
  await expect
    .poll(
      async () => {
        const room = await peek<TriviaState>(request, code);
        state = room.state;
        return room.phase === 'PLAYING' ? room.round : -1;
      },
      { timeout: 20_000 },
    )
    .toBe(round);
  for (const screen of screens) {
    await expect(screen.page.getByTestId('question')).toHaveText(state!.question);
  }
  return state!;
}

test.describe('Trivia Blitz', () => {
  test('a full game: locked in, reveal highlights, streak and podium', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'trivia-blitz', { rounds: '5', roundDuration: '30 s' });
    await startGame(ann, request, code);

    for (let round = 1; round <= 5; round++) {
      const { correct } = await question(request, code, round, [ann, bob]);
      const wrong = (correct + 1) % 4;

      // Ann is always right — by click, then with the 1–4 and A–D shortcuts.
      if (round === 1) {
        await ann.page.getByTestId(`answer-${correct}`).click();
      } else if (round % 2 === 0) {
        await ann.page.keyboard.press(String(correct + 1));
      } else {
        await ann.page.keyboard.press('abcd'[correct]);
      }
      const annsPick = ann.page.getByTestId(`answer-${correct}`);
      await expect(annsPick).toHaveClass(/\bmine\b/);
      await expect(annsPick.getByTestId('locked-in')).toHaveText(/Locked in/);

      if (round === 1) {
        // Bob learns that Ann answered — not what she picked.
        await expect(bob.page.getByTestId('answered-count')).toContainText('1 of 2 answered');
        await expect(bob.page.getByTestId('locked-in')).toHaveCount(0);
        await expect(bob.page.locator('.answer.mine')).toHaveCount(0);
        // The first pick is final: another key changes nothing.
        await ann.page.keyboard.press(String(wrong + 1));
        await expect(ann.page.locator('.answer.mine')).toHaveCount(1);
      }

      // Bob is always wrong; his answer completes the turn and the reveal starts at once.
      await bob.page.getByTestId(`answer-${wrong}`).click();
      await expect(ann.page.getByTestId(`answer-${correct}`)).toHaveClass(/\bcorrect\b/);

      if (round === 1) {
        await expect(ann.page.getByTestId(`answer-${wrong}`)).toHaveClass(/\bwrong\b/);
        await expect(bob.page.getByTestId(`pickers-${correct}`).locator('[data-player="Ann"]')).toBeVisible();
        await expect(bob.page.getByTestId(`pickers-${wrong}`).locator('[data-player="Bob"]')).toBeVisible();
        await expect(ann.page.getByTestId('reveal-result')).toContainText('Correct');
        await expect(bob.page.getByTestId('reveal-result')).toContainText('Not quite');
        await expect(bob.page.getByTestId('fastest')).toContainText('Ann');
      }
      if (round === 2) {
        await expect(ann.page.getByTestId('my-streak')).toContainText('2');
        await expect(ann.page.getByTestId('streak-note')).toContainText('2 in a row');
      }
    }

    await waitForPhase(request, code, 'FINISHED');
    await expect(ann.page.getByTestId('results-headline')).toContainText('You won');
    await expect(bob.page.getByTestId('results-headline')).toContainText('Ann wins');
    await expect(ann.page.locator('ff-results')).toContainText('Brainiac');
    await expect(ann.page.locator('ff-results')).toContainText('On fire');
  });

  test('keyboard shortcuts lock in; spectators only see who has answered', async ({ players, request }) => {
    const [ann, bob, cleo] = await players('Ann', 'Bob', 'Cleo');
    const code = await setUpRoom([ann, bob], 'trivia-blitz', { rounds: '5', roundDuration: '30 s' });
    await startGame(ann, request, code);
    await joinRoom(cleo, code); // joining a running game: Cleo watches
    const { correct } = await question(request, code, 1, [ann, bob, cleo]);

    await expect(cleo.page.getByText("You're watching this game")).toBeVisible();
    await expect(cleo.page.getByTestId('answer-0')).toBeDisabled();
    await expect(cleo.page.getByTestId('my-streak')).toHaveCount(0);

    // Ann answers with a letter key; a second key is ignored.
    await ann.page.keyboard.press('abcd'[correct]);
    await expect(ann.page.getByTestId(`answer-${correct}`).getByTestId('locked-in')).toBeVisible();
    await ann.page.keyboard.press('abcd'[(correct + 1) % 4]);
    await expect(ann.page.locator('.answer.mine')).toHaveCount(1);

    await expect(cleo.page.getByTestId('answered-count')).toContainText('1 of 2 answered');
    await expect(cleo.page.locator('.answer.mine, .answer.correct')).toHaveCount(0);

    // Bob answers with a digit key; the reveal shows everyone the result.
    const bobsPick = (correct + 2) % 4;
    await bob.page.keyboard.press(String(bobsPick + 1));
    await expect(cleo.page.getByTestId(`answer-${correct}`)).toHaveClass(/\bcorrect\b/);
    await expect(cleo.page.getByTestId(`answer-${bobsPick}`)).toHaveClass(/\bwrong\b/);
    await expect(ann.page.getByTestId('reveal-result')).toContainText('Correct');
  });
});
