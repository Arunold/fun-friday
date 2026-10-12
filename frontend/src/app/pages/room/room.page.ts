import { ChangeDetectionStrategy, Component, computed, effect, inject, input, untracked } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';

import { RoomStore } from '../../core/room.store';
import { normalizeRoomCode } from '../../core/util';
import { Countdown } from './countdown.component';
import { Lobby } from './lobby.component';
import { Results } from './results.component';
import { RoomActions } from './room-actions.service';
import { RoomToolbar } from './room-toolbar.component';
import { Stage } from './stage.component';

/**
 * The room: one page for the whole life of a room. What it shows follows the room phase pushed by
 * the server — lobby, countdown, game stage (turns and reveals) and podium. Refreshing the page
 * resumes the seat and lands in the same phase.
 */
@Component({
  selector: 'ff-room-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatProgressSpinnerModule, RoomToolbar, Lobby, Countdown, Stage, Results],
  providers: [RoomActions],
  template: `
    @if (store.snapshot(); as room) {
      <div class="room ff-fullscreen" [attr.data-phase]="room.phase">
        <ff-room-toolbar />
        <div class="body">
          @switch (room.phase) {
            @case ('LOBBY') {
              <ff-lobby />
            }
            @case ('STARTING') {
              <ff-lobby />
              <ff-countdown />
            }
            @case ('FINISHED') {
              <ff-results />
            }
            @default {
              <ff-stage />
            }
          }
        </div>
      </div>
    } @else {
      <div class="loading ff-fullscreen" role="status">
        <mat-spinner diameter="40" />
        <p>
          Joining room <span class="ff-mono">{{ normalizedCode() }}</span
          >…
        </p>
      </div>
    }
  `,
  styles: `
    .body {
      position: relative;
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    .loading {
      align-items: center;
      justify-content: center;
      gap: 16px;
      color: var(--ff-text-muted);
    }
  `,
})
export class RoomPage {
  /** Route parameter (`/room/:code`). */
  readonly code = input.required<string>();

  protected readonly store = inject(RoomStore);
  private readonly router = inject(Router);
  private readonly title = inject(Title);
  protected readonly normalizedCode = computed(() => normalizeRoomCode(this.code()));

  constructor() {
    // Without a seat in this room, go to its join page (e.g. someone opened a shared /room link).
    effect(() => {
      const code = this.normalizedCode();
      untracked(() => {
        if (!this.store.hasSessionFor(code)) {
          void this.router.navigate(['/join', code], { replaceUrl: true });
        }
      });
    });
    effect(() => {
      const room = this.store.snapshot();
      if (room) {
        this.title.setTitle(`${room.game.icon} ${room.game.name} · ${room.code} · Fun Friday`);
      }
    });
  }
}
