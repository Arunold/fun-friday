import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';

import { GameSummary } from '../core/protocol';

const DIFFICULTY_LABEL: Record<GameSummary['difficulty'], string> = {
  EASY: 'Easy',
  MEDIUM: 'Medium',
  HARD: 'Hard',
};

/** A game in the catalog: icon, name, pitch, player range and difficulty. */
@Component({
  selector: 'ff-game-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="icon" aria-hidden="true">{{ game().icon }}</span>
    <span class="text">
      <span class="name">{{ game().name }}</span>
      <span class="description">{{ game().description }}</span>
      <span class="meta">
        <span class="ff-pill">👥 {{ players() }}</span>
        <span class="ff-pill difficulty" [attr.data-level]="game().difficulty">{{ difficulty() }}</span>
      </span>
    </span>
  `,
  host: { '[class.selected]': 'selected()', '[class.compact]': 'compact()' },
  styles: `
    :host {
      display: flex;
      gap: 16px;
      align-items: flex-start;
      padding: 18px;
      border-radius: var(--ff-radius);
      border: 1px solid var(--ff-border);
      background: var(--mat-sys-surface-container);
      text-align: left;
      transition:
        transform 200ms var(--ff-ease-out),
        border-color 200ms,
        background 200ms;
    }
    :host(:hover) {
      transform: translateY(-2px);
      border-color: color-mix(in srgb, var(--mat-sys-primary) 60%, transparent);
    }
    :host(.selected) {
      border-color: var(--mat-sys-primary);
      background: color-mix(in srgb, var(--mat-sys-primary) 14%, var(--mat-sys-surface-container));
    }
    :host(.compact) {
      padding: 12px;
      gap: 12px;
    }
    :host(.compact) .description {
      display: none;
    }
    .icon {
      flex: none;
      display: grid;
      place-items: center;
      width: 56px;
      height: 56px;
      border-radius: 16px;
      font-size: 32px;
      background: var(--mat-sys-surface-container-highest);
    }
    :host(.compact) .icon {
      width: 44px;
      height: 44px;
      font-size: 24px;
    }
    .text {
      display: flex;
      flex-direction: column;
      gap: 6px;
      min-width: 0;
    }
    .name {
      font-size: 17px;
      font-weight: 700;
      color: var(--mat-sys-on-surface);
    }
    .description {
      color: var(--ff-text-muted);
      font-size: 14px;
      line-height: 1.4;
    }
    .meta {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
    .difficulty[data-level='EASY'] {
      color: var(--ff-success);
    }
    .difficulty[data-level='MEDIUM'] {
      color: var(--ff-warning);
    }
    .difficulty[data-level='HARD'] {
      color: var(--ff-danger);
    }
  `,
})
export class GameCard {
  readonly game = input.required<GameSummary>();
  readonly selected = input(false, { transform: booleanAttribute });
  readonly compact = input(false, { transform: booleanAttribute });

  protected readonly players = computed(() => {
    const { minPlayers, maxPlayers } = this.game();
    return minPlayers === maxPlayers ? `${minPlayers}` : `${minPlayers}–${maxPlayers}`;
  });
  protected readonly difficulty = computed(() => DIFFICULTY_LABEL[this.game().difficulty]);
}
