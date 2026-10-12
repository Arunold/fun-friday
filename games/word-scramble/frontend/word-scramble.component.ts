import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
  viewChildren,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { Autofocus, GamePlayer, PlayerChip, injectGameApi } from '@ff/sdk';

import { PlayerStatus, WordScrambleView } from './word-scramble.types';

/** One letter tile; `id` is the letter's index in the server's scramble. */
interface Tile {
  id: number;
  letter: string;
}

/** A player's status with their platform info. */
interface Entry {
  status: PlayerStatus;
  player: GamePlayer;
}

/** Inline message under the input. */
interface Feedback {
  kind: 'miss' | 'error';
  text: string;
}

/** A local tile order, valid for one round only. */
interface Arrangement {
  round: string;
  ids: number[];
}

const MEDALS = ['🥇', '🥈', '🥉'];

/** Same rule as the server (GuessText.java): upper case, accents dropped, letters A–Z only. */
function normalize(text: string): string {
  return text
    .normalize('NFD')
    .toUpperCase()
    .replace(/[^A-Z]/g, '');
}

function prefersReducedMotion(): boolean {
  return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

/**
 * Word Scramble: everyone gets the same scrambled tiles and races to type the word. Wrong guesses
 * shake the input and stay listed for the guesser only; a correct guess locks the player in with
 * their rank while the others keep going. Tiles can be shuffled locally (button or Space), clicked
 * to type, and flip into the answer at the reveal.
 */
@Component({
  selector: 'ff-word-scramble',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, PlayerChip, Autofocus],
  templateUrl: './word-scramble.component.html',
  styleUrl: './word-scramble.component.scss',
  host: {
    '(document:keydown)': 'onDocumentKey($event)',
  },
})
export class WordScrambleComponent {
  protected readonly api = injectGameApi<WordScrambleView>();
  protected readonly view = this.api.view;

  protected readonly guess = signal('');
  protected readonly feedback = signal<Feedback | null>(null);
  protected readonly sending = signal(false);

  private readonly injector = inject(Injector);
  private readonly input = viewChild<ElementRef<HTMLInputElement>>('guessInput');
  private readonly guessRow = viewChild<ElementRef<HTMLElement>>('guessRow');
  private readonly tileElements = viewChildren<ElementRef<HTMLElement>>('tile');
  private readonly arrangement = signal<Arrangement | null>(null);
  /** My correct guess (to flip my own tiles before the reveal), for one round. */
  private readonly solvedWith = signal<{ round: string; word: string } | null>(null);
  private lastSent = '';
  private missesSeen = { round: '', count: 0 };

  /** Identifies the current word: changes whenever a new round starts. */
  protected readonly round = computed(() => {
    const progress = this.api.progress();
    return `${progress?.round ?? 0}.${progress?.turn ?? 0}.${this.view()?.scramble ?? ''}`;
  });
  protected readonly scramble = computed(() => this.view()?.scramble ?? '');
  protected readonly me = computed(() => this.view()?.me ?? null);
  protected readonly revealed = computed(() => this.view()?.revealed ?? false);
  private readonly myMisses = computed(() => this.me()?.misses ?? 0);
  private readonly iSolved = computed(() => this.me()?.solved ?? false);

  protected readonly canGuess = computed(() => {
    const view = this.view();
    const me = this.me();
    return (
      !!view &&
      !!me &&
      this.api.isPlaying() &&
      this.api.phase() === 'PLAYING' &&
      !view.revealed &&
      !me.solved &&
      me.misses < view.maxMisses
    );
  });
  protected readonly outOfGuesses = computed(() => {
    const view = this.view();
    const me = this.me();
    return !!view && !!me && !me.solved && me.misses >= view.maxMisses;
  });

  /** The tiles in this browser's (possibly shuffled) order. */
  protected readonly tiles = computed<Tile[]>(() => {
    const scramble = this.scramble();
    const arrangement = this.arrangement();
    const ids =
      arrangement?.round === this.round() && arrangement.ids.length === scramble.length
        ? arrangement.ids
        : Array.from(scramble, (_, i) => i);
    return ids.map((id) => ({ id, letter: scramble[id] }));
  });

