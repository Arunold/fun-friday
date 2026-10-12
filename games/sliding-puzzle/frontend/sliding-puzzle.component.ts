import { ChangeDetectionStrategy, Component, computed, linkedSignal, signal } from '@angular/core';

import { GamePlayer, PlayerChip, injectGameApi } from '@ff/sdk';

import {
  Cells,
  Direction,
  GAP,
  LocalBoard,
  adopt,
  applyMoves,
  formatSolveTime,
  isSolved,
  ordinal,
  pushPath,
  reconcile,
  tileForDirection,
  tilesInPlace,
} from './puzzle';
import { PlayerStatus, SlidingPuzzleView } from './sliding-puzzle.types';

/** Physical keys (so WASD also works on AZERTY or QWERTZ keyboards). */
const KEY_DIRECTIONS: Partial<Record<string, Direction>> = {
  ArrowLeft: 'left',
  ArrowRight: 'right',
  ArrowUp: 'up',
  ArrowDown: 'down',
  KeyA: 'left',
  KeyD: 'right',
  KeyW: 'up',
  KeyS: 'down',
};

const RING_RADIUS = 42;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

/** One tile as rendered: where it is, where it belongs and how it looks. */
interface TileView {
  value: number;
  x: number;
  y: number;
  /** 0 (top-left home) … 1 (bottom-right home): position on the colour gradient. */
  hue: number;
  /** Diagonal of the home cell, staggers the solve wave. */
  wave: number;
  home: boolean;
  movable: boolean;
}

/** A player's progress with their profile and (during the reveal) this round's points. */
interface Entry {
  status: PlayerStatus;
  player: GamePlayer;
  me: boolean;
  points: number | null;
}

/**
 * Sliding Puzzle: everyone races to solve the same scramble on their own board. Moves are applied
 * locally right away and animated, then confirmed by the server (see `reconcile`). Opponents only
 * ever show up as progress; spectators get a leader board next to the starting scramble.
 */
@Component({
  selector: 'ff-sliding-puzzle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip],
  templateUrl: './sliding-puzzle.component.html',
  styleUrl: './sliding-puzzle.component.scss',
  host: { '(document:keydown)': 'onKey($event)' },
})
export class SlidingPuzzleComponent {
  protected readonly api = injectGameApi<SlidingPuzzleView>();
  protected readonly view = this.api.view;

  protected readonly ringLength = RING_LENGTH;
  protected readonly ringRadius = RING_RADIUS;
  protected readonly formatTime = formatSolveTime;
  protected readonly ordinal = ordinal;

  /** Moves sent but not answered yet, and whether one of them was refused. */
  private inFlight = 0;
  private refused = false;

  /** My board: the server's plus my optimistic moves (null for spectators). */
  protected readonly local = linkedSignal<SlidingPuzzleView | null, LocalBoard | null>({
    source: this.view,
    computation: (view, previous) =>
      view?.board && view.moves != null
        ? reconcile(previous?.value ?? null, view.round, view.size, view.board, view.moves, view.revealed)
        : null,
  });

  /** The tile under the mouse: it and the tiles it would push light up. */
  protected readonly hovered = signal<number | null>(null);

  protected readonly size = computed(() => this.view()?.size ?? 4);
  protected readonly tileCount = computed(() => this.size() * this.size() - 1);
  protected readonly slots = computed(() => Array.from({ length: this.size() * this.size() }, (_, i) => i));
  private readonly cells = computed(() => this.local()?.cells ?? null);

  protected readonly tiles = computed(() => toTiles(this.cells(), this.size(), true));
  protected readonly startTiles = computed(() => toTiles(this.view()?.start ?? null, this.size(), false));

  protected readonly myMoves = computed(() => {
    const local = this.local();
    return local ? local.baseMoves + local.pending.length : 0;
  });
  protected readonly myInPlace = computed(() => tilesInPlace(this.cells() ?? []));
  protected readonly myProgress = computed(() => Math.round((100 * this.myInPlace()) / this.tileCount()));
  protected readonly solved = computed(() => isSolved(this.cells() ?? []));

  protected readonly canMove = computed(() => {
    const view = this.view();
    return (
      this.api.isPlaying() &&
      this.api.phase() === 'PLAYING' &&
      !!view &&
      !view.revealed &&
      !view.finished &&
      !!this.cells() &&
      !this.solved()
    );
  });

  /** Tiles that move when the hovered tile is clicked. */
  protected readonly lifted = computed(() => {
    const tile = this.hovered();
    const cells = this.cells();
    if (tile == null || !cells || !this.canMove()) {
      return new Set<number>();
    }
    return new Set(pushPath(cells, this.size(), tile) ?? []);
  });

