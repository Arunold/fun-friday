import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CountUp, GamePlayer } from '@ff/sdk';

import { medal, ordinal } from './typing';

/** One lane of the race track. */
export interface TrackLane {
  player: GamePlayer;
  /** Correct prefix in percent. */
  progress: number;
  finished: boolean;
  rank?: number;
  wpm?: number;
  /** The viewer's own lane: highlighted, and it follows the keystrokes without delay. */
  me: boolean;
}

/**
 * Everyone's progress as lanes: a bar in the player's colour with their avatar riding at the
 * front, a checkered finish line, and medal plus speed once they are through.
 */
@Component({
  selector: 'ff-race-track',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CountUp],
  templateUrl: './race-track.component.html',
  styleUrl: './race-track.component.scss',
  host: {
    role: 'list',
    'aria-label': 'Race progress',
    '[attr.data-columns]': 'columns()',
  },
})
export class RaceTrack {
  readonly lanes = input.required<readonly TrackLane[]>();

  /** One column for a handful of players, more (and more compact) for big groups. */
  protected readonly columns = computed(() => {
    const count = this.lanes().length;
    return count > 12 ? 3 : count > 6 ? 2 : 1;
  });

  protected place(lane: TrackLane): string {
    return medal(lane.rank) || ordinal(lane.rank ?? 0);
  }
}
