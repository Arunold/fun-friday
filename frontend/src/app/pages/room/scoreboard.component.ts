import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { MemberView } from '../../core/protocol';
import { RoomStore } from '../../core/room.store';
import { CountUp, PlayerChip } from '../../sdk';

/** Competition ranking: equal scores share a rank (1, 1, 3). */
export function rankOf(sorted: readonly MemberView[], index: number): number {
  let i = index;
  while (i > 0 && sorted[i - 1].score === sorted[i].score) {
    i--;
  }
  return i + 1;
}

/** Live scores next to the game, with the points of the revealed turn. */
@Component({
  selector: 'ff-scoreboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip, CountUp],
  template: `
    <h3 class="title">Scores</h3>
    <ol class="list" data-testid="scoreboard">
      @for (player of ranked(); track player.id; let i = $index) {
        <li [class.me]="player.id === meId()" [attr.data-player]="player.name">
          <span class="rank">{{ rank(i) }}</span>
          <ff-player-chip [player]="player" size="sm" />
          <span class="ff-spacer"></span>
          @if (gain(player.id); as points) {
            <span class="gain" [class.negative]="points < 0" animate.enter="ff-enter-pop">
              {{ points > 0 ? '+' : '' }}{{ points }}
            </span>
          }
          <b class="score" [ffCountUp]="player.score"></b>
        </li>
      }
    </ol>
    @if (store.spectators().length; as watching) {
      <p class="watchers">👀 {{ watching }} watching</p>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 10px;
      min-height: 0;
    }
    .title {
      font-size: 14px;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      color: var(--ff-text-muted);
    }
    .list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
      overflow: auto;
    }
    li {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 8px;
      border-radius: 10px;
      font-variant-numeric: tabular-nums;
    }
    li.me {
      background: color-mix(in srgb, var(--mat-sys-primary) 12%, transparent);
    }
    .rank {
      width: 18px;
      text-align: right;
      color: var(--ff-text-muted);
      font-weight: 700;
      font-size: 13px;
    }
    .gain {
      padding: 1px 7px;
      border-radius: 999px;
      background: color-mix(in srgb, var(--ff-success) 20%, transparent);
      color: var(--ff-success);
      font-size: 12px;
      font-weight: 800;
    }
    .gain.negative {
      background: color-mix(in srgb, var(--ff-danger) 20%, transparent);
      color: var(--ff-danger);
    }
    .score {
      min-width: 36px;
      text-align: right;
      font-size: 16px;
    }
    .watchers {
      font-size: 13px;
      color: var(--ff-text-muted);
    }
  `,
})
export class Scoreboard {
  protected readonly store = inject(RoomStore);
  protected readonly meId = computed(() => this.store.you()?.memberId);
  protected readonly ranked = computed(() => [...this.store.players()].sort((a, b) => b.score - a.score));

  protected rank(index: number): number {
    return rankOf(this.ranked(), index);
  }

  /** Points of the turn being revealed. */
  protected gain(playerId: string): number | null {
    if (this.store.phase() !== 'REVEAL') {
      return null;
    }
    return this.store.lastTurn()?.points[playerId] || null;
  }
}
