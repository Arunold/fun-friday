import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { NotifyService, errorMessage } from '../../core/notify.service';
import { Profile, ProfileService } from '../../core/profile.service';
import { RoomSummary } from '../../core/protocol';
import { RequestError } from '../../core/realtime.service';
import { RoomStore } from '../../core/room.store';
import { normalizeRoomCode } from '../../core/util';
import { ProfileForm, nameError } from '../../shared/profile-form.component';

const PHASE_LABEL: Record<RoomSummary['phase'], string> = {
  LOBBY: 'Waiting in the lobby',
  STARTING: 'Starting',
  PLAYING: 'Game in progress',
  REVEAL: 'Game in progress',
  FINISHED: 'Between games',
};

/** Join a room by code: shows what's going on in the room, then asks for name and avatar. */
@Component({
  selector: 'ff-join-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ProfileForm,
  ],
  template: `
    <main class="page">
      <a mat-button routerLink="/" class="back"><mat-icon svgIcon="arrow-back" /> Home</a>

      @if (!normalizedCode()) {
        <section class="ff-card ff-stack narrow">
          <h1>Join a room</h1>
          <form class="code-form" (submit)="$event.preventDefault(); goToCode()">
            <mat-form-field class="code-field">
              <mat-label>Room code</mat-label>
              <input
                matInput
                name="code"
                class="ff-mono"
                maxlength="5"
                autocomplete="off"
                [ngModel]="codeInput()"
                (ngModelChange)="codeInput.set($event)"
                data-testid="join-code"
              />
            </mat-form-field>
            <button mat-flat-button class="ff-big">Continue</button>
          </form>
        </section>
      } @else if (summary() === undefined) {
        <mat-spinner diameter="36" class="spinner" />
      } @else if (summary(); as room) {
        <div class="layout">
          <section class="ff-card room">
            <span class="ff-eyebrow"
              >Room <span class="ff-mono">{{ room.code }}</span></span
            >
            <div class="game">
              <span class="icon" aria-hidden="true">{{ room.gameIcon }}</span>
              <div>
                <h1>{{ room.gameName }}</h1>
                <p class="ff-muted">Hosted by {{ room.hostName }}</p>
              </div>
            </div>
            <div class="ff-row ff-wrap">
              <span class="ff-pill">👥 {{ room.players }} / {{ room.maxPlayers }} players</span>
              <span class="ff-pill">{{ phaseLabel() }}</span>
            </div>
            @if (!room.joinable) {
              <p class="note">
                {{
                  room.players >= room.maxPlayers
                    ? 'The room is full — you can watch and take a seat when one frees up.'
                    : 'A game is running — you will watch it and play from the next game on.'
                }}
              </p>
            }
          </section>

          <form class="ff-card ff-stack" (submit)="$event.preventDefault(); join(false)">
            <h2>You</h2>
            <ff-profile-form
              [(profile)]="profile"
              (profileChange)="nameServerError.set(null)"
              [serverError]="nameServerError()"
            />
            <div class="actions">
              <button
                mat-button
                type="button"
                [disabled]="!canJoin()"
                (click)="join(true)"
                data-testid="join-spectator"
              >
                <mat-icon svgIcon="visibility" /> Just watch
              </button>
              <button mat-flat-button class="ff-big" [disabled]="!canJoin()" data-testid="join-room">
                @if (busy()) {
                  <mat-spinner diameter="20" />
                }
                {{ room.joinable ? 'Join game' : 'Join room' }}
              </button>
            </div>
          </form>
        </div>
      } @else {
        <section class="ff-card ff-stack narrow" role="alert">
          <span class="big-emoji" aria-hidden="true">🔍</span>
          <h1>No room “{{ normalizedCode() }}”</h1>
          <p class="ff-muted">{{ loadError() ?? 'Check the code — rooms close when everyone has left.' }}</p>
          <div class="ff-row">
            <a mat-flat-button routerLink="/join">Try another code</a>
            <a mat-button routerLink="/">Home</a>
          </div>
        </section>
      }
    </main>
  `,
  styles: `
    .page {
      max-width: 1000px;
      margin: 0 auto;
      padding: 24px 32px 48px;
    }
    .back {
      margin-bottom: 16px;
    }
    .narrow {
      max-width: 480px;
      margin: 40px auto;
    }
    .layout {
      display: grid;
      grid-template-columns: 1fr 1.2fr;
      gap: 24px;
      align-items: start;
      animation: ff-fade-up 400ms var(--ff-ease-out) both;
    }
    .room {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .game {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .icon {
      font-size: 56px;
    }
    .note {
      padding: 12px 14px;
      border-radius: var(--ff-radius-sm);
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
      font-size: 14px;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      align-items: center;
      gap: 12px;
    }
    .actions mat-spinner {
      display: inline-block;
      margin-right: 8px;
    }
    .code-form {
      display: flex;
      gap: 12px;
      align-items: flex-start;
    }
    .code-field {
      flex: 1;
    }
    .code-field input {
      font-size: 22px;
      letter-spacing: 0.3em;
      text-transform: uppercase;
    }
    .big-emoji {
      font-size: 48px;
    }
    .spinner {
      margin: 64px auto;
    }
  `,
})
export class JoinPage {
  /** Route parameter (`/join/:code`); absent on `/join`. */
  readonly code = input<string>();

