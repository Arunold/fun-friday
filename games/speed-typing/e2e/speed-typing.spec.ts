import { expect, joinRoom, peek, setUpRoom, startGame, test, waitForPhase } from '../../../e2e/fixtures';

interface SpeedTypingState {
  text: string;
  difficulty: string;
}

/** A character that differs from `char`, to type a deliberate typo. */
function typoFor(char: string): string {
  return char === 'x' ? 'y' : 'x';
}

test.describe('Speed Typing', () => {
  test('a full game: one racer finishes, the other runs out of time, then the podium', async ({ players, request }) => {
    test.setTimeout(120_000);
    const [ann, bob, cat] = await players('Ann', 'Bob', 'Cat');
    const code = await setUpRoom([ann, bob], 'speed-typing', { rounds: '1', difficulty: 'Short', roundTime: '30 s' });
    await joinRoom(cat, code, { spectator: true });
    await startGame(ann, request, code);
    const { state } = await peek<SpeedTypingState>(request, code);
    const text = state!.text;

    // Everyone sees the same text; spectators have no input.
    await expect(cat.page.getByTestId('typing-text')).toHaveAttribute('aria-label', text);
    await expect(cat.page.getByTestId('typing-input')).toHaveCount(0);

    // Ann types the whole text (focus is already in the input) and finishes first.
    await expect(ann.page.getByTestId('typing-input')).toBeFocused();
    await ann.page.keyboard.type(text, { delay: 50 });
    await expect(ann.page.getByTestId('finish-card')).toContainText('1st place!');
    await expect(ann.page.getByTestId('finish-card')).toContainText('100% accuracy');

    // Bob only manages the beginning; his lane moves on Ann's screen, Ann's shows her speed.
    await bob.page.keyboard.type(text.slice(0, 12), { delay: 50 });
    const bobLane = ann.page.locator('[data-testid="lane"][data-player="Bob"]');
    await expect(bobLane.getByTestId('lane-progress')).not.toHaveText('0%');
    await expect(bob.page.locator('[data-testid="lane"][data-player="Ann"]')).toContainText('WPM');
    await expect(bob.page.getByTestId('finish-card')).toHaveCount(0);

    // Time runs out: Ann first, then Bob with how far he got.
    await waitForPhase(request, code, 'REVEAL', 40_000);
    await expect(bob.page.getByTestId('reveal')).toContainText('Ann wins the race');
    const rows = bob.page.getByTestId('result-row');
    await expect(rows).toHaveCount(2);
    await expect(rows.first()).toHaveAttribute('data-player', 'Ann');
    await expect(rows.nth(1)).toHaveAttribute('data-player', 'Bob');

    await waitForPhase(request, code, 'FINISHED');
    await expect(ann.page.getByTestId('results-headline')).toContainText('You won');
    await expect(bob.page.getByTestId('results-headline')).toContainText('Ann wins');
    await expect(ann.page.locator('ff-results')).toContainText('Fastest fingers');
  });

  test('paste is blocked', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'speed-typing', { rounds: '1', difficulty: 'Short' });
    await startGame(ann, request, code);
    const { state } = await peek<SpeedTypingState>(request, code);
    const input = ann.page.getByTestId('typing-input');
    await expect(input).toBeFocused();

    await ann.context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await ann.page.evaluate((text) => navigator.clipboard.writeText(text), state!.text);
    await ann.page.keyboard.press('ControlOrMeta+V');

    await expect(ann.page.getByTestId('notice')).toContainText('Pasting is switched off');
    await expect(input).toHaveValue('');
    await expect(ann.page.getByTestId('my-progress')).toHaveText(/0%/);
    await expect(ann.page.getByTestId('finish-card')).toHaveCount(0);
    const annOnBobsScreen = bob.page.locator('[data-testid="lane"][data-player="Ann"]');
    await expect(annOnBobsScreen.getByTestId('lane-progress')).toHaveText('0%');

    // Typing still works afterwards.
    await ann.page.keyboard.type(state!.text.slice(0, 6), { delay: 40 });
    await expect(input).toHaveValue(state!.text.slice(0, 6));
  });

  test('errors are highlighted and must be fixed', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'speed-typing', { rounds: '1', difficulty: 'Short' });
    await startGame(ann, request, code);
    const { state } = await peek<SpeedTypingState>(request, code);
    const text = state!.text;
    const at = text.length - 4;

    // The whole length with one wrong character: highlighted, and no finish.
    await ann.page.keyboard.type(text.slice(0, at) + typoFor(text[at]) + text.slice(at + 1), { delay: 30 });
    const wrong = ann.page.locator('[data-testid="typing-text"] .bad');
    await expect(wrong).toHaveCount(1);
    await expect(ann.page.getByTestId('fix-errors')).toHaveText('Fix 1 error to finish');
    await expect(ann.page.getByTestId('finish-card')).toHaveCount(0);
    await expect(bob.page.locator('[data-testid="lane"][data-player="Ann"]')).not.toContainText('WPM');

    // Back to the typo, retype: finished, and the typo still costs accuracy.
    for (let i = 0; i < 4; i++) {
      await ann.page.keyboard.press('Backspace');
    }
    await expect(wrong).toHaveCount(0);
    await ann.page.keyboard.type(text.slice(at), { delay: 30 });
    await expect(ann.page.getByTestId('finish-card')).toContainText('1st place!');
    await expect(ann.page.getByTestId('my-accuracy')).not.toContainText('100%');
  });
});
