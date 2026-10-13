import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal, untracked } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { injectGameApi } from '@ff/sdk';

import { BackseatDriversView } from './backseat-drivers.types';
import { BusFeed } from './bus-feed';
import { SIGNALS, SignalInfo } from './controls';

/** How long a signal stays on screen. */
const SHOW_MS = 1600;

/**
 * A silent navigator's signal, flashed big over everybody's screen for a moment (older signals
 * from a snapshot, e.g. after a reconnect, are not shown again).
 */
@Component({
  selector: 'ff-bd-signal-flash',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    @if (shown(); as item) {
      <div
        class="flash"
        role="status"
        animate.enter="ff-enter-pop"
        animate.leave="ff-leave-fade"
        data-testid="signal-flash"
      >
        @if (item.svgIcon) {
          <mat-icon [svgIcon]="item.svgIcon" />
        } @else {
          <span class="emoji" aria-hidden="true">{{ item.emoji }}</span>
        }
        <b>{{ item.label }}!</b>
      </div>
    }
  `,
  styles: `
    :host {
      position: absolute;
      top: 18px;
      left: 50%;
      translate: -50% 0;
      pointer-events: none;
    }
    .flash {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 24px 10px 16px;
      border-radius: 999px;
      border: 2px solid var(--mat-sys-tertiary);
      background: color-mix(in srgb, var(--mat-sys-tertiary-container) 92%, transparent);
      color: var(--mat-sys-on-tertiary-container);
      box-shadow: var(--ff-shadow);
    }
    mat-icon {
      width: 44px;
      height: 44px;
    }
    .emoji {
      font-size: 38px;
      line-height: 44px;
    }
    b {
      font-size: 30px;
      font-weight: 800;
      text-transform: uppercase;
    }
  `,
})
export class SignalFlash {
  protected readonly shown = signal<SignalInfo | null>(null);

  private readonly api = injectGameApi<BackseatDriversView>();
  private readonly feed = inject(BusFeed);
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      const latest = this.feed.lastSignal();
      untracked(() => {
        if (latest && this.api.now() - latest.at < SHOW_MS) {
          this.shown.set(SIGNALS.find((item) => item.kind === latest.kind) ?? null);
          clearTimeout(this.timer);
          this.timer = setTimeout(() => this.shown.set(null), SHOW_MS);
        }
      });
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }
}