  private readonly api = inject(ApiService);
  private readonly store = inject(RoomStore);
  private readonly profiles = inject(ProfileService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);

  protected readonly codeInput = signal('');
  protected readonly normalizedCode = computed(() => normalizeRoomCode(this.code()));
  /** `undefined` while loading, `null` if the room doesn't exist. */
  protected readonly summary = signal<RoomSummary | null | undefined>(undefined);
  protected readonly loadError = signal<string | null>(null);
  protected readonly profile = signal<Profile>(this.profiles.profile());
  protected readonly busy = signal(false);
  protected readonly nameServerError = signal<string | null>(null);
  protected readonly phaseLabel = computed(() => {
    const room = this.summary();
    return room ? PHASE_LABEL[room.phase] : '';
  });
  protected readonly canJoin = computed(() => !!this.summary() && !nameError(this.profile().name) && !this.busy());

  constructor() {
    effect(() => {
      const code = this.normalizedCode();
      untracked(() => void this.load(code));
    });
  }

  protected goToCode(): void {
    const code = normalizeRoomCode(this.codeInput());
    if (code) {
      void this.router.navigate(['/join', code]);
    }
  }

  protected async join(spectator: boolean): Promise<void> {
    const room = this.summary();
    if (!room || !this.canJoin()) {
      return;
    }
    this.busy.set(true);
    this.nameServerError.set(null);
    try {
      this.profiles.save(this.profile());
      await this.store.join(room.code, this.profiles.profile(), spectator);
      await this.router.navigate(['/room', room.code]);
    } catch (error) {
      if (error instanceof RequestError && (error.code === 'NAME_TAKEN' || error.code === 'INVALID_NAME')) {
        this.nameServerError.set(error.message);
      } else if (error instanceof RequestError && error.code === 'ROOM_NOT_FOUND') {
        this.summary.set(null);
      } else {
        this.notify.error(error);
      }
    } finally {
      this.busy.set(false);
    }
  }

  private async load(code: string): Promise<void> {
    this.loadError.set(null);
    if (!code) {
      return;
    }
    if (this.store.hasSessionFor(code)) {
      await this.router.navigate(['/room', code], { replaceUrl: true });
      return;
    }
    this.summary.set(undefined);
    try {
      this.summary.set(await this.api.room(code));
    } catch (error) {
      this.loadError.set(errorMessage(error));
      this.summary.set(null);
    }
  }
}
