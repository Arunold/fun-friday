import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';

import { GamePlayer, PlayerChip, TimerRing, injectGameApi } from '@ff/sdk';

import { CardView, MemoryMatchView, PlayerProgress } from './memory-match.types';

/** Length of the card flip transition (keep in sync with `$flip` in the stylesheet). */
const FLIP_MS = 460;
/** How long a hint ("wait for your turn") stays visible. */
const HINT_MS = 2600;

/** A seat in the turn-order strip. */
interface Seat {
  progress: PlayerProgress;
  player: GamePlayer;
  active: boolean;
}

/** What the banner above the board says. */
interface Banner {
  /** Changes whenever the banner should animate in again. */
  key: string;
  title: string;
  hint: string;
  /** It's this browser's move: "Your turn!" is highlighted. */
  mine: boolean;
  /** A miss is being shown (neutral title). */
  miss: boolean;
  /** The active player's colour, for the title. */
  color: string | null;
}

/**
 * Memory Match: one shared board of face-down cards. The active player flips two cards; a pair
 * stays up in their colour and they go again, a miss flips back after a moment and the next
 * player moves. Everyone — other players and spectators — watches the same board live.
 */
@Component({
  selector: 'ff-memory-match',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip, TimerRing],
  templateUrl: './memory-match.component.html',
  styleUrl: './memory-match.component.scss',
})
export class MemoryMatchComponent {
  protected readonly api = injectGameApi<MemoryMatchView>();
  protected readonly view = this.api.view;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly timers = new Set<ReturnType<typeof setTimeout>>();

  /** A short message after an impossible click (e.g. "Wait for your turn"). */
  protected readonly hint = signal<string | null>(null);
  /** The card whose flip is on its way to the server. */
  protected readonly pending = signal<number | null>(null);
  /** Faces of cards that just turned face down, kept until the flip animation is over. */
  private readonly fading = signal<ReadonlyMap<number, string>>(new Map());
  private shownFaces = new Map<number, string>();
  private hintTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly active = computed(() => this.api.player(this.view()?.activePlayerId));
  protected readonly myTurn = computed(() => {
    const view = this.view();
    return (
      !!view &&
      this.api.isPlaying() &&
      this.api.phase() === 'PLAYING' &&
      !view.revealed &&
      view.activePlayerId != null &&
      view.activePlayerId === this.api.meId()
    );
  });
  protected readonly canFlip = computed(() => this.myTurn() && !this.view()?.resolving && this.pending() == null);
  /** Face-up cards of the current move (not matched yet). */
  private readonly upCards = computed(() => (this.view()?.cards ?? []).filter((card) => card.state === 'UP').length);

  /** Time left for the current move; re-evaluated with the server clock (it ticks 4×/s). */
  protected readonly moveLeftMs = computed(() => {
    const deadline = this.view()?.moveDeadline;
    this.api.timeLeftMs();
    return deadline == null ? null : Math.max(0, deadline - this.api.now());
  });

  /** The round's order of play with each player's pairs. */
  protected readonly seats = computed<Seat[]>(() => {
    const view = this.view();
    const seats: Seat[] = [];
    for (const progress of view?.players ?? []) {
      const player = this.api.player(progress.playerId);
      if (player) {
        seats.push({ progress, player, active: !view?.revealed && view?.activePlayerId === progress.playerId });
      }
    }
    return seats;
  });

  protected readonly banner = computed<Banner>(() => this.describe(this.view()));
  /** Changes with every new move (another player, a new result or a new board). */
  private readonly moveKey = computed(() => `${this.view()?.activePlayerId ?? '-'}:${this.view()?.last?.seq ?? 0}`);

  constructor() {
    // Remember faces while they are shown so a card turning face down keeps its emoji during the
    // flip animation (the server stops sending a face as soon as the card is hidden).
    effect(() => {
      const view = this.view();
      untracked(() => this.trackFaces(view));
    });
    // A new move or a new board: old hints are stale.
    effect(() => {
      if (this.moveKey()) {
        untracked(() => this.hint.set(null));
      }
    });
    inject(DestroyRef).onDestroy(() => {
      this.timers.forEach((timer) => clearTimeout(timer));
      if (this.hintTimer) {
        clearTimeout(this.hintTimer);
      }
    });
  }

  // ------------------------------------------------------------------ template helpers

  /** The emoji to draw on a card's front (also while it is turning face down). */
  protected face(card: CardView): string {
    return card.emoji ?? this.fading().get(card.index) ?? '';
  }

  protected flippable(card: CardView): boolean {
    return this.canFlip() && card.state === 'HIDDEN';
  }

  protected justMatched(card: CardView): boolean {
    const last = this.view()?.last;
    return card.state === 'MATCHED' && last?.kind === 'MATCH' && last.cards.includes(card.index);
  }

  protected missed(card: CardView): boolean {
    const view = this.view();
    return !!view?.resolving && view.last?.kind === 'MISS' && view.last.cards.includes(card.index);
  }

