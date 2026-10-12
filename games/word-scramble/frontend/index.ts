import { defineGameUi } from '@ff/sdk';

import { WordScrambleComponent } from './word-scramble.component';

/** Entry point picked up by scripts/generate-game-registry.mjs. */
export default defineGameUi({ component: WordScrambleComponent });
