/**
 * Fun Friday frontend game SDK — import from `@ff/sdk` in `games/<id>/frontend`.
 *
 * - {@link defineGameUi}: the default export of a game's `frontend/index.ts`
 * - {@link injectGameApi}: typed access to the game view, players, timer and actions
 * - Building blocks that match the platform look: {@link PlayerChip}, {@link TimerRing},
 *   {@link CountUp}, {@link Autofocus}
 *
 * See docs/adding-a-game.md for a walkthrough.
 */
export * from './game-api';
export * from './player-chip.component';
export * from './timer-ring.component';
export * from './directives';
export { formatDuration } from '../core/util';
export type { IconName } from '../core/icons';
