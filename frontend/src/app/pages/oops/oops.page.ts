import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';

import { NotifyService } from '../../core/notify.service';
import { RoomStore } from '../../core/room.store';

interface Explanation {
  emoji: string;
  title: string;
  text: string;
}

const EXPLANATIONS: Record<string, Explanation> = {
  kicked: {
    emoji: '👋',
    title: 'You were removed from the room',
    text: 'The host removed you. If that was a mistake, ask for the code and join again.',
  },
  closed: {
    emoji: '🚪',
    title: 'The room was closed',
    text: 'Thanks for playing! Start a new room or join another one.',
  },
  replaced: {
    emoji: '🪟',
    title: 'Opened in another tab',
    text: 'Your seat is now used by another tab or window. You can take it back here.',
  },
  expired: {
    emoji: '⌛',
    title: 'Your seat has expired',
    text: "You were away for a while, or the room doesn't exist anymore.",
  },
};

const FALLBACK: Explanation = {
  emoji: '🤔',
  title: 'Something went wrong',
  text: 'Head back home and try again.',
};

/** Explains why you are not in a room anymore (kicked, closed, replaced, expired). */
@Component({
  selector: 'ff-oops-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <main class="page">
      <section class="ff-card card" role="alert">
        <span class="emoji" aria-hidden="true">{{ explanation().emoji }}</span>
        <h1>{{ explanation().title }}</h1>
        <p class="ff-muted">{{ message() || explanation().text }}</p>
        <div class="ff-row">
          @if (reason() === 'replaced') {
            <button mat-flat-button [disabled]="busy()" (click)="playHere()" data-testid="play-here">
              Play here instead
            </button>
          }
          <a mat-flat-button routerLink="/" [class.secondary]="reason() === 'replaced'" data-testid="go-home">
            <mat-icon svgIcon="home" /> Home
          </a>
        </div>
      </section>
    </main>
  `,
  styles: `
    .page {
      min-height: 100dvh;
      display: grid;
      place-items: center;
      padding: 24px;
    }
    .card {
      max-width: 480px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      text-align: center;
      animation: ff-pop-in 400ms var(--ff-ease-spring) both;
    }
    .emoji {
      font-size: 64px;
    }
    .secondary {
      --mat-button-filled-container-color: var(--mat-sys-surface-container-highest);
      --mat-button-filled-label-text-color: var(--mat-sys-on-surface);
    }
  `,
})
export class OopsPage {
  /** Route parameter (`/oops/:reason`). */
  readonly reason = input.required<string>();
  /** Optional server message (`?message=…`). */
  readonly message = input<string>();

  private readonly store = inject(RoomStore);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);
  protected readonly busy = signal(false);
  protected readonly explanation = computed(() => EXPLANATIONS[this.reason()] ?? FALLBACK);

  protected async playHere(): Promise<void> {
    this.busy.set(true);
    try {
      const code = await this.store.playHere();
      await this.router.navigate(code ? ['/room', code] : ['/']);
    } catch (error) {
      this.notify.error(error);
    } finally {
      this.busy.set(false);
    }
  }
}
