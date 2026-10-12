import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** The minimum a chip needs to render someone. */
export interface ChipPlayer {
  name: string;
  avatar: string;
  color: string;
  online?: boolean;
}

/**
 * A player's avatar in their colour ring, optionally with the name. Projected content is shown
 * after the name (e.g. a score or a check mark).
 *
 * ```html
 * <ff-player-chip [player]="p" size="sm"><b>{{ p.score }}</b></ff-player-chip>
 * ```
 */
@Component({
  selector: 'ff-player-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="avatar" [class.offline]="!online()" aria-hidden="true">{{ player().avatar }}</span>
    @if (showName()) {
      <span class="name">{{ player().name }}</span>
    }
    <ng-content />
  `,
  host: {
    class: 'ff-player-chip',
    '[class.sm]': "size() === 'sm'",
    '[class.lg]': "size() === 'lg'",
    '[style.--ff-player-color]': 'player().color',
    '[attr.title]': "online() ? player().name : player().name + ' (offline)'",
  },
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
      --size: 32px;
    }
    :host(.sm) {
      --size: 24px;
      gap: 6px;
      font-size: 13px;
    }
    :host(.lg) {
      --size: 48px;
      gap: 12px;
      font-size: 18px;
    }
    .avatar {
      flex: none;
      display: grid;
      place-items: center;
      width: var(--size);
      height: var(--size);
      border-radius: 50%;
      font-size: calc(var(--size) * 0.58);
      line-height: 1;
      background: color-mix(in srgb, var(--ff-player-color) 22%, transparent);
      box-shadow: 0 0 0 2px var(--ff-player-color);
      transition:
        filter 200ms,
        opacity 200ms;
    }
    .avatar.offline {
      filter: grayscale(1);
      opacity: 0.45;
    }
    .name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-weight: 600;
    }
  `,
})
export class PlayerChip {
  readonly player = input.required<ChipPlayer>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly showName = input(true);

  protected readonly online = computed(() => this.player().online !== false);
}
