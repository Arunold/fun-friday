import { signal } from '@angular/core';

import { CONTROLS, Control, SignalKind } from './backseat-drivers.types';

/** How a control looks on screen. */
export interface ControlInfo {
  /** Big banner label. */
  label: string;
  /** Roster tag. */
  short: string;
  /** Emoji, or `null` when {@link svgIcon} is used. */
  emoji: string | null;
  /** Platform icon (arrows). */
  svgIcon: string | null;
  /** Key hints (Space is added for a player's first control). */
  keys: readonly string[];
  /** Held (steering, pedals) or tapped (horn, wipers, headlights). */
  hold: boolean;
  /** One-line help under the label. */
  help: string;
}

export const CONTROL_INFO: Record<Control, ControlInfo> = {
  NAVIGATOR: {
    label: 'Navigator',
    short: 'Navigator',
    emoji: '🧭',
    svgIcon: null,
    keys: [],
    hold: false,
    help: 'Sees the map',
  },
  STEER_LEFT: {
    label: 'Steer left',
    short: 'Left',
    emoji: null,
    svgIcon: 'arrow-back',
    keys: ['←', 'A'],
    hold: true,
    help: 'Hold to turn',
  },
  STEER_RIGHT: {
    label: 'Steer right',
    short: 'Right',
    emoji: null,
    svgIcon: 'arrow-forward',
    keys: ['→', 'D'],
    hold: true,
    help: 'Hold to turn',
  },
  GAS: {
    label: 'Gas',
    short: 'Gas',
    emoji: null,
    svgIcon: 'arrow-up',
    keys: ['↑', 'W'],
    hold: true,
    help: 'Hold to go',
  },
  BRAKE: {
    label: 'Brake',
    short: 'Brake',
    emoji: null,
    svgIcon: 'arrow-down',
    keys: ['↓', 'S'],
    hold: true,
    help: 'Hold · reverses when stopped',
  },
  HORN: {
    label: 'Horn',
    short: 'Horn',
    emoji: '📯',
    svgIcon: null,
    keys: ['H'],
    hold: false,
    help: 'Tap · scares geese',
  },
  WIPERS: {
    label: 'Wipers',
    short: 'Wipers',
    emoji: '🌧️',
    svgIcon: null,
    keys: ['E'],
    hold: false,
    help: 'Tap · clears the rain',
  },
  HEADLIGHTS: {
    label: 'Headlights',
    short: 'Lights',
    emoji: '💡',
    svgIcon: null,
    keys: ['L'],
    hold: false,
    help: 'Tap · on / off',
  },
};

/** Keys (lower-case `KeyboardEvent.key`) of the drivers' controls. Space means "my first control". */
const KEY_CONTROLS: Readonly<Record<string, Control>> = {
  arrowleft: 'STEER_LEFT',
  a: 'STEER_LEFT',
  arrowright: 'STEER_RIGHT',
  d: 'STEER_RIGHT',
  arrowup: 'GAS',
  w: 'GAS',
  arrowdown: 'BRAKE',
  s: 'BRAKE',
  h: 'HORN',
  e: 'WIPERS',
  l: 'HEADLIGHTS',
};

/** WASD by physical position (`KeyboardEvent.code`), so they work on any keyboard layout. */
const CODE_CONTROLS: Readonly<Record<string, Control>> = {
  KeyA: 'STEER_LEFT',
  KeyD: 'STEER_RIGHT',
  KeyW: 'GAS',
  KeyS: 'BRAKE',
};

/** Keys of a silent navigator's signals (the same keys as the controls they ask for). */
const KEY_SIGNALS: Readonly<Record<string, SignalKind>> = {
  arrowleft: 'LEFT',
  a: 'LEFT',
  arrowright: 'RIGHT',
  d: 'RIGHT',
  arrowup: 'STRAIGHT',
  w: 'STRAIGHT',
  arrowdown: 'STOP',
  s: 'STOP',
  h: 'HONK',
  e: 'WIPE',
  l: 'LIGHTS',
};

/** The same by physical position. */
const CODE_SIGNALS: Readonly<Record<string, SignalKind>> = {
  KeyA: 'LEFT',
  KeyD: 'RIGHT',
  KeyW: 'STRAIGHT',
  KeyS: 'STOP',
};

/** The parts of a keyboard event the key maps read (`key` is missing on some synthetic events). */
export type KeyPress = Pick<KeyboardEvent, 'code'> & { key?: string };

