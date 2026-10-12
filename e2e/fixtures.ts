import { APIRequestContext, Browser, BrowserContext, Page, expect, test as base } from '@playwright/test';

/**
 * Shared helpers for platform and game specs. Game specs import them with a relative path:
 * `import { test, expect, createRoom, joinRoom } from '../../../e2e/fixtures';`
 */

/** A person in their own browser context (separate storage = separate seat). */
export interface Player {
  name: string;
  context: BrowserContext;
  page: Page;
}

/** What `GET /api/e2e/rooms/{code}` returns (only with the e2e profile). */
export interface RoomPeek<S = Record<string, unknown>> {
  phase: 'LOBBY' | 'STARTING' | 'PLAYING' | 'REVEAL' | 'FINISHED';
  gameId: string;
  round?: number;
  turn?: number;
  /** The game's `debugState()` (e.g. the secret number). */
  state?: S;
}

export interface Fixtures {
  /** Creates players in fresh browser contexts; closed automatically after the test. */
  players: (...names: string[]) => Promise<Player[]>;
}

export const test = base.extend<Fixtures>({
  players: async ({ browser }, use) => {
    const created: Player[] = [];
    await use(async (...names: string[]) => {
      const list = await Promise.all(names.map((name) => newPlayer(browser, name)));
      created.push(...list);
      return list;
    });
    await Promise.all(created.map((player) => player.context.close()));
  },
});

export { expect };

export async function newPlayer(browser: Browser, name: string): Promise<Player> {
  const context = await browser.newContext();
  const page = await context.newPage();
  return { name, context, page };
}

/**
 * The host creates a room for `gameId`. `settings` maps setting keys to the label of the option to
 * pick (segmented controls), e.g. `{ rounds: '1', roundDuration: '15 s' }`. Returns the room code.
 */
export async function createRoom(host: Player, gameId: string, settings: Record<string, string> = {}): Promise<string> {
  await host.page.goto(`/host/${gameId}`);
  await host.page.getByTestId('player-name').fill(host.name);
  for (const [key, label] of Object.entries(settings)) {
    await chooseSetting(host.page, key, label);
  }
  await host.page.getByTestId('create-room').click();
  await host.page.waitForURL(/\/room\/[A-Z0-9]{5}$/);
  await expect(host.page.locator('ff-lobby')).toBeVisible();
  return new URL(host.page.url()).pathname.split('/').pop()!;
}

/** Picks an option of a setting in a settings form on the page. */
export async function chooseSetting(page: Page, key: string, label: string): Promise<void> {
  const setting = page.locator(`[data-setting="${key}"]`);
  const toggle = setting.locator('mat-button-toggle').filter({ hasText: new RegExp(`^\\s*${escape(label)}\\s*$`) });
  if (await toggle.count()) {
    await toggle.locator('button').click();
    return;
  }
  await setting.locator('mat-select').click();
  await page.getByRole('option', { name: label, exact: true }).click();
}

/** Joins a room through its invite link. */
export async function joinRoom(player: Player, code: string, options: { spectator?: boolean } = {}): Promise<void> {
  await player.page.goto(`/join/${code}`);
  await player.page.getByTestId('player-name').fill(player.name);
  await player.page.getByTestId(options.spectator ? 'join-spectator' : 'join-room').click();
  await player.page.waitForURL(new RegExp(`/room/${code}$`));
}

/** Host creates the room, everybody else joins; returns the code once all are in the lobby. */
export async function setUpRoom(
  [host, ...guests]: Player[],
  gameId: string,
  settings: Record<string, string> = {},
): Promise<string> {
  const code = await createRoom(host, gameId, settings);
  for (const guest of guests) {
    await joinRoom(guest, code);
  }
  await expect(host.page.getByTestId('player-count')).toContainText(`${guests.length + 1} /`);
  return code;
}

/** Host presses Start; resolves once the first turn is running. */
export async function startGame(host: Player, request: APIRequestContext, code: string): Promise<void> {
  await host.page.getByTestId('start-game').click();
  await waitForPhase(request, code, 'PLAYING');
}

/** Reads the room's phase and the game's secret state (e2e profile only). */
export async function peek<S = Record<string, unknown>>(
  request: APIRequestContext,
  code: string,
): Promise<RoomPeek<S>> {
  const response = await request.get(`/api/e2e/rooms/${code}`);
  expect(response.ok(), `peek ${code}: HTTP ${response.status()}`).toBeTruthy();
  return (await response.json()) as RoomPeek<S>;
}

export async function waitForPhase(
  request: APIRequestContext,
  code: string,
  phase: RoomPeek['phase'],
  timeout = 20_000,
): Promise<void> {
  await expect.poll(async () => (await peek(request, code)).phase, { timeout }).toBe(phase);
}

function escape(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
