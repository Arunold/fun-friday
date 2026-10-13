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
import { ArenaRenderer } from './arena-renderer';
import { resolveTheme } from './arena-theme';
import { FrameClock } from './frame-clock';
import { Point, Pose, pointAt, poseAt, shiftHead } from './motion';
import { Person, crashCause, feedLines, leaderRows, resultLines, secondsUntil } from './panel-model';
import { SidePanel } from './side-panel.component';
import { Dir, Frame, SnakeArenaView } from './snake-arena.types';
import { Steering } from './steering';

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
 * How far ahead of "one tick behind the newest frame" the own snake must be drawn for a turn to
 * reach the server before the snake moves on (ticks): half a tick plus the network round trip,
 * within these bounds.
 */
const MIN_SEND_LEAD = 0.8;
const MAX_SEND_LEAD = 1.3;
/**
 * How long a picture takes to reach the player's eyes and a key press to reach the page (ms) on a
 * usual screen. The own snake is drawn this much further ahead, and a key press counts for the
 * moment the player saw — so the snake turns where its head was on the player's screen, not where
 * the page has drawn it by the time the key press arrives.
 */
const SEEN_LATENCY_MS = 40;
/** Other snakes are guessed straight on, half a tick ahead: small corrections when they turn. */
const OTHERS_LEAD = 0.5;
/** Round trip assumed until the platform has measured it (ms). */
const DEFAULT_ROUND_TRIP_MS = 30;
/** Extra ticks a snake may run on when a frame is late before it waits for the frame. */
const LATE_SLACK = 0.35;
/** A drawn head that jumps (a correction) glides to its new place with this time constant (ms). */
const SMOOTHING_MS = 45;

/** Where a snake's head was drawn last and the jump still being eased out (cells). */
interface Shown {
  round: number;
  time: number;
  head: Point;
  offset: Point;
}