/** The control a key operates for a player holding `mine` (whether it is theirs or not), or `null`. */
export function controlForKey(event: KeyPress, mine: readonly Control[]): Control | null {
  const key = event.key?.toLowerCase() ?? '';
  if (key === ' ' || event.code === 'Space') {
    return mine.find((control) => control !== 'NAVIGATOR') ?? null;
  }
  return KEY_CONTROLS[key] ?? CODE_CONTROLS[event.code] ?? null;
}

/** The signal a key gives for a silent navigator, or `null`. */
export function signalForKey(event: KeyPress): SignalKind | null {
  return KEY_SIGNALS[event.key?.toLowerCase() ?? ''] ?? CODE_SIGNALS[event.code] ?? null;
}

/** Whether a frame's pressed mask contains the control. */
export function pressedIn(mask: number, control: Control): boolean {
  return (mask & (1 << CONTROLS.indexOf(control))) !== 0;
}

/** What a silent-navigator signal looks like. */
export interface SignalInfo {
  kind: SignalKind;
  label: string;
  emoji: string | null;
  svgIcon: string | null;
  key: string;
}

export const SIGNALS: readonly SignalInfo[] = [
  { kind: 'LEFT', label: 'Left', emoji: null, svgIcon: 'arrow-back', key: '←' },
  { kind: 'STRAIGHT', label: 'Straight', emoji: null, svgIcon: 'arrow-up', key: '↑' },
  { kind: 'RIGHT', label: 'Right', emoji: null, svgIcon: 'arrow-forward', key: '→' },
  { kind: 'STOP', label: 'Stop', emoji: '✋', svgIcon: null, key: '↓' },
  { kind: 'HONK', label: 'Honk', emoji: '📯', svgIcon: null, key: 'H' },
  { kind: 'WIPE', label: 'Wipe', emoji: '🌧️', svgIcon: null, key: 'E' },
  { kind: 'LIGHTS', label: 'Lights', emoji: '💡', svgIcon: null, key: 'L' },
];

/**
 * The player's keys and on-screen buttons. Several sources (a key, Space, a pointer) can hold the
 * same control; the server hears only the changes: a hold control goes down when its first source
 * goes down and up when the last one is released, a tap control taps once per key press.
 */
export class InputTracker {
  /** Controls held right now (for the buttons' pressed look). */
  readonly held = signal<ReadonlySet<Control>>(new Set());
  private readonly sources = new Map<string, Control>();

  constructor(private readonly send: (control: Control, pressed: boolean) => void) {}

  /** A key or button went down. */
  down(source: string, control: Control): void {
    if (this.sources.has(source)) {
      return;
    }
    const wasHeld = this.isHeld(control);
    this.sources.set(source, control);
    if (!CONTROL_INFO[control].hold || !wasHeld) {
      this.send(control, true);
    }
    this.publish();
  }

  /** A key or button went up. */
  up(source: string): void {
    const control = this.sources.get(source);
    if (!control) {
      return;
    }
    this.sources.delete(source);
    if (CONTROL_INFO[control].hold && !this.isHeld(control)) {
      this.send(control, false);
    }
    this.publish();
  }

  /**
   * The seats were dealt again and the server released everything: forget the controls the player
   * lost and press the ones they still hold again (a key held through a shuffle keeps working).
   */
  reseat(mine: readonly Control[]): void {
    for (const [source, control] of [...this.sources]) {
      if (!mine.includes(control)) {
        this.sources.delete(source);
      }
    }
    for (const control of new Set(this.sources.values())) {
      if (CONTROL_INFO[control].hold) {
        this.send(control, true);
      }
    }
    this.publish();
  }

  /** Releases everything (the window lost focus). */
  releaseAll(): void {
    for (const control of new Set(this.sources.values())) {
      if (CONTROL_INFO[control].hold) {
        this.send(control, false);
      }
    }
    this.clear();
  }

  /** Forgets everything without telling the server (the level is over anyway). */
  clear(): void {
    if (this.sources.size) {
      this.sources.clear();
      this.publish();
    }
  }

  /** Whether some key or button holds the control down right now. */
  isHeld(control: Control): boolean {
    for (const held of this.sources.values()) {
      if (held === control) {
        return true;
      }
    }
    return false;
  }

  private publish(): void {
    this.held.set(new Set(this.sources.values()));
  }
}

/** Whether a keyboard event comes from a text field (never hijack typing). */
export function isTyping(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable ||
      target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.tagName === 'SELECT')
  );
}
