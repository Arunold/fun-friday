import { defineGameUi } from '@ff/sdk';

import { SlidingPuzzleComponent } from './sliding-puzzle.component';

/** Entry point picked up by scripts/generate-game-registry.mjs. */
export default defineGameUi({ component: SlidingPuzzleComponent });
