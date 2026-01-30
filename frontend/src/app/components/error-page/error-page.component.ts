import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export type ErrorType = 'not-found' | 'game-ended' | 'host-left' | 'removed' | 'loading';

interface ErrorConfig {
  icon: string;
  title: string;
  message: string;
  hint: string;
}

@Component({
  selector: 'app-error-page',
  standalone: true,
  styleUrls: ['./error-page.component.css'],
  imports: [CommonModule, MatButtonModule, MatIconModule],
  template: `
    <div class="error-container">
      <div class="error-content">
        @if (isLoading) {
          <mat-icon class="loading-spinner">hourglass_empty</mat-icon>
          <h1>Loading...</h1>
        } @else {
          <mat-icon class="error-icon">{{ errorConfig.icon }}</mat-icon>
          <h1>{{ errorConfig.title }}</h1>
          <p class="error-message">{{ errorConfig.message }}</p>
          <p class="error-hint">{{ errorConfig.hint }}</p>
          <button mat-raised-button color="primary" (click)="goHome()">
            <mat-icon>home</mat-icon>
            Go Home
          </button>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .error-container {
        min-height: 100vh;
        background: var(--spotify-black);
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 20px;
      }

      .error-content {
        text-align: center;
        max-width: 500px;
      }

      .error-icon {
        font-size: 6rem !important;
        width: 96px !important;
        height: 96px !important;
        color: var(--spotify-text-gray);
        margin-bottom: 20px;
      }

      .loading-spinner {
        font-size: 4rem !important;
        width: 64px !important;
        height: 64px !important;
        color: var(--spotify-green);
        margin-bottom: 20px;
        animation: spin 2s linear infinite;
      }

      h1 {
        color: var(--spotify-white);
        font-size: 2rem;
        margin-bottom: 15px;
      }

      .error-message {
        color: var(--spotify-text-gray);
        font-size: 1.1rem;
        margin-bottom: 10px;
      }

      .error-hint {
        color: var(--spotify-text-gray);
        font-size: 0.9rem;
        opacity: 0.7;
        margin-bottom: 30px;
      }

      button {
        padding: 12px 32px !important;
        font-size: 1rem !important;
      }

      button mat-icon {
        margin-right: 8px;
      }
    `,
  ],
})
export class ErrorPageComponent implements OnInit {
  errorType: ErrorType = 'not-found';
  isLoading = false;

  private errorConfigs: Record<ErrorType, ErrorConfig> = {
    'not-found': {
      icon: 'search_off',
      title: 'Game Not Found',
      message: "The game you're looking for doesn't exist or the code is incorrect.",
      hint: 'Game codes are 6 characters long and case-insensitive.',
    },
    'game-ended': {
      icon: 'event_busy',
      title: 'Game Has Ended',
      message: 'This game session has already finished.',
      hint: 'Start a new game or join an active one!',
    },
    'host-left': {
      icon: 'person_off',
      title: 'Host Left the Game',
      message: 'The host has ended the game session.',
      hint: 'You can start a new game or join another one!',
    },
    removed: {
      icon: 'person_remove',
      title: 'Removed from Game',
      message: 'You have been removed from the game by the host.',
      hint: 'You can join another game or start your own!',
    },
    loading: {
      icon: 'hourglass_empty',
      title: 'Loading...',
      message: '',
      hint: '',
    },
  };

  errorConfig: ErrorConfig = this.errorConfigs['not-found'];

  private route = inject(ActivatedRoute);
  private router = inject(Router);

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const type = params.get('type') as ErrorType;
      if (type && this.errorConfigs[type]) {
        this.errorType = type;
        this.errorConfig = this.errorConfigs[type];
        this.isLoading = type === 'loading';
      }
    });
  }

  goHome(): void {
    this.router.navigate(['/']);
  }
}
