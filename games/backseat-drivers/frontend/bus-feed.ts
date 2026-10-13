import { Injectable, effect, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { injectGameApi } from '@ff/sdk';

import { AreaView, BackseatDriversView, Effect, FrameView, SignalView, TICK_MS } from './backseat-drivers.types';

/** Where the bus is drawn. */
export interface BusPose {
  x: number;
  y: number;
  /** Radians, 0 = east, π/2 = south. */
  heading: number;
  /** Tiles per second, negative while reversing. */
  speed: number;
}

/** Pressed-control bits per seat and the roles version they belong to. */
export interface Pressed {
  r: number;
  p: readonly number[];
}

/** Render this many ticks behind the newest frame, so there is (almost) always a frame to move towards. */
const BUFFER_TICKS = 1.5;
/** Frames kept for interpolation. */
const MAX_FRAMES = 12;
/** A tick this far behind the newest one means a new run (a new game in the same room). */
const RESTART_TICKS = 20;
/** Effect bits the UI around the canvas cares about (hint chips). */
const ALERT_BITS = Effect.BLUR | Effect.DARK | Effect.GEESE | Effect.OFFROAD;
/** Below this speed (tiles/s × 1000, as in frames) the bus counts as standing still. */
const STOPPED_SPEED = 50;

/**
 * The live feed of a level: frames, the drivers' window and silent-navigator signals, from the
 * `frame` / `window` / `signal` stream events and from snapshots (whichever is newer wins).
 *
 * Render loops read plain fields ({@link latest}, {@link window}, {@link pose}) 60 times a second;
 * the UI around the canvases gets signals that change only when their value does.
 */
@Injectable()
export class BusFeed {
  private readonly api = injectGameApi<BackseatDriversView>();

  /** The newest frame. */
  latest: FrameView | null = null;
  /** The drivers' window around the bus (newest version). */
  window: AreaView | null = null;

  /** Pressed controls per seat (roster chips). */
  readonly pressed = signal<Pressed>({ r: -1, p: [] }, { equal: samePressed });
  /** Rain blur, tunnel darkness, geese, off-road (hint chips). */
  readonly alerts = signal(0);
  /** Cones hit and coffee cups collected. */
  readonly counts = signal(
    { cones: 0, coffees: 0 },
    { equal: (a, b) => a.cones === b.cones && a.coffees === b.coffees },
  );
  /** The newest silent-navigator signal. */
  readonly lastSignal = signal<SignalView | null>(null);
  /** The bus stands still (steering does nothing then). */
  readonly stopped = signal(true);

  private frames: FrameView[] = [];
  /** Local time (performance.now) at which tick 0 arrived, estimated from the earliest arrivals. */
  private offset = 0;

  constructor() {
    this.api.stream$.pipe(takeUntilDestroyed()).subscribe((message) => {
      if (message.type === 'frame') {
        this.addFrame(message.payload as FrameView);
      } else if (message.type === 'window') {
        this.addWindow(message.payload as AreaView);
      } else if (message.type === 'signal') {
        this.addSignal(message.payload as SignalView);
      }
    });
    effect(() => {
      const level = this.api.view()?.level;
      untracked(() => {
        if (level?.frame) {
          this.addFrame(level.frame);
        }
        if (level?.window) {
          this.addWindow(level.window);
        }
        if (level?.signal) {
          this.addSignal(level.signal);
        }
      });
    });
  }

  /** The bus at local time `now`, interpolated between the frames around it. */
  pose(now: number): BusPose | null {
    const frames = this.frames;
    if (!frames.length) {
      return null;
    }
    const tick = (now - this.offset) / TICK_MS - BUFFER_TICKS;
    let before = frames[0];
    if (tick <= before.t) {
      return poseOf(before);
    }
    for (let i = 1; i < frames.length; i++) {
      const after = frames[i];
      if (tick < after.t) {
        return interpolate(before, after, (tick - before.t) / (after.t - before.t));
      }
      before = after;
    }
    return poseOf(before);
  }

  private addFrame(frame: FrameView): void {
    const now = performance.now();
    const last = this.frames.at(-1);
    if (!last || frame.l !== last.l || frame.t + RESTART_TICKS < last.t) {
      if (last) {
        this.window = null; // another level: the old window is useless
      }
      this.frames = [frame];
      this.offset = now - frame.t * TICK_MS;
      this.publish(frame);
      return;
    }
    if (frame.t <= last.t) {
      return;
    }
    this.frames.push(frame);
    if (this.frames.length > MAX_FRAMES) {
      this.frames.shift();
    }
    const arrival = now - frame.t * TICK_MS;
    if (Math.abs(arrival - this.offset) > 1000) {
      this.offset = arrival; // the tab slept or the connection stalled: start over
    } else if (arrival < this.offset) {
      this.offset = arrival;
    } else {
      this.offset += (arrival - this.offset) * 0.01; // slowly follow a longer delay
    }
    this.publish(frame);
  }

  private publish(frame: FrameView): void {
    this.latest = frame;
    this.pressed.set({ r: frame.r, p: frame.p });
    this.alerts.set(frame.e & ALERT_BITS);
    this.counts.set({ cones: frame.c, coffees: frame.k });
    this.stopped.set(Math.abs(frame.b[3]) < STOPPED_SPEED);
  }

  private addWindow(area: AreaView): void {
    const current = this.window;
    if (!current || area.l !== current.l || area.v > current.v) {
      this.window = area;
    }
  }

  private addSignal(signal: SignalView): void {
    const current = this.lastSignal();
    if (!current || signal.at > current.at) {
      this.lastSignal.set(signal); // signals are at least half a second apart
    }
  }
}

function poseOf(frame: FrameView): BusPose {
  return { x: frame.b[0] / 1000, y: frame.b[1] / 1000, heading: frame.b[2] / 1000, speed: frame.b[3] / 1000 };
}

function interpolate(a: FrameView, b: FrameView, f: number): BusPose {
  const from = poseOf(a);
  const to = poseOf(b);
  let turn = to.heading - from.heading;
  if (turn > Math.PI) {
    turn -= 2 * Math.PI;
  } else if (turn < -Math.PI) {
    turn += 2 * Math.PI;
  }
  return {
    x: from.x + (to.x - from.x) * f,
    y: from.y + (to.y - from.y) * f,
    heading: from.heading + turn * f,
    speed: from.speed + (to.speed - from.speed) * f,
  };
}

function samePressed(a: Pressed, b: Pressed): boolean {
  return a.r === b.r && a.p.length === b.p.length && a.p.every((mask, i) => mask === b.p[i]);
}
