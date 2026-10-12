import { ChangeDetectionStrategy, Component, afterRenderEffect, computed, signal } from '@angular/core';

import { GamePlayer, PlayerChip, injectGameApi } from '@ff/sdk';

import {
  FakeOut,
  ReactionShowdownView,
  ResultEntry,
  SignalResult,
  SignalState,
  TapPayload,
} from './reaction-showdown.types';

/** Label of each decoy colour (the colour itself comes from the stylesheet). */
const FAKE_LABELS: Record<FakeOut, string> = {
  HOLD: '✋ NOT YET',
  STEADY: '🤚 STEADY…',
  FREEZE: '🧊 FREEZE!',
};

/** Mirrors `ReactionShowdownLogic.ANTICIPATION_MS`: faster taps are guesses (false starts). */
const ANTICIPATION_MS = 100;

/** My outcome on the current signal: from the server, or from my own tap until it answers. */
type Outcome =
  | { kind: 'none' }
  | { kind: 'tapped'; ms?: number }
  | { kind: 'early'; fake: boolean }
  | { kind: 'rejected'; message: string };

/** A player in the status row under the field. */
interface StatusChip {
  player: GamePlayer;
  me: boolean;
  /** `tapped`/`jumped` while the signal runs; `fastest`/`timed`/`jumped`/`missed` on its result. */
  mark: 'none' | 'tapped' | 'jumped' | 'fastest' | 'timed' | 'missed';
  text: string;
}

/** A cell of the round table (one signal of one player). */
interface TableCell {
  text: string;
  kind: 'timed' | 'jump' | 'miss';
  /** The fastest reaction of the round. */
  best: boolean;
}

/**
 * Reaction Showdown: a full-stage colour field. Wait while it's dark, tap the moment it turns
 * green, don't fall for the decoys.
 *
 * Fair timing (IMP-21): when a GO frame has rendered, the component notes `performance.now()` for
 * that signal id; a tap sends the elapsed milliseconds, so network latency never counts. The field
 * itself never moves — only an inner orb pulses, scale-only (BUG-23).
 */
@Component({
  selector: 'ff-reaction-showdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip],
  templateUrl: './reaction-showdown.component.html',
  styleUrl: './reaction-showdown.component.scss',
  host: {
    '(document:keydown)': 'onKey($event)',
  },
})
export class ReactionShowdownComponent {
  protected readonly api = injectGameApi<ReactionShowdownView>();
  protected readonly view = this.api.view;

  protected readonly state = computed<SignalState>(() => this.view()?.state ?? 'WAITING');
  protected readonly signalId = computed(() => this.view()?.signalId ?? 0);
  protected readonly revealed = computed(() => !!this.view()?.revealed);
  protected readonly fakeLabel = computed(() => FAKE_LABELS[this.view()?.fake ?? 'HOLD']);

  /** My tap on the current signal before the server confirms it. */
  private readonly local = signal<{ signalId: number; outcome: Outcome } | null>(null);
  /** When this screen rendered GO, per signal (`performance.now()`). */
  private goSeen: { signalId: number; at: number } | null = null;
  private readonly goSignal = computed(() => (this.state() === 'GO' ? this.signalId() : null));

  protected readonly outcome = computed<Outcome>(() => {
    const me = this.view()?.me;
    if (me?.falseStart) {
      return { kind: 'early', fake: me.fake };
    }
    if (me?.tapped) {
      return { kind: 'tapped', ms: me.ms };
    }
    const local = this.local();
    return local && local.signalId === this.signalId() ? local.outcome : { kind: 'none' };
  });
  protected readonly tappedMs = computed(() => {
    const outcome = this.outcome();
    return outcome.kind === 'tapped' ? outcome.ms : undefined;
  });
  protected readonly jumpedOnFake = computed(() => {
    const outcome = this.outcome();
    return outcome.kind === 'early' && outcome.fake;
  });
  protected readonly rejection = computed(() => {
    const outcome = this.outcome();
    return outcome.kind === 'rejected' ? outcome.message : '';
  });

