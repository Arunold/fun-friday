import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  effect,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { Autofocus, ChipPlayer, PlayerChip, injectGameApi } from '@ff/sdk';

import { ChoiceView, CloseEvent, CorrectGuess, FeedEntry, PictionaryView } from './pictionary.types';
import { SketchBoard } from './sketch-board.component';

/** One letter box of the word banner. */
interface Tile {
  char: string;
  kind: 'letter' | 'hidden' | 'gap' | 'mark';
}

const LEVEL_LABELS: Record<ChoiceView['level'], string> = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };

/**
 * Pictionary: one player draws, everybody else guesses in the feed next to the canvas.
 *
 * The word banner shows the drawer their word, guessers the masked word with hint letters, and
 * everybody the word once revealed (the server never sends it to anyone else — BUG-07). Guesses
 * appear live; a near miss triggers a private "So close!" (BUG-09).
 */
@Component({
  selector: 'ff-pictionary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, PlayerChip, Autofocus, SketchBoard],
  templateUrl: './pictionary.component.html',
  styleUrl: './pictionary.component.scss',
  host: { '(document:keydown)': 'onKey($event)' },
})
export class PictionaryComponent {
  protected readonly api = injectGameApi<PictionaryView>();
  protected readonly view = this.api.view;

  protected readonly guess = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly closeGuess = signal<string | null>(null);
  protected readonly choosingWord = signal(false);
  private readonly feedList = viewChild<ElementRef<HTMLElement>>('feed');
  private readonly guessRow = viewChild<ElementRef<HTMLElement>>('guessRow');
  private closeTimer: ReturnType<typeof setTimeout> | undefined;

  /** The turn is running (not revealed). */
  protected readonly running = computed(() => this.api.phase() === 'PLAYING' && !this.view()?.revealed);
  protected readonly isDrawer = computed(() => this.api.isPlaying() && this.view()?.drawerId === this.api.meId());
  protected readonly choosing = computed(() => this.running() && this.view()?.phase === 'CHOOSING');
  protected readonly canDraw = computed(() => this.running() && this.isDrawer() && this.view()?.phase === 'DRAWING');
  protected readonly showGuessInput = computed(
    () => this.api.isPlaying() && !this.isDrawer() && this.running() && !this.view()?.guessed,
  );
  protected readonly canGuess = computed(() => this.showGuessInput() && this.view()?.phase === 'DRAWING');
  protected readonly secondsLeft = computed(() => Math.ceil((this.api.timeLeftMs() ?? 0) / 1000));
  protected readonly lastFeedId = computed(() => this.view()?.feed.at(-1)?.id ?? 0);
  protected readonly ops = computed(() => this.view()?.drawing.ops.length ?? 0);

  protected readonly drawerChip = computed<ChipPlayer>(() => {
    const view = this.view();
    return (
      this.api.player(view?.drawerId) ?? { name: view?.drawerName ?? '', avatar: '🎨', color: 'var(--mat-sys-outline)' }
    );
  });

  /** The word if this viewer may see it, otherwise the mask (`_` for hidden letters). */
  protected readonly tiles = computed<Tile[]>(() => {
    const view = this.view();
    const text = view?.word ?? view?.mask;
    if (!text) {
      return [];
    }
    return [...text].map((char) => ({
      char,
      kind: char === ' ' ? 'gap' : char === '_' ? 'hidden' : /\p{L}/u.test(char) ? 'letter' : 'mark',
    }));
  });

  /** "8 letters", "3 + 5 letters". */
  protected readonly letterCount = computed(() => {
    const text = this.view()?.word ?? this.view()?.mask;
    if (!text) {
      return '';
    }
    const words = text.split(' ').map((part) => part.replace(/[^\p{L}_]/gu, '').length);
    const total = words.reduce((sum, count) => sum + count, 0);
    return words.length > 1 ? `${words.join(' + ')} letters` : `${total} letters`;
  });

  protected readonly bannerLabel = computed(() => {
    const view = this.view();
    if (!view) {
      return '';
    }
    if (view.revealed) {
      return view.word ? 'The word was' : '';
    }
    if (view.phase === 'CHOOSING') {
      return this.isDrawer() ? 'Pick a word to draw' : 'Waiting for a word…';
    }
    if (this.isDrawer()) {
      return 'Draw';
    }
    return view.guessed ? 'You got it!' : 'Guess the word';
  });

  /** "Ann is drawing", "You are choosing…", "Ann drew this". */
  protected readonly drawerLine = computed(() => {
    const view = this.view();
    const me = this.isDrawer();
    if (!view) {
      return { name: '', verb: '' };
    }
    let verb: string;
    if (view.revealed) {
      verb = 'drew this';
    } else if (view.phase === 'CHOOSING') {
      verb = me ? 'are choosing…' : 'is choosing…';
    } else {
      verb = me ? 'are drawing' : 'is drawing';
    }
    return { name: me ? 'You' : view.drawerName, verb };
  });

