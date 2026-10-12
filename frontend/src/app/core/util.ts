/** Small helpers shared by the core services. */

/**
 * A random id. Uses `crypto.getRandomValues`, which (unlike `crypto.randomUUID`) also works on
 * plain-http LAN addresses — a common setup for office party games.
 */
export function newId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(12));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

/** Reads JSON from web storage; returns `null` when missing, unreadable or blocked. */
export function readStorage<T>(storage: Storage | undefined, key: string): T | null {
  try {
    const raw = storage?.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

/** Writes (or with `null`, removes) JSON in web storage; ignores blocked storage. */
export function writeStorage(storage: Storage | undefined, key: string, value: unknown): void {
  try {
    if (value === null) {
      storage?.removeItem(key);
    } else {
      storage?.setItem(key, JSON.stringify(value));
    }
  } catch {
    // Private mode or quota exceeded: the app still works, it just won't remember.
  }
}

/** Normalizes user input to a room code: upper case, no spaces or separators. */
export function normalizeRoomCode(raw: string | null | undefined): string {
  return (raw ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}

/** Formats milliseconds as `m:ss` (or `s` below a minute when `compact`). */
export function formatDuration(ms: number, compact = false): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  if (compact && total < 60) {
    return String(total);
  }
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

/** Equality of two plain objects by their own enumerable values (one level deep). */
export function shallowEqual<T>(a: T, b: T): boolean {
  if (Object.is(a, b)) {
    return true;
  }
  if (typeof a !== 'object' || typeof b !== 'object' || a === null || b === null) {
    return false;
  }
  const keysA = Object.keys(a);
  const keysB = Object.keys(b);
  return (
    keysA.length === keysB.length &&
    keysA.every((key) => Object.is((a as Record<string, unknown>)[key], (b as Record<string, unknown>)[key]))
  );
}

/** Equality of two arrays whose items are compared with {@link shallowEqual}. */
export function shallowArrayEqual<T>(a: readonly T[], b: readonly T[]): boolean {
  return a === b || (a.length === b.length && a.every((item, i) => shallowEqual(item, b[i])));
}
