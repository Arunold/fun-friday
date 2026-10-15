import { Dir, Frame, SnakeFrame } from './snake-arena.types';

const OPPOSITE: Readonly<Record<Dir, Dir>> = { UP: 'DOWN', DOWN: 'UP', LEFT: 'RIGHT', RIGHT: 'LEFT' };
/** The server queues at most this many turns. */
const MAX_QUEUED = 2;
/** A turn the server hasn't carried out this many ticks after it was due is given up. */
const GIVE_UP_TICKS = 3;

/** A turn to send: the new heading and the tick whose head position it starts from. */
export interface Turn {
  dir: Dir;
  at: number;
}

/**
 * This browser's turns that the server hasn't carried out yet — a mirror of the server's queue —
 * so the own snake can be drawn turning the moment a key is pressed (client-side prediction).
 *
 * Each turn names the tick whose head position it starts from: the cell centre where the drawn
 * head turns. The server carries it out by the first move after that tick, so the snake turns
 * exactly there — early turns wait, late ones go with the next move.
 */
export class Steering {
  private turns: Turn[] = [];
  private round = -1;

  /**
   * A key press that turns the snake in the cell of tick `at`. Returns the turn to send, or `null`
   * when it changes nothing (the current heading, a reversal, a full queue — the server would
   * ignore it as well).
   */
  press(dir: Dir, me: SnakeFrame, at: number): Turn | null {
    const last = this.turns.at(-1);
    const heading = last?.dir ?? me.dir;
    if (this.turns.length >= MAX_QUEUED || dir === heading || dir === OPPOSITE[heading]) {
      return null;
    }
    const turn = { dir, at: Math.max(0, Math.round(at), last?.at ?? 0) };
    this.turns.push(turn);
    return turn;
  }

  /** A new frame: forgets the turns the server has carried out — or never will. */
  update(frame: Frame, me: SnakeFrame | undefined): void {
    if (frame.round !== this.round || !me?.alive) {
      this.round = frame.round;
      this.turns = [];
      return;
    }
    let done = -1;
    this.turns.forEach((turn, i) => {
      if (turn.at < frame.tick && turn.dir === me.dir) {
        done = i;
      }
    });
    this.turns = this.turns.slice(done + 1).filter((turn) => turn.at >= frame.tick - GIVE_UP_TICKS);
  }

  /**
   * The headings of the next `count` moves after the frame of `tick`, starting from `dir` — two
   * moves per tick up to and including tick `boostUntil`, one after — with the queued turns taken
   * just as the server will take them (once due, at most one per move).
   */
  ahead(tick: number, dir: Dir, count: number, boostUntil: number): Dir[] {
    const moves: Dir[] = [];
    let heading = dir;
    let next = 0;
    for (let t = tick + 1; moves.length < count; t++) {
      for (let step = t <= boostUntil ? 2 : 1; step > 0 && moves.length < count; step--) {
        const turn = this.turns[next];
        if (turn && turn.at < t) {
          heading = turn.dir;
          next++;
        }
        moves.push(heading);
      }
    }
    return moves;
  }

  /** The newest turn not carried out yet, if any (the eyes already look that way). */
  pending(): Dir | undefined {
    return this.turns.at(-1)?.dir;
  }
}
