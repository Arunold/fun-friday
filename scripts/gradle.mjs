#!/usr/bin/env node
/**
 * Cross-platform Gradle launcher used by the root npm scripts.
 *
 * Runs the Gradle wrapper in `backend/` (gradlew on macOS/Linux, gradlew.bat on Windows) with the
 * arguments passed to this script, e.g. `node scripts/gradle.mjs :app:bootRun`.
 */
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const backendDir = fileURLToPath(new URL('../backend/', import.meta.url));
const isWindows = process.platform === 'win32';

const child = spawn(isWindows ? 'gradlew.bat' : './gradlew', process.argv.slice(2), {
  cwd: backendDir,
  stdio: 'inherit',
  // .bat files can only be started through a shell on Windows.
  shell: isWindows,
});

child.on('error', (error) => {
  console.error(`Could not start Gradle: ${error.message}`);
  process.exit(1);
});
child.on('exit', (code, signal) => process.exit(code ?? (signal ? 1 : 0)));
