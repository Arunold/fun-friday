import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { Autofocus, PlayerChip, injectGameApi } from '@ff/sdk';

import { GuessView, NumberGuessView, PlayerStatus } from './number-guess.types';

/**
 * Number Guess: everyone hunts the same secret number with higher/lower hints. Other players'
 * guesses stay hidden until the reveal, which shows where everyone landed.
 */
@Component({
  selector: 'ff-number-guess',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, PlayerChip, Autofocus],
  templateUrl: './number-guess.component.html',
  styleUrl: './number-guess.component.scss',
})
export class NumberGuessComponent {
  protected readonly api = injectGameApi<NumberGuessView>();
  protected readonly view = this.api.view;

  protected readonly guess = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);
  private readonly inputRow = viewChild<ElementRef<HTMLElement>>('inputRow');
  /** Guesses already seen, to react to new hints only (-1 before the first view). */
  private seenGuesses = -1;

  protected readonly myGuesses = computed(() => this.view()?.myGuesses ?? []);
  protected readonly attemptsLeft = computed(() => (this.view()?.attempts ?? 0) - this.myGuesses().length);
  protected readonly attemptDots = computed(() =>
    Array.from({ length: this.view()?.attempts ?? 0 }, (_, i) => i < this.myGuesses().length),
  );
  protected readonly canGuess = computed(
    () =>
      this.api.isPlaying() &&
      this.api.phase() === 'PLAYING' &&
      this.view()?.myStatus === 'GUESSING' &&
      !this.view()?.revealed,
  );

  /** The interval that can still contain the number, from my hints. */
  protected readonly bounds = computed(() => {
    const view = this.view();
    if (!view) {
      return { low: 0, high: 0 };
    }
    let low = view.min;
    let high = view.max;
    for (const guess of this.myGuesses()) {
      if (guess.hint === 'HIGHER') {
        low = Math.max(low, guess.value + 1);
      } else if (guess.hint === 'LOWER') {
        high = Math.min(high, guess.value - 1);
      } else {
        low = high = guess.value;
      }
    }
    return { low, high };
  });

  /** Everyone's status with their player info, me first. */
  protected readonly statuses = computed(() => {
    const me = this.api.meId();
    return (this.view()?.players ?? [])
      .map((status) => ({ status, player: this.api.player(status.playerId) }))
      .filter((entry) => !!entry.player)
      .sort((a, b) => Number(b.status.playerId === me) - Number(a.status.playerId === me));
  });

  /** Players' closest guesses on the reveal, for markers on the range bar. */
  protected readonly revealMarkers = computed(() =>
    this.statuses()
      .filter((entry) => entry.status.best !== undefined)
      .map((entry) => ({ ...entry, position: this.position(entry.status.best!) })),
  );

  constructor() {
    // A new wrong guess shakes the input (the hint arrives with the next snapshot).
    effect(() => {
      const guesses = this.myGuesses();
      untracked(() => {
        const last = guesses.at(-1);
        if (this.seenGuesses >= 0 && guesses.length > this.seenGuesses && last?.hint !== 'CORRECT') {
          this.shake();
        }
        this.seenGuesses = guesses.length;
      });
    });
    // A new round: clear the input and errors.
    effect(() => {
      this.api.progress(); // changes once per turn (round/turn counters)
      untracked(() => {
        this.guess.set(null);
        this.error.set(null);
      });
    });
  }

  /** Position of a value on the range bar in percent. */
  protected position(value: number): number {
    const view = this.view();
    if (!view || view.max === view.min) {
      return 0;
    }
    return ((value - view.min) / (view.max - view.min)) * 100;
  }

  protected hintArrow(guess: GuessView): string {
    return guess.hint === 'HIGHER' ? '↑' : guess.hint === 'LOWER' ? '↓' : '🎯';
  }

  protected hintText(guess: GuessView): string {
    return guess.hint === 'HIGHER' ? 'higher' : guess.hint === 'LOWER' ? 'lower' : 'correct!';
  }

  protected progressDots(status: PlayerStatus): boolean[] {
    return Array.from({ length: this.view()?.attempts ?? 0 }, (_, i) => i < status.guessesUsed);
  }

  protected async submit(): Promise<void> {
    const view = this.view();
    const value = this.guess();
    if (!view || value == null || !this.canGuess() || this.sending()) {
      return;
    }
    if (!Number.isInteger(value) || value < view.min || value > view.max) {
      this.fail(`Pick a whole number from ${view.min} to ${view.max}.`);
      return;
    }
    if (this.myGuesses().some((g) => g.value === value)) {
      this.fail(`You already tried ${value}.`);
      return;
    }
    this.sending.set(true);
    const result = await this.api.send('guess', { value }, { quiet: true });
    this.sending.set(false);
    if (!result.ok) {
      this.fail(result.message);
      return;
    }
    this.error.set(null);
    this.guess.set(null);
  }

  private fail(message: string): void {
    this.error.set(message);
    this.shake();
  }

  private shake(): void {
    this.inputRow()?.nativeElement.animate(
      [
        { transform: 'translateX(0)' },
        { transform: 'translateX(-8px)' },
        { transform: 'translateX(8px)' },
        { transform: 'translateX(-4px)' },
        { transform: 'translateX(0)' },
      ],
      { duration: 380, easing: 'ease-in-out' },
    );
  }
}