  /** Tiles whose letter is already in the input (dimmed). */
  protected readonly usedTiles = computed(() => {
    const used = new Set<number>();
    const tiles = this.tiles();
    for (const letter of normalize(this.guess())) {
      const tile = tiles.find((t) => t.letter === letter && !used.has(t.id));
      if (tile) {
        used.add(tile.id);
      }
    }
    return used;
  });

  /** What the tiles flip to: the answer at the reveal, or my own correct guess before it. */
  protected readonly answer = computed(() => {
    const word = this.view()?.word;
    if (word) {
      return word;
    }
    const mine = this.solvedWith();
    return mine?.round === this.round() ? mine.word : null;
  });

  /** Hint slots `_ A _ _ E`: the letters my hints revealed, in place. */
  protected readonly slots = computed(() => {
    const view = this.view();
    const slots: (string | null)[] = Array.from({ length: view?.length ?? 0 }, () => null);
    for (const hint of view?.me?.hints ?? []) {
      slots[hint.position] = hint.letter;
    }
    return slots;
  });
  protected readonly hintsLeft = computed(() => (this.view()?.maxHints ?? 0) - (this.me()?.hintsUsed ?? 0));
  protected readonly slotsLabel = computed(() => {
    const letters = this.slots().map((slot) => slot ?? 'blank');
    return `Hint: ${letters.join(' ')}`;
  });
  /** E.g. "in 6.5 s · 1 miss · 1 hint". */
  protected readonly solvedLine = computed(() => {
    const me = this.me();
    if (!me?.solved) {
      return '';
    }
    const misses = me.misses ? ` · ${me.misses} ${me.misses === 1 ? 'miss' : 'misses'}` : '';
    const hints = me.hintsUsed ? ` · ${me.hintsUsed} ${me.hintsUsed === 1 ? 'hint' : 'hints'}` : '';
    return `in ${this.seconds(me.timeMillis)}${misses}${hints}`;
  });

  protected readonly entries = computed<Entry[]>(() =>
    (this.view()?.players ?? []).flatMap((status) => {
      const player = this.api.player(status.playerId);
      return player ? [{ status, player }] : [];
    }),
  );
  protected readonly solvers = computed(() =>
    this.entries()
      .filter((entry) => entry.status.solved)
      .sort((a, b) => (a.status.rank ?? 0) - (b.status.rank ?? 0)),
  );
  protected readonly pending = computed(() => this.entries().filter((entry) => !entry.status.solved));
  protected readonly lettersLabel = computed(() => {
    const letters = this.tiles().map((tile) => tile.letter);
    return `Scrambled letters: ${letters.join(' ')}`;
  });

  constructor() {
    // A new round: empty input, no feedback.
    effect(() => {
      this.round();
      untracked(() => {
        this.guess.set('');
        this.feedback.set(null);
        this.lastSent = '';
      });
    });
    // A new wrong guess: shake, say so and start over with an empty input (the miss is listed).
    effect(() => {
      const round = this.round();
      const misses = this.myMisses();
      untracked(() => {
        if (round === this.missesSeen.round && misses > this.missesSeen.count) {
          this.feedback.set({ kind: 'miss', text: 'Not quite — try again!' });
          this.shake();
          if (normalize(this.guess()) === this.lastSent) {
            this.guess.set('');
          }
          this.focusInput();
        }
        this.missesSeen = { round, count: misses };
      });
    });
    // Solved: my last guess was the word — flip my tiles to it right away.
    effect(() => {
      const solved = this.iSolved();
      untracked(() => {
        if (solved && this.lastSent) {
          this.solvedWith.set({ round: this.round(), word: this.lastSent });
          this.feedback.set(null);
        }
      });
    });
  }

  protected medal(rank: number | undefined): string {
    return rank != null && rank <= MEDALS.length ? MEDALS[rank - 1] : `#${rank ?? '?'}`;
  }

  protected seconds(millis: number | undefined): string {
    return `${((millis ?? 0) / 1000).toFixed(1)} s`;
  }

  protected onInput(event: Event): void {
    this.guess.set((event.target as HTMLInputElement).value);
    this.feedback.set(null);
  }

