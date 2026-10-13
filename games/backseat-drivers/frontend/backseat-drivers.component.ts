import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';

import { injectGameApi } from '@ff/sdk';

import { BackseatDriversView, Control, SignalKind } from './backseat-drivers.types';
import { BusFeed } from './bus-feed';
import { CONTROL_INFO, InputTracker, controlForKey, isTyping, signalForKey } from './controls';
import { MapView } from './map-view.component';
import { MvpVote } from './mvp-vote.component';
import { RoadView } from './road-view.component';
import { RoleBanner } from './role-banner.component';
import { SidePanel } from './side-panel.component';
import { SignalFlash } from './signal-flash.component';

/** The server drops signals closer together than this. */
const SIGNAL_GAP_MS = 520;
/** The level's title card shows this long at the start of a level. */
const INTRO_MS = 3200;
/** A key change the rate limit dropped is sent again after this long. */
const RETRY_MS = 250;
/** A note about a key that isn't yours stays this long. */
const KEY_HINT_MS = 2400;

/**
 * Backseat Drivers: the whole team drives one bus. The navigator sees the map; everybody else
 * holds one or two controls and sees only the road around the bus.
 *
 * This component owns the keyboard (document listeners while a level is driving: only changes are
 * sent, a key already held is not sent again, everything is released when the window loses focus;
 * a key for someone else's control says whose it is) and lays out the banner, the screen (road
 * view or map) and the side panel — or the MVP vote after the last level. The frames themselves
 * flow through {@link BusFeed} straight to the canvases.
 */
@Component({
  selector: 'ff-backseat-drivers',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RoleBanner, RoadView, MapView, SidePanel, MvpVote, SignalFlash],
  providers: [BusFeed],
  templateUrl: './backseat-drivers.component.html',
  styleUrl: './backseat-drivers.component.scss',
  host: {
    '(document:keydown)': 'onKeyDown($event)',
    '(document:keyup)': 'onKeyUp($event)',
    '(document:visibilitychange)': 'onVisibilityChange()',
    '(window:blur)': 'releaseAll()',
    '[attr.data-mode]': 'view()?.mode',
  },
})
export class BackseatDriversComponent {
  protected readonly api = injectGameApi<BackseatDriversView>();
  /** Created with the game screen (not with the first canvas) so it hears the stream from the start. */
  protected readonly feed = inject(BusFeed);
  protected readonly view = this.api.view;
  protected readonly level = computed(() => this.view()?.level ?? null);
  protected readonly over = computed(() => {
    const level = this.level();
    return !!level && level.status !== 'DRIVING';
  });
  /** The navigator (while driving) and everybody (once the level is over) see the whole map. */
  protected readonly showMap = computed(() => {
    const level = this.level();
    return !!level?.map && (level.status !== 'DRIVING' || level.navigator);
  });
  /** Keys and buttons reach the server: a level is being driven and this browser plays. */
  protected readonly driving = computed(
    () =>
      this.api.phase() === 'PLAYING' &&
      this.api.isPlaying() &&
      this.view()?.mode === 'LEVEL' &&
      this.level()?.status === 'DRIVING',
  );
  protected readonly tracker = new InputTracker((control, pressed) => void this.sendInput(control, pressed));
  protected readonly intro = signal<{ number: number; name: string } | null>(null);
  /** Why the key just pressed did nothing: whose control it is. */
  protected readonly keyHint = signal<string | null>(null);

  private readonly myControls = computed(() => this.level()?.myControls ?? [], { equal: sameControls });
  private readonly rolesVersion = computed(() => this.level()?.rolesVersion ?? -1);
  private readonly signalling = computed(
    () => this.driving() && !!this.level()?.navigator && this.view()?.variation === 'silent-navigator',
  );
  /** Changes once per level (a primitive, so snapshots within a level don't retrigger). */
  private readonly levelKey = computed(() => (this.level()?.status === 'DRIVING' ? (this.level()?.number ?? 0) : 0));
  private lastSignalAt = -Infinity;
  private introTimer: ReturnType<typeof setTimeout> | undefined;
  private keyHintTimer: ReturnType<typeof setTimeout> | undefined;
  private destroyed = false;

  constructor() {
    effect(() => {
      if (!this.driving()) {
        untracked(() => {
          this.tracker.clear();
          this.keyHint.set(null);
        });
      }
    });
    // New seats: the server released every control; keys still held for a control we kept press again.
    effect(() => {
      this.rolesVersion();
      const mine = this.myControls();
      untracked(() => this.tracker.reseat(mine));
    });
    effect(() => {
      const number = this.levelKey();
      untracked(() => this.showIntro(number));
    });
    inject(DestroyRef).onDestroy(() => {
      this.releaseAll();
      this.destroyed = true;
      clearTimeout(this.introTimer);
      clearTimeout(this.keyHintTimer);
    });
  }

