import { Dir } from './snake-arena.types';

/** Arrow keys, and WASD by physical position (`code`) or by letter (`key`), so any layout works. */
const KEYS = new Map<string, Dir>([
  ['ArrowUp', 'UP'],
  ['ArrowDown', 'DOWN'],
  ['ArrowLeft', 'LEFT'],
  ['ArrowRight', 'RIGHT'],
  ['KeyW', 'UP'],
  ['KeyS', 'DOWN'],
  ['KeyA', 'LEFT'],
  ['KeyD', 'RIGHT'],
  ['w', 'UP'],
  ['s', 'DOWN'],
  ['a', 'LEFT'],
  ['d', 'RIGHT'],
]);

/** The heading a key press steers to, or `null` for other keys. */
export function directionOf(event: KeyboardEvent): Dir | null {
  return KEYS.get(event.code) ?? KEYS.get(event.key) ?? KEYS.get(event.key?.toLowerCase() ?? '') ?? null;
}

/** Whether a key event comes from a text field (never steer while somebody types). */
export function isTyping(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable ||
      target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.tagName === 'SELECT')
  );
}