  protected async submit(): Promise<void> {
    const text = this.guess().trim();
    if (!text || !this.canGuess() || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.lastSent = normalize(text);
    const result = await this.api.send('guess', { text }, { quiet: true });
    this.sending.set(false);
    if (!result.ok) {
      this.feedback.set({ kind: 'error', text: result.message });
      this.shake();
      this.focusInput();
    }
  }

  protected async takeHint(): Promise<void> {
    if (!this.canGuess() || this.hintsLeft() <= 0 || this.sending()) {
      return;
    }
    const result = await this.api.send('hint', {}, { quiet: true });
    if (!result.ok) {
      this.feedback.set({ kind: 'error', text: result.message });
    }
    this.focusInput();
  }

  /** Clicking a tile types its letter (replacing a fully selected old guess). */
  protected pick(tile: Tile): void {
    if (!this.canGuess() || this.usedTiles().has(tile.id)) {
      return;
    }
    const input = this.input()?.nativeElement;
    const replace =
      !!input && input.value.length > 0 && input.selectionStart === 0 && input.selectionEnd === input.value.length;
    this.guess.set(replace ? tile.letter : this.guess() + tile.letter);
    this.focusInput();
  }

  /** Rearranges the tiles in this browser only, animating every tile to its new place. */
  protected shuffle(): void {
    const tiles = this.tiles();
    if (tiles.length < 2 || this.answer()) {
      return;
    }
    const before = this.tilePositions();
    const ids = tiles.map((tile) => tile.id);
    let next = ids;
    while (next.every((id, i) => id === ids[i])) {
      next = [...ids];
      for (let i = next.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [next[i], next[j]] = [next[j], next[i]];
      }
    }
    this.arrangement.set({ round: this.round(), ids: next });
    afterNextRender(() => this.slideFrom(before), { injector: this.injector });
  }

  protected onInputKey(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      // Explicit: the browser's implicit submit is ignored while the Guess button is still
      // disabled from the previous render (fast type-then-Enter or paste-then-Enter).
      event.preventDefault();
      void this.submit();
    } else if (event.key === ' ') {
      event.preventDefault();
      this.shuffle();
    } else if (event.key === 'Escape') {
      this.guess.set('');
    }
  }

  /** Typing anywhere on the page goes to the guess input; Space (outside buttons) shuffles. */
  protected onDocumentKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (
      event.defaultPrevented ||
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      !target ||
      target.isContentEditable ||
      ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) ||
      !this.view() ||
      this.revealed()
    ) {
      return;
    }
    if (event.key === ' ' && target.tagName !== 'BUTTON') {
      event.preventDefault();
      this.shuffle();
    } else if (/^[a-z]$/i.test(event.key) && this.canGuess()) {
      event.preventDefault();
      this.guess.update((text) => text + event.key.toUpperCase());
      this.focusInput();
    }
  }

  private focusInput(): void {
    setTimeout(() => this.input()?.nativeElement.focus({ preventScroll: true }));
  }

  private shake(): void {
    if (prefersReducedMotion()) {
      return;
    }
    this.guessRow()?.nativeElement.animate(
      [
        { transform: 'translateX(0)' },
        { transform: 'translateX(-10px)' },
        { transform: 'translateX(9px)' },
        { transform: 'translateX(-6px)' },
        { transform: 'translateX(3px)' },
        { transform: 'translateX(0)' },
      ],
      { duration: 420, easing: 'ease-in-out' },
    );
  }

  private tilePositions(): Map<number, number> {
    const positions = new Map<number, number>();
    for (const ref of this.tileElements()) {
      const element = ref.nativeElement as HTMLElement;
      positions.set(Number(element.dataset['id']), element.getBoundingClientRect().left);
    }
    return positions;
  }

  /** FLIP: every tile starts where it was and glides to its new place. */
  private slideFrom(before: Map<number, number>): void {
    if (prefersReducedMotion()) {
      return;
    }
    for (const ref of this.tileElements()) {
      const element = ref.nativeElement as HTMLElement;
      const left = before.get(Number(element.dataset['id']));
      const dx = left == null ? 0 : left - element.getBoundingClientRect().left;
      if (Math.abs(dx) > 0.5) {
        element.animate([{ transform: `translateX(${dx}px)` }, { transform: 'translateX(0)' }], {
          duration: 380,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
        });
      }
    }
  }
}