  protected readonly statusText = computed(() => {
    const view = this.view();
    if (!view) {
      return '';
    }
    if (!this.api.isPlaying()) {
      return '👀 You are watching — guesses show up here.';
    }
    if (this.isDrawer()) {
      return view.phase === 'CHOOSING' && !view.revealed
        ? 'Pick a word — everybody is waiting for you.'
        : 'You are drawing — guesses show up here.';
    }
    if (view.guessed) {
      return view.revealed ? '🎉 You guessed it!' : '🎉 You got it! Waiting for the others…';
    }
    return view.revealed ? 'Next drawing coming up…' : '';
  });

  protected readonly emptyFeedText = computed(() =>
    this.showGuessInput() ? 'No guesses yet — be the first!' : 'Guesses will show up here.',
  );

  /** Faint hint on the blank paper while drawing. */
  protected readonly paperHint = computed(() => {
    const view = this.view();
    if (!view || view.phase !== 'DRAWING' || view.revealed || this.ops() > 0) {
      return null;
    }
    return this.isDrawer() ? 'Start drawing — no letters or numbers!' : `${view.drawerName} is drawing…`;
  });

  /** Changes once per turn (a primitive, so snapshots within a turn don't retrigger effects). */
  private readonly turnKey = computed(() => this.view()?.turn ?? 0);

  constructor() {
    // A new turn: fresh input.
    effect(() => {
      this.turnKey();
      untracked(() => {
        this.guess.set('');
        this.error.set(null);
        this.closeGuess.set(null);
        this.choosingWord.set(false);
      });
    });
    // Newest guesses at the bottom: keep the feed scrolled down.
    afterRenderEffect(() => {
      this.lastFeedId();
      const list = this.feedList()?.nativeElement;
      if (list) {
        list.scrollTop = list.scrollHeight;
      }
    });
    this.api.events$.pipe(takeUntilDestroyed()).subscribe((event) => {
      if (event.type === 'close') {
        this.showClose((event.payload as CloseEvent).text);
      }
    });
  }

  protected async choose(index: number): Promise<void> {
    if (!this.choosing() || !this.isDrawer() || this.choosingWord()) {
      return;
    }
    this.choosingWord.set(true);
    const result = await this.api.send('choose', { index });
    if (!result.ok) {
      this.choosingWord.set(false);
    }
  }

  protected async submit(): Promise<void> {
    const text = this.guess().trim();
    if (!text || !this.canGuess()) {
      return;
    }
    this.guess.set('');
    const result = await this.api.send('guess', { text }, { quiet: true });
    if (result.ok) {
      this.error.set(null);
    } else {
      this.error.set(result.message);
      this.closeGuess.set(null);
      this.shake();
    }
  }

  protected onKey(event: KeyboardEvent): void {
    const index = Number(event.key) - 1;
    const typing = event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement;
    if (this.choosing() && this.isDrawer() && !typing && index >= 0 && index < (this.view()?.choices?.length ?? 0)) {
      event.preventDefault();
      void this.choose(index);
    }
  }

  protected levelLabel(choice: ChoiceView): string {
    return LEVEL_LABELS[choice.level];
  }

  protected chipFor(entry: FeedEntry | CorrectGuess): ChipPlayer {
    return this.api.player(entry.playerId) ?? { name: entry.name, avatar: '🙂', color: 'var(--mat-sys-outline)' };
  }

  protected colorOf(entry: FeedEntry): string {
    return this.api.player(entry.playerId)?.color ?? 'var(--ff-text-muted)';
  }

  protected seconds(ms: number): string {
    return `${(ms / 1000).toFixed(1)} s`;
  }

  private showClose(text: string): void {
    this.error.set(null);
    this.closeGuess.set(text);
    clearTimeout(this.closeTimer);
    this.closeTimer = setTimeout(() => this.closeGuess.set(null), 4000);
    this.guessRow()?.nativeElement.animate(
      [{ transform: 'scale(1)' }, { transform: 'scale(1.03)' }, { transform: 'scale(1)' }],
      { duration: 360, easing: 'ease-out' },
    );
  }

  private shake(): void {
    this.guessRow()?.nativeElement.animate(
      [
        { transform: 'translateX(0)' },
        { transform: 'translateX(-6px)' },
        { transform: 'translateX(6px)' },
        { transform: 'translateX(-3px)' },
        { transform: 'translateX(0)' },
      ],
      { duration: 360, easing: 'ease-in-out' },
    );
  }
}
