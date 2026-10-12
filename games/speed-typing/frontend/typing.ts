/**
 * Pure helpers of the Speed Typing screen: character states, keystroke counting and the live
 * statistics. They mirror the server's rules (`Racer.java`), so what a player sees while typing
 * matches what the server scores.
 */

/** Characters that may be typed past the end of the text (`SpeedTypingLogic.OVERFLOW`). */
export const OVERFLOW = 10;

/** Typing speed convention: five characters make one word. */
const CHARS_PER_WORD = 5;

/** `ok` typed right · `bad` typed wrong · `todo` not typed yet · `extra` typed past the end. */
export type CharState = 'ok' | 'bad' | 'todo' | 'extra';

/** One character of the text block. */
export interface CharCell {
  /** The character to show: the expected one, or for `extra` the typed one. */
  ch: string;
  state: CharState;
}

/** Every character of the text with its state, followed by characters typed past the end. */
export function charCells(text: string, typed: string): CharCell[] {
  const cells: CharCell[] = [];
  for (let i = 0; i < text.length; i++) {
    const state: CharState = i >= typed.length ? 'todo' : typed[i] === text[i] ? 'ok' : 'bad';
    cells.push({ ch: text[i], state });
  }
  for (let i = text.length; i < typed.length; i++) {
    cells.push({ ch: typed[i], state: 'extra' });
  }
  return cells;
}

/** Length of the common prefix of two strings. */
export function commonPrefix(a: string, b: string): number {
  const max = Math.min(a.length, b.length);
  let i = 0;
  while (i < max && a[i] === b[i]) {
    i++;
  }
  return i;
}

/** Typed characters that match the text at their position (the basis of the live speed). */
export function matchingChars(text: string, typed: string): number {
  let matching = 0;
  const max = Math.min(text.length, typed.length);
  for (let i = 0; i < max; i++) {
    if (typed[i] === text[i]) {
      matching++;
    }
  }
  return matching;
}

/** Wrong characters plus characters past the end: what stands between the player and the finish. */
export function errorsToFix(text: string, typed: string): number {
  let wrong = Math.max(0, typed.length - text.length);
  const max = Math.min(text.length, typed.length);
  for (let i = 0; i < max; i++) {
    if (typed[i] !== text[i]) {
      wrong++;
    }
  }
  return wrong;
}

/**
 * Keystrokes and errors added by an edit of the input from `before` to `after`. Every inserted
 * character is a keystroke; it is an error when it doesn't match the text at its position.
 * Deletions add nothing.
 */
export function countEdit(text: string, before: string, after: string): { keystrokes: number; errors: number } {
  const start = commonPrefix(before, after);
  let errors = 0;
  for (let i = start; i < after.length; i++) {
    if (after[i] !== text[i]) {
      errors++;
    }
  }
  return { keystrokes: after.length - start, errors };
}

/** Words per minute for `chars` characters in `ms` milliseconds (null below one second). */
export function wordsPerMinute(chars: number, ms: number): number | null {
  return ms < 1000 ? null : Math.round(chars / CHARS_PER_WORD / (ms / 60_000));
}

/** Correct keystrokes ÷ all keystrokes in whole percent, rounded down like the server. */
export function accuracyPercent(keystrokes: number, errors: number): number | null {
  return keystrokes === 0 ? null : Math.floor(((keystrokes - errors) * 100) / keystrokes);
}

/** Whether the accuracy is below `minPercent`, exactly as the server decides it. */
export function belowAccuracy(keystrokes: number, errors: number, minPercent: number): boolean {
  return (keystrokes - errors) * 100 < minPercent * keystrokes;
}

/** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st. */
export function ordinal(n: number): string {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) {
    return `${n}th`;
  }
  switch (n % 10) {
    case 1:
      return `${n}st`;
    case 2:
      return `${n}nd`;
    case 3:
      return `${n}rd`;
    default:
      return `${n}th`;
  }
}

/** A medal for the podium places, otherwise empty. */
export function medal(rank: number | undefined): string {
  return rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : '';
}

/** Seconds with one decimal, e.g. "23.4 s". */
export function seconds(ms: number): string {
  return `${(ms / 1000).toFixed(1)} s`;
}

/** Monospace glyph width and line height relative to the font size (see the text block styles). */
const GLYPH_WIDTH = 0.6;
export const LINE_HEIGHT = 1.6;

/**
 * The largest font size (px) at which a text of `length` characters fits into a box of the given
 * size, leaving a few characters per line for word wrapping.
 */
export function fitFontSize(width: number, height: number, length: number, min = 16, max = 40): number {
  for (let size = max; size > min; size--) {
    const perLine = Math.floor(width / (GLYPH_WIDTH * size)) - 4;
    if (perLine > 8 && Math.ceil(length / perLine) * LINE_HEIGHT * size <= height) {
      return size;
    }
  }
  return min;
}
