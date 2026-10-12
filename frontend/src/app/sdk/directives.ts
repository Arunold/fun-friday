import { DestroyRef, Directive, ElementRef, afterNextRender, effect, inject, input, untracked } from '@angular/core';

/**
 * Animates a number from its previous value to the new one (ease-out), e.g. scores.
 *
 * ```html
 * <b [ffCountUp]="player.score"></b>
 * ```
 */
@Directive({ selector: '[ffCountUp]' })
export class CountUp {
  readonly ffCountUp = input.required<number>();
  /** Animation length in milliseconds. */
  readonly countUpDuration = input(700);

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private shown = 0;
  private frame = 0;

  constructor() {
    effect(() => {
      const target = this.ffCountUp();
      untracked(() => this.animateTo(target));
    });
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
  }

  private animateTo(target: number): void {
    cancelAnimationFrame(this.frame);
    const from = this.shown;
    const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (from === target || reducedMotion) {
      this.render(target);
      return;
    }
    const duration = this.countUpDuration();
    const startedAt = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, (now - startedAt) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      this.render(Math.round(from + (target - from) * eased));
      if (t < 1) {
        this.frame = requestAnimationFrame(step);
      }
    };
    this.frame = requestAnimationFrame(step);
  }

  private render(value: number): void {
    this.shown = value;
    this.element.nativeElement.textContent = String(value);
  }
}

/**
 * Focuses the element once it is rendered (`ffAutofocus`), or whenever the bound condition
 * becomes true (`[ffAutofocus]="isMyTurn()"`).
 */
@Directive({ selector: '[ffAutofocus]' })
export class Autofocus {
  readonly ffAutofocus = input<boolean | ''>(true);

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private rendered = false;

  constructor() {
    afterNextRender(() => {
      this.rendered = true;
      this.focusIfWanted();
    });
    effect(() => {
      this.ffAutofocus();
      untracked(() => this.focusIfWanted());
    });
  }

  private focusIfWanted(): void {
    if (this.rendered && this.ffAutofocus() !== false) {
      setTimeout(() => this.element.nativeElement.focus({ preventScroll: true }));
    }
  }
}
