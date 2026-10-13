import { defineGameUi } from '@ff/sdk';

import { SnakeArenaComponent } from './snake-arena.component';

/** Entry point picked up by scripts/generate-game-registry.mjs. */
export default defineGameUi({ component: SnakeArenaComponent });
