import { expect, peek, setUpRoom, startGame, test, waitForPhase } from '../../../e2e/fixtures';

test.describe('__GAME_NAME__', () => {
  test('a full game: picks, reveal and podium', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], '__GAME_ID__', { rounds: '1' });
    await startGame(ann, request, code);

    const { state } = await peek<{ lucky: number }>(request, code);
    const lucky = state!.lucky;

    await ann.page.getByTestId(`pick-${lucky}`).click();
    await bob.page.getByTestId(`pick-${lucky === 1 ? 2 : lucky - 1}`).click();

    await expect(ann.page.getByTestId('lucky')).toHaveText(String(lucky));
    await waitForPhase(request, code, 'FINISHED');
    await expect(ann.page.getByTestId('results-headline')).toContainText('You won');
  });
});
