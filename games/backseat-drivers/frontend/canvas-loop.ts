import { DestroyRef } from '@angular/core';

/** What a draw callback gets on every animation frame. */
export interface FrameInfo {
  /** `performance.now()` of the frame. */
  now: number;
  /** Device pixels per CSS pixel. */
  dpr: number;
}

/**
 * Keeps `canvas` as large as `host` in device pixels (crisp on HiDPI) and calls `draw` on every
 * animation frame until `destroyRef` fires — and right after a resize, which clears the canvas, so
 * no blank picture shows. The loop reads plain fields, never signals, so 60 pictures a second cost
 * Angular nothing.
 */
export function runCanvas(
  canvas: HTMLCanvasElement,
  host: HTMLElement,
  destroyRef: DestroyRef,
  draw: (frame: FrameInfo) => void,
): void {
  let dpr = globalThis.devicePixelRatio || 1;
  let last = performance.now();
  const observer = new ResizeObserver(([entry]) => {
    dpr = globalThis.devicePixelRatio || 1;
    const { width, height } = entry.contentRect;
    canvas.width = Math.max(1, Math.round(width * dpr));
    canvas.height = Math.max(1, Math.round(height * dpr));
    draw({ now: last, dpr });
  });
  observer.observe(host);
  let frame = 0;
  const loop = (now: number) => {
    frame = requestAnimationFrame(loop);
    last = now;
    draw({ now, dpr });
  };
  frame = requestAnimationFrame(loop);
  destroyRef.onDestroy(() => {
    cancelAnimationFrame(frame);
    observer.disconnect();
  });
}

/** Whether the user asked for less motion (decorative canvas effects are toned down). */
export function prefersReducedMotion(): boolean {
  return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

/** The font of an element, for text drawn on a canvas. */
export function fontOf(element: Element): string {
  return getComputedStyle(element).fontFamily || 'system-ui, sans-serif';
}
