import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';

import { RealtimeService } from '../../core/realtime.service';
import { RoomStore } from '../../core/room.store';
import { TimerRing } from '../../sdk';
import { RoomActions } from './room-actions.service';

/** Top bar of the room: game, progress, timer, room code and the room menu. */
@Component({
  selector: 'ff-room-toolbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatDividerModule, MatIconModule, MatMenuModule, MatTooltipModule, TimerRing],
  template: `
    <header class="toolbar">
      <span class="brand" aria-hidden="true">🎉</span>
      @if (store.game(); as game) {
        <div class="game">
          <span class="game-name">{{ game.icon }} {{ game.name }}</span>
          @if (progressLabel(); as label) {
            <span class="ff-pill progress" data-testid="progress">{{ label }}</span>
          }
          @if (watching()) {
            <span class="ff-pill watching">👀 Watching</span>
          }
        </div>
      }

      <span class="ff-spacer"></span>

      @if (showTimer()) {
        <ff-timer-ring [remainingMs]="store.timeLeftMs()" [totalMs]="store.phaseDurationMs()" [size]="44" />
      }

      @if (realtime.state() !== 'connected') {
        <mat-icon svgIcon="offline" class="offline" matTooltip="Reconnecting…" aria-label="Offline" />
      }

      <button
        type="button"
        class="code"
        (click)="actions.copyInvite()"
        matTooltip="Copy invite link"
        [attr.aria-label]="'Room code ' + store.code() + '. Copy invite link'"
        data-testid="room-code"
      >
        <span class="ff-mono">{{ store.code() }}</span>
        <mat-icon svgIcon="copy" />
      </button>

      <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Room menu" data-testid="room-menu">
        <mat-icon svgIcon="more-vert" />
      </button>
      <mat-menu #menu="matMenu" xPosition="before">
        <button mat-menu-item (click)="actions.copyInvite()"><mat-icon svgIcon="link" /> Copy invite link</button>
        <mat-divider />
        <button mat-menu-item (click)="actions.leave()" data-testid="leave-room">
          <mat-icon svgIcon="logout" /> Leave room
        </button>
        @if (store.isHost()) {
          <button mat-menu-item class="danger" (click)="actions.end()" data-testid="end-room">
            <mat-icon svgIcon="close" /> End room for everyone
          </button>
        }
      </mat-menu>
    </header>
  `,
  styles: `
    .toolbar {
      height: var(--ff-toolbar-height);
      flex: none;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 0 16px 0 20px;
      border-bottom: 1px solid var(--ff-border);
      background: color-mix(in srgb, var(--ff-bg) 70%, transparent);
      backdrop-filter: blur(10px);
    }
    .brand {
      font-size: 24px;
    }
    .game {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 0;
    }
    .game-name {
      font-weight: 700;
      font-size: 17px;
      white-space: nowrap;
    }
    .watching {
      color: var(--mat-sys-tertiary);
    }
    .offline {
      color: var(--ff-warning);
    }
    .code {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 6px 12px;
      border: 1px solid var(--ff-border);
      border-radius: 12px;
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);
      font: inherit;
      font-size: 17px;
      font-weight: 700;
      letter-spacing: 0.15em;
      cursor: pointer;
    }
    .code:hover {
      border-color: var(--mat-sys-primary);
    }
    .code mat-icon {
      width: 18px;
      height: 18px;
      color: var(--ff-text-muted);
    }
    .danger {
      color: var(--mat-sys-error);
    }
  `,
})
export class RoomToolbar {
  protected readonly store = inject(RoomStore);
  protected readonly realtime = inject(RealtimeService);
  protected readonly actions = inject(RoomActions);

  protected readonly showTimer = computed(() => {
    const phase = this.store.phase();
    return (phase === 'PLAYING' || phase === 'REVEAL') && this.store.timeLeftMs() != null;
  });
  protected readonly watching = computed(() => {
    const phase = this.store.phase();
    return (phase === 'PLAYING' || phase === 'REVEAL') && !this.store.isPlaying();
  });
  protected readonly progressLabel = computed(() => {
    const progress = this.store.progress();
    const phase = this.store.phase();
    if (!progress || (phase !== 'PLAYING' && phase !== 'REVEAL')) {
      return null;
    }
    const round = `Round ${progress.round} / ${progress.totalRounds}`;
    return progress.turnsInRound > 1 ? `${round} · Turn ${progress.turn} / ${progress.turnsInRound}` : round;
  });
}
