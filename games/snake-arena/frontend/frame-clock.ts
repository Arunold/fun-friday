import { Frame } from './snake-arena.types';

/** How quickly the clock follows the frames' arrival times (share of the difference per frame). */
const SETTLE = 0.15;
/** A frame this many ticks off the clock restarts it (a new round, a tab that slept). */
const RESYNC_TICKS = 3;
/** How much of the remembered lateness of frames is kept per frame (it fades in a few seconds). */
const JITTER_KEEP = 0.97;
/** Frames kept: enough to draw the other snakes up to two ticks behind the newest one. */
const HISTORY = 4;

/**
 * Keeps the newest frames of the round for drawing — whichever is newer wins, stream or snapshot —
 * and a steady clock of the server's ticks: when the frame of each tick is due in this browser,
 * smoothed over the actual arrivals so network jitter doesn't make the snakes stutter. Plain
 * fields: the render loop reads them 60 times a second without touching Angular.
 */
export class FrameClock {
  /** Length of a tick (from the view). */
  tickMs = 100;
  /**
   * How much later than the clock frames have come lately, at most (ms, up to a tick): drawing
   * other snakes that much earlier means their next frame is there before it is needed.
   */
  jitter = 0;
  /** The newest frames, oldest first, without gaps of more than one missed tick. */
  private frames: Frame[] = [];
  /** `performance.now()` at which the round's tick 0 was due (by the smoothed clock). */
  private base = Number.NaN;

  /** The newest frame. */
  get current(): Frame | null {
    return this.frames.at(-1) ?? null;
  }

  /**
   * Offers a frame. Stream frames must be newer than the current one; a snapshot's frame may also
   * have the same tick (it can carry newer details, e.g. the round's bonuses or a departed
   * player), which replaces the current frame without restarting the animation.
   *
   * @returns whether the frame was taken
   */
  offer(frame: Frame, fromSnapshot: boolean, now: number): boolean {
    const current = this.current;
    const sameRound = !!current && frame.round === current.round;
    if (current && (frame.round < current.round || (sameRound && frame.tick < current.tick))) {
      return false;
    }
    if (sameRound && frame.tick === current.tick) {
      if (fromSnapshot) {
        this.frames[this.frames.length - 1] = frame;
      }
      return fromSnapshot;
    }
    this.frames = sameRound && frame.tick - current.tick <= 2 ? [...this.frames.slice(1 - HISTORY), frame] : [frame];
    this.time(frame.tick, fromSnapshot, !sameRound, now);
    return true;
  }

  /**
   * The frames on either side of moment `at` (ticks): the first one at or after it — the newest if
   * `at` lies beyond — and the one before (`null`: none kept).
   */
  around(at: number): [Frame | null, Frame | null] {
    const index = this.frames.findIndex((frame) => frame.tick >= at);
    const to = index < 0 ? this.frames.length - 1 : index;
    return [this.frames[to - 1] ?? null, this.frames[to] ?? null];
  }

  /** Where the server's tick count stands now by the clock: 12.5 = halfway from tick 12 to 13. */
  position(now: number): number {
    return Number.isNaN(this.base) ? (this.current?.tick ?? 0) : (now - this.base) / this.tickMs;
  }

  /** Moves the clock towards the arrival of a frame (snapshots only start it: they come late). */
  private time(tick: number, fromSnapshot: boolean, newRound: boolean, now: number): void {
    const due = now - tick * this.tickMs;
    if (Number.isNaN(this.base) || newRound || Math.abs(due - this.base) > RESYNC_TICKS * this.tickMs) {
      this.base = due;
      this.jitter = 0;
    } else if (!fromSnapshot) {
      this.jitter = Math.min(this.tickMs, Math.max(due - this.base, this.jitter * JITTER_KEEP));
      this.base += (due - this.base) * SETTLE;
    }
  }
}
