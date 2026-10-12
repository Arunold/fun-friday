import { readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';

import type { Page } from '@playwright/test';

import { createRoom, expect, joinRoom, startGame, test } from '../fixtures';

/**
 * GameIdeas.md: Fun Friday is desktop only and a room always fits the window — the page itself
 * never scrolls (panels like a guess feed may scroll inside). Checked for every installed game on
 * a small laptop screen, from every player's point of view.
 */
const GAMES = readdirSync(join(__dirname, '../../games')).filter(
  (id) => !id.startsWith('_') && existsSync(join(__dirname, '../../games', id, 'frontend', 'index.ts')),
);
const VIEWPORT = { width: 1366, height: 700 };
const NAMES = ['Ann', 'Bob', 'Cleo', 'Dan'];

/** Waits for entrance animations (finite ones) to settle so the layout is final. */
async function settle(page: Page): Promise<void> {
  await page.evaluate(() =>
    Promise.all(
      document
        .getAnimations()
        .filter((animation) => animation.effect?.getTiming().iterations !== Infinity)
        .map((animation) => animation.finished.catch(() => undefined)),
    ),
  );
}

async function pageOverflow(page: Page): Promise<{ x: number; y: number }> {
  return page.evaluate(() => ({
    x: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    y: document.documentElement.scrollHeight - document.documentElement.clientHeight,
  }));
}

test.describe('layout', () => {
  for (const gameId of GAMES) {
    test(`${gameId} fits a ${VIEWPORT.width}×${VIEWPORT.height} window without page scroll`, async ({
      players,
      request,
    }) => {
      const catalog = (await (await request.get('/api/games')).json()) as { id: string; minPlayers: number }[];
      const minPlayers = catalog.find((game) => game.id === gameId)?.minPlayers ?? 2;
      const everyone = await players(...NAMES.slice(0, minPlayers));
      for (const { page } of everyone) {
        await page.setViewportSize(VIEWPORT);
      }
      const [host, ...guests] = everyone;
      const code = await createRoom(host, gameId);
      for (const guest of guests) {
        await joinRoom(guest, code);
      }
      await startGame(host, request, code);

      for (const { name, page } of everyone) {
        await expect(page.locator(`.game[data-game="${gameId}"] > :not(mat-spinner)`).first()).toBeVisible();
        await settle(page);
        expect(await pageOverflow(page), `${name}'s page overflows`).toEqual({ x: 0, y: 0 });
      }
    });
  }
});
