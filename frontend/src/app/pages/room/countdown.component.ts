import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { RoomStore } from '../../core/room.store';

/** Full-screen 3-2-1 before the first turn. */
@Component({
  selector: 'ff-countdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="overlay" role="alert" animate.enter="ff-enter-fade" animate.leave="ff-leave-fade">
      <span class="ff-eyebrow">{{ store.game()?.icon }} {{ store.game()?.name }}</span>
      <!-- The keyframes alternate with the number (odd/even), which restarts the animation. -->
      <span class="number" data-testid="countdown" [class.odd]="seconds() % 2 === 1">{{ seconds() }}</span>
      <span class="get-ready">Get ready!</span>
    </div>
  `,
  styles: `
    .overlay {
      position: absolute;
      inset: 0;
      z-index: 20;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      background: color-mix(in srgb, var(--ff-bg) 82%, transparent);
      backdrop-filter: blur(6px);
    }
    .number {
      font-size: 160px;
      font-weight: 900;
      line-height: 1;
      background: linear-gradient(135deg, var(--mat-sys-primary), var(--mat-sys-tertiary));
      -webkit-background-clip: text;
      background-clip: text;
      color: transparent;
      animation: count 900ms var(--ff-ease-spring) both;
    }
    .number.odd {
      animation-name: count-odd;
    }
    .get-ready {
      font-size: 20px;
      font-weight: 700;
      color: var(--ff-text-muted);
    }
    @keyframes count {
      0% {
        opacity: 0;
        scale: 1.8;
      }
      30% {
        opacity: 1;
        scale: 1;
      }
      85% {
        opacity: 1;
      }
      100% {
        opacity: 0.2;
        scale: 0.9;
      }
    }
    @keyframes count-odd {
      0% {
        opacity: 0;
        scale: 1.8;
      }
      30% {
        opacity: 1;
        scale: 1;
      }
      85% {
        opacity: 1;
      }
      100% {
        opacity: 0.2;
        scale: 0.9;
      }
    }
  `,
})
export class Countdown {
  protected readonly store = inject(RoomStore);
  protected readonly seconds = computed(() => Math.max(1, Math.ceil((this.store.timeLeftMs() ?? 0) / 1000)));
}
