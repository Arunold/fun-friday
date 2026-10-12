import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { CatalogService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { Profile, ProfileService } from '../../core/profile.service';
import { SettingValues } from '../../core/protocol';
import { RequestError } from '../../core/realtime.service';
import { RoomStore } from '../../core/room.store';
import { ProfileForm, nameError } from '../../shared/profile-form.component';
import { SettingsForm } from '../../shared/settings-form.component';

/** Host a game: pick your name/avatar and the settings, then create the room. */
@Component({
  selector: 'ff-host-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, ProfileForm, SettingsForm],
  template: `
    <main class="page">
      <a mat-button routerLink="/" class="back"><mat-icon svgIcon="arrow-back" /> All games</a>

      @if (game(); as game) {
        <div class="layout">
          <section class="info ff-card">
            <span class="icon" aria-hidden="true">{{ game.icon }}</span>
            <span class="ff-eyebrow">You're hosting</span>
            <h1>{{ game.name }}</h1>
            <p class="ff-muted">{{ game.description }}</p>
            <div class="ff-row ff-wrap">
              <span class="ff-pill">👥 {{ game.minPlayers }}–{{ game.maxPlayers }} players</span>
              <span class="ff-pill">🎚️ {{ game.difficulty.toLowerCase() }}</span>
            </div>
            @if (game.rules.length) {
              <h2>How to play</h2>
              <ol class="rules">
                @for (rule of game.rules; track rule) {
                  <li>{{ rule }}</li>
                }
              </ol>
            }
            <button
              mat-flat-button
              type="button"
              class="ff-big create"
              [disabled]="!canCreate()"
              (click)="create()"
              data-testid="create-room"
            >
              @if (busy()) {
                <mat-spinner diameter="20" />
              } @else {
                <mat-icon svgIcon="play" />
              }
              Create room
            </button>
          </section>

          <form class="setup" (submit)="$event.preventDefault(); create()">
            <section class="ff-card ff-stack">
              <h2>You</h2>
              <ff-profile-form
                [(profile)]="profile"
                (profileChange)="nameServerError.set(null)"
                [serverError]="nameServerError()"
              />
            </section>
            <section class="ff-card ff-stack">
              <h2>Settings</h2>
              <ff-settings-form [defs]="game.settings" [(values)]="settings" />
              <p class="ff-muted note">You can still change the game and its settings in the lobby.</p>
            </section>
            <!-- Enter in the name field submits the form. -->
            <button type="submit" hidden aria-hidden="true" tabindex="-1"></button>
          </form>
        </div>
      } @else if (catalog.games()) {
        <section class="ff-card missing">
          <h1>Unknown game</h1>
          <p class="ff-muted">There is no game called “{{ gameId() }}”.</p>
          <a mat-flat-button routerLink="/">Pick a game</a>
        </section>
      } @else if (catalog.error(); as error) {
        <section class="ff-card missing" role="alert">
          <p>{{ error }}</p>
          <button mat-button (click)="catalog.load()">Retry</button>
        </section>
      } @else {
        <mat-spinner diameter="36" class="spinner" />
      }
    </main>
  `,
  styles: `
    .page {
      max-width: 1180px;
      margin: 0 auto;
      padding: 24px 32px 48px;
    }
    .back {
      margin-bottom: 16px;
    }
    .layout {
      display: grid;
      grid-template-columns: 1fr 1.25fr;
      gap: 24px;
      align-items: start;
      animation: ff-fade-up 400ms var(--ff-ease-out) both;
    }
    .info {
      display: flex;
      flex-direction: column;
      gap: 12px;
      position: sticky;
      top: 24px;
    }
    .info .icon {
      font-size: 64px;
      line-height: 1;
    }
    .info h1 {
      font-size: 36px;
    }
    .info h2 {
      margin-top: 8px;
      font-size: 16px;
    }
    .rules {
      margin: 0;
      padding-left: 20px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      color: var(--ff-text-muted);
      line-height: 1.45;
    }
    .setup {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .note {
      font-size: 13px;
    }
    .create {
      align-self: stretch;
      margin-top: 8px;
    }
    .create mat-spinner {
      display: inline-block;
      margin-right: 8px;
    }
    .missing {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 12px;
    }
    .spinner {
      margin: 64px auto;
    }
  `,
})
export class HostPage {
  /** Route parameter (`/host/:gameId`). */
  readonly gameId = input.required<string>();

  protected readonly catalog = inject(CatalogService);
  private readonly profiles = inject(ProfileService);
  private readonly store = inject(RoomStore);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);

  protected readonly game = computed(() => this.catalog.find(this.gameId()));
  protected readonly profile = signal<Profile>(this.profiles.profile());
  protected readonly settings = linkedSignal<SettingValues>(() => ({ ...(this.game()?.defaults ?? {}) }));
  protected readonly busy = signal(false);
  protected readonly nameServerError = signal<string | null>(null);
  protected readonly canCreate = computed(() => !!this.game() && !nameError(this.profile().name) && !this.busy());

  constructor() {
    void this.catalog.load();
  }

  protected async create(): Promise<void> {
    if (!this.canCreate()) {
      return;
    }
    this.busy.set(true);
    this.nameServerError.set(null);
    try {
      this.profiles.save(this.profile());
      const code = await this.store.create(this.profiles.profile(), this.gameId(), this.settings());
      await this.router.navigate(['/room', code]);
    } catch (error) {
      if (error instanceof RequestError && error.code === 'INVALID_NAME') {
        this.nameServerError.set(error.message);
      } else {
        this.notify.error(error);
      }
    } finally {
      this.busy.set(false);
    }
  }
}
