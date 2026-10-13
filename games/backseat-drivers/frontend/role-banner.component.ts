import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { injectGameApi } from '@ff/sdk';

import { BackseatDriversView, Control, LevelView, SignalKind, Variation } from './backseat-drivers.types';
import { CONTROL_INFO, ControlInfo, SIGNALS } from './controls';

const RING_RADIUS = 21;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;
/** The siren sounds (visually) this long before a shuffle. */
const SIREN_MS = 2000;

/** One of the viewer's controls as a big button. */
interface ControlButton {
  control: Control;
  info: ControlInfo;
  /** The full name, or the short one when the banner is crowded. */
  label: string;
  keys: readonly string[];
}

/**
 * The top banner: who you are on the bus right now — your control(s) as big buttons with their
 * keys, or the navigator's call to shout (or, silent, to signal) — and the countdown ring to the
 * next SEAT SHUFFLE, which turns into a flashing siren for its last two seconds.
 */
@Component({
  selector: 'ff-bd-role-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  templateUrl: './role-banner.component.html',
  styleUrl: './role-banner.component.scss',
  host: {
    'data-testid': 'role-banner',
    '[attr.data-role]': 'role()',
    '[class.siren]': 'siren()',
    '[style.--me]': 'myColor()',
  },
})
export class RoleBanner {
  readonly level = input.required<LevelView>();
  readonly variation = input.required<Variation>();
  readonly shuffleSeconds = input.required<number>();
  /** Controls the viewer holds down right now (keys or buttons). */
  readonly held = input<ReadonlySet<Control>>(new Set());
  /** Inputs are accepted right now. */
  readonly active = input(false);

  /** A control's on-screen button went down / up. */
  readonly controlDown = output<Control>();
  readonly controlUp = output<Control>();
  /** A silent navigator's signal button. */
  readonly signal = output<SignalKind>();

  protected readonly api = injectGameApi<BackseatDriversView>();
  protected readonly signals = SIGNALS;
  protected readonly ringLength = RING_LENGTH;
  protected readonly ringRadius = RING_RADIUS;

  protected readonly role = computed(() => {
    const level = this.level();
    if (!this.api.isPlaying()) {
      return 'spectator';
    }
    if (level.navigator) {
      return 'navigator';
    }
    return level.myControls?.length ? 'driver' : 'waiting';
  });
  protected readonly silent = computed(() => this.variation() === 'silent-navigator');
  protected readonly myColor = computed(() => this.api.me()?.color ?? 'var(--mat-sys-primary)');

  protected readonly buttons = computed<ControlButton[]>(() => {
    const controls = (this.level().myControls ?? []).filter((control) => control !== 'NAVIGATOR');
    return controls.map((control, index) => ({
      control,
      info: CONTROL_INFO[control],
      label: controls.length > 2 ? CONTROL_INFO[control].short : CONTROL_INFO[control].label,
      keys: index === 0 ? [...CONTROL_INFO[control].keys, 'Space'] : CONTROL_INFO[control].keys,
    }));
  });

  /** Milliseconds to the next shuffle (re-evaluated with the platform's 4 Hz clock). */
  private readonly shuffleIn = computed(() => {
    this.api.timeLeftMs();
    const at = this.level().nextShuffleAt;
    return at ? Math.max(0, at - this.api.now()) : null;
  });
  protected readonly seconds = computed(() => Math.ceil((this.shuffleIn() ?? 0) / 1000));
  protected readonly siren = computed(() => {
    const left = this.shuffleIn();
    return this.level().shuffleWarning || (left != null && left <= SIREN_MS);
  });
  protected readonly ringOffset = computed(() => {
    const total = this.shuffleSeconds() * 1000;
    const left = this.shuffleIn() ?? total;
    return RING_LENGTH * (1 - Math.min(1, left / total));
  });

  protected press(event: PointerEvent, control: Control): void {
    if (event.button !== 0) {
      return;
    }
    event.preventDefault();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.controlDown.emit(control);
  }

  protected release(control: Control): void {
    this.controlUp.emit(control);
  }
}
