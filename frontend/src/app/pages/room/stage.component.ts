import { NgComponentOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  Type,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { RoomStore } from '../../core/room.store';
import { GameUiService } from '../../games/game-ui.service';
import { Scoreboard } from './scoreboard.component';
import { timerUrgency } from './turn-timer.component';

/**
 * The game stage: the running game's own component (loaded lazily from `games/<id>/frontend`),
 * the reveal banner between turns, the scoreboard, and the time left as a bar along the top edge
 * of the game (amber in the last 10 seconds; red, with a pulsing edge glow, in the last 5).
 */
@Component({
  selector: 'ff-stage',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgComponentOutlet, MatProgressSpinnerModule, Scoreboard],
  template: `
    <div class="stage">
      <section class="main">
        @if (store.phase() === 'REVEAL') {
          <div
            class="reveal"
            role="status"
            animate.enter="ff-enter-pop"
            animate.leave="ff-leave-fade"
            data-testid="reveal"
          >
            <span class="headline">{{ store.lastTurn()?.headline || "Time's up!" }}</span>
            <span class="next">{{ nextLabel() }}</span>
          </div>
        }
        @if (!store.isPlaying()) {
          <div class="watching">👀 You're watching this game — you'll play in the next one.</div>
        }
        <div class="frame">
          <div class="game" [attr.data-game]="store.game()?.id">
            @if (component(); as game) {
              <ng-container *ngComponentOutlet="game; injector: gameInjector" />
            } @else if (component() === null) {
              <p class="missing">This game has no screen in this build of the app.</p>
            } @else {
              <mat-spinner diameter="40" />
            }
          </div>
          <!-- Time left, where the players are looking: drains along the top edge of the game. -->
          @if (playing()) {
            <div class="overlay" aria-hidden="true" animate.leave="ff-leave-fade">
              <div class="turn-bar" [class.urgent]="urgent()" [class.critical]="critical()" data-testid="turn-bar">
                <div class="fill" [style.transform]="'scaleX(' + fraction() + ')'"></div>
              </div>
              @if (critical()) {
                <div class="glow" animate.enter="ff-enter-fade" animate.leave="ff-leave-fade"></div>
              }
            </div>
          }
        </div>
      </section>
      <ff-scoreboard class="ff-card scoreboard" />
    </div>
  `,
  styles: `
    :host {
      flex: 1;
      min-height: 0;
      display: flex;
    }
    .stage {
      flex: 1;
      min-height: 0;
      display: grid;
      grid-template-columns: minmax(0, 1fr) 280px;
      gap: 16px;
      padding: 16px 20px 20px;
    }
    .main {
      position: relative;
      min-height: 0;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .frame {
      position: relative;
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    .game {
      position: relative;
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
      align-items: stretch;
      justify-content: center;
      overflow: hidden;
      border-radius: var(--ff-radius-lg);
      border: 1px solid var(--ff-border);
      background: var(--ff-surface);
    }
    .game > * {
      flex: 1;
      min-height: 0;
    }
    .game mat-spinner {
      align-self: center;
      flex: none;
    }
    /* Drawn on top of the game card and clipped to its rounded shape; never takes input or space. */
    .overlay {
      position: absolute;
      inset: 0;
      z-index: 2;
      overflow: hidden;
      border-radius: var(--ff-radius-lg);
      pointer-events: none;
    }
    .turn-bar {
      --bar-color: var(--mat-sys-primary);
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 6px;
      background: color-mix(in srgb, var(--bar-color) 18%, transparent);
      transition: height 300ms var(--ff-ease-out);
    }
    .turn-bar .fill {
      height: 100%;
      transform-origin: left center;
      background: linear-gradient(90deg, var(--bar-color), color-mix(in srgb, var(--bar-color) 55%, white));
      box-shadow: 0 0 14px color-mix(in srgb, var(--bar-color) 75%, transparent);
      transition: transform 250ms linear;
    }
    .turn-bar.urgent {
      --bar-color: var(--ff-warning);
      height: 8px;
    }
    .turn-bar.critical {
      --bar-color: var(--ff-danger);
      height: 10px;
    }
    .glow {
      position: absolute;
      inset: 0;
      border-radius: inherit;
      box-shadow:
        inset 0 0 0 3px color-mix(in srgb, var(--ff-danger) 85%, transparent),
        inset 0 0 64px color-mix(in srgb, var(--ff-danger) 32%, transparent);
      animation: ff-stage-alarm 1000ms ease-in-out infinite;
    }
    @keyframes ff-stage-alarm {
      0%,
      100% {
        opacity: 1;
      }
      50% {
        opacity: 0.45;
      }
    }
    .reveal {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 14px 20px;
      border-radius: var(--ff-radius);
      background: linear-gradient(
        90deg,
        color-mix(in srgb, var(--mat-sys-primary) 30%, transparent),
        color-mix(in srgb, var(--mat-sys-tertiary) 22%, transparent)
      );
      border: 1px solid color-mix(in srgb, var(--mat-sys-primary) 50%, transparent);
    }
    .headline {
      flex: 1;
      font-size: 22px;
      font-weight: 800;
    }
    .next {
      color: var(--ff-text-muted);
      font-weight: 600;
      white-space: nowrap;
    }
    .watching {
      padding: 8px 14px;
      border-radius: var(--ff-radius-sm);
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
      font-size: 14px;
      font-weight: 600;
    }
    .missing {
      text-align: center;
      color: var(--ff-text-muted);
    }
    .scoreboard {
      min-height: 0;
      padding: 16px;
    }
  `,
})
export class Stage {
  protected readonly store = inject(RoomStore);
  private readonly games = inject(GameUiService);

  /** `undefined` while loading, `null` when the game has no UI in this build. */
  protected readonly component = signal<Type<unknown> | null | undefined>(undefined);
  protected readonly gameInjector = this.games.injectorFor(inject(Injector));

  protected readonly nextLabel = computed(() => {
    const seconds = Math.ceil((this.store.timeLeftMs() ?? 0) / 1000);
    const progress = this.store.progress();
    const last = !!progress && progress.round >= progress.totalRounds && progress.turn >= progress.turnsInRound;
    return `${last ? 'Final results' : 'Next turn'} in ${seconds}…`;
  });

  /** The turn timer on the stage only runs while a turn is played (not during the reveal). */
  protected readonly playing = computed(() => this.store.phase() === 'PLAYING' && this.store.timeLeftMs() != null);
  private readonly remaining = computed(() => this.store.timeLeftMs() ?? 0);
  protected readonly fraction = computed(() => {
    const total = this.store.phaseDurationMs();
    return total ? Math.min(1, Math.max(0, this.remaining() / total)) : 1;
  });
  private readonly urgency = computed(() =>
    this.playing() ? timerUrgency(this.remaining(), this.store.phaseDurationMs()) : 'calm',
  );
  protected readonly urgent = computed(() => this.urgency() === 'urgent');
  protected readonly critical = computed(() => this.urgency() === 'critical');

  /** Only a different game loads another component (snapshots arrive many times a second). */
  private readonly gameId = computed(() => this.store.game()?.id ?? null);

  constructor() {
    effect(() => {
      const gameId = this.gameId();
      if (gameId) {
        untracked(() => {
          this.component.set(undefined);
          void this.games.load(gameId).then((component) => this.component.set(component));
        });
      }
    });
  }
}
