import { ChangeDetectionStrategy, Component, computed, effect, signal, untracked } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { GamePlayer, PlayerChip, injectGameApi } from '@ff/sdk';

import { PlayerStatus, QuestionDifficulty, TriviaBlitzView } from './trivia-blitz.types';

const LETTERS = ['A', 'B', 'C', 'D'] as const;
/** Keyboard shortcuts: 1–4 and A–D pick an answer. */
const KEY_TO_OPTION: Readonly<Record<string, number>> = { '1': 0, '2': 1, '3': 2, '4': 3, a: 0, b: 1, c: 2, d: 3 };
/** Avatars shown under an option at the reveal before collapsing into "+N". */
const MAX_PICKERS = 6;
/** Questions longer than this get a slightly smaller font, so they never push the answers off screen. */
const LONG_QUESTION = 100;
const DIFFICULTY_LABELS: Readonly<Record<QuestionDifficulty, string>> = {
  easy: 'Easy',
  medium: 'Medium',
  hard: 'Hard',
};

/** A player together with their status for this question. */
interface Seat {
  player: GamePlayer;
  status: PlayerStatus;
}

/** Someone who picked an option, shown at the reveal. */
interface Picker {
  player: GamePlayer;
  streak: number;
  title: string;
}

/** One answer button, ready to render. */
interface AnswerCard {
  index: number;
  letter: string;
  text: string;
  /** This browser's (pending or locked-in) pick. */
  mine: boolean;
  /** Revealed: the right answer. */
  correct: boolean;
  /** Revealed: a wrong answer somebody picked. */
  wrong: boolean;
  /** Pushed into the background (not picked while locked in; neither right nor picked at the reveal). */
  dim: boolean;
  pickers: Picker[];
  hiddenPickers: number;
  label: string;
}

type MyResult = 'correct' | 'wrong' | 'missed';

/**
 * Trivia Blitz: a question card with four big answer buttons (click or press 1–4 / A–D). The first
 * pick locks in; until the reveal everyone only sees *who* has answered. The reveal lights up the
 * right answer, marks wrong picks and shows who picked what, the fastest player and a fun fact.
 */
@Component({
  selector: 'ff-trivia-blitz',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, PlayerChip],
  templateUrl: './trivia-blitz.component.html',
  styleUrl: './trivia-blitz.component.scss',
  host: { '(document:keydown)': 'onKeydown($event)' },
})
export class TriviaBlitzComponent {
  protected readonly api = injectGameApi<TriviaBlitzView>();
  protected readonly view = this.api.view;

