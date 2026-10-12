import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterRenderEffect,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';

import { Autofocus, injectGameApi } from '@ff/sdk';

import { RaceResults, ResultLine } from './race-results.component';
import { RaceTrack, TrackLane } from './race-track.component';
import { Difficulty, ProgressPayload, SpeedTypingView } from './speed-typing.types';
import {
  LINE_HEIGHT,
  OVERFLOW,
  accuracyPercent,
  belowAccuracy,
  charCells,
  commonPrefix,
  countEdit,
  errorsToFix,
  fitFontSize,
  matchingChars,
  medal,
  ordinal,
  seconds,
  wordsPerMinute,
} from './typing';

/** Progress updates while typing go out at most this often (ms). */
const SYNC_INTERVAL = 150;
/** When the server still shows an older text after this long, the current one is sent again. */
const RESYNC_AFTER = 600;
/** Catching up after a hiccup happens in steps the server's speed check accepts at once. */
const CATCH_UP_STEP = 12;
/** No updates in the last moments of a round: they would arrive after the end and bounce. */
const DEADLINE_GUARD = 250;
/** Edits that would insert text that wasn't typed (IMP-20) or move it around. */
const BLOCKED_INPUT = new Set([
  'insertFromPaste',
  'insertFromPasteAsQuotation',
  'insertFromDrop',
  'insertFromYank',
  'insertReplacementText',
  'insertTranspose',
  'historyUndo',
  'historyRedo',
  'deleteByCut',
  'deleteByDrag',
]);
/** Keys that would move the caret away from the end of the input. */
const CARET_KEYS = new Set(['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End', 'PageUp', 'PageDown']);
const DIFFICULTY_LABELS: Record<Difficulty, string> = { SHORT: 'Short', MEDIUM: 'Medium', LONG: 'Long' };
/** Largest font size per text length; smaller boards shrink it further (see `fitFontSize`). */
const MAX_FONT: Record<Difficulty, number> = { SHORT: 40, MEDIUM: 36, LONG: 32 };

/** What this browser types in the current round (the server decides progress and finish). */
interface Session {
  /** Round and turn it belongs to. */
  key: string;
  text: string;
  typed: string;
  keystrokes: number;
  errors: number;
  /** Server time of the first keystroke. */
  startedAt: number | null;
  /** Server time when the typed text first matched exactly. */
  completedAt: number | null;
}

/**
 * Speed Typing: everybody types the same text. The text block shows every character's state with a
 * caret; a transparent input on top captures the keystrokes (no paste, drop, autocorrect or caret
 * jumps). Live WPM and accuracy are computed here; progress updates go to the server about every
 * 150 ms, which decides progress, finish and place. Below the text, lanes show everyone's race.
 */
@Component({
  selector: 'ff-speed-typing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Autofocus, RaceTrack, RaceResults],
  templateUrl: './speed-typing.component.html',
  styleUrl: './speed-typing.component.scss',
  host: { '(document:keydown)': 'onDocumentKeydown($event)' },
})
export class SpeedTypingComponent {
  protected readonly api = injectGameApi<SpeedTypingView>();
  protected readonly view = this.api.view;
  protected readonly overflow = OVERFLOW;
  protected readonly medal = medal;
  protected readonly ordinal = ordinal;
  protected readonly seconds = seconds;

  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');
  private readonly board = viewChild<ElementRef<HTMLElement>>('board');
  private readonly textBlock = viewChild<ElementRef<HTMLElement>>('textBlock');
  private readonly caret = viewChild<ElementRef<HTMLElement>>('caret');

  private readonly session = signal<Session | null>(null);
  protected readonly focused = signal(false);
  /** True right after a keystroke: the caret stops blinking while you type. */
  protected readonly typing = signal(false);
  /** A short message, e.g. after a blocked paste. */
  protected readonly notice = signal<string | null>(null);
  private readonly boardSize = signal({ width: 0, height: 0 });
  /** Server time, ticking while the round runs (live WPM). */
  private readonly clock = signal(0);

