import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { formatDuration } from '../core/util';

const RADIUS = 19;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * A countdown ring with the seconds left in the middle. Turns amber under 10 s and pulses under
 * 5 s (scale-only animation, so it never shifts the layout).
 *
 * ```html
 * <ff-timer-ring [remainingMs]="api.timeLeftMs()" [totalMs]="api.phaseDurationMs()" />
 * ```
 */
@Component({
  selector: 'ff-timer-ring',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 44 44" aria-hidden="true">
      <circle class="track" cx="22" cy="22" [attr.r]="radius" />
      <circle
        class="bar"
        cx="22"
        cy="22"
        [attr.r]="radius"
        [attr.stroke-dasharray]="circumference"
        [style.stroke-dashoffset]="dashOffset()"
      />
    </svg>
    <span class="label">{{ label() }}</span>
  `,
  host: {
    role: 'timer',
    '[attr.aria-label]': 'ariaLabel()',
    '[class.urgent]': 'urgent()',
    '[class.critical]': 'critical()',
    '[style.--size.px]': 'size()',
  },
  styles: `
    :host {
      --size: 48px;
      position: relative;
      display: inline-grid;
      place-items: center;
      width: var(--size);
      height: var(--size);
      flex: none;
      color: var(--mat-sys-primary);
    }
    :host(.urgent) {
      color: var(--ff-warning);
    }
    :host(.critical) {
      color: var(--ff-danger);
      animation: ff-pulse 900ms ease-in-out infinite;
    }
    svg {
      position: absolute;
      inset: 0;
      rotate: -90deg;
    }
    circle {
      fill: none;
      stroke-width: 4;
    }
    .track {
      stroke: var(--mat-sys-surface-container-highest);
    }
    .bar {
      stroke: currentColor;
      stroke-linecap: round;
      transition:
        stroke-dashoffset 250ms linear,
        stroke 300ms;
    }
    .label {
      font-weight: 800;
      font-size: calc(var(--size) * 0.3);
      font-variant-numeric: tabular-nums;
      color: var(--mat-sys-on-surface);
    }
  `,
})
export class TimerRing {
  /** Time left; `null` hides the number. */
  readonly remainingMs = input<number | null>(null);
  /** Full length of the phase; the ring shows `remainingMs / totalMs`. */
  readonly totalMs = input<number | null>(null);
  readonly size = input(48);

  protected readonly radius = RADIUS;
  protected readonly circumference = CIRCUMFERENCE;

  private readonly fraction = computed(() => {
    const remaining = this.remainingMs();
    const total = this.totalMs();
    if (remaining == null || !total) {
      return 1;
    }
    return Math.min(1, Math.max(0, remaining / total));
  });

  protected readonly dashOffset = computed(() => CIRCUMFERENCE * (1 - this.fraction()));
  protected readonly label = computed(() => {
    const remaining = this.remainingMs();
    return remaining == null ? '–' : formatDuration(remaining, true);
  });
  protected readonly ariaLabel = computed(() => {
    const remaining = this.remainingMs();
    return remaining == null ? 'No time limit' : `${Math.ceil(remaining / 1000)} seconds left`;
  });
  protected readonly urgent = computed(() => {
    const remaining = this.remainingMs();
    return remaining != null && remaining > 0 && remaining <= 10_000;
  });
  protected readonly critical = computed(() => {
    const remaining = this.remainingMs();
    return remaining != null && remaining > 0 && remaining <= 5_000;
  });
}
