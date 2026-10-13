import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';

import { injectGameApi } from '@ff/sdk';

import { BackseatDriversView, Control, Effect, LevelView } from './backseat-drivers.types';
import { BusFeed } from './bus-feed';
import { fontOf, prefersReducedMotion, runCanvas } from './canvas-loop';
import { CONTROL_INFO, pressedIn } from './controls';
import { RoadRenderer } from './road-renderer';

/** A hazard the drivers need to react to, and who can. */
interface Hint {
  icon: string;
  title: string;
  detail: string;
  /** The viewer holds the control that helps. */
  mine: boolean;
}

/** Speed in the gauge: tiles per second × 8 ≈ km/h of a small shuttle. */
const KMH_PER_TILE = 8;

/**
 * The drivers' (and spectators') view: the fogged road around the bus on a canvas, a speed gauge,
 * and chips that say which hazard needs which control right now.
 */
@Component({
  selector: 'ff-bd-road-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <canvas #canvas></canvas>
    <div class="gauge" aria-hidden="true">
      <b #speed>0</b>
      <small>km/h</small>
    </div>
    <div class="compass" aria-hidden="true">N</div>
    @if (hints().length) {
      <ul class="hints" aria-live="polite">
        @for (hint of hints(); track hint.icon) {
          <li class="hint" [class.mine]="hint.mine" animate.enter="ff-enter-pop">
            <span class="icon" aria-hidden="true">{{ hint.icon }}</span>
            <b>{{ hint.title }}</b>
            <span>{{ hint.detail }}</span>
          </li>
        }
      </ul>
    }
  `,
  styleUrl: './road-view.component.scss',
  host: {
    'data-testid': 'road-view',
    role: 'img',
    'aria-label': 'The road around the bus. Only the navigator sees the whole map.',
  },
})
export class RoadView {
  readonly level = input.required<LevelView>();

  private readonly api = injectGameApi<BackseatDriversView>();
  private readonly feed = inject(BusFeed);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly speed = viewChild.required<ElementRef<HTMLElement>>('speed');

  protected readonly hints = computed<Hint[]>(() => {
    const alerts = this.feed.alerts();
    const level = this.level();
    const hints: Hint[] = [];
    const add = (bit: number, icon: string, title: string, control: Control) => {
      if (!(alerts & bit)) {
        return;
      }
      const holders = level.seats.filter((seat) => seat.controls.includes(control)).map((seat) => seat.playerId);
      const mine = !!level.myControls?.includes(control);
      const names = holders.map((id) => (id === this.api.meId() ? 'you' : (this.api.player(id)?.name ?? '?')));
      const key = CONTROL_INFO[control].keys[0];
      hints.push({
        icon,
        title,
        detail: mine ? `That's you — tap ${key}!` : `${CONTROL_INFO[control].label}: ${names.join(' & ') || 'nobody'}`,
        mine,
      });
    };
    add(Effect.BLUR, '🌧️', 'Rain!', 'WIPERS');
    add(Effect.DARK, '🚇', 'Pitch black!', 'HEADLIGHTS');
    add(Effect.GEESE, '🦢', 'Geese!', 'HORN');
    if (alerts & Effect.OFFROAD) {
      hints.push({ icon: '🌱', title: 'Off the road', detail: 'slow going — back onto the tarmac!', mine: false });
    }
    if (this.feed.stopped() && this.steeringNow()) {
      hints.push({ icon: '🛑', title: 'Standing still!', detail: 'steering needs speed — shout for gas!', mine: true });
    }
    return hints;
  });

  /** This player is pressing a steering control right now (as the server's frames say). */
  private readonly steeringNow = computed(() => {
    const level = this.level();
    const pressed = this.feed.pressed();
    const seat = level.seats.findIndex((each) => each.playerId === this.api.meId());
    const mask = seat >= 0 && pressed.r === level.rolesVersion ? (pressed.p[seat] ?? 0) : 0;
    return pressedIn(mask, 'STEER_LEFT') || pressedIn(mask, 'STEER_RIGHT');
  });

  private shownSpeed = '';

  constructor() {
    afterNextRender(() => {
      const canvas = this.canvas().nativeElement;
      const renderer = new RoadRenderer(canvas, fontOf(this.host.nativeElement));
      const reducedMotion = prefersReducedMotion();
      runCanvas(canvas, this.host.nativeElement, this.destroyRef, ({ now }) => {
        const pose = this.feed.pose(now);
        if (!pose) {
          renderer.clear();
          return;
        }
        const frame = this.feed.latest;
        renderer.render({
          now,
          pose,
          frame,
          area: this.feed.window,
          brake: !!frame?.p.some((mask) => pressedIn(mask, 'BRAKE')),
          reducedMotion,
        });
        this.showSpeed(pose.speed);
      });
    });
  }

  /** Writes the gauge directly (60 times a second is no job for change detection). */
  private showSpeed(speed: number): void {
    const text = `${speed < -0.05 ? 'R ' : ''}${Math.round(Math.abs(speed) * KMH_PER_TILE)}`;
    if (text !== this.shownSpeed) {
      this.shownSpeed = text;
      this.speed().nativeElement.textContent = text;
    }
  }
}
