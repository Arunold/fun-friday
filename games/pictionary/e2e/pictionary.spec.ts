import type { APIRequestContext, Page } from '@playwright/test';

import { expect, peek, setUpRoom, startGame, test, waitForPhase, type Player } from '../../../e2e/fixtures';

/** `PictionaryLogic.debugState()`. */
interface PictionaryState {
  turn: number;
  phase: 'CHOOSING' | 'DRAWING';
  drawerName: string;
  word: string | null;
  choices: string[];
  version: number;
  ops: number;
}

/** Waits until turn `turn` is running in `phase` and returns its secret state. */
async function turnState(
  request: APIRequestContext,
  code: string,
  turn: number,
  phase: PictionaryState['phase'],
): Promise<PictionaryState> {
  await expect
    .poll(async () => {
      const room = await peek<PictionaryState>(request, code);
      return room.phase === 'PLAYING' && room.state?.turn === turn && room.state.phase === phase;
    })
    .toBe(true);
  return (await peek<PictionaryState>(request, code)).state!;
}

/** The drawer and the guessers of the current turn. */
function roles(players: Player[], state: PictionaryState): { drawer: Player; guessers: Player[] } {
  const drawer = players.find((player) => player.name === state.drawerName)!;
  return { drawer, guessers: players.filter((player) => player !== drawer) };
}

/** The drawer picks the first word; resolves with it once the drawing phase runs. */
async function pickFirstWord(request: APIRequestContext, code: string, drawer: Player, turn: number): Promise<string> {
  await drawer.page.getByTestId('word-choice').first().click();
  const state = await turnState(request, code, turn, 'DRAWING');
  await expect(drawer.page.getByTestId('word')).toHaveText(state.word!);
  return state.word!;
}

/** Drags a short stroke across the middle of the drawer's canvas. */
async function drawStroke(page: Page): Promise<void> {
  const canvas = page.getByTestId('canvas');
  const box = (await canvas.boundingBox())!;
  const x = box.x + box.width * 0.3;
  const y = box.y + box.height * 0.4;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + box.width * 0.2, y + box.height * 0.1, { steps: 12 });
  await page.mouse.move(x + box.width * 0.35, y - box.height * 0.05, { steps: 12 });
  await page.mouse.up();
  await expect(canvas).toHaveAttribute('data-ops', '1');
}

async function guess(page: Page, text: string): Promise<void> {
  await page.getByTestId('guess-input').fill(text);
  await page.getByTestId('guess-input').press('Enter');
}

test.describe('Pictionary', () => {
  test('a full round: pick a word, draw, live guesses, early turn end and the podium', async ({ players, request }) => {
    const everyone = await players('Ann', 'Bob', 'Cleo');
    const code = await setUpRoom(everyone, 'pictionary', { rounds: '1' });
    await startGame(everyone[0], request, code);

    // One round = one turn per player.
    for (const turn of [1, 2, 3]) {
      const choosing = await turnState(request, code, turn, 'CHOOSING');
      const { drawer, guessers } = roles(everyone, choosing);
      // Only the drawer sees the choices; the others wait (BUG-07: no word in their views).
      await expect(guessers[0].page.getByText(`${drawer.name} is choosing a word`)).toBeVisible();
      await expect(guessers[0].page.getByTestId('word-choice')).toHaveCount(0);

      const word = await pickFirstWord(request, code, drawer, turn);
      await expect(guessers[0].page.getByTestId('word')).toHaveCount(0);
      await expect(guessers[0].page.getByTestId('word-mask')).toBeVisible();

      if (turn === 1) {
        // The stroke reaches every canvas (BUG-18).
        await drawStroke(drawer.page);
        for (const guesser of guessers) {
          await expect(guesser.page.getByTestId('canvas')).toHaveAttribute('data-ops', '1');
        }
        // A wrong guess shows up live in everybody's feed (BUG-09).
        await guess(guessers[0].page, 'qqq zzz');
        await expect(drawer.page.getByTestId('guess-feed')).toContainText('qqq zzz');
        await expect(guessers[1].page.getByTestId('guess-feed')).toContainText('qqq zzz');
      }

      // A correct guess is announced without the word; the guesser now sees it.
      await guess(guessers[0].page, word.toUpperCase());
      await expect(drawer.page.getByTestId('guess-feed')).toContainText(`${guessers[0].name} guessed it!`);
      await expect(guessers[1].page.getByTestId('guess-feed')).not.toContainText(word);
      await expect(guessers[0].page.getByTestId('guess-status')).toContainText('You got it');
      await expect(guessers[0].page.getByTestId('word')).toHaveText(word);

      // The last guesser ends the turn early; the result shows the real points (BUG-19).
      await guess(guessers[1].page, word);
      for (const player of everyone) {
        await expect(player.page.getByTestId('turn-result')).toContainText('+');
        await expect(player.page.getByTestId('word')).toHaveText(word);
      }
    }

    await waitForPhase(request, code, 'FINISHED');
    await expect(everyone[0].page.getByTestId('results-headline')).toBeVisible();
    await expect(everyone[0].page.locator('ff-results')).toContainText('Picasso');
    await expect(everyone[0].page.locator('ff-results')).toContainText('Quick eye');
  });

  test('undo and redo work from the keyboard; near misses stay private', async ({ players, request }) => {
    const everyone = await players('Ann', 'Bob', 'Cleo');
    const code = await setUpRoom(everyone, 'pictionary', { rounds: '1' });
    await startGame(everyone[0], request, code);

    const { drawer, guessers } = roles(everyone, await turnState(request, code, 1, 'CHOOSING'));
    const word = await pickFirstWord(request, code, drawer, 1);

    // Ctrl+Z / Ctrl+Y (BUG-32) change every canvas, not only the drawer's.
    await drawStroke(drawer.page);
    await expect(guessers[0].page.getByTestId('canvas')).toHaveAttribute('data-ops', '1');
    await drawer.page.keyboard.press('Control+z');
    await expect(guessers[0].page.getByTestId('canvas')).toHaveAttribute('data-ops', '0');
    await expect(guessers[1].page.getByTestId('canvas')).toHaveAttribute('data-ops', '0');
    await expect(drawer.page.getByTestId('redo')).toBeEnabled();
    await drawer.page.waitForTimeout(150); // history steps are throttled to one per 120 ms
    await drawer.page.keyboard.press('Control+y');
    await expect(guessers[0].page.getByTestId('canvas')).toHaveAttribute('data-ops', '1');

    // A guess containing the word is "so close" — only its author sees it.
    const nearMiss = `maybe ${word} again`;
    await guess(guessers[0].page, nearMiss);
    await expect(guessers[0].page.locator('.close-flash')).toContainText('So close!');
    await expect(guessers[0].page.getByTestId('guess-feed')).toContainText(nearMiss);
    await expect(guessers[1].page.getByTestId('guess-feed')).not.toContainText(nearMiss);
    await expect(drawer.page.getByTestId('guess-feed')).not.toContainText(nearMiss);
    await expect(guessers[0].page.getByTestId('guess-input')).toBeEnabled();
  });
});
