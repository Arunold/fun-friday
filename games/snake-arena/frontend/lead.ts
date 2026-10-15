/** Time a turn gets on top of the round trip, against jitter (ticks). */
const MARGIN = 0.3;
/** Round trips remembered; once there are a few, the longest counts as an outlier. */
const SAMPLES = 8;
const ROBUST_FROM = 4;
/** Assumed until a round trip has been measured (ms). */
const DEFAULT_ROUND_TRIP_MS = 100;
/** The furthest the own snake is drawn past the frames' clock (ticks): round trips up to ~370 ms. */
const MAX_LEAD = 3;
/**
 * How fast the lead follows a new round trip (ticks per tick): meanwhile the own snake runs a
 * little faster or slower instead of jumping.
 */
const RISE = 0.3;
const FALL = 0.05;

/**
 * How far past the frames' clock ({@link FrameClock.position}) the own snake is drawn, so that
 * every turn reaches the server in time — near or far.
 *
 * A key turns the snake at the next cell centre its drawn head reaches and names that cell's tick;
 * the server can carry the turn out until it steps the tick after it. Frames are a one-way trip old
 * when they arrive and the turn needs another one, so drawing the own snake a round trip minus one
 * tick (plus a margin) past the frames' clock makes every turn arrive in time. With the server
 * close by that is about the others' moment; far away, the own snake runs ahead of the others.
 *
 * Round trips come from the platform's ping (the best of three when connecting) and from the
 * answers to turns.
 */
export class Lead {
  private samples: number[] = [];
  private value = Number.NaN;
  private time = 0;

  /** A measured round trip (ms). */
  sample(ms: number): void {
    this.samples = [...this.samples.slice(1 - SAMPLES), Math.max(0, ms)];
  }

  /**
   * The lead at `now` (ticks), eased towards what the recent round trips need.
   *
   * @param jitterMs how unevenly frames arrive lately ({@link FrameClock.jitter})
   */
  ticks(now: number, tickMs: number, jitterMs: number): number {
    const target = Math.min(MAX_LEAD, this.roundTripMs(jitterMs) / tickMs - 1 + MARGIN);
    if (Number.isNaN(this.value)) {
      this.value = target;
    } else {
      const step = ((target > this.value ? RISE : FALL) * Math.max(0, now - this.time)) / tickMs;
      this.value += Math.max(-step, Math.min(step, target - this.value));
    }
    this.time = now;
    return this.value;
  }

  /**
   * The round trip to plan for (ms): the second longest of the recent ones. Until there are a few,
   * the longest plus twice the frames' jitter — the ping is a best case.
   */
  private roundTripMs(jitterMs: number): number {
    const sorted = [...this.samples].sort((a, b) => b - a);
    return sorted.length >= ROBUST_FROM ? sorted[1] : (sorted[0] ?? DEFAULT_ROUND_TRIP_MS) + 2 * jitterMs;
  }
}
