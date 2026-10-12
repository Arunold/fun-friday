import { ChangeDetectionStrategy, Component, model, signal } from '@angular/core';

/** Curated avatar emoji (no images, no uploads — every avatar is one emoji). */
export const AVATAR_GROUPS: readonly { label: string; emoji: readonly string[] }[] = [
  {
    label: 'Animals',
    emoji: [
      '🦊',
      '🐼',
      '🐨',
      '🐯',
      '🦁',
      '🐸',
      '🐵',
      '🐧',
      '🦉',
      '🐙',
      '🦄',
      '🐝',
      '🐢',
      '🦖',
      '🐳',
      '🦩',
      '🦔',
      '🐲',
      '🐱',
      '🐶',
    ],
  },
  {
    label: 'People',
    emoji: [
      '😀',
      '😎',
      '🤓',
      '🥳',
      '🤠',
      '🥸',
      '🤩',
      '😇',
      '🧐',
      '🤖',
      '👻',
      '👽',
      '🦸',
      '🧙',
      '🧛',
      '🧜',
      '🧚',
      '🥷',
      '🧑‍🚀',
      '🧑‍🍳',
    ],
  },
  {
    label: 'Food',
    emoji: ['🍕', '🌮', '🍩', '🍉', '🍓', '🥑', '🍔', '🧁', '🍿', '🥨', '🍪', '🌶️', '🍣', '🥐', '🍦', '🍋'],
  },
  {
    label: 'Things',
    emoji: ['🚀', '⚡', '🔥', '🌈', '⭐', '🎸', '🎮', '🎲', '🏆', '💎', '🎨', '🛸', '🌵', '🍀', '🌙', '⚽'],
  },
];

/** Emoji grid with category tabs; two-way bind the selected emoji with `[(value)]`. */
@Component({
  selector: 'ff-avatar-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tabs" role="tablist" aria-label="Avatar categories">
      @for (group of groups; track group.label; let i = $index) {
        <button
          type="button"
          role="tab"
          class="tab"
          [class.active]="tab() === i"
          [attr.aria-selected]="tab() === i"
          (click)="tab.set(i)"
        >
          {{ group.label }}
        </button>
      }
    </div>
    <div class="grid" role="radiogroup" aria-label="Avatar">
      @for (emoji of groups[tab()].emoji; track emoji) {
        <button
          type="button"
          role="radio"
          class="emoji"
          [class.selected]="value() === emoji"
          [attr.aria-checked]="value() === emoji"
          [attr.aria-label]="emoji"
          (click)="value.set(emoji)"
        >
          {{ emoji }}
        </button>
      }
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .tabs {
      display: flex;
      gap: 4px;
    }
    .tab {
      border: 0;
      background: none;
      color: var(--ff-text-muted);
      font: inherit;
      font-size: 13px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 999px;
      cursor: pointer;
    }
    .tab.active {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(10, 1fr);
      gap: 4px;
    }
    .emoji {
      aspect-ratio: 1;
      border: 0;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-high);
      font-size: 22px;
      line-height: 1;
      cursor: pointer;
      transition:
        transform 150ms var(--ff-ease-spring),
        background 150ms;
    }
    .emoji:hover {
      transform: scale(1.12);
    }
    .emoji.selected {
      background: var(--mat-sys-primary-container);
      box-shadow: 0 0 0 2px var(--mat-sys-primary);
    }
  `,
})
export class AvatarPicker {
  readonly value = model('🦊');
  protected readonly groups = AVATAR_GROUPS;
  protected readonly tab = signal(0);
}
