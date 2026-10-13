import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, computed, effect, inject, untracked } from '@angular/core';

import { RoomStore } from '../../core/room.store';
import { formatDuration } from '../../core/util';

const RADIUS = 15;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/** At most this much time left: the turn timer turns amber. */
const URGENT_MS = 10_000;
/** At most this much time left: it turns red, pulses and ticks every second. */
const CRITICAL_MS = 5_000;

/** How alarming the time left should look. */
export type TimerUrgency = 'calm' | 'urgent' | 'critical';

/**
 * Amber in the last 10 seconds and red in the last 5 — but never for most of a short phase (a
 * 10-second "pick a word" phase turns amber at 5 s and red at 3 s, not right away).
 */
export function timerUrgency(remainingMs: number, totalMs: number | null): TimerUrgency {
  const total = totalMs ?? Number.POSITIVE_INFINITY;
  if (remainingMs <= Math.min(CRITICAL_MS, total * 0.3)) {
    return 'critical';
  }
  if (remainingMs <= Math.min(URGENT_MS, total * 0.5)) {
    return 'urgent';
  }
  return 'calm';
}

/** Seconds at which screen readers hear how much time is left. */
const ANNOUNCE_AT = new Set([30, 10, 5]);

/**
 * The turn timer in the room toolbar: big digits and a draining ring, amber in the last 10 seconds,
 * red with a pulse and a "tick" per second in the last 5. During the reveal it calmly counts down to
 * the next turn instead (no alarm colours). Screen readers hear "10 seconds left" and so on.
 */
@Component({
  selector: 'ff-turn-timer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 36 36" aria-hidden="true">
      <circle class="track" cx="18" cy="18" [attr.r]="radius" />
      <circle
        class="bar"
        cx="18"
        cy="18"
        [attr.r]="radius"
        [attr.stroke-dasharray]="circumference"
        [style.stroke-dashoffset]="dashOffset()"
      />
    </svg>
    <!-- The keyframes alternate with the second (odd/even), which restarts the "tick" animation. -->
    <span class="digits" data-testid="turn-timer-digits" [class.odd]="seconds() % 2 === 1">{{ label() }}</span>
    <span class="caption">{{ caption() }}</span>
  `,
  host: {
    role: 'timer',
    '[attr.aria-label]': 'ariaLabel()',
    '[class.revealing]': 'revealing()',
    '[class.urgent]': 'urgent()',
    '[class.critical]': 'critical()',
    'data-testid': 'turn-timer',
  },
  styles: `
    :host {
      --timer-color: var(--mat-sys-primary);
      display: inline-flex;
      align-items: center;
      gap: 10px;
      height: 48px;
      padding: 0 18px 0 8px;
      border-radius: 999px;
      border: 1px solid color-mix(in srgb, var(--timer-color) 45%, transparent);
      background: color-mix(in srgb, var(--timer-color) 14%, var(--mat-sys-surface-container-high));
      transition:
        background-color 300ms,
        border-color 300ms,
        box-shadow 300ms;
    }
    :host(.urgent) {
      --timer-color: var(--ff-warning);
    }
    :host(.critical) {
      --timer-color: var(--ff-danger);
      box-shadow: 0 0 22px color-mix(in srgb, var(--ff-danger) 45%, transparent);
      animation: ff-pulse 1000ms ease-in-out infinite;
    }
    :host(.revealing) {
      --timer-color: var(--ff-text-muted);
    }
    svg {
      width: 32px;
      height: 32px;
      flex: none;
      rotate: -90deg;
    }
    circle {
      fill: none;
      stroke-width: 4;
    }
    .track {
      stroke: color-mix(in srgb, var(--timer-color) 22%, transparent);
    }
    .bar {
      stroke: var(--timer-color);
      stroke-linecap: round;
      transition:
        stroke-dashoffset 250ms linear,
        stroke 300ms;
    }
    .digits {
      min-width: 4ch;
      text-align: center;
      font-size: 28px;
      font-weight: 800;
      line-height: 1;
      font-variant-numeric: tabular-nums;
      color: var(--mat-sys-on-surface);
    }
    :host(.urgent) .digits,
    :host(.critical) .digits {
      color: var(--timer-color);
    }
    :host(.critical) .digits {
      animation: ff-timer-tick 1000ms var(--ff-ease-out);
    }
    :host(.critical) .digits.odd {
      animation-name: ff-timer-tick-odd;
    }
    :host(.revealing) .digits {
      color: var(--ff-text-muted);
    }
    .caption {
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--ff-text-muted);
      white-space: nowrap;
    }
    @keyframes ff-timer-tick {
      0% {
        scale: 1.4;
      }
      35% {
        scale: 1;
      }
    }
    @keyframes ff-timer-tick-odd {
      0% {
        scale: 1.4;
      }
      35% {
        scale: 1;
      }
    }
  `,
})
export class TurnTimer {
  private readonly store = inject(RoomStore);
  private readonly announcer = inject(LiveAnnouncer);

  protected readonly radius = RADIUS;
  protected readonly circumference = CIRCUMFERENCE;

  protected readonly revealing = computed(() => this.store.phase() === 'REVEAL');
  private readonly remaining = computed(() => this.store.timeLeftMs() ?? 0);
  protected readonly seconds = computed(() => Math.ceil(this.remaining() / 1000));

  private readonly urgency = computed<TimerUrgency>(() =>
    this.revealing() ? 'calm' : timerUrgency(this.remaining(), this.store.phaseDurationMs()),
  );
  protected readonly urgent = computed(() => this.urgency() === 'urgent');
  protected readonly critical = computed(() => this.urgency() === 'critical');
  protected readonly label = computed(() => formatDuration(this.remaining()));
  protected readonly dashOffset = computed(() => {
    const total = this.store.phaseDurationMs();
    const fraction = total ? Math.min(1, Math.max(0, this.remaining() / total)) : 1;
    return CIRCUMFERENCE * (1 - fraction);
  });
  protected readonly caption = computed(() => {
    if (!this.revealing()) {
      return 'left';
    }
    const progress = this.store.progress();
    const last = !!progress && progress.round >= progress.totalRounds && progress.turn >= progress.turnsInRound;
    return last ? 'results' : 'next turn';
  });
  protected readonly ariaLabel = computed(() =>
    this.revealing() ? `Next turn in ${this.seconds()} seconds` : `${this.seconds()} seconds left`,
  );

  /** Last announcement, so each threshold is announced once per turn. */
  private announced = '';

  constructor() {
    effect(() => {
      const seconds = this.seconds();
      if (this.revealing() || !ANNOUNCE_AT.has(seconds)) {
        return;
      }
      // "10 seconds left" at the very start of a 10-second turn would only be noise.
      if ((this.store.phaseDurationMs() ?? 0) < seconds * 1500) {
        return;
      }
      const progress = this.store.progress();
      const key = `${progress?.round}/${progress?.turn}/${seconds}`;
      if (key !== this.announced) {
        this.announced = key;
        untracked(() => void this.announcer.announce(`${seconds} seconds left`));
      }
    });
  }
}
