import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { PlayerChip, injectGameApi } from '@ff/sdk';

import { directionOf, isTyping } from './arena-input';
import { ArenaPlayback } from './arena-playback';
import { ArenaRenderer } from './arena-renderer';
import { resolveTheme } from './arena-theme';
import { Person, crashCause, feedLines, leaderRows, resultLines, secondsUntil } from './panel-model';
import { SidePanel } from './side-panel.component';
import { Frame, SnakeArenaView } from './snake-arena.types';
import { Turn } from './steering';

/** The overlays on the arena, derived from the newest frame. */
interface Hud {
  /** Nothing moves yet / the first moments / the round runs. */
  stage: 'ready' | 'go' | 'play';
  /** This browser's snake: none (spectator), on the board, waiting to respawn, out for the round. */
  mine: 'none' | 'alive' | 'respawning' | 'out';
  respawnIn: number;
  cause: string | null;
  /** The walls are about to close in (shrinking arena). */
  walls: boolean;
}

const NO_HUD: Hud = { stage: 'play', mine: 'none', respawnIn: 0, cause: null, walls: false };
const VARIATIONS: Record<SnakeArenaView['variation'], string> = {
  classic: 'Classic',
  shrinking: 'Shrinking arena',
  'last-standing': 'Last snake standing',
};
const ARENAS: Record<SnakeArenaView['arena'], string> = { SMALL: 'Small', MEDIUM: 'Medium', LARGE: 'Large' };
/** How long "Go!" stays up after the start delay (ticks). */
const GO_TICKS = 8;
/** The "YOU" tag stays this long into a round (ticks) … */
const YOU_TAG_TICKS = 25;
/** … and this long after a respawn (ms). */
const YOU_TAG_RESPAWN_MS = 1600;
/** Room around the board for its frame and shadow (px). */
const BOARD_INSET = 12;

/**
 * Snake Arena: everyone steers a snake in one shared arena.
 *
 * The server streams a compact frame every tick. {@link ArenaPlayback} decides where each snake is
 * drawn at any moment (the others between frames, the own snake ahead along its pending turns) and
 * which tick a key press turns the snake at; a `requestAnimationFrame` loop draws that on a canvas
 * without touching Angular. Signals only carry the low-frequency UI around it: the leaderboard and
 * kill feed in the side panel, the "get ready", crash and wall overlays and the round's results.
 */
@Component({
  selector: 'ff-snake-arena',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip, SidePanel],
  templateUrl: './snake-arena.component.html',
  styleUrl: './snake-arena.component.scss',
  host: { '(document:keydown)': 'onKey($event)' },
})
export class SnakeArenaComponent {
  protected readonly api = injectGameApi<SnakeArenaView>();
  protected readonly view = this.api.view;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly stage = viewChild.required<ElementRef<HTMLElement>>('stage');
  private readonly motion = window.matchMedia('(prefers-reduced-motion: reduce)');

  private readonly playback = new ArenaPlayback();
  private renderer: ArenaRenderer | null = null;
  private animation = 0;
  private colors = new Map<string, string>();
  private names = new Map<string, string>();
  private youTagUntil = 0;

  /** The newest frame, for the UI around the canvas (changes every tick). */
  private readonly latest = signal<Frame | null>(null);
  private readonly stageSize = signal({ width: 0, height: 0 });

  protected readonly revealed = computed(() => !!this.view()?.results);
  protected readonly round = computed(() => this.view()?.frame.round ?? 1);
  protected readonly mode = computed(() => {
    const view = this.view();
    return view ? VARIATIONS[view.variation] : '';
  });
  protected readonly arenaLabel = computed(() => {
    const view = this.view();
    return view ? `${ARENAS[view.arena]} arena` : '';
  });
  protected readonly canSteer = computed(
    () => this.api.isPlaying() && this.api.phase() === 'PLAYING' && !this.revealed(),
  );

