#!/usr/bin/env node
/**
 * Scaffolds a new game from games/_template.
 *
 *   npm run new-game -- <id> ["Display name"] ["🎲"]
 *
 * <id> becomes the folder name and game id (kebab-case, e.g. "quick-math"). The template is a tiny
 * but complete game (backend, scenario test, UI, e2e spec) to edit from there. The new files are
 * formatted the way CI checks them (Prettier, and Spotless for Java when Gradle can run).
 */
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, readFileSync, readdirSync, renameSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import * as prettier from 'prettier';

const root = fileURLToPath(new URL('..', import.meta.url));
const [id, name, icon = '🎲'] = process.argv.slice(2);

if (!id || !/^[a-z][a-z0-9]*(-[a-z0-9]+)*$/.test(id)) {
  console.error('Usage: npm run new-game -- <id> ["Display name"] ["🎲"]   (id: kebab-case, e.g. quick-math)');
  process.exit(1);
}

const target = path.join(root, 'games', id);
if (existsSync(target)) {
  console.error(`games/${id} already exists.`);
  process.exit(1);
}

const className = id
  .split('-')
  .map((part) => part[0].toUpperCase() + part.slice(1))
  .join('');
const replacements = {
  __GAME_ID__: id,
  __GAME_NAME__: name ?? className.replace(/([a-z])([A-Z])/g, '$1 $2'),
  __GAME_ICON__: icon,
  __PACKAGE__: id.replaceAll('-', ''),
  __CLASS__: className,
};
const replaceAll = (text) =>
  Object.entries(replacements).reduce((result, [key, value]) => result.replaceAll(key, value), text);

cpSync(path.join(root, 'games', '_template'), target, { recursive: true });

/** Replaces placeholders in file contents and names, deepest paths first. */
function rewrite(dir) {
  for (const entry of readdirSync(dir)) {
    const current = path.join(dir, entry);
    if (statSync(current).isDirectory()) {
      rewrite(current);
    } else {
      writeFileSync(current, replaceAll(readFileSync(current, 'utf8')));
    }
    const renamed = path.join(dir, replaceAll(entry));
    if (renamed !== current) {
      renameSync(current, renamed);
    }
  }
}
rewrite(target);

/** Formats the new files with the project's Prettier config: the real names change line lengths. */
async function format(dir) {
  const ignorePath = path.join(root, '.prettierignore');
  for (const entry of readdirSync(dir, { recursive: true })) {
    const file = path.join(dir, entry);
    if (!statSync(file).isFile()) {
      continue;
    }
    const { ignored, inferredParser } = await prettier.getFileInfo(file, { ignorePath });
    if (!ignored && inferredParser) {
      const options = await prettier.resolveConfig(file);
      writeFileSync(file, await prettier.format(readFileSync(file, 'utf8'), { ...options, filepath: file }));
    }
  }
}
await format(target);

// Java is formatted by Spotless (Google Java Format) through Gradle; without a JDK, format later.
const gradle = spawnSync(
  process.execPath,
  [path.join(root, 'scripts', 'gradle.mjs'), `-PffGames=${id}`, `:games:${id}:spotlessApply`, '--quiet'],
  { stdio: ['ignore', 'inherit', 'inherit'] },
);
if (gradle.status !== 0) {
  console.warn('The Java files were not formatted; run `npm run format` before committing.');
}

console.log(`Created games/${id}. Next steps:
  1. Edit the rules in games/${id}/backend and the screen in games/${id}/frontend
  2. cd backend && ./gradlew :games:${id}:test        (scenario tests)
  3. npm run dev:backend  +  npm run dev:frontend      (play it at http://localhost:4200)
  4. npm run build && npm run e2e -- games/${id}        (end-to-end test)
See docs/adding-a-game.md for the full guide.`);
