import { Frame } from './snake-arena.types';

/** How quickly the clock follows the frames' arrival times (share of the difference per frame). */
const SETTLE = 0.15;
/** A frame this many ticks off the clock restarts it (a new round, a tab that slept). */
const RESYNC_TICKS = 3;

/**
 * Keeps the two newest frames for drawing — whichever is newer wins, stream or snapshot — and a
 * steady clock of the server's ticks: when the frame of each tick is due in this browser, smoothed
 * over the actual arrivals so network jitter doesn't make the snakes stutter. Plain fields: the
 * render loop reads them 60 times a second without touching Angular.
 */
export class FrameClock {
  /** The frame before {@link current} (`null`: none to glide from). */
  previous: Frame | null = null;
  current: Frame | null = null;
  /** Length of a tick (from the view). */
  tickMs = 100;
  /** `performance.now()` at which the round's tick 0 was due (by the smoothed clock). */
  private base = Number.NaN;

  /**
   * Offers a frame. Stream frames must be newer than the current one; a snapshot's frame may also
   * have the same tick (it can carry newer details, e.g. the round's bonuses or a departed
   * player), which replaces the current frame without restarting the animation.
   *
   * @returns whether the frame was taken
   */
  offer(frame: Frame, fromSnapshot: boolean, now: number): boolean {
    const current = this.current;
    if (current && frame.round === current.round) {
      if (frame.tick < current.tick || (frame.tick === current.tick && !fromSnapshot)) {
        return false;
      }
      if (frame.tick === current.tick) {
        this.current = frame;
        return true;
      }
    } else if (current && frame.round < current.round) {
      return false;
    }
    const sameRound = !!current && current.round === frame.round;
    this.previous = sameRound && frame.tick - current.tick <= 2 ? current : null;
    this.current = frame;
    this.time(frame.tick, fromSnapshot, !sameRound, now);
    return true;
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
    } else if (!fromSnapshot) {
      this.base += (due - this.base) * SETTLE;
    }
  }
}
