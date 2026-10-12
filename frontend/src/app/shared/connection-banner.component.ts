import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { RealtimeService } from '../core/realtime.service';
import { RoomStore } from '../core/room.store';

/** Shows "Reconnecting…" while the WebSocket is down (after a short delay to avoid flicker). */
@Component({
  selector: 'ff-connection-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatProgressSpinnerModule],
  template: `
    @if (visible()) {
      <div class="banner" role="status" animate.enter="ff-enter-fade-up" animate.leave="ff-leave-fade">
        <mat-spinner diameter="16" strokeWidth="2" />
        <span>{{ message() }}</span>
      </div>
    }
  `,
  styles: `
    .banner {
      position: fixed;
      top: 12px;
      left: 50%;
      translate: -50% 0;
      z-index: 1000;
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 16px;
      border-radius: 999px;
      background: var(--mat-sys-inverse-surface);
      color: var(--mat-sys-inverse-on-surface);
      box-shadow: var(--ff-shadow);
      font-size: 14px;
      font-weight: 600;
    }
  `,
})
export class ConnectionBanner {
  private readonly realtime = inject(RealtimeService);
  private readonly store = inject(RoomStore);
  protected readonly visible = signal(false);
  protected readonly message = signal('Connecting…');

  constructor() {
    let timer: ReturnType<typeof setTimeout> | undefined;
    effect(() => {
      const state = this.realtime.state();
      const resuming = this.store.resuming();
      clearTimeout(timer);
      if (state === 'connected' && !resuming) {
        this.visible.set(false);
        return;
      }
      this.message.set(
        state === 'reconnecting'
          ? 'Connection lost — reconnecting…'
          : resuming
            ? 'Rejoining your room…'
            : 'Connecting…',
      );
      timer = setTimeout(() => this.visible.set(true), state === 'reconnecting' ? 600 : 1500);
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(timer));
  }
}
