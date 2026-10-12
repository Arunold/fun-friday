import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { CatalogService } from '../core/api.service';
import { GameSummary, SettingValues } from '../core/protocol';
import { GameCard } from './game-card.component';
import { SettingsForm } from './settings-form.component';

export interface ChangeGameData {
  gameId: string;
  settings: SettingValues;
  /** Players currently seated, to warn about games with a lower limit. */
  playerCount: number;
}

export interface ChangeGameResult {
  gameId: string;
  settings: SettingValues;
}

/** Host picks the next game and its settings (from the lobby or the podium). */
@Component({
  selector: 'ff-change-game-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatDialogModule, GameCard, SettingsForm],
  template: `
    <h2 mat-dialog-title>Choose the next game</h2>
    <mat-dialog-content>
      <div class="games" role="listbox" aria-label="Games">
        @for (game of catalog.games() ?? []; track game.id) {
          <button
            type="button"
            class="pick"
            role="option"
            [attr.aria-selected]="game.id === selectedId()"
            [attr.data-game]="game.id"
            (click)="select(game)"
          >
            <ff-game-card [game]="game" [selected]="game.id === selectedId()" compact />
          </button>
        }
      </div>
      @if (selected(); as game) {
        <section class="settings">
          <h3>{{ game.icon }} {{ game.name }}</h3>
          <ff-settings-form [defs]="game.settings" [(values)]="values" />
          @if (tooMany()) {
            <p class="warning">
              {{ game.name }} allows at most {{ game.maxPlayers }} players, but {{ data.playerCount }} are seated.
            </p>
          }
        </section>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button [disabled]="!selected() || tooMany()" (click)="confirm()" data-testid="use-game">
        Use this game
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .games {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
      gap: 10px;
    }
    .pick {
      padding: 0;
      border: 0;
      background: none;
      font: inherit;
      color: inherit;
      cursor: pointer;
      border-radius: var(--ff-radius);
    }
    .pick ff-game-card {
      height: 100%;
    }
    .settings {
      display: flex;
      flex-direction: column;
      gap: 14px;
      margin-top: 24px;
    }
    .warning {
      color: var(--ff-warning);
    }
  `,
})
export class ChangeGameDialog {
  protected readonly catalog = inject(CatalogService);
  protected readonly data = inject<ChangeGameData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<ChangeGameDialog, ChangeGameResult>>(MatDialogRef);

  protected readonly selectedId = signal(this.data.gameId);
  protected readonly values = signal<SettingValues>({ ...this.data.settings });
  protected readonly selected = computed(() => this.catalog.find(this.selectedId()));
  protected readonly tooMany = computed(() => {
    const game = this.selected();
    return !!game && this.data.playerCount > game.maxPlayers;
  });

  constructor() {
    void this.catalog.load();
  }

  protected select(game: GameSummary): void {
    if (game.id === this.selectedId()) {
      return;
    }
    this.selectedId.set(game.id);
    this.values.set(game.id === this.data.gameId ? { ...this.data.settings } : { ...game.defaults });
  }

  protected confirm(): void {
    this.ref.close({ gameId: this.selectedId(), settings: this.values() });
  }
}
