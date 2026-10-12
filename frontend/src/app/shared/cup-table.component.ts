import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { CupEntryView } from '../core/protocol';

/** The room's "Friday Cup": points across all games played in this room (10/6/4, 2 for playing). */
@Component({
  selector: 'ff-cup-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <table>
      <caption class="ff-visually-hidden">
        Friday Cup standings
      </caption>
      <thead>
        <tr>
          <th scope="col">#</th>
          <th scope="col">Player</th>
          <th scope="col" class="num">Wins</th>
          <th scope="col" class="num">Games</th>
          <th scope="col" class="num">Points</th>
        </tr>
      </thead>
      <tbody>
        @for (entry of entries(); track entry.name; let i = $index) {
          <tr [class.leader]="i === 0">
            <td class="rank">{{ i === 0 ? '🏆' : i + 1 }}</td>
            <td class="who">
              <span aria-hidden="true">{{ entry.avatar }}</span>
              {{ entry.name }}
            </td>
            <td class="num">{{ entry.wins }}</td>
            <td class="num">{{ entry.games }}</td>
            <td class="num points">{{ entry.points }}</td>
          </tr>
        }
      </tbody>
    </table>
  `,
  styles: `
    table {
      width: 100%;
      border-collapse: collapse;
      font-variant-numeric: tabular-nums;
    }
    th {
      font-size: 12px;
      font-weight: 600;
      color: var(--ff-text-muted);
      text-align: left;
      padding: 4px 8px;
    }
    td {
      padding: 6px 8px;
      border-top: 1px solid var(--ff-border);
    }
    .num {
      text-align: right;
    }
    .rank {
      width: 32px;
    }
    .who {
      font-weight: 600;
    }
    .points {
      font-weight: 800;
    }
    .leader td {
      color: var(--mat-sys-tertiary);
    }
  `,
})
export class CupTable {
  readonly entries = input.required<readonly CupEntryView[]>();
}