  /** Taps count in WAITING, FAKE (both false starts) and GO — once per signal, players only (BUG-33). */
  protected readonly canTap = computed(
    () =>
      this.api.isPlaying() &&
      this.api.phase() === 'PLAYING' &&
      !this.revealed() &&
      this.state() !== 'RESULT' &&
      this.outcome().kind === 'none',
  );

  /** CSS classes of the field. */
  protected readonly fieldClass = computed(() => {
    const state = this.state();
    const kind = this.outcome().kind;
    const classes = [state.toLowerCase()];
    if (state === 'FAKE') {
      classes.push(`fake-${(this.view()?.fake ?? 'HOLD').toLowerCase()}`);
    }
    if (kind === 'tapped' && state === 'GO') {
      classes.push('locked');
    }
    if (kind === 'early' && state !== 'RESULT') {
      classes.push('jumped');
    }
    return classes.join(' ');
  });

  /** The result of the current signal while it is shown. */
  protected readonly result = computed<SignalResult | null>(() =>
    this.state() === 'RESULT' ? (this.view()?.results.at(-1) ?? null) : null,
  );
  protected readonly myEntry = computed<ResultEntry | null>(() => {
    const me = this.api.meId();
    return this.result()?.entries.find((entry) => entry.playerId === me) ?? null;
  });
  protected readonly fastest = computed(() => {
    const result = this.result();
    const player = this.api.player(result?.fastestPlayerId);
    return result && player ? { player, ms: result.fastestMs ?? 0, mine: player.id === this.api.meId() } : null;
  });
  protected readonly everyoneJumped = computed(() => {
    const entries = this.result()?.entries ?? [];
    return entries.length > 0 && entries.every((entry) => entry.falseStart);
  });

  /** One pip per signal of the round, coloured by my outcome once a signal is over. */
  protected readonly pips = computed(() => {
    const view = this.view();
    if (!view) {
      return [];
    }
    const me = this.api.meId();
    return Array.from({ length: view.signals }, (_, i) => {
      const result = view.results.find((r) => r.signal === i + 1);
      if (!result) {
        return i + 1 === view.signal ? 'current' : 'todo';
      }
      const entry = result.entries.find((e) => e.playerId === me);
      if (!entry) {
        return 'done';
      }
      return entry.ms != null ? 'good' : entry.falseStart ? 'bad' : 'miss';
    });
  });

  protected readonly chips = computed<StatusChip[]>(() => {
    const view = this.view();
    if (!view) {
      return [];
    }
    const me = this.api.meId();
    const result = this.result();
    const chips: StatusChip[] = [];
    for (const status of view.players) {
      const player = this.api.player(status.playerId);
      if (!player) {
        continue;
      }
      const chip: StatusChip = { player, me: player.id === me, mark: 'none', text: '' };
      const entry = result?.entries.find((e) => e.playerId === status.playerId);
      if (result && entry) {
        Object.assign(chip, this.resultMark(entry, result));
      } else if (status.falseStart) {
        Object.assign(chip, { mark: 'jumped', text: '✋' });
      } else if (status.tapped) {
        Object.assign(chip, { mark: 'tapped', text: '✓' });
      } else if (view.state === 'GO') {
        chip.text = '…';
      }
      chips.push(chip);
    }
    return chips;
  });

  protected readonly signalNumbers = computed(() => Array.from({ length: this.view()?.signals ?? 0 }, (_, i) => i + 1));

  /** The round table with per-signal cells, best first. */
  protected readonly tableRows = computed(() => {
    const view = this.view();
    if (!view?.table) {
      return [];
    }
    const me = this.api.meId();
    const bestMs = Math.min(...view.table.map((row) => row.bestMs ?? Number.POSITIVE_INFINITY));
    return view.table.flatMap((row) => {
      const player = this.api.player(row.playerId);
      if (!player) {
        return [];
      }
      const cells = this.signalNumbers().map((n): TableCell => {
        const entry = view.results.find((r) => r.signal === n)?.entries.find((e) => e.playerId === row.playerId);
        if (entry?.ms != null) {
          return { text: String(entry.ms), kind: 'timed', best: entry.ms === bestMs };
        }
        return entry?.falseStart ? { text: '✋', kind: 'jump', best: false } : { text: '–', kind: 'miss', best: false };
      });
      return [{ ...row, player, me: row.playerId === me, cells }];
    });
  });

