/**
 * Pre-commit checks on staged files (run by .husky/pre-commit).
 * Java is formatted once per commit with Spotless (Google Java Format, AOSP style).
 */
export default {
  '*.{ts,mjs,js}': ['prettier --write', 'eslint --fix --no-warn-ignored'],
  '*.{html,scss,css,json,md,yml,yaml}': ['prettier --write'],
  '*.java': () => 'node scripts/gradle.mjs spotlessApply --quiet',
};