  /** Everyone of the round (also those who left since) plus live info of current players. */
  protected readonly people = computed(() => {
    const people = new Map<string, Person>();
    for (const entry of this.view()?.roster ?? []) {
      people.set(entry.id, { name: entry.name, avatar: '🐍', color: entry.color, online: false });
    }
    for (const player of this.api.players()) {
      people.set(player.id, { name: player.name, avatar: player.avatar, color: player.color, online: player.online });
    }
    return people;
  });

  protected readonly rows = computed(
    () => leaderRows(this.latest(), this.people(), this.api.meId(), this.view()?.tickMs ?? 100, this.revealed()),
    { equal: sameJson },
  );
  protected readonly feed = computed(() => feedLines(this.latest()?.feed ?? [], this.people()), {
    equal: sameJson,
  });
  protected readonly results = computed(() => resultLines(this.view()?.results ?? [], this.people(), this.api.meId()));
  protected readonly hud = computed(() => this.describe(this.latest()), { equal: sameJson });
  protected readonly ariaLabel = computed(() => {
    const rows = this.rows();
    const alive = rows.filter((row) => row.status === 'alive').length;
    return `Snake arena: ${alive} of ${rows.length} snakes on the board`;
  });

  /** The board: the largest box with the grid's aspect ratio that fits the stage. */
  protected readonly box = computed(
    () => {
      const view = this.view();
      const { width, height } = this.stageSize();
      if (!view || width <= BOARD_INSET || height <= BOARD_INSET) {
        return { width: 0, height: 0 };
      }
      const cell = Math.min((width - BOARD_INSET) / view.cols, (height - BOARD_INSET) / view.rows);
      return { width: Math.floor(cell * view.cols), height: Math.floor(cell * view.rows) };
    },
    { equal: (a, b) => a.width === b.width && a.height === b.height },
  );

  constructor() {
    this.api.stream$.pipe(takeUntilDestroyed()).subscribe((message) => {
      if (message.type === 'frame') {
        this.take(message.payload as Frame, false);
      }
    });
    effect(() => {
      const view = this.view();
      if (view) {
        untracked(() => this.take(view.frame, true));
      }
    });
    effect(() => {
      const roundTrip = this.api.roundTripMs();
      if (roundTrip != null) {
        untracked(() => this.playback.roundTrip(roundTrip));
      }
    });
    effect(() => {
      const people = this.people();
      untracked(() => {
        this.colors = new Map([...people].map(([id, person]) => [id, person.color]));
        this.names = new Map([...people].map(([id, person]) => [id, person.name]));
      });
    });
    effect(() => {
      const box = this.box();
      const cols = this.view()?.cols ?? 1;
      untracked(() => {
        // Resizing clears the canvas: paint again at once rather than show a blank picture.
        this.renderer?.resize(box.width, box.height, window.devicePixelRatio || 1, cols);
        this.paint(performance.now());
      });
    });
    afterNextRender(() => {
      this.renderer = new ArenaRenderer(this.canvas().nativeElement, resolveTheme(this.host.nativeElement));
      const box = this.box();
      this.renderer.resize(box.width, box.height, window.devicePixelRatio || 1, this.view()?.cols ?? 1);
      const observer = new ResizeObserver(([entry]) => {
        this.stageSize.set({ width: entry.contentRect.width, height: entry.contentRect.height });
      });
      observer.observe(this.stage().nativeElement);
      this.animation = requestAnimationFrame(this.draw);
      this.destroyRef.onDestroy(() => observer.disconnect());
    });
    this.destroyRef.onDestroy(() => cancelAnimationFrame(this.animation));
  }

  /**
   * Arrow keys / WASD steer; auto-repeat is ignored, text fields are left alone. The snake turns at
   * the next cell centre its drawn head reaches — and so does the server (see {@link ArenaPlayback}).
   */
  protected onKey(event: KeyboardEvent): void {
    if (!this.canSteer() || event.ctrlKey || event.metaKey || event.altKey || isTyping(event.target)) {
      return;
    }
    const dir = directionOf(event);
    if (!dir) {
      return;
    }
    event.preventDefault();
    const view = this.view();
    const me = this.api.meId();
    if (event.repeat || !view || !me) {
      return;
    }
    const turn = this.playback.press(dir, me, view, performance.now());
    if (turn) {
      this.send(turn);
    }
  }

