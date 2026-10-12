import { defineGameUi } from '@ff/sdk';

import { TriviaBlitzComponent } from './trivia-blitz.component';

/** Entry point picked up by scripts/generate-game-registry.mjs. */
export default defineGameUi({ component: TriviaBlitzComponent });
