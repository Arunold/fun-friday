import { defineGameUi } from '@ff/sdk';

import { NumberGuessComponent } from './number-guess.component';

/** Entry point picked up by scripts/generate-game-registry.mjs. */
export default defineGameUi({ component: NumberGuessComponent });