  /** Sends a turn; its answer measures the round trip, which keeps the own snake's lead right. */
  private send(turn: Turn): void {
    const sent = performance.now();
    void this.api.send('turn', turn, { quiet: true }).then(() => this.playback.roundTrip(performance.now() - sent));
  }

  // ------------------------------------------------------------------ frames & drawing

  /** Takes a frame from the stream or a snapshot if it is the newest. */
  private take(frame: Frame, fromSnapshot: boolean): void {
    const view = this.view();
    const before = this.playback.current;
    const me = this.api.isPlaying() ? this.api.meId() : null;
    const now = performance.now();
    if (!view || before === frame || !this.playback.receive(frame, fromSnapshot, me, view.tickMs, now)) {
      return;
    }
    this.renderer?.frameArrived(before, frame, {
      cols: view.cols,
      colors: this.colors,
      me,
      reducedMotion: this.motion.matches,
      now,
    });
    const alive = (each: Frame | null) => !!each?.snakes.some((snake) => snake.id === me && snake.alive);
    if (alive(frame) && !(before?.round === frame.round && alive(before))) {
      this.youTagUntil = now + YOU_TAG_RESPAWN_MS;
    }
    this.latest.set(frame);
    const canvas = this.canvas().nativeElement;
    canvas.dataset['round'] = String(frame.round);
    canvas.dataset['tick'] = String(frame.tick);
    canvas.dataset['alive'] = String(frame.snakes.filter((snake) => snake.alive).length);
  }

  private readonly draw = (): void => {
    this.animation = requestAnimationFrame(this.draw);
    // The clock frames arrive on (not the animation frame's timestamp, which can be a little older
    // than an arrival in the same frame): nothing drawn is ever younger than zero.
    this.paint(performance.now());
  };

  /** Draws the arena as of `now` (`performance.now()`). */
  private paint(now: number): void {
    const view = this.view();
    const current = this.playback.current;
    const renderer = this.renderer;
    if (!view || !current || !renderer) {
      return;
    }
    const dpr = window.devicePixelRatio || 1;
    if (dpr !== renderer.pixelRatio) {
      const box = this.box();
      renderer.resize(box.width, box.height, dpr, view.cols);
    }
    const me = this.api.isPlaying() ? this.api.meId() : null;
    renderer.render({
      cols: view.cols,
      rows: view.rows,
      current,
      poses: this.playback.poses(view, me, this.revealed(), now),
      now,
      me,
      colors: this.colors,
      names: this.names,
      youTag: current.tick < view.startDelay + YOU_TAG_TICKS || now < this.youTagUntil,
      shrink: view.shrink ?? null,
      reducedMotion: this.motion.matches,
    });
  }

  private describe(frame: Frame | null): Hud {
    const view = this.view();
    if (!frame || !view) {
      return NO_HUD;
    }
    const tick = frame.tick;
    const stage = tick < view.startDelay ? 'ready' : tick < view.startDelay + GO_TICKS ? 'go' : 'play';
    const me = this.api.isPlaying() ? this.api.meId() : null;
    const snake = me ? frame.snakes.find((each) => each.id === me) : undefined;
    const shrink = view.shrink;
    const walls = !!shrink && tick >= shrink.from - 30 && tick < shrink.from + 20;
    if (!snake || !me) {
      return { ...NO_HUD, stage, walls };
    }
    if (snake.alive) {
      return { stage, mine: 'alive', respawnIn: 0, cause: null, walls };
    }
    return {
      stage,
      mine: snake.respawnAt != null ? 'respawning' : 'out',
      respawnIn: snake.respawnAt != null ? secondsUntil(snake.respawnAt, tick, view.tickMs) : 0,
      cause: crashCause(frame.feed, me, this.people()),
      walls,
    };
  }
}

function sameJson(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
