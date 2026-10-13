import type { APIRequestContext } from '@playwright/test';

import { expect, peek, setUpRoom, startGame, test, waitForPhase, type Player } from '../../../e2e/fixtures';

/** The parts of the game's `debugState()` these tests read. */
interface DebugState {
  mode: 'LEVEL' | 'VOTE';
  status?: string;
  bus?: { x: number; y: number; heading: number; speed: number };
  rolesByName?: Record<string, string[]>;
  rolesVersion?: number;
  shuffleWarning?: boolean;
}

async function state(request: APIRequestContext, code: string): Promise<DebugState> {
  const room = await peek<DebugState>(request, code);
  expect(room.state, 'debug state').toBeTruthy();
  return room.state!;
}

/** The player holding `control` right now. */
function holder(everyone: Player[], debug: DebugState, control: string): Player {
  const name = Object.entries(debug.rolesByName ?? {}).find(([, controls]) => controls.includes(control))?.[0];
  const player = everyone.find((p) => p.name === name);
  expect(player, `the ${control} holder`).toBeTruthy();
  return player!;
}

test.describe('Backseat Drivers', () => {
  test("one bus: roles, the navigator's map, gas, reversing into the CEO's car, the MVP vote and the podium", async ({
    players,
    request,
  }) => {
    const everyone = await players('Ann', 'Bob', 'Cleo');
    const code = await setUpRoom(everyone, 'backseat-drivers', { rounds: '1' });
    await startGame(everyone[0], request, code);

    // Everyone sees their job; only the navigator sees the map, the drivers see the road.
    let debug = await state(request, code);
    const navigator = holder(everyone, debug, 'NAVIGATOR');
    for (const player of everyone) {
      await expect(player.page.getByTestId('role-banner')).toBeVisible();
      if (player === navigator) {
        await expect(player.page.getByTestId('navigator-map')).toBeVisible();
        await expect(player.page.getByTestId('road-view')).toHaveCount(0);
      } else {
        await expect(player.page.getByTestId('road-view')).toBeVisible();
        await expect(player.page.getByTestId('navigator-map')).toHaveCount(0);
      }
    }
    await expect(navigator.page.getByTestId('role-banner')).toContainText('Navigator');

    // Space presses your first control — everybody's roster lights it up (the bus stands still,
    // so steering does nothing yet).
    const steer = holder(everyone, debug, 'STEER_LEFT');
    await steer.page.keyboard.down(' ');
    await expect(steer.page.locator('[data-control="STEER_LEFT"]')).toHaveClass(/down/);
    await expect(navigator.page.locator('ff-bd-side-panel li', { hasText: steer.name }).locator('.tag.on')).toHaveText(
      'Left',
    );
    await steer.page.keyboard.up(' ');

    // A key that isn't yours says whose it is.
    const gas = holder(everyone, debug, 'GAS');
    await navigator.page.keyboard.press('ArrowUp');
    await expect(navigator.page.getByTestId('key-hint')).toContainText(`at ${gas.name}`);

    // The GAS holder holds W: the bus drives off (east, away from the CEO's car).
    const startX = debug.bus!.x;
    await expect(gas.page.getByTestId('role-banner')).toContainText('Gas');
    await gas.page.keyboard.down('w');
    await expect
      .poll(async () => (await state(request, code)).bus!.x, { timeout: 5_000 })
      .toBeGreaterThan(startX + 0.5);
    await gas.page.keyboard.up('w');

    // The BRAKE holder holds theirs: the bus stops, reverses… into the CEO's car. Level over.
    debug = await state(request, code);
    const brake = holder(everyone, debug, 'BRAKE');
    await brake.page.keyboard.down('ArrowDown');
    await waitForPhase(request, code, 'REVEAL', 15_000);
    await brake.page.keyboard.up('ArrowDown');
    await expect(everyone[0].page.getByTestId('reveal')).toContainText("The CEO's car");
    await expect(everyone[1].page.getByTestId('level-result')).toContainText("The CEO's car");

    // The MVP vote: a card for everybody but yourself; the last vote ends it early.
    await expect.poll(async () => (await state(request, code)).mode, { timeout: 10_000 }).toBe('VOTE');
    for (const { page } of everyone) {
      await expect(page.getByTestId('vote-option')).toHaveCount(2);
    }
    for (const { page } of everyone) {
      await page.getByTestId('vote-option').first().click();
    }
    await waitForPhase(request, code, 'FINISHED');
    await expect(everyone[0].page.getByTestId('results-headline')).toBeVisible();
    await expect(everyone[0].page.locator('ff-results')).toContainText('Team MVP');
  });

  test('a silent navigator signals; spectators see the fogged road, never the map', async ({ players, request }) => {
    const everyone = await players('Ann', 'Bob', 'Cleo');
    const [watcher] = await players('Dave');
    const code = await setUpRoom(everyone, 'backseat-drivers', { rounds: '1', variation: 'Silent navigator' });
    await watcher.page.goto(`/join/${code}`);
    await watcher.page.getByTestId('player-name').fill(watcher.name);
    await watcher.page.getByTestId('join-spectator').click();
    await startGame(everyone[0], request, code);

    await expect(watcher.page.getByTestId('road-view')).toBeVisible();
    await expect(watcher.page.getByTestId('navigator-map')).toHaveCount(0);

    const navigator = holder(everyone, await state(request, code), 'NAVIGATOR');
    const drivers = everyone.filter((p) => p !== navigator);
    await navigator.page.locator('[data-signal="LEFT"]').click();
    for (const { page } of [...drivers, watcher]) {
      await expect(page.getByTestId('signal-flash')).toContainText('Left');
    }
    // Keys work too: ↓ asks the drivers to stop.
    await navigator.page.waitForTimeout(600); // signals are at most two per second
    await navigator.page.keyboard.press('ArrowDown');
    await expect(drivers[0].page.getByTestId('signal-flash')).toContainText('Stop');
  });

  test('SEAT SHUFFLE: a siren, then new seats — the navigator changes', async ({ players, request }) => {
    test.setTimeout(45_000);
    const everyone = await players('Ann', 'Bob', 'Cleo');
    const code = await setUpRoom(everyone, 'backseat-drivers', { rounds: '1', shuffle: '20 s' });
    await startGame(everyone[0], request, code);

    const before = await state(request, code);
    const oldNavigator = holder(everyone, before, 'NAVIGATOR');
    await expect.poll(async () => (await state(request, code)).shuffleWarning, { timeout: 25_000 }).toBe(true);
    await expect(everyone[0].page.getByTestId('role-banner')).toHaveClass(/siren/);
    await expect
      .poll(async () => (await state(request, code)).rolesVersion, { timeout: 5_000 })
      .toBeGreaterThan(before.rolesVersion!);

    const newNavigator = holder(everyone, await state(request, code), 'NAVIGATOR');
    expect(newNavigator).not.toBe(oldNavigator);
    await expect(newNavigator.page.getByTestId('navigator-map')).toBeVisible();
    await expect(oldNavigator.page.getByTestId('road-view')).toBeVisible();
    await expect(everyone[0].page.getByTestId('role-banner')).not.toHaveClass(/siren/);
  });
});