  /** The option just sent, shown as locked in before the server's snapshot confirms it. */
  private readonly pending = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);

  private readonly questionNumber = computed(() => this.view()?.number ?? 0);
  protected readonly chosen = computed(() => this.view()?.myAnswer ?? this.pending());
  protected readonly canAnswer = computed(() => {
    const view = this.view();
    return this.api.isPlaying() && this.api.phase() === 'PLAYING' && !!view && !view.revealed && this.chosen() == null;
  });
  protected readonly myStreak = computed(() => this.view()?.myStreak ?? 0);
  protected readonly difficultyLabel = computed(() => DIFFICULTY_LABELS[this.view()?.difficulty ?? 'easy']);
  protected readonly longQuestion = computed(() => (this.view()?.question.length ?? 0) > LONG_QUESTION);
  protected readonly streakHint = computed(() => {
    const next = Math.min(300, 100 * this.myStreak());
    return next > 0 ? `Next right answer: +${next} streak bonus` : 'Answer right in a row for a streak bonus';
  });

  /** Everyone's status with their player info, in join order. */
  protected readonly seats = computed<Seat[]>(() =>
    (this.view()?.players ?? []).flatMap((status) => {
      const player = this.api.player(status.playerId);
      return player ? [{ player, status }] : [];
    }),
  );

  /** Connected players who still have to answer. */
  protected readonly waitingFor = computed(
    () => this.seats().filter((seat) => !seat.status.answered && seat.player.online !== false).length,
  );

  protected readonly answers = computed<AnswerCard[]>(() => {
    const view = this.view();
    if (!view) {
      return [];
    }
    const chosen = this.chosen();
    const statuses = new Map(this.seats().map((seat) => [seat.player.id, seat]));
    return view.options.map((text, index) => {
      const mine = chosen === index;
      const pickerIds = view.revealed ? (view.pickers?.[index] ?? []) : [];
      const correct = view.revealed && view.correct === index;
      const wrong = view.revealed && !correct && (pickerIds.length > 0 || mine);
      const dim = view.revealed ? !correct && !wrong : chosen != null && !mine;
      const pickers = pickerIds.flatMap((id) => {
        const seat = statuses.get(id);
        return seat ? [this.picker(seat)] : [];
      });
      const letter = LETTERS[index];
      const notes = [correct ? 'right answer' : '', wrong ? 'wrong' : '', mine ? 'your pick' : ''].filter(Boolean);
      return {
        index,
        letter,
        text,
        mine,
        correct,
        wrong,
        dim,
        pickers: pickers.slice(0, MAX_PICKERS),
        hiddenPickers: Math.max(0, pickers.length - MAX_PICKERS),
        label: `${letter}: ${text}${notes.length ? ` (${notes.join(', ')})` : ''}`,
      };
    });
  });

  /** How this browser's player did at the reveal (null while playing and for spectators). */
  protected readonly myResult = computed<MyResult | null>(() => {
    const view = this.view();
    if (!view?.revealed || !this.api.isPlaying()) {
      return null;
    }
    if (view.myAnswer == null) {
      return 'missed';
    }
    return view.myAnswer === view.correct ? 'correct' : 'wrong';
  });

  protected readonly myStatus = computed(() => {
    const me = this.api.meId();
    return this.seats().find((seat) => seat.player.id === me)?.status ?? null;
  });

  protected readonly fastest = computed(() => {
    const fastest = this.view()?.fastest;
    const player = this.api.player(fastest?.playerId);
    return fastest && player ? { player, time: seconds(fastest.millis) } : null;
  });

  constructor() {
    // A new question: forget the pending pick and any error.
    effect(() => {
      this.questionNumber();
      untracked(() => {
        this.pending.set(null);
        this.error.set(null);
      });
    });
  }

  /** Locks in an answer (first pick only). */
  protected async answer(option: number): Promise<void> {
    if (!this.canAnswer()) {
      return;
    }
    this.pending.set(option);
    this.error.set(null);
    const result = await this.api.send('answer', { option }, { quiet: true });
    if (!result.ok) {
      this.pending.set(null);
      this.error.set(result.message);
    }
  }

  /** 1–4 or A–D answers, unless the user is typing somewhere or a dialog is open. */
  protected onKeydown(event: KeyboardEvent): void {
    if (event.repeat || event.ctrlKey || event.metaKey || event.altKey || !this.canAnswer()) {
      return;
    }
    const target = event.target;
    if (
      target instanceof HTMLElement &&
      target.closest('input, textarea, select, [contenteditable="true"], .cdk-overlay-container')
    ) {
      return;
    }
    const option = KEY_TO_OPTION[event.key.toLowerCase()];
    if (option === undefined) {
      return;
    }
    event.preventDefault();
    void this.answer(option);
  }

  protected seconds(millis: number | undefined): string {
    return seconds(millis ?? 0);
  }

  protected plural(count: number, word: string): string {
    return `${count} ${word}${count === 1 ? '' : 's'}`;
  }

  protected seatTitle(seat: Seat): string {
    const state = seat.status.answered ? 'has answered' : 'is thinking…';
    const streak = seat.status.streak >= 2 ? ` · 🔥 ${seat.status.streak} in a row` : '';
    return `${seat.player.name} ${state}${streak}`;
  }

  private picker(seat: Seat): Picker {
    const time = seat.status.answerMillis == null ? '' : ` · ${seconds(seat.status.answerMillis)}`;
    const streak = seat.status.streak >= 2 ? ` · 🔥 ${seat.status.streak}` : '';
    return { player: seat.player, streak: seat.status.streak, title: `${seat.player.name}${time}${streak}` };
  }
}

function seconds(millis: number): string {
  return `${(millis / 1000).toFixed(1)} s`;
}
