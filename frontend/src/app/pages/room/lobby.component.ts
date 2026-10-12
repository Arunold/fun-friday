import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, linkedSignal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { CatalogService } from '../../core/api.service';
import { SettingValues } from '../../core/protocol';
import { RoomStore } from '../../core/room.store';
import { PlayerChip } from '../../sdk';
import { CupTable } from '../../shared/cup-table.component';
import { SettingsForm } from '../../shared/settings-form.component';
import { RoomActions } from './room-actions.service';

/** The lobby: next game and its settings, players, invite and the start button. */
@Component({
  selector: 'ff-lobby',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, PlayerChip, CupTable, SettingsForm],
  templateUrl: './lobby.component.html',
  styleUrl: './lobby.component.scss',
})
export class Lobby {
  protected readonly store = inject(RoomStore);
  protected readonly actions = inject(RoomActions);
  private readonly catalog = inject(CatalogService);

  /** Catalog entry of the selected game (rules, settings schema). */
  protected readonly game = computed(() => this.catalog.find(this.store.game()?.id));
  protected readonly meId = computed(() => this.store.you()?.memberId);
  protected readonly amSpectator = computed(() => this.store.you()?.role === 'SPECTATOR');
  protected readonly onlinePlayers = computed(() => this.store.players().filter((p) => p.online).length);
  protected readonly maxPlayers = computed(() => this.store.game()?.maxPlayers ?? 0);
  protected readonly missing = computed(() => Math.max(0, (this.store.game()?.minPlayers ?? 1) - this.onlinePlayers()));
  protected readonly tooMany = computed(() => this.store.players().length > this.maxPlayers());
  protected readonly readyCount = computed(() => this.store.players().filter((p) => p.ready || p.host).length);
  protected readonly startHint = computed(() => {
    if (this.missing() > 0) {
      const n = this.missing();
      return `Waiting for ${n} more player${n === 1 ? '' : 's'}…`;
    }
    if (this.tooMany()) {
      return `Too many players for ${this.store.game()?.name}.`;
    }
    const total = this.store.players().length;
    return `${this.readyCount()} of ${total} ready`;
  });
  protected readonly seatsFree = computed(() => this.store.players().length < this.maxPlayers());

  /** The host's edits; reset whenever the server pushes new settings. */
  protected readonly settingsDraft = linkedSignal<SettingValues>(() => this.store.settings());
  private pushTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    void this.catalog.load();
    inject(DestroyRef).onDestroy(() => clearTimeout(this.pushTimer));
  }

  protected onSettingsChange(values: SettingValues): void {
    this.settingsDraft.set(values);
    clearTimeout(this.pushTimer);
    const gameId = this.store.game()?.id;
    if (gameId) {
      this.pushTimer = setTimeout(() => void this.actions.run(() => this.store.updateSettings(gameId, values)), 250);
    }
  }

  protected start(): void {
    void this.actions.run(() => this.store.start());
  }

  protected toggleReady(): void {
    void this.actions.run(() => this.store.setReady(!this.store.me()?.ready));
  }

  protected becomePlayer(): void {
    void this.actions.run(() => this.store.becomePlayer());
  }
}
