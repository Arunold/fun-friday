import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { StandingView } from '../../core/protocol';
import { RoomStore } from '../../core/room.store';
import { CountUp } from '../../sdk';
import { Confetti } from '../../shared/confetti.component';
import { CupTable } from '../../shared/cup-table.component';
import { RoomActions } from './room-actions.service';

interface PodiumPlace {
  rank: number;
  players: StandingView[];
}

/** The podium after a game: ranking with ties, awards, per-round points and the Friday Cup. */
@Component({
  selector: 'ff-results',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, CountUp, Confetti, CupTable],
  templateUrl: './results.component.html',
  styleUrl: './results.component.scss',
})
export class Results {
  protected readonly store = inject(RoomStore);
  protected readonly actions = inject(RoomActions);

  protected readonly standings = computed(() => this.store.results()?.standings ?? []);
  protected readonly awards = computed(() => this.store.results()?.awards ?? []);
  protected readonly winners = computed(() => this.standings().filter((s) => s.rank === 1));

  protected readonly headline = computed(() => {
    const winners = this.winners();
    if (winners.length === 0) {
      return 'Game over';
    }
    if (winners.length === 1) {
      return winners[0].playerId === this.store.you()?.memberId ? 'You won! 🎉' : `${winners[0].name} wins!`;
    }
    return `It's a tie: ${winners.map((w) => w.name).join(' & ')}!`;
  });

  /** Podium columns in visual order (2nd, 1st, 3rd); tied players share a column. */
  protected readonly podium = computed<PodiumPlace[]>(() => {
    const byRank = new Map<number, StandingView[]>();
    for (const standing of this.standings()) {
      if (standing.rank <= 3) {
        byRank.set(standing.rank, [...(byRank.get(standing.rank) ?? []), standing]);
      }
    }
    return [2, 1, 3].filter((rank) => byRank.has(rank)).map((rank) => ({ rank, players: byRank.get(rank)! }));
  });

  /** Points per round for each player (sum of the round's turns). */
  protected readonly rounds = computed(() => {
    const history = this.store.snapshot()?.history ?? [];
    const rounds = [...new Set(history.map((turn) => turn.round))].sort((a, b) => a - b);
    const points = (playerId: string, round: number) =>
      history.filter((turn) => turn.round === round).reduce((sum, turn) => sum + (turn.points[playerId] ?? 0), 0);
    return { rounds, points };
  });

  protected medal(rank: number): string {
    return rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : `${rank}.`;
  }

  protected playAgain(): void {
    void this.actions.run(() => this.store.playAgain());
  }
}
