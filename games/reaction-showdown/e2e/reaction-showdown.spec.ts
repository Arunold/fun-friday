import type { Page } from '@playwright/test';

import { type Player, expect, joinRoom, peek, setUpRoom, startGame, test, waitForPhase } from '../../../e2e/fixtures';

/** `ReactionShowdownLogic.debugState()`. */
interface ShowdownState {
  signalId: number;
  signal: number;
  state: 'WAITING' | 'FAKE' | 'GO' | 'RESULT';
  fakeComing: boolean;
  nextInMs: number;
  signals: number;
  fakeouts: boolean;
}

/**
 * Waits until the page shows GO for a signal newer than `after` and returns its id. Polls every
 * animation frame: GO is open for two seconds only, so expect's back-off would be too slow.
 */
async function waitForGo(page: Page, after: number): Promise<number> {
  const handle = await page.waitForFunction(
    (previous) => {
      const stage = document.querySelector<HTMLElement>('[data-testid="stage"]');
      const id = Number(stage?.dataset['signal'] ?? 0);
      return stage?.dataset['state'] === 'GO' && id > previous ? id : 0;
    },
    after,
    { polling: 'raf', timeout: 20_000 },
  );
  return (await handle.jsonValue()) as number;
}

/** Switches fake-outs off in the lobby; the guest's read-only summary confirms the server has it. */
async function disableFakeOuts(host: Player, guest: Player): Promise<void> {
  await host.page.locator('[data-setting="fakeouts"]').getByRole('switch').click();
  await expect(guest.page.locator('ff-settings-form')).toContainText(/Fake-outs\s*Off/);
}

test.describe('Reaction Showdown', () => {
  test('a full game: three signals with results, the round table and the podium', async ({ players, request }) => {
    const [ann, bob, cy] = await players('Ann', 'Bob', 'Cy');
    const code = await setUpRoom([ann, bob], 'reaction-showdown', { rounds: '1', signals: '3' });
    await joinRoom(cy, code, { spectator: true });
    await disableFakeOuts(ann, bob); // no decoys: every signal goes straight from WAITING to GO
    await startGame(ann, request, code);
    expect((await peek<ShowdownState>(request, code)).state).toMatchObject({ signals: 3, fakeouts: false });

    let signalId = 0;
    for (let signal = 1; signal <= 3; signal++) {
      const [seenByAnn, seenByBob] = await Promise.all([waitForGo(ann.page, signalId), waitForGo(bob.page, signalId)]);
      expect(seenByBob).toBe(seenByAnn);
      signalId = seenByAnn;
      await expect(cy.page.getByTestId('tap-target')).toBeDisabled(); // spectators only watch

      // Ann reacts (click) well after the 100 ms anticipation limit; Bob (Space) only after her.
      await ann.page.waitForTimeout(150);
      await ann.page.getByTestId('tap-target').click();
      await expect(ann.page.getByTestId('my-time')).toContainText('ms');
      await expect(ann.page.getByTestId('tap-target')).toBeDisabled();
      await bob.page.waitForTimeout(200);
      await bob.page.keyboard.press('Space');

      // Everyone tapped: the signal ends at once (BUG-06) and shows its mini-result.
      await expect(ann.page.getByTestId('signal-result')).toContainText('Fastest');
      await expect(bob.page.getByTestId('signal-result')).toContainText('2nd');
      await expect(cy.page.getByTestId('signal-result')).toContainText('Ann');
    }

    // After the last signal: the reveal with the round table (no tap target), then the podium.
    await expect(bob.page.getByTestId('reveal')).toContainText('Ann was quickest');
    const table = ann.page.getByTestId('round-table');
    await expect(table.locator('[data-player="Ann"]')).toContainText('+300');
    await expect(table.locator('[data-player="Bob"]')).toContainText('+240');
    await expect(ann.page.getByTestId('tap-target')).toHaveCount(0);

    await waitForPhase(request, code, 'FINISHED');
    await expect(ann.page.getByTestId('results-headline')).toContainText('You won');
    await expect(bob.page.getByTestId('results-headline')).toContainText('Ann wins');
    await expect(ann.page.locator('ff-results')).toContainText('Lightning');
  });

  test('tapping while waiting is a false start', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'reaction-showdown', { rounds: '1' });
    await disableFakeOuts(ann, bob);
    await startGame(ann, request, code);

    // A WAITING phase with enough time left before GO, on the server and on Ann's screen.
    let waiting: ShowdownState | undefined;
    await expect
      .poll(
        async () => {
          const { state } = await peek<ShowdownState>(request, code);
          waiting = state?.state === 'WAITING' && state.nextInMs > 900 ? state : undefined;
          return waiting !== undefined;
        },
        { intervals: [50], timeout: 30_000 },
      )
      .toBe(true);
    const stage = ann.page.getByTestId('stage');
    await expect(stage).toHaveAttribute('data-signal', String(waiting!.signalId));
    await expect(stage).toHaveAttribute('data-state', 'WAITING');
    await ann.page.keyboard.press('Space');

    await expect(ann.page.getByTestId('false-start')).toHaveText('Too soon!');
    await expect(ann.page.getByTestId('tap-target')).toBeDisabled();
    await expect(bob.page.locator('.player[data-player="Ann"]')).toContainText('✋');

    // Bob still gets his GO; once he has tapped too, the signal ends and Ann loses 25 points.
    await waitForGo(bob.page, waiting!.signalId - 1);
    await bob.page.waitForTimeout(150);
    await bob.page.getByTestId('tap-target').click();
    await expect(ann.page.getByTestId('signal-result')).toContainText('Too soon!');
    await expect(ann.page.getByTestId('signal-result')).toContainText('−25');
    await expect(bob.page.getByTestId('signal-result')).toContainText('Fastest');
  });
});
