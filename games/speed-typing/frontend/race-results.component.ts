import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { GamePlayer, PlayerChip } from '@ff/sdk';

import { ResultRow } from './speed-typing.types';
import { medal, ordinal, seconds } from './typing';

/** A row of the final table with its player. */
export interface ResultLine {
  row: ResultRow;
  player: GamePlayer;
  /** The viewer's own row (highlighted). */
  me: boolean;
}

/**
 * The reveal: finishers by place with time, speed and accuracy, then everyone who ran out of
 * time with how far they got. Points are on the scoreboard next to the stage.
 */
@Component({
  selector: 'ff-race-results',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip],
  templateUrl: './race-results.component.html',
  styleUrl: './race-results.component.scss',
})
export class RaceResults {
  readonly lines = input.required<readonly ResultLine[]>();
  readonly minAccuracy = input.required<number>();

  protected readonly seconds = seconds;

  protected place(row: ResultRow): string {
    return row.rank ? medal(row.rank) || ordinal(row.rank) : 'DNF';
  }
}
