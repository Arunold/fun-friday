import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests: platform specs in e2e/tests and one spec folder per game in games/<id>/e2e.
 *
 * By default the tests start the packaged app (`backend/app/build/libs/fun-friday.jar`, built with
 * the frontend bundled: `npm run build`) with the `e2e` profile — short timers, in-memory DB and
 * the test-only endpoint `GET /api/e2e/rooms/{code}` that exposes secret game state.
 *
 * Environment:
 *   FF_E2E_BASE_URL  test an already running server instead (e.g. http://localhost:4200)
 *   FF_E2E_PORT      port for the started server (default 8099)
 *   FF_E2E_CHANNEL   browser channel (default: "chrome" locally, bundled Chromium on CI)
 */
const port = Number(process.env.FF_E2E_PORT ?? 8099);
const baseURL = process.env.FF_E2E_BASE_URL ?? `http://localhost:${port}`;
const ci = !!process.env.CI;
const channel = process.env.FF_E2E_CHANNEL ?? (ci ? undefined : 'chrome');

export default defineConfig({
  testDir: '..',
  testMatch: ['e2e/tests/**/*.spec.ts', 'games/*/e2e/**/*.spec.ts'],
  testIgnore: ['**/node_modules/**', '**/build/**', '**/dist/**', 'games/_*/**'],
  fullyParallel: true,
  workers: ci ? 2 : 4,
  retries: ci ? 1 : 0,
  forbidOnly: ci,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  outputDir: 'test-results',
  reporter: ci ? [['github'], ['html', { open: 'never', outputFolder: 'playwright-report' }]] : [['list']],
  use: {
    ...devices['Desktop Chrome'],
    baseURL,
    channel,
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: process.env.FF_E2E_BASE_URL
    ? undefined
    : {
        command: `java -jar ../backend/app/build/libs/fun-friday.jar --spring.profiles.active=e2e --server.port=${port}`,
        url: `${baseURL}/actuator/health`,
        reuseExistingServer: !ci,
        timeout: 120_000,
        stdout: 'ignore',
        stderr: 'pipe',
      },
});