  protected onKeyDown(event: KeyboardEvent): void {
    if (isTyping(event.target) || event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    if (this.signalling()) {
      const kind = signalForKey(event);
      if (kind) {
        event.preventDefault();
        if (!event.repeat) {
          this.sendSignal(kind);
        }
      }
      return;
    }
    if (!this.driving()) {
      return;
    }
    const control = controlForKey(event, this.myControls());
    if (!control) {
      return;
    }
    event.preventDefault();
    if (this.myControls().includes(control)) {
      // Auto-repeats count as presses too (the tracker ignores a key it already holds): a key held
      // since before the level started or since before a SEAT SHUFFLE gave you its control works.
      this.tracker.down(keySource(event), control);
    } else if (!event.repeat) {
      this.nudge(control);
    }
  }

  protected onKeyUp(event: KeyboardEvent): void {
    this.tracker.up(keySource(event));
    if (this.driving() && controlForKey(event, this.myControls())) {
      event.preventDefault(); // Space must not "click" a focused button
    }
  }

  protected onVisibilityChange(): void {
    if (document.hidden) {
      this.releaseAll();
    }
  }

  protected releaseAll(): void {
    this.tracker.releaseAll();
  }

  protected pointerDown(control: Control): void {
    if (this.driving() && this.myControls().includes(control)) {
      this.tracker.down(`pointer:${control}`, control);
    }
  }

  protected pointerUp(control: Control): void {
    this.tracker.up(`pointer:${control}`);
  }

  protected sendSignal(kind: SignalKind): void {
    const now = performance.now();
    if (!this.signalling() || now - this.lastSignalAt < SIGNAL_GAP_MS) {
      return;
    }
    this.lastSignalAt = now;
    void this.api.send('signal', { kind }, { quiet: true });
  }

  /**
   * Sends a key change as a quiet request (not `sendFast`) so we learn about the rate limit: a hold
   * change it dropped is sent again, so no pedal stays pressed on the server. Rejections (e.g. a
   * key released just as the level ends) stay silent.
   */
  private async sendInput(control: Control, pressed: boolean): Promise<void> {
    if (this.destroyed || !this.driving() || !this.myControls().includes(control)) {
      return;
    }
    const result = await this.api.send('input', { control, pressed }, { quiet: true });
    if (!result.ok && result.code === 'RATE_LIMITED' && CONTROL_INFO[control].hold) {
      setTimeout(() => {
        if (this.tracker.isHeld(control) === pressed) {
          void this.sendInput(control, pressed);
        }
      }, RETRY_MS);
    }
  }

  /** The level's title card, at the start of a level only (not after a reconnect mid-level). */
  private showIntro(number: number): void {
    clearTimeout(this.introTimer);
    const level = this.level();
    if (!number || !level || (level.frame?.t ?? 0) > 40) {
      this.intro.set(null);
      return;
    }
    this.intro.set({ number, name: level.name });
    this.introTimer = setTimeout(() => this.intro.set(null), INTRO_MS);
  }

  /** A key for a control this player doesn't hold: say whose it is (so they know whom to shout at). */
  private nudge(control: Control): void {
    const level = this.level();
    if (!level) {
      return;
    }
    const who = level.seats
      .filter((seat) => seat.controls.includes(control))
      .map((seat) => this.api.player(seat.playerId)?.name ?? '?')
      .join(' & ');
    const label = CONTROL_INFO[control].label;
    const text = level.navigator
      ? `🧭 Navigators don't drive — shout “${label}!”${who ? ` at ${who}` : ''}`
      : who
        ? `${label} is ${who}'s job — shout at them!`
        : null;
    if (!text) {
      return;
    }
    this.keyHint.set(text);
    clearTimeout(this.keyHintTimer);
    this.keyHintTimer = setTimeout(() => this.keyHint.set(null), KEY_HINT_MS);
  }
}

/** Identifies a physical key, so its keyup matches its keydown even if Shift changed `key`. */
function keySource(event: KeyboardEvent): string {
  return `key:${event.code || event.key?.toLowerCase() || 'unknown'}`;
}

function sameControls(a: readonly Control[], b: readonly Control[]): boolean {
  return a.length === b.length && a.every((control, i) => control === b[i]);
}
