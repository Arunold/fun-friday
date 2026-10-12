import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/notify.service';
import { GameHistoryEntry, LeaderboardEntry } from '../../core/protocol';

/** Hall of fame: Friday Cup leaderboard and the latest finished games (stored in H2). */
@Component({
  selector: 'ff-history-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <main class="page">
      <a mat-button routerLink="/" class="back"><mat-icon svgIcon="arrow-back" /> Home</a>
      <h1 class="ff-display">Hall of <span class="ff-gradient-text">fame</span> 🏆</h1>

      @if (error(); as message) {
        <div class="ff-card error" role="alert">
          {{ message }} <button mat-button (click)="load(days())">Retry</button>
        </div>
      }

      <div class="layout">
        <section class="ff-card panel">
          <div class="panel-head">
            <h2>Friday Cup leaderboard</h2>
            <mat-button-toggle-group
              hideSingleSelectionIndicator
              [value]="days()"
              (change)="days.set($event.value)"
              aria-label="Period"
            >
              <mat-button-toggle [value]="7">7 days</mat-button-toggle>
              <mat-button-toggle [value]="30">30 days</mat-button-toggle>
              <mat-button-toggle [value]="365">Year</mat-button-toggle>
            </mat-button-toggle-group>
          </div>
          @if (leaderboard(); as entries) {
            @if (entries.length) {
              <ol class="leaderboard ff-stagger">
                @for (entry of entries; track entry.name; let i = $index) {
                  <li [style.--i]="i">
                    <span class="rank">{{ i < 3 ? ['🥇', '🥈', '🥉'][i] : i + 1 }}</span>
                    <span class="avatar" aria-hidden="true">{{ entry.avatar }}</span>
                    <span class="name">{{ entry.name }}</span>
                    <span class="ff-muted stats">{{ entry.wins }} wins · {{ entry.games }} games</span>
                    <b class="points">{{ entry.points }}</b>
                  </li>
                }
              </ol>
            } @else {
              <p class="ff-muted empty">No games in this period yet. Go play one!</p>
            }
          } @else {
            <mat-spinner diameter="32" />
          }
        </section>

        <section class="ff-card panel">
          <h2>Latest games</h2>
          @if (recent(); as games) {
            @if (games.length) {
              <ul class="games">
                @for (game of games; track $index) {
                  <li>
                    <div class="game-head">
                      <b>{{ game.gameName }}</b>
                      <span class="ff-muted">{{ game.finishedAt | date: 'EEE d MMM, HH:mm' }}</span>
                    </div>
                    <div class="podium">
                      @for (player of game.players; track $index) {
                        <span class="ff-pill" [class.winner]="player.placement === 1">
                          {{ player.placement === 1 ? '🥇' : player.placement + '.' }} {{ player.avatar }}
                          {{ player.playerName }} · {{ player.score }}
                        </span>
                      }
                    </div>
                  </li>
                }
              </ul>
            } @else {
              <p class="ff-muted empty">Finished games will show up here.</p>
            }
          } @else {
            <mat-spinner diameter="32" />
          }
        </section>
      </div>
    </main>
  `,
  styles: `
    .page {
      max-width: 1200px;
      margin: 0 auto;
      padding: 24px 32px 48px;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .back {
      align-self: flex-start;
    }
    .layout {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
      align-items: start;
    }
    .panel {
      display: flex;
      flex-direction: column;
      gap: 14px;
    }
    .panel-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .leaderboard,
    .games {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .leaderboard li {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 10px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-high);
    }
    .rank {
      width: 28px;
      text-align: center;
      font-weight: 700;
    }
    .avatar {
      font-size: 22px;
    }
    .name {
      flex: 1;
      font-weight: 700;
    }
    .stats {
      font-size: 13px;
    }
    .points {
      min-width: 40px;
      text-align: right;
      font-size: 18px;
      font-variant-numeric: tabular-nums;
    }
    .games li {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 12px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-high);
    }
    .game-head {
      display: flex;
      justify-content: space-between;
      gap: 12px;
    }
    .podium {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .winner {
      color: var(--mat-sys-tertiary);
    }
    .empty {
      padding: 12px 0;
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class HistoryPage {
  private readonly api = inject(ApiService);
  protected readonly days = signal(30);
  protected readonly leaderboard = signal<LeaderboardEntry[] | null>(null);
  protected readonly recent = signal<GameHistoryEntry[] | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const days = this.days();
      untracked(() => void this.load(days));
    });
  }

  protected async load(days: number): Promise<void> {
    this.error.set(null);
    this.leaderboard.set(null);
    try {
      const [leaderboard, recent] = await Promise.all([this.api.leaderboard(days), this.api.recentGames()]);
      this.leaderboard.set(leaderboard);
      this.recent.set(recent);
    } catch (error) {
      this.error.set(errorMessage(error));
      this.leaderboard.set([]);
      this.recent.set(this.recent() ?? []);
    }
  }
}
