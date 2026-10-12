import { expect, peek, setUpRoom, startGame, test, waitForPhase } from '../../../e2e/fixtures';

interface NumberGuessState {
  target: number;
}

test.describe('Number Guess', () => {
  test('a full game: hints, exact hit, reveal and podium', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'number-guess', { rounds: '1', attempts: '3' });
    await startGame(ann, request, code);

    const { state } = await peek<NumberGuessState>(request, code);
    const target = state!.target;
    const below = target > 1 ? target - 1 : target + 2;

    // A wrong guess gives a hint and uses one attempt.
    await ann.page.getByTestId('guess-input').fill(String(below));
    await ann.page.getByTestId('guess-submit').click();
    await expect(ann.page.locator('.history')).toContainText(below < target ? 'higher' : 'lower');
    await expect(ann.page.locator('.attempts')).toContainText('2 guesses left');

    // Bob doesn't see Ann's guesses, only her progress.
    await expect(bob.page.locator('.history')).toHaveCount(0);

    // Ann hits; Bob uses up his guesses.
    await ann.page.getByTestId('guess-input').fill(String(target));
    await ann.page.getByTestId('guess-submit').click();
    await expect(ann.page.getByText('Got it in 2!')).toBeVisible();

    const misses = [10, 20, 30, 40].filter((value) => value !== target).slice(0, 3);
    for (const [index, value] of misses.entries()) {
      await bob.page.getByTestId('guess-input').fill(String(value));
      await bob.page.getByTestId('guess-submit').click();
      if (index < misses.length - 1) {
        await expect(bob.page.locator('.history .guess-chip')).toHaveCount(index + 1);
      }
    }

    // The round ends early once everybody is done; the reveal shows the number.
    await expect(ann.page.getByTestId('target')).toHaveText(String(target));
    await expect(bob.page.getByTestId('reveal')).toContainText(`The number was ${target}`);

    // Podium: Ann wins.
    await waitForPhase(request, code, 'FINISHED');
    await expect(ann.page.getByTestId('results-headline')).toContainText('You won');
    await expect(bob.page.getByTestId('results-headline')).toContainText('Ann wins');
    await expect(ann.page.locator('ff-results')).toContainText('Bullseye');
  });

  test('guesses outside the range are refused with an explanation', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'number-guess', { rounds: '1' });
    await startGame(ann, request, code);

    await ann.page.getByTestId('guess-input').fill('1000');
    await ann.page.getByTestId('guess-submit').click();
    await expect(ann.page.locator('.error')).toContainText('from 1 to 100');
    await expect(ann.page.locator('.attempts')).toContainText('3 guesses left');
  });
});