  /** What a screen reader announces. */
  protected readonly announcement = computed(() => {
    const view = this.view();
    if (!view || view.revealed) {
      return '';
    }
    const outcome = this.outcome();
    if (outcome.kind === 'early') {
      return 'Too soon! False start.';
    }
    if (outcome.kind === 'tapped' && view.state !== 'RESULT') {
      return `${outcome.ms ?? ''} milliseconds.`;
    }
    switch (view.state) {
      case 'WAITING':
        return `Signal ${view.signal} of ${view.signals}. Wait for green.`;
      case 'FAKE':
        return 'Not yet!';
      case 'GO':
        return 'Go!';
      default: {
        const fastest = this.fastest();
        return fastest ? `${fastest.player.name} was fastest.` : 'Nobody made it.';
      }
    }
  });

  constructor() {
    // Note when GO is on screen; reaction times are measured from this frame (IMP-21).
    afterRenderEffect(() => {
      const id = this.goSignal();
      if (id !== null && this.goSeen?.signalId !== id) {
        this.goSeen = { signalId: id, at: performance.now() };
      }
    });
  }

  protected onPointerDown(event: PointerEvent): void {
    if (event.button !== 0) {
      return;
    }
    event.preventDefault();
    this.tap();
  }

  /** Space or Enter tap too (outside text fields and dialogs). */
  protected onKey(event: KeyboardEvent): void {
    if (event.code !== 'Space' && event.key !== 'Enter') {
      return;
    }
    if (!this.api.isPlaying() || this.api.phase() !== 'PLAYING' || this.revealed()) {
      return;
    }
    if (
      event.target instanceof Element &&
      event.target.closest('input, textarea, select, [contenteditable], .cdk-overlay-container')
    ) {
      return;
    }
    event.preventDefault();
    if (!event.repeat) {
      this.tap();
    }
  }

  protected ordinal(rank: number): string {
    const tens = rank % 100;
    if (tens >= 11 && tens <= 13) {
      return `${rank}th`;
    }
    return `${rank}${['th', 'st', 'nd', 'rd'][rank % 10] ?? 'th'}`;
  }

  protected medal(rank: number): string {
    return ['🥇', '🥈', '🥉'][rank - 1] ?? String(rank);
  }

  protected signed(points: number): string {
    return points > 0 ? `+${points}` : points < 0 ? `−${Math.abs(points)}` : '0';
  }

  private tap(): void {
    const view = this.view();
    if (!view || !this.canTap()) {
      return;
    }
    const signalId = view.signalId;
    const payload: TapPayload = { signal: signalId };
    let outcome: Outcome;
    if (view.state === 'GO' && this.goSeen?.signalId === signalId) {
      const ms = Math.max(0, Math.round(performance.now() - this.goSeen.at));
      payload.ms = ms;
      outcome = ms < ANTICIPATION_MS ? { kind: 'early', fake: false } : { kind: 'tapped', ms };
    } else {
      // Before GO reached this screen: a false start, whatever the server's state is by now.
      outcome = { kind: 'early', fake: view.state === 'FAKE' };
    }
    this.local.set({ signalId, outcome });
    void this.api.send('tap', payload, { quiet: true }).then((result) => {
      if (!result.ok && this.local()?.signalId === signalId) {
        this.local.set({ signalId, outcome: { kind: 'rejected', message: result.message } });
      }
    });
  }

  private resultMark(entry: ResultEntry, result: SignalResult): Pick<StatusChip, 'mark' | 'text'> {
    if (entry.ms != null) {
      const fastest = entry.playerId === result.fastestPlayerId;
      return { mark: fastest ? 'fastest' : 'timed', text: `${fastest ? '⚡ ' : ''}${entry.ms} ms` };
    }
    return entry.falseStart ? { mark: 'jumped', text: '✋ −25' } : { mark: 'missed', text: '–' };
  }
}
