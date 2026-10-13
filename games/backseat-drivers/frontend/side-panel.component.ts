import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { ChipPlayer, CountUp, PlayerChip, injectGameApi } from '@ff/sdk';

import { BackseatDriversView, Control, LevelResult, LevelView } from './backseat-drivers.types';
import { BusFeed } from './bus-feed';
import { CONTROL_INFO, pressedIn } from './controls';

/** One seat on the roster. */
interface RosterSeat {
  id: string;
  player: ChipPlayer;
  me: boolean;
  tags: { control: Control | 'WAITING'; label: string; on: boolean }[];
}

const VERDICTS: Record<LevelResult['status'], { emoji: string; title: string }> = {
  PARKED: { emoji: '🅿️', title: 'Parked!' },
  CRASHED: { emoji: '💥', title: "The CEO's car!" },
  TIME_UP: { emoji: '⏱️', title: 'Out of time' },
};

const VARIATIONS: Record<string, string> = {
  'rush-hour': '🚦 Rush hour: faster bus, weaker brakes',
  'silent-navigator': '🤫 Silent navigator',
};

/**
 * The side of the level screen: level name, coffees and cones, the score the team would get by
 * parking now, who holds what (lighting up while pressed), and — once the level is over — the
 * score breakdown.
 */
@Component({
  selector: 'ff-bd-side-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip, CountUp],
  templateUrl: './side-panel.component.html',
  styleUrl: './side-panel.component.scss',
})
export class SidePanel {
  readonly view = input.required<BackseatDriversView>();
  readonly level = input.required<LevelView>();

  private readonly api = injectGameApi<BackseatDriversView>();
  private readonly feed = inject(BusFeed);

  protected readonly totalLevels = computed(() => this.api.progress()?.totalRounds ?? this.level().number);
  protected readonly variation = computed(() => VARIATIONS[this.view().variation] ?? null);
  protected readonly verdict = computed(() => {
    const result = this.level().result;
    return result ? VERDICTS[result.status] : null;
  });

  protected readonly counts = computed(() => {
    const result = this.level().result;
    return result ? { cones: result.cones, coffees: result.coffees } : this.feed.counts();
  });

  /** Seconds left × 10 + coffees × 50 − cones × 25, never below 0 (re-evaluated 4 times a second). */
  protected readonly preview = computed(() => {
    const seconds = Math.ceil((this.api.timeLeftMs() ?? 0) / 1000);
    const { cones, coffees } = this.counts();
    return Math.max(0, seconds * 10 + coffees * 50 - cones * 25);
  });

  protected readonly roster = computed<RosterSeat[]>(() => {
    const level = this.level();
    const pressed = this.feed.pressed();
    const masks = pressed.r === level.rolesVersion && level.status === 'DRIVING' ? pressed.p : [];
    const me = this.api.meId();
    const seats: RosterSeat[] = level.seats.map((seat, i) => ({
      id: seat.playerId,
      player: this.chip(seat.playerId),
      me: seat.playerId === me,
      tags: seat.controls.map((control) => ({
        control,
        label: control === 'NAVIGATOR' ? '🧭 Navigator' : CONTROL_INFO[control].short,
        on: pressedIn(masks[i] ?? 0, control),
      })),
    }));
    const seated = new Set(level.seats.map((seat) => seat.playerId));
    for (const player of this.api.players()) {
      if (!seated.has(player.id)) {
        seats.push({
          id: player.id,
          player,
          me: player.id === me,
          tags: [{ control: 'WAITING', label: player.online ? 'Next shuffle' : 'Offline', on: false }],
        });
      }
    }
    return seats;
  });

  /** Levels finished before this one. */
  protected readonly history = computed(() => this.view().levels.filter((done) => done.number !== this.level().number));

  protected statusEmoji(status: LevelResult['status']): string {
    return VERDICTS[status].emoji;
  }

  private chip(id: string): ChipPlayer {
    return this.api.player(id) ?? { name: '?', avatar: '🙂', color: 'var(--mat-sys-outline)' };
  }
}
