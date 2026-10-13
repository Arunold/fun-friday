import { FeedEntry, FeedKind, Frame, PowerType, ResultRow } from './snake-arena.types';

/** What the side panel needs to know about a player. */
export interface Person {
  name: string;
  avatar: string;
  color: string;
  online: boolean;
}

/** A row of the live round leaderboard. */
export interface LeaderRow {
  id: string;
  person: Person;
  points: number;
  length: number;
  status: 'alive' | 'respawning' | 'out';
  /** Whole seconds until the respawn. */
  respawnIn: number | null;
  power: PowerType | null;
  me: boolean;
}

/** A line of the kill feed. */
export interface FeedLine {
  id: number;
  kind: FeedKind;
  a: Person;
  b: Person | null;
  icon: string;
  title: string;
}

/** A row of the reveal's results table. */
export interface ResultLine extends ResultRow {
  person: Person;
  me: boolean;
}

const ICONS: Record<FeedKind, string> = { BODY: '⚔️', HEAD: '💥', WALL: '🧱', SELF: '🌀', LEFT: '👋' };
const NOBODY: Person = { name: 'Someone', avatar: '🙂', color: '#9e9aa7', online: false };

/** Seconds (rounded up) from `tick` to `target`. */
export function secondsUntil(target: number, tick: number, tickMs: number): number {
  return Math.max(1, Math.ceil(((target - tick) * tickMs) / 1000));
}

/** The leaderboard: round points, then length, then join order (no countdowns once `frozen`). */
export function leaderRows(
  frame: Frame | null,
  people: ReadonlyMap<string, Person>,
  me: string | null,
  tickMs: number,
  frozen = false,
): LeaderRow[] {
  if (!frame) {
    return [];
  }
  return frame.snakes
    .map((snake, order) => ({ snake, order }))
    .sort((a, b) => b.snake.points - a.snake.points || b.snake.length - a.snake.length || a.order - b.order)
    .map(({ snake }): LeaderRow => ({
      id: snake.id,
      person: people.get(snake.id) ?? NOBODY,
      points: snake.points,
      length: snake.length,
      status: snake.alive ? 'alive' : snake.respawnAt != null ? 'respawning' : 'out',
      respawnIn:
        !frozen && !snake.alive && snake.respawnAt != null ? secondsUntil(snake.respawnAt, frame.tick, tickMs) : null,
      power: snake.alive ? (snake.power ?? null) : null,
      me: snake.id === me,
    }));
}

/** The kill feed, newest first. */
export function feedLines(feed: readonly FeedEntry[], people: ReadonlyMap<string, Person>): FeedLine[] {
  return [...feed].reverse().map((entry): FeedLine => {
    const a = people.get(entry.a) ?? NOBODY;
    const b = entry.b ? (people.get(entry.b) ?? NOBODY) : null;
    return { id: entry.id, kind: entry.kind, a, b, icon: ICONS[entry.kind], title: describe(entry.kind, a, b) };
  });
}

/** Why the viewer crashed most recently, from their point of view ("You ran into Ana"). */
export function crashCause(feed: readonly FeedEntry[], me: string, people: ReadonlyMap<string, Person>): string | null {
  for (let i = feed.length - 1; i >= 0; i--) {
    const entry = feed[i];
    const name = (id: string | undefined) => (id ? (people.get(id) ?? NOBODY).name : NOBODY.name);
    if (entry.kind === 'BODY' && entry.b === me) {
      return `You ran into ${name(entry.a)}`;
    }
    if (entry.kind === 'HEAD' && (entry.a === me || entry.b === me)) {
      return `Head-on with ${name(entry.a === me ? entry.b : entry.a)}`;
    }
    if (entry.kind === 'WALL' && entry.a === me) {
      return 'You hit the wall';
    }
    if (entry.kind === 'SELF' && entry.a === me) {
      return 'You bit your own tail';
    }
  }
  return null;
}

/** The results table with the players' chips. */
export function resultLines(
  rows: readonly ResultRow[],
  people: ReadonlyMap<string, Person>,
  me: string | null,
): ResultLine[] {
  return rows.map((row): ResultLine => ({
    ...row,
    person: people.get(row.playerId) ?? NOBODY,
    me: row.playerId === me,
  }));
}

function describe(kind: FeedKind, a: Person, b: Person | null): string {
  switch (kind) {
    case 'BODY':
      return `${b?.name ?? NOBODY.name} ran into ${a.name} (+5 for ${a.name})`;
    case 'HEAD':
      return `${a.name} and ${b?.name ?? NOBODY.name} crashed head-on`;
    case 'WALL':
      return `${a.name} hit the wall`;
    case 'SELF':
      return `${a.name} bit their own tail`;
    default:
      return `${a.name} left the game`;
  }
}
