import { defineGameUi } from '@ff/sdk';

import { MemoryMatchComponent } from './memory-match.component';

/** Entry point picked up by scripts/generate-game-registry.mjs. */
export default defineGameUi({ component: MemoryMatchComponent });
