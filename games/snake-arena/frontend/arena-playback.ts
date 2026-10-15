import { FrameClock } from './frame-clock';
import { Lead } from './lead';
import { Point, Pose, cellsMoved, pointAt, poseAt, shiftHead } from './motion';
import { Dir, Frame, SnakeFrame } from './snake-arena.types';
import { Steering, Turn } from './steering';

/** What the playback needs to know about the arena. */
export interface Arena {
  cols: number;
  tickMs: number;
  /** Ticks before anything moves. */
  startDelay: number;
}

/**
 * How far a snake runs on past the newest frame when the next one is late (ticks) before it waits:
 * the own snake along its known moves, the others guessed straight on.
 */
const OWN_SLACK = 1;
const OTHERS_SLACK = 0.5;
/** A drawn head that jumps (a correction) glides to its new place with this time constant (ms). */
const SMOOTHING_MS = 45;

/** Where a snake's head was drawn last and the jump still being eased out (cells). */
interface Shown {
  round: number;
  time: number;
  head: Point;
  offset: Point;
}

/**
 * Plays the streamed frames back smoothly, the way the player should see them.
 *
 * - The other snakes glide between frames that have arrived, against a steady clock of the
 *   server's ticks ({@link FrameClock}): a tick behind it, and further while frames arrive unevenly
 *   — so they only show moves the server has made, without stutter.
 * - The own snake runs ahead along its pending turns ({@link Steering}), far enough for a turn to
 *   reach the server in time ({@link Lead}). A key turns it at the next cell centre its drawn head
 *   reaches and the turn names that cell's tick, so the server turns it in exactly the same place:
 *   the head never jumps back and needs no correction.
 *
 * What still needs correcting (a frame later than ever, a boost that starts) glides out within
 * ~100 ms instead of snapping.
 */
export class ArenaPlayback {
  private readonly clock = new FrameClock();
  private readonly steering = new Steering();
  private readonly lead = new Lead();
  private readonly shown = new Map<string, Shown>();
  private food: ReadonlySet<number> = new Set();

  /** The newest frame. */
  get current(): Frame | null {
    return this.clock.current;
  }

  /**
   * Takes a frame from the stream or a snapshot if it is the newest.
   *
   * @param me this browser's snake (`null` for spectators)
   * @returns whether the frame was taken
   */
  receive(frame: Frame, fromSnapshot: boolean, me: string | null, tickMs: number, now: number): boolean {
    this.clock.tickMs = tickMs;
    if (!this.clock.offer(frame, fromSnapshot, now)) {
      return false;
    }
    const mine = frame.snakes.find((snake) => snake.id === me);
    this.steering.update(frame, mine);
    this.food = new Set(frame.food);
    return true;
  }

  /** A measured round trip to the server (ms): a ping or the answer to a turn. */
  roundTrip(ms: number): void {
    this.lead.sample(ms);
  }

  /** A steering key at `now`: the turn to send, or `null` when it changes nothing. */
  press(dir: Dir, me: string, arena: Arena, now: number): Turn | null {
    const current = this.clock.current;
    const mine = current?.snakes.find((snake) => snake.id === me);
    if (!current || !mine?.alive) {
      return null;
    }
    const next = Math.ceil(this.ownTick(current, arena.tickMs, now));
    return this.steering.press(dir, mine, Math.max(arena.startDelay, current.tick, next));
  }

  /** Where every living snake is drawn at `now` (`frozen`: the round is over, show its last frame). */
  poses(arena: Arena, me: string | null, frozen: boolean, now: number): Map<string, Pose> {
    const poses = new Map<string, Pose>();
    const current = this.clock.current;
    if (!current) {
      return poses;
    }
    const others = Math.min(
      this.clock.position(now) - 1 - this.clock.jitter / arena.tickMs,
      current.tick + OTHERS_SLACK,
    );
    for (const snake of current.snakes) {
      // Crashes and respawns show with the newest frame, in step with their effects.
      if (!snake.alive || !snake.body.length) {
        this.shown.delete(snake.id);
        continue;
      }
      const mine = snake.id === me;
      const at = frozen ? current.tick : mine ? this.ownTick(current, arena.tickMs, now) : others;
      const { pose, cellsPerMs } = this.pose(snake, current, mine, at, arena, frozen);
      poses.set(snake.id, this.smooth(snake.id, pose, current.round, cellsPerMs, now));
    }
    return poses;
  }

  /**
   * One snake at moment `at` (ticks): between the two frames around it, or past the newest one —
   * the own snake along its pending turns, the others straight on.
   */
  private pose(
    latest: SnakeFrame,
    newest: Frame,
    mine: boolean,
    at: number,
    arena: Arena,
    frozen: boolean,
  ): { pose: Pose; cellsPerMs: number } {
    const [from, to] = this.clock.around(at);
    const then = to?.snakes.find((each) => each.id === latest.id);
    // Not on the board yet at that moment (it has just respawned): show it where it appeared.
    const [frame, snake] = to && then?.alive ? [to, then] : [newest, latest];
    const before = frozen || frame !== to ? undefined : from?.snakes.find((each) => each.id === latest.id);
    const boostUntil = snake.power === 'BOOST' ? (snake.powerUntil ?? frame.tick) : -1;
    const moving = !frozen && frame.tick >= arena.startDelay && at > frame.tick;
    const cells = moving ? Math.ceil(cellsMoved(frame.tick, at, boostUntil)) : 0;
    const pose = poseAt({
      cols: arena.cols,
      previous: before?.alive ? before : undefined,
      previousTick: from?.tick ?? frame.tick,
      current: snake,
      currentTick: frame.tick,
      at,
      ahead: mine
        ? this.steering.ahead(frame.tick, snake.dir, cells, boostUntil)
        : new Array<Dir>(cells).fill(snake.dir),
      boostUntil,
      food: this.food,
      bounds: frame.bounds,
      look: mine && !frozen ? this.steering.pending() : undefined,
    });
    return { pose, cellsPerMs: (boostUntil >= 0 ? 2 : 1) / arena.tickMs };
  }

  /** The moment (in ticks) the own snake is drawn at: led by the round trip, waiting for late frames. */
  private ownTick(current: Frame, tickMs: number, now: number): number {
    const lead = this.lead.ticks(now, tickMs, this.clock.jitter);
    return Math.min(this.clock.position(now) + lead, current.tick + lead + 1 + OWN_SLACK);
  }

  /**
   * Eases out a jump of a drawn head so it glides to its new place within a few frames instead of
   * snapping there. Respawns and new rounds start fresh.
   */
  private smooth(id: string, pose: Pose, round: number, cellsPerMs: number, now: number): Pose {
    const head = pointAt(pose.points, pose.head);
    const last = this.shown.get(id);
    let offset = { x: 0, y: 0 };
    if (last && last.round === round && now - last.time < 250) {
      const dt = now - last.time;
      const jump = Math.hypot(head.x - last.head.x, head.y - last.head.y);
      offset = last.offset;
      if (jump > cellsPerMs * dt + 0.1 && jump < 4) {
        offset = { x: offset.x + last.head.x - head.x, y: offset.y + last.head.y - head.y };
      }
      const keep = Math.exp(-dt / SMOOTHING_MS);
      offset = { x: offset.x * keep, y: offset.y * keep };
    }
    this.shown.set(id, { round, time: now, head, offset });
    return Math.hypot(offset.x, offset.y) < 0.02 ? pose : shiftHead(pose, offset.x, offset.y);
  }
}