  protected matcher(card: CardView): GamePlayer | undefined {
    return this.api.player(card.matchedBy);
  }

  protected cardLabel(card: CardView): string {
    const position = `Card ${card.index + 1}`;
    if (card.state === 'HIDDEN') {
      return `${position}, face down`;
    }
    const finder = this.matcher(card);
    return finder ? `${position}, ${card.emoji}, found by ${finder.name}` : `${position}, ${card.emoji}`;
  }

  // ------------------------------------------------------------------ input

  protected async flip(card: CardView): Promise<void> {
    const view = this.view();
    if (!view || !this.api.isPlaying() || view.revealed || this.api.phase() !== 'PLAYING') {
      return;
    }
    if (!this.myTurn()) {
      const name = this.active()?.name;
      this.say(name ? `Wait for your turn — it's ${name}'s move.` : 'Wait for your turn.');
      return;
    }
    if (view.resolving) {
      this.say('Wait for the cards to flip back.');
      return;
    }
    if (card.state !== 'HIDDEN' || this.pending() != null) {
      return;
    }
    this.pending.set(card.index);
    const result = await this.api.send('flip', { index: card.index }, { quiet: true });
    this.pending.set(null);
    if (!result.ok) {
      this.say(result.message);
    }
  }

  /** Arrow keys move the focus across the grid (Enter/Space flip the focused card). */
  protected onKeydown(event: KeyboardEvent, index: number): void {
    const view = this.view();
    if (!view) {
      return;
    }
    const steps: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -view.cols, ArrowDown: view.cols };
    const step = steps[event.key];
    if (step === undefined) {
      return;
    }
    const target = index + step;
    const sameRow = Math.floor(target / view.cols) === Math.floor(index / view.cols);
    if (target < 0 || target >= view.cards.length || (Math.abs(step) === 1 && !sameRow)) {
      return;
    }
    event.preventDefault();
    this.host.nativeElement.querySelector<HTMLElement>(`[data-index="${target}"]`)?.focus();
  }

  // ------------------------------------------------------------------ internals

  private say(message: string): void {
    this.hint.set(message);
    if (this.hintTimer) {
      clearTimeout(this.hintTimer);
    }
    this.hintTimer = setTimeout(() => this.hint.set(null), HINT_MS);
  }

  /** The banner while a round is played (at the reveal the stage shows the round's headline). */
  private describe(view: MemoryMatchView | null): Banner {
    const empty: Banner = { key: 'none', title: '', hint: '', mine: false, miss: false, color: null };
    if (!view || view.revealed) {
      return empty;
    }
    const active = this.active();
    if (!active) {
      return { ...empty, key: 'waiting', title: 'Waiting for players…' };
    }
    const mine = this.myTurn();
    const last = view.last;
    const lastName = this.api.player(last?.playerId)?.name ?? 'A player';
    const base = { key: `${active.id}`, mine, miss: false, color: active.color };
    if (view.resolving) {
      return {
        ...base,
        key: `${active.id}-miss`,
        mine: false,
        miss: true,
        title: mine ? 'No match!' : `${active.name} missed`,
        hint: 'Remember where they are…',
      };
    }
    const title = mine ? 'Your turn!' : `${active.name}'s turn`;
    let hint: string;
    if (this.upCards() > 0) {
      hint = mine ? 'Now find its twin.' : 'Picking a second card…';
    } else if (last?.kind === 'MATCH' && last.playerId === active.id && view.streak > 0) {
      hint = mine ? `A pair! +${last.points} — go again.` : `Found a pair (+${last.points}) and goes again.`;
    } else if (last?.kind === 'TIMEOUT') {
      hint = `${lastName} ran out of time.`;
    } else if (last?.kind === 'AWAY') {
      hint = `${lastName} is away — skipped.`;
    } else {
      hint = mine ? 'Flip two cards.' : 'Picking a card…';
    }
    return { ...base, title, hint };
  }

  private trackFaces(view: MemoryMatchView | null): void {
    const cards = view?.cards ?? [];
    if (!view?.last && cards.every((card) => card.state === 'HIDDEN')) {
      // A fresh board: nothing to fade, forget the previous board.
      this.shownFaces = new Map();
      if (this.fading().size) {
        this.fading.set(new Map());
      }
      return;
    }
    const shown = new Map<number, string>();
    const turnedDown: [number, string][] = [];
    for (const card of cards) {
      const before = this.shownFaces.get(card.index);
      if (card.emoji) {
        shown.set(card.index, card.emoji);
      } else if (before) {
        turnedDown.push([card.index, before]);
      }
    }
    this.shownFaces = shown;
    if (!turnedDown.length) {
      return;
    }
    this.fading.update((faces) => new Map([...faces, ...turnedDown]));
    const timer = setTimeout(() => {
      this.timers.delete(timer);
      this.fading.update((faces) => {
        const rest = new Map(faces);
        turnedDown.forEach(([index]) => rest.delete(index));
        return rest;
      });
    }, FLIP_MS + 140);
    this.timers.add(timer);
  }
}
