import { expect, joinRoom, peek, setUpRoom, startGame, test, waitForPhase } from '../fixtures';

/** The room life cycle that every game shares: lobby, readiness, refresh, host changes, kicks. */
test.describe('room', () => {
  test('host and guest meet in the lobby and see each other', async ({ players }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'number-guess');

    for (const { page } of [ann, bob]) {
      await expect(page.locator('[data-player="Ann"]')).toContainText('Host');
      await expect(page.locator('[data-player="Bob"]')).toBeVisible();
      await expect(page.getByTestId('room-code')).toContainText(code);
    }
    // Only the host can start; the guest marks themselves ready.
    await expect(bob.page.getByTestId('start-game')).toHaveCount(0);
    await bob.page.getByTestId('ready').click();
    await expect(ann.page.locator('[data-player="Bob"]')).toContainText('Ready');
    await expect(ann.page.getByTestId('start-hint')).toContainText('2 of 2 ready');
  });

  test('the host sees how many players are missing', async ({ players }) => {
    const [ann] = await players('Ann');
    await setUpRoom([ann], 'number-guess');
    await expect(ann.page.getByTestId('start-game')).toBeDisabled();
    await expect(ann.page.getByTestId('start-hint')).toContainText('1 more player');
  });

  test('a refresh during the game keeps the seat and the phase (BUG-01, BUG-13)', async ({ players, request }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'number-guess', { rounds: '2' });
    await startGame(ann, request, code);

    await bob.page.reload();
    await expect(bob.page.locator('ff-number-guess')).toBeVisible();
    await expect(bob.page.getByTestId('progress')).toContainText('Round 1 / 2');
    // Bob still plays: his guess is accepted.
    await bob.page.getByTestId('guess-input').fill('1');
    await bob.page.getByTestId('guess-submit').click();
    await expect(bob.page.locator('.history')).toContainText('1');
  });

  test('when the host leaves, the next player becomes host', async ({ players }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    await setUpRoom([ann, bob], 'number-guess');

    await ann.page.getByTestId('room-menu').click();
    await ann.page.getByTestId('leave-room').click();
    await ann.page.waitForURL((url) => url.pathname === '/');

    await expect(bob.page.locator('[data-player="Bob"]')).toContainText('Host');
    await expect(bob.page.getByTestId('start-game')).toBeVisible();
    await expect(bob.page.locator('[data-player="Ann"]')).toHaveCount(0);
  });

  test('going back from the room asks first, then really leaves (BUG-03, BUG-12)', async ({ players }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann, bob], 'number-guess');

    // Cancel keeps the seat and the page.
    await bob.page.goBack();
    await bob.page.getByRole('button', { name: 'Cancel' }).click();
    await expect(bob.page).toHaveURL(new RegExp(`/room/${code}$`));
    await expect(ann.page.locator('[data-player="Bob"]')).toBeVisible();

    // Confirming leaves the room for good: nobody is left behind as a ghost.
    await bob.page.goBack();
    await bob.page.getByTestId('confirm').click();
    await bob.page.waitForURL((url) => !url.pathname.startsWith('/room/'));
    await expect(ann.page.locator('[data-player="Bob"]')).toHaveCount(0);
  });

  test('the host can remove a player, who lands on an explanation page', async ({ players }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    await setUpRoom([ann, bob], 'number-guess');

    await ann.page.locator('[data-player="Bob"]').hover();
    await ann.page.getByRole('button', { name: 'Remove Bob' }).click();
    await ann.page.getByTestId('confirm').click();

    await bob.page.waitForURL(/\/oops\/kicked/);
    await expect(bob.page.getByRole('heading')).toContainText('removed');
    await expect(ann.page.locator('[data-player="Bob"]')).toHaveCount(0);
  });

  test('a taken name is rejected next to the name field', async ({ players }) => {
    const [ann, otherAnn] = await players('Ann', 'Ann');
    const code = await setUpRoom([ann], 'number-guess');
    await otherAnn.page.goto(`/join/${code}`);
    await otherAnn.page.getByTestId('player-name').fill('ann');
    await otherAnn.page.getByTestId('join-room').click();
    await expect(otherAnn.page.locator('ff-profile-form')).toContainText('already called');
  });

  test('spectators watch the running game and play from the next one (IMP-36)', async ({ players, request }) => {
    const [ann, bob, cleo] = await players('Ann', 'Bob', 'Cleo');
    const code = await setUpRoom([ann, bob], 'number-guess', { rounds: '1', attempts: '1' });
    await startGame(ann, request, code);

    await joinRoom(cleo, code);
    await expect(cleo.page.getByText("You're watching this game")).toBeVisible();
    await expect(cleo.page.getByTestId('guess-input')).toHaveCount(0);

    // Finish the game: one guess each.
    for (const player of [ann, bob]) {
      await player.page.getByTestId('guess-input').fill('5');
      await player.page.getByTestId('guess-submit').click();
    }
    await waitForPhase(request, code, 'FINISHED');
    await ann.page.getByTestId('play-again').click();
    await expect(ann.page.locator('[data-player="Cleo"]')).toBeVisible();
    expect((await peek(request, code)).phase).toBe('LOBBY');
  });

  test('an unknown room code explains itself', async ({ page }) => {
    await page.goto('/join/ZZZZZ');
    await expect(page.getByRole('heading')).toContainText('No room');
  });

  test('old invite links (/#/setup/join/CODE) still work', async ({ players }) => {
    const [ann, bob] = await players('Ann', 'Bob');
    const code = await setUpRoom([ann], 'number-guess');
    await bob.page.goto(`/ui/#/setup/join/${code}`);
    await bob.page.waitForURL(new RegExp(`/join/${code}$`));
    await expect(bob.page.getByTestId('join-room')).toBeVisible();
  });
});
