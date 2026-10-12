import { expect, test } from '../fixtures';

/** Pages outside a room. */
test.describe('pages', () => {
  test('home lists every installed game and links to its host page', async ({ page, request }) => {
    const games = (await (await request.get('/api/games')).json()) as { id: string; name: string }[];
    expect(games.length).toBeGreaterThan(0);

    await page.goto('/');
    for (const game of games) {
      await expect(page.locator(`[data-game="${game.id}"]`)).toContainText(game.name);
    }
    await page.locator(`[data-game="${games[0].id}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/host/${games[0].id}$`));
    await expect(page.getByTestId('create-room')).toBeDisabled();
  });

  test('joining from the home page goes through the join page', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('join-code').fill('abcde');
    await page.getByTestId('join-submit').click();
    await expect(page).toHaveURL(/\/join\/ABCDE$/);
  });

  test('the hall of fame loads', async ({ page }) => {
    await page.goto('/history');
    await expect(page.getByRole('heading', { name: 'Friday Cup leaderboard' })).toBeVisible();
  });

  test('deep links are served by the single-page app', async ({ request }) => {
    for (const path of ['/room/ABCDE', '/join/ABCDE', '/host/number-guess', '/history', '/oops/closed']) {
      const response = await request.get(path);
      expect(response.status(), path).toBe(200);
      expect(await response.text()).toContain('<ff-root>');
    }
  });
});
