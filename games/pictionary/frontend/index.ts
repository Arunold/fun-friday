import { defineGameUi } from '@ff/sdk';

import { PictionaryComponent } from './pictionary.component';

/** Entry point picked up by scripts/generate-game-registry.mjs. */
export default defineGameUi({ component: PictionaryComponent });