  protected readonly entries = computed<Entry[]>(() => {
    const me = this.api.meId();
    const byId = new Map(this.api.players().map((p) => [p.id, p]));
    const points = this.api.revealing() ? (this.api.lastTurn()?.points ?? {}) : null;
    return (this.view()?.players ?? []).flatMap((status) => {
      const player = byId.get(status.playerId);
      return player
        ? [{ status, player, me: status.playerId === me, points: points ? (points[status.playerId] ?? 0) : null }]
        : [];
    });
  });
  protected readonly opponents = computed(() => this.entries().filter((entry) => !entry.me));
  /** Solvers by finishing order, then everyone else by progress. */
  protected readonly ranking = computed(() => [...this.entries()].sort(byStanding));
  protected readonly othersSolving = computed(() => this.opponents().some((entry) => !entry.status.finished));

  /** Stroke offset of a progress ring at `percent`. */
  protected ringOffset(percent: number): number {
    return RING_LENGTH * (1 - Math.min(100, Math.max(0, percent)) / 100);
  }

  protected press(event: PointerEvent, tile: number): void {
    if (event.button !== 0) {
      return;
    }
    event.preventDefault();
    const face = (event.currentTarget as HTMLElement).firstElementChild;
    this.push(tile, face instanceof HTMLElement ? face : null);
  }

  protected onKey(event: KeyboardEvent): void {
    const direction = KEY_DIRECTIONS[event.code];
    if (
      !direction ||
      event.defaultPrevented ||
      event.altKey ||
      event.ctrlKey ||
      event.metaKey ||
      isTyping(event.target)
    ) {
      return;
    }
    const cells = this.cells();
    if (!cells || !this.canMove()) {
      return;
    }
    event.preventDefault();
    const tile = tileForDirection(cells, this.size(), direction);
    if (tile != null) {
      this.push(tile, null);
    }
  }

  /** Pushes a tile toward the gap: animate now, let the server confirm. */
  private push(tile: number, face: HTMLElement | null): void {
    const local = this.local();
    if (!local || !this.canMove()) {
      return;
    }
    const path = pushPath(local.cells, this.size(), tile);
    const cells = path && applyMoves(local.cells, this.size(), path);
    if (!path || !cells) {
      nudge(face);
      return;
    }
    this.local.set({ ...local, pending: [...local.pending, ...path], cells });
    void this.send(tile);
  }

  private async send(tile: number): Promise<void> {
    this.inFlight++;
    const result = await this.api.send('move', { tile }, { quiet: true });
    this.inFlight--;
    this.refused ||= !result.ok;
    if (this.refused && this.inFlight === 0) {
      // Something diverged (or the round just ended): the server's board wins.
      this.refused = false;
      const view = this.view();
      this.local.set(view?.board && view.moves != null ? adopt(view.round, view.board, view.moves) : null);
    }
  }
}

function toTiles(cells: Cells | null, size: number, interactive: boolean): TileView[] {
  if (!cells) {
    return [];
  }
  const gap = cells.indexOf(GAP);
  const tiles: TileView[] = [];
  cells.forEach((value, index) => {
    if (value === GAP) {
      return;
    }
    const x = index % size;
    const y = Math.floor(index / size);
    const homeX = (value - 1) % size;
    const homeY = Math.floor((value - 1) / size);
    tiles.push({
      value,
      x,
      y,
      hue: (homeX + homeY) / (2 * (size - 1)),
      wave: homeX + homeY,
      home: value === index + 1,
      movable: interactive && (x === gap % size || y === Math.floor(gap / size)),
    });
  });
  // A fixed DOM order: tiles only ever move through their transform, so slides animate.
  return tiles.sort((a, b) => a.value - b.value);
}

function byStanding(a: Entry, b: Entry): number {
  if (a.status.finished !== b.status.finished) {
    return a.status.finished ? -1 : 1;
  }
  if (a.status.finished) {
    return (a.status.rank ?? 0) - (b.status.rank ?? 0);
  }
  return b.status.progress - a.status.progress || a.status.moves - b.status.moves;
}

function isTyping(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))
  );
}

/** A small "no" wiggle for a tile that can't move. */
function nudge(face: HTMLElement | null): void {
  if (!face || globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    return;
  }
  face.animate(
    [
      { translate: '0 0' },
      { translate: '-5px 0' },
      { translate: '4px 0' },
      { translate: '-2px 0' },
      { translate: '0 0' },
    ],
    {
      duration: 260,
      easing: 'ease-in-out',
    },
  );
}