  private lastSyncAt = 0;
  private syncTimer: ReturnType<typeof setTimeout> | undefined;
  private noticeTimer: ReturnType<typeof setTimeout> | undefined;
  private typingTimer: ReturnType<typeof setTimeout> | undefined;
  private syncedInput: HTMLInputElement | null = null;
  private syncedKey: string | null = null;

  // ------------------------------------------------------------------ derived state

  protected readonly text = computed(() => this.view()?.text ?? '');
  protected readonly typed = computed(() => this.session()?.typed ?? '');
  /** A player of this round (spectators only watch). */
  protected readonly racing = computed(() => this.api.isPlaying() && !!this.view()?.me);
  protected readonly revealed = computed(() => !!this.view()?.revealed);
  protected readonly finished = computed(() => !!this.view()?.me?.finished);
  protected readonly complete = computed(() => this.text().length > 0 && this.typed() === this.text());
  protected readonly canType = computed(
    () => this.racing() && this.api.phase() === 'PLAYING' && !this.revealed() && !this.finished() && !this.complete(),
  );
  protected readonly cells = computed(() => charCells(this.text(), this.racing() ? this.typed() : ''));
  protected readonly correct = computed(() => commonPrefix(this.text(), this.typed()));
  protected readonly progress = computed(() =>
    this.finished() ? 100 : Math.min(99, Math.floor((this.correct() * 100) / Math.max(1, this.text().length))),
  );
  /** Wrong and extra characters once the whole length is typed ("Fix 3 errors to finish"). */
  protected readonly errorsLeft = computed(() =>
    this.typed().length >= this.text().length && !this.complete() ? errorsToFix(this.text(), this.typed()) : 0,
  );
  protected readonly keystrokes = computed(() => this.session()?.keystrokes ?? 0);
  protected readonly accuracy = computed(() => {
    const me = this.view()?.me;
    if (me?.finished && me.accuracy != null) {
      return me.accuracy;
    }
    const session = this.session();
    return session ? accuracyPercent(session.keystrokes, session.errors) : null;
  });
  protected readonly lowAccuracy = computed(() => {
    const session = this.session();
    const view = this.view();
    return (
      !!session &&
      !!view &&
      session.keystrokes >= 10 &&
      belowAccuracy(session.keystrokes, session.errors, view.minAccuracy)
    );
  });
  protected readonly wpm = computed(() => {
    const me = this.view()?.me;
    if (me?.finished && me.wpm != null) {
      return me.wpm;
    }
    const session = this.session();
    if (session?.startedAt == null) {
      return null;
    }
    const end = session.completedAt ?? Math.max(this.clock(), session.startedAt);
    return wordsPerMinute(matchingChars(session.text, session.typed), end - session.startedAt);
  });
  protected readonly difficultyLabel = computed(() => DIFFICULTY_LABELS[this.view()?.difficulty ?? 'MEDIUM']);
  protected readonly fontSize = computed(() => {
    const { width, height } = this.boardSize();
    const max = MAX_FONT[this.view()?.difficulty ?? 'MEDIUM'];
    return width > 0 ? fitFontSize(width, height, this.text().length + 4, 16, max) : 28;
  });
  protected readonly lineHeight = LINE_HEIGHT;

  /** Everyone's lane; mine follows my keystrokes right away. */
  protected readonly lanes = computed<TrackLane[]>(() => {
    const view = this.view();
    const meId = this.api.meId();
    if (!view) {
      return [];
    }
    return view.lanes.flatMap((lane) => {
      const player = this.api.player(lane.playerId);
      if (!player) {
        return [];
      }
      const me = lane.playerId === meId && this.racing();
      const progress = me && !lane.finished ? this.progress() : lane.progress;
      return [{ player, progress, finished: lane.finished, rank: lane.rank, wpm: lane.wpm, me }];
    });
  });

  protected readonly results = computed<ResultLine[]>(() => {
    const meId = this.api.meId();
    return (this.view()?.results ?? []).flatMap((row) => {
      const player = this.api.player(row.playerId);
      return player ? [{ row, player, me: row.playerId === meId }] : [];
    });
  });

  private readonly sessionKey = computed(() => this.session()?.key ?? null);