/**
 * Snake Arena: everyone steers a snake in one shared arena.
 *
 * The server streams a compact frame every tick; this component keeps the two newest frames
 * (stream or snapshot, whichever is newer) in plain fields and a `requestAnimationFrame` loop
 * draws them on a canvas against a smoothed clock of the server's ticks — gliding between frames
 * and a little past the newest one, the own snake along its pending turns (see {@link
 * SEEN_LATENCY_MS}); jumps are eased out. Signals only carry the low-frequency UI around it: the
 * leaderboard and kill feed in the side panel, the "get ready", crash and wall overlays and the
 * round's results. Arrow keys / WASD send `turn` actions (key presses only) naming the tick of the
 * cell the player saw the head in.
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

  private readonly clock = new FrameClock();
  private readonly steering = new Steering();
  private renderer: ArenaRenderer | null = null;
  private animation = 0;
  private colors = new Map<string, string>();
  private names = new Map<string, string>();
  private food: ReadonlySet<number> = new Set();
  private youTagUntil = 0;
  private shown = new Map<string, Shown>();

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
  /** How far ahead the own snake is drawn (ticks): what a turn needs plus what the player hasn't seen yet. */
  private readonly myLead = computed(() => {
    const tickMs = this.view()?.tickMs ?? 100;
    const roundTrip = this.api.roundTripMs() ?? DEFAULT_ROUND_TRIP_MS;
    const send = Math.min(MAX_SEND_LEAD, Math.max(MIN_SEND_LEAD, 0.5 + (roundTrip * 1.25 + 10) / tickMs));
    return send + SEEN_LATENCY_MS / tickMs;
  });

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
        untracked(() => {
          this.clock.tickMs = view.tickMs;
          this.take(view.frame, true);
        });
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
   * Arrow keys / WASD steer; auto-repeat is ignored, text fields are left alone. The turn names the
   * tick of the cell the head was in on the player's screen when they pressed the key (the page has
   * drawn it {@link SEEN_LATENCY_MS} further since), so the snake turns exactly there.
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
    const current = this.clock.current;
    const mine = current?.snakes.find((snake) => snake.id === this.api.meId());
    if (event.repeat || !view || !current || !mine?.alive) {
      return;
    }
    const now = performance.now();
    const pressed = event.timeStamp > now - 1000 && event.timeStamp <= now ? event.timeStamp : now;
    const seen = this.drawTick(current, this.myLead(), pressed) - SEEN_LATENCY_MS / view.tickMs;
    const turn = this.steering.press(dir, mine, Math.max(view.startDelay, current.tick - 1, seen));
    if (turn) {
      this.api.sendFast('turn', turn);
    }
  }

  // ------------------------------------------------------------------ frames & drawing

  /** Takes a frame from the stream or a snapshot if it is the newest. */
  private take(frame: Frame, fromSnapshot: boolean): void {
    const before = this.clock.current;
    const now = performance.now();
    if (before === frame || !this.clock.offer(frame, fromSnapshot, now)) {
      return;
    }
    const view = this.view();
    const me = this.api.isPlaying() ? this.api.meId() : null;
    if (view) {
      this.renderer?.frameArrived(before, frame, {
        cols: view.cols,
        colors: this.colors,
        me,
        reducedMotion: this.motion.matches,
        now,
      });
    }
    const mine = frame.snakes.find((snake) => snake.id === me);
    this.steering.update(frame, mine);
    this.food = new Set(frame.food);
    const wasAlive = before?.round === frame.round && before.snakes.some((snake) => snake.id === me && snake.alive);
    if (mine?.alive && !wasAlive) {
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
    const current = this.clock.current;
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
      poses: this.poses(view, current, me, now),
      now,
      me,
      colors: this.colors,
      names: this.names,
      youTag: current.tick < view.startDelay + YOU_TAG_TICKS || now < this.youTagUntil,
      shrink: view.shrink ?? null,
      reducedMotion: this.motion.matches,
    });
  }

  /** The moment (in ticks) to draw a snake with this lead, never further than the slack allows. */
  private drawTick(current: Frame, lead: number, now: number): number {
    return Math.min(this.clock.position(now) - 1 + lead, current.tick + lead + LATE_SLACK);
  }

  /** Where every living snake is drawn now: mine along its pending turns, the others straight on. */
  private poses(view: SnakeArenaView, current: Frame, me: string | null, now: number): Map<string, Pose> {
    const frozen = this.revealed();
    const previous = frozen ? null : this.clock.previous;
    const moving = !frozen && current.tick >= view.startDelay;
    const poses = new Map<string, Pose>();
    for (const snake of current.snakes) {
      if (!snake.alive || !snake.body.length) {
        this.shown.delete(snake.id);
        continue;
      }
      const mine = snake.id === me;
      const lead = mine ? this.myLead() : OTHERS_LEAD;
      const perTick = snake.power === 'BOOST' ? 2 : 1;
      const cells = moving ? Math.ceil((lead + LATE_SLACK) * perTick) : 0;
      const before = previous?.snakes.find((each) => each.id === snake.id);
      const pose = poseAt({
        cols: view.cols,
        previous: before?.alive ? before : undefined,
        previousTick: previous?.tick ?? current.tick,
        current: snake,
        currentTick: current.tick,
        at: frozen ? current.tick : this.drawTick(current, lead, now),
        ahead: mine
          ? this.steering.ahead(current.tick, snake.dir, cells, perTick)
          : new Array<Dir>(cells).fill(snake.dir),
        speed: perTick,
        food: this.food,
        bounds: current.bounds,
        look: mine && !frozen ? this.steering.pending() : undefined,
      });
      poses.set(snake.id, this.smooth(snake.id, pose, current.round, perTick / view.tickMs, now));
    }
    return poses;
  }

  /**
   * Eases out a jump of a drawn head — a turn that counts for a moment the page had already drawn
   * past, a guess the next frame corrects — so the head glides to its new place within a few
   * frames instead of snapping there. Respawns and new rounds start fresh.
   */
  private smooth(id: string, pose: Pose, round: number, cellsPerMs: number, now: number): Pose {
    const head = pointAt(pose.points, pose.head);
    const last = this.shown.get(id);
    let offset = { x: 0, y: 0 };
    if (last && last.round === round && now - last.time < 250) {
      const dt = now - last.time;
      const jump = Math.hypot(head.x - last.head.x, head.y - last.head.y);
      offset = last.offset;
      if (jump > cellsPerMs * dt + 0.3 && jump < 4) {
        offset = { x: offset.x + last.head.x - head.x, y: offset.y + last.head.y - head.y };
      }
      const keep = Math.exp(-dt / SMOOTHING_MS);
      offset = { x: offset.x * keep, y: offset.y * keep };
    }
    this.shown.set(id, { round, time: now, head, offset });
    return Math.hypot(offset.x, offset.y) < 0.02 ? pose : shiftHead(pose, offset.x, offset.y);
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
