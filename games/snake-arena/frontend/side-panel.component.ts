import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { PlayerChip } from '@ff/sdk';

import { FeedLine, LeaderRow } from './panel-model';
import { PowerType } from './snake-arena.types';

const POWER_ICONS: Record<PowerType, string> = { BOOST: '⚡', GHOST: '👻', MAGNET: '🧲' };
const POWER_NAMES: Record<PowerType, string> = { BOOST: 'Boost', GHOST: 'Ghost', MAGNET: 'Magnet' };

/** From this many players on, the leaderboard rows get denser so everyone fits. */
const DENSE_FROM = 9;

/**
 * The slim panel next to the arena: the round's live leaderboard (points, length, respawn
 * countdowns, active power-ups), the kill feed, the controls hint and the power-up legend.
 * The leaderboard always shows everyone; the rest gives way when the window is short.
 * Purely presentational; the arena component feeds it.
 */
@Component({
  selector: 'ff-snake-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip],
  templateUrl: './side-panel.component.html',
  styleUrl: './side-panel.component.scss',
  host: { '[class.dense]': 'dense()' },
})
export class SidePanel {
  readonly round = input(1);
  /** E.g. "Small arena". */
  readonly arena = input('');
  /** The variation, e.g. "Shrinking arena". */
  readonly mode = input('');
  readonly rows = input.required<LeaderRow[]>();
  readonly feed = input.required<FeedLine[]>();
  /** Show the keyboard hint (players during a round). */
  readonly controls = input(false);
  /** Show the power-up legend. */
  readonly powers = input(false);

  protected readonly dense = computed(() => this.rows().length >= DENSE_FROM);

  protected powerIcon(power: PowerType): string {
    return POWER_ICONS[power];
  }

  protected powerName(power: PowerType): string {
    return POWER_NAMES[power];
  }
}