  constructor() {
    // A new round (or the first view after a refresh): start typing from what the server has.
    effect(() => {
      const view = this.view();
      const progress = this.api.progress();
      if (!view || !progress) {
        return;
      }
      const key = `${progress.round}.${progress.turn}`;
      untracked(() => {
        if (this.session()?.key !== key) {
          this.startSession(key, view);
        }
      });
    });

    // The input element shows the session's text when it (or the round) is new.
    afterRenderEffect(() => {
      const input = this.input()?.nativeElement ?? null;
      const key = this.sessionKey();
      if (input && key && (input !== this.syncedInput || key !== this.syncedKey)) {
        input.value = untracked(() => this.typed());
        this.syncedInput = input;
        this.syncedKey = key;
      }
    });

    // The caret glides to the next character to type.
    afterRenderEffect(() => {
      const index = this.typed().length;
      this.cells();
      this.fontSize();
      this.boardSize();
      const block = this.textBlock()?.nativeElement;
      const caret = this.caret()?.nativeElement;
      const target = block?.children.item(index);
      if (caret && target instanceof HTMLElement) {
        caret.style.transform = `translate(${target.offsetLeft}px, ${target.offsetTop}px)`;
        caret.style.height = `${target.offsetHeight}px`;
      }
    });

    // The text block's font fits the board.
    effect((onCleanup) => {
      const board = this.board()?.nativeElement;
      if (!board) {
        return;
      }
      const observer = new ResizeObserver(([entry]) =>
        this.boardSize.set({ width: entry.contentRect.width, height: entry.contentRect.height }),
      );
      observer.observe(board);
      onCleanup(() => observer.disconnect());
    });

    const ticker = setInterval(() => {
      if (this.api.phase() === 'PLAYING') {
        this.clock.set(this.api.now());
        this.resync();
      }
    }, 200);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(ticker);
      clearTimeout(this.syncTimer);
      clearTimeout(this.noticeTimer);
      clearTimeout(this.typingTimer);
    });
  }

  // ------------------------------------------------------------------ input events

  protected onInput(event: Event): void {
    if ((event as InputEvent).isComposing) {
      return; // dead keys: the composed character arrives with compositionend
    }
    this.accept(event.target as HTMLInputElement);
  }

  protected onCompositionEnd(event: CompositionEvent): void {
    this.accept(event.target as HTMLInputElement);
  }

  protected onBeforeInput(event: InputEvent): void {
    if (BLOCKED_INPUT.has(event.inputType)) {
      event.preventDefault();
      if (event.inputType.startsWith('insertFrom')) {
        this.flash('Pasting is switched off: type it out!');
      }
    }
  }

  protected blockPaste(event: Event): void {
    event.preventDefault();
    this.flash('Pasting is switched off: type it out!');
  }

  protected onKeydown(event: KeyboardEvent): void {
    const selectAll = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a';
    if (CARET_KEYS.has(event.key) || selectAll) {
      event.preventDefault();
    }
  }

  /** Typing always happens at the end of the text: clicks and selections can't move the caret. */
  protected keepCaretAtEnd(event: Event): void {
    const input = event.target as HTMLInputElement;
    const end = input.value.length;
    if (input.selectionStart !== end || input.selectionEnd !== end) {
      input.setSelectionRange(end, end);
    }
  }

  /** Typing anywhere on the page keeps going in the race (focus stays in the input). */
  protected onDocumentKeydown(event: KeyboardEvent): void {
    const input = this.input()?.nativeElement;
    const active = document.activeElement;
    if (
      !input ||
      active === input ||
      !this.canType() ||
      event.key.length !== 1 ||
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      active instanceof HTMLInputElement ||
      active instanceof HTMLTextAreaElement ||
      active?.closest('.cdk-overlay-container')
    ) {
      return;
    }
    event.preventDefault();
    input.focus({ preventScroll: true });
    input.value += event.key;
    this.accept(input);
  }

  // ------------------------------------------------------------------ typing

  private startSession(key: string, view: SpeedTypingView): void {
    clearTimeout(this.syncTimer);
    this.syncTimer = undefined;
    this.lastSyncAt = 0;
    this.notice.set(null);
    const me = view.me;
    this.session.set({
      key,
      text: view.text,
      typed: me?.typed ?? '',
      keystrokes: me?.keystrokes ?? 0,
      errors: me?.errors ?? 0,
      startedAt: me?.startedAt ?? null,
      completedAt: null,
    });
  }

  /** Takes the input's new value: counts the keystrokes, then reports the progress. */
  private accept(input: HTMLInputElement): void {
    const session = this.session();
    if (!session || !this.canType()) {
      input.value = session?.typed ?? '';
      return;
    }
    const value = input.value.slice(0, session.text.length + OVERFLOW);
    if (value !== input.value) {
      input.value = value;
    }
    if (value === session.typed) {
      return;
    }
    const added = countEdit(session.text, session.typed, value);
    const now = this.api.now();
    this.session.set({
      ...session,
      typed: value,
      keystrokes: session.keystrokes + added.keystrokes,
      errors: session.errors + added.errors,
      startedAt: session.startedAt ?? (added.keystrokes > 0 ? now : null),
      completedAt: value === session.text ? now : null,
    });
    this.markTyping();
    if (value === session.text) {
      this.report();
    } else {
      this.scheduleSync();
    }
  }

  private scheduleSync(): void {
    if (this.syncTimer !== undefined) {
      return;
    }
    const wait = Math.max(0, this.lastSyncAt + SYNC_INTERVAL - performance.now());
    this.syncTimer = setTimeout(() => {
      this.syncTimer = undefined;
      this.report();
    }, wait);
  }

  /**
   * Sends the progress: throttled updates fire-and-forget, the finishing one reliably (the server
   * checks it and records the finish).
   */
  private report(): void {
    const payload = this.payload();
    if (!payload) {
      return;
    }
    const final = payload.typed === this.text();
    if (!this.mayReport(final)) {
      return;
    }
    clearTimeout(this.syncTimer);
    this.syncTimer = undefined;
    this.lastSyncAt = performance.now();
    if (!final) {
      this.api.sendFast('progress', payload);
      return;
    }
    void this.api.send('progress', payload, { quiet: true }).then((result) => {
      if (!result.ok && this.api.phase() === 'PLAYING') {
        this.flash(result.message);
      }
    });
  }

  /**
   * What to report: everything typed — or, when the server fell behind (a network hiccup), the
   * next step its speed check accepts at once (the text may grow by a burst in length and in
   * correct characters); the resync loop sends the rest.
   */
  private payload(): ProgressPayload | null {
    const session = this.session();
    if (!session) {
      return null;
    }
    let length = session.typed.length;
    const server = this.view()?.me;
    if (server && !server.finished) {
      const serverCorrect = commonPrefix(session.text, server.typed);
      length = Math.min(length, server.typed.length + CATCH_UP_STEP);
      if (commonPrefix(session.text, session.typed) > serverCorrect + CATCH_UP_STEP) {
        length = Math.min(length, serverCorrect + CATCH_UP_STEP);
      }
    }
    return { typed: session.typed.slice(0, length), keystrokes: session.keystrokes, errors: session.errors };
  }

  private mayReport(final: boolean): boolean {
    const view = this.view();
    if (!view?.me || view.me.finished || view.revealed || !this.api.isPlaying() || this.api.phase() !== 'PLAYING') {
      return false;
    }
    return final || this.api.now() < view.endsAt - DEADLINE_GUARD;
  }

  /** Sends the current text again when the server missed or ignored the last update. */
  private resync(): void {
    const session = this.session();
    const server = this.view()?.me;
    if (
      session &&
      server &&
      !server.finished &&
      session.typed !== server.typed &&
      performance.now() - this.lastSyncAt > RESYNC_AFTER
    ) {
      this.report();
    }
  }

  private markTyping(): void {
    this.typing.set(true);
    clearTimeout(this.typingTimer);
    this.typingTimer = setTimeout(() => this.typing.set(false), 700);
  }

  private flash(message: string): void {
    this.notice.set(message);
    clearTimeout(this.noticeTimer);
    this.noticeTimer = setTimeout(() => this.notice.set(null), 2600);
  }
}
