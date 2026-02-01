import { Component, OnInit, OnDestroy, inject, NgZone, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { WebsocketService, GameService } from '../../services';
import { Game } from '../../models';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../confirm-dialog/confirm-dialog.component';
import { getRoundDuration, getCurrentTurn } from '../../utils/game-config.utils';

/**
 * Base class for all game type components.
 * Provides common functionality for lobby, countdown, timers, and game state management.
 */
@Component({
  template: '',
})
export abstract class BaseGameComponent implements OnInit, OnDestroy {
  game: Game | undefined;
  gameId: string | null = null;
  playerName = '';
  isHost = false;
  playerAvatar = '👤';
  hasGuessed = false;

  // Signals for reactive state
  private _countdown = signal(0);
  private _roundTimer = signal(0);
  private _isLoading = signal(true);

  get countdown(): number {
    return this._countdown();
  }

  get roundTimer(): number {
    return this._roundTimer();
  }

  get isLoading(): boolean {
    return this._isLoading();
  }

  protected countdownInterval: ReturnType<typeof setInterval> | null = null;
  protected roundTimerInterval: ReturnType<typeof setInterval> | null = null;
  protected dialog = inject(MatDialog);
  protected ngZone = inject(NgZone);
  protected route = inject(ActivatedRoute);
  protected router = inject(Router);
  protected websocketService = inject(WebsocketService);
  protected gameService = inject(GameService);

  ngOnInit(): void {
    this.gameId = this.route.snapshot.paramMap.get('id');
    this.playerName = sessionStorage.getItem('playerName') || '';
    this.isHost = sessionStorage.getItem('isHost') === 'true';
    this.playerAvatar = sessionStorage.getItem('playerAvatar') || '👤';

    if (this.gameId) {
      this.checkGameStatus();
    } else {
      this.router.navigate(['/error', 'not-found']);
    }
  }

  private checkGameStatus(): void {
    if (!this.gameId) return;

    this.gameService.getGameStatus(this.gameId).subscribe({
      next: status => {
        this._isLoading.set(false);

        if (!status.exists) {
          this.router.navigate(['/error', 'not-found']);
          return;
        }

        if (status.gameState === 'FINISHED') {
          this.router.navigate(['/error', 'game-ended']);
          return;
        }

        if (!this.playerName) {
          this.router.navigate(['/']);
          return;
        }

        this.initializeGame();
      },
      error: () => {
        this._isLoading.set(false);
        this.router.navigate(['/error', 'not-found']);
      },
    });
  }

  protected initializeGame(): void {
    const initialGameData = sessionStorage.getItem('initialGame');
    if (initialGameData) {
      try {
        this.game = JSON.parse(initialGameData);
        sessionStorage.removeItem('initialGame');
      } catch {
        // Ignore parse errors
      }
    }

    if (this.gameId) {
      this.websocketService.connect();
      this.websocketService.subscribe('/topic/game/' + this.gameId, (message: unknown) => {
        const game = message as Game;
        this.handleGameUpdate(game);
      });

      this.websocketService.subscribe('/topic/game/' + this.gameId + '/terminated', () => {
        this.clearCountdown();
        this.clearRoundTimer();
        this.router.navigate(['/error', 'host-left']);
      });

      // Subscribe to player removal notifications
      this.websocketService.subscribe(
        '/topic/game/' + this.gameId + '/removed/' + this.playerName,
        () => {
          this.clearCountdown();
          this.clearRoundTimer();
          this.router.navigate(['/error', 'removed']);
        }
      );

      // If no initial game data (e.g., coming from Play Again), fetch the game details
      if (!this.game) {
        this.gameService.getGameDetails(this.gameId).subscribe({
          next: game => {
            this.game = game;
          },
          error: () => {
            // Will rely on WebSocket update
          },
        });
      }
    }
  }

  protected handleGameUpdate(game: Game): void {
    const wasGuessing = this.game?.gameState === 'GUESSING';
    const wasDrawing = this.game?.gameState === 'DRAWING';
    const wasStarting = this.game?.gameState === 'STARTING';
    const wasNotFinished = this.game?.gameState !== 'FINISHED';
    const previousRoundNumber = this.game?.currentRoundNumber;
    const previousTurn = getCurrentTurn(this.game);
    this.game = game;

    if (game.gameState === 'STARTING' && !wasStarting) {
      this.startCountdown(3);
    }

    // Handle both GUESSING (most games) and DRAWING (Pictionary) states
    const isPlayingRound = game.gameState === 'GUESSING' || game.gameState === 'DRAWING';
    const wasPlayingRound = wasGuessing || wasDrawing;

    // Detect new round/turn: state transition OR round/turn number changed while in playing state
    const currentTurn = getCurrentTurn(game);
    const isNewRoundOrTurn =
      (!wasPlayingRound && isPlayingRound) ||
      (isPlayingRound &&
        (game.currentRoundNumber !== previousRoundNumber ||
          (currentTurn !== 0 && currentTurn !== previousTurn)));

    if (isNewRoundOrTurn) {
      this.hasGuessed = false;
      this.onNewRound();
      this.clearCountdown();
      this.startRoundTimer();
    }

    if (!isPlayingRound) {
      this.clearRoundTimer();
    }

    if (game.gameState === 'FINISHED' && wasNotFinished) {
      this.router.navigate(['/results', this.gameId], { state: { game } });
      return;
    }

    this.checkIfPlayerAnswered();
  }

  /** Called when a new round starts - override to reset game-specific state */
  protected abstract onNewRound(): void;

  /** Called to check if current player has already answered - override for game-specific logic */
  protected abstract checkIfPlayerAnswered(): void;

  ngOnDestroy(): void {
    this.clearCountdown();
    this.clearRoundTimer();
    if (this.gameId) {
      this.websocketService.unsubscribe('/topic/game/' + this.gameId);
      this.websocketService.unsubscribe('/topic/game/' + this.gameId + '/terminated');
      this.websocketService.unsubscribe(
        '/topic/game/' + this.gameId + '/removed/' + this.playerName
      );
    }
  }

  protected startCountdown(seconds: number): void {
    this.clearCountdown();
    this._countdown.set(seconds);
    this.ngZone.runOutsideAngular(() => {
      this.countdownInterval = setInterval(() => {
        this.ngZone.run(() => {
          this._countdown.update(v => v - 1);
          if (this._countdown() <= 0) {
            this.clearCountdown();
          }
        });
      }, 1000);
    });
  }

  protected clearCountdown(): void {
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
      this.countdownInterval = null;
    }
    this._countdown.set(0);
  }

  protected startRoundTimer(): void {
    this.clearRoundTimer();
    // Use game's configured round duration from gameConfig
    const duration = getRoundDuration(this.game);
    this._roundTimer.set(duration);
    this.ngZone.runOutsideAngular(() => {
      this.roundTimerInterval = setInterval(() => {
        this.ngZone.run(() => {
          this._roundTimer.update(v => v - 1);
          if (this._roundTimer() <= 0) {
            this.clearRoundTimer();
          }
        });
      }, 1000);
    });
  }

  protected clearRoundTimer(): void {
    if (this.roundTimerInterval) {
      clearInterval(this.roundTimerInterval);
      this.roundTimerInterval = null;
    }
    this._roundTimer.set(0);
  }

  startGame(): void {
    if (this.gameId && this.isHost) {
      this.websocketService.sendMessage('/app/start', {
        sender: this.playerName,
        content: this.gameId,
      });
    }
  }

  goHome(): void {
    sessionStorage.clear();
    this.router.navigate(['/']);
  }

  leaveGame(): void {
    if (this.gameId) {
      this.websocketService.sendMessage('/app/leave', {
        sender: this.playerName,
        content: this.gameId,
      });
    }
    this.goHome();
  }

  exitGame(): void {
    if (this.gameId && this.isHost) {
      // Host leaving will terminate the game for all players via the /leave endpoint
      this.websocketService.sendMessage('/app/leave', {
        sender: this.playerName,
        content: this.gameId,
      });
    }
    this.goHome();
  }

  copyGameCode(): void {
    if (this.gameId) {
      navigator.clipboard.writeText(this.gameId);
    }
  }

  copyJoinUrl(): void {
    if (this.gameId) {
      // Use current URL's origin and pathname (handles dynamic prefixes and hash routing)
      const currentUrl = window.location.href;
      // Extract the base URL (everything before the route path)
      const hashIndex = currentUrl.indexOf('#');
      const baseUrl =
        hashIndex !== -1 ? currentUrl.substring(0, hashIndex + 1) : window.location.origin;
      const joinUrl = `${baseUrl}/setup/join/${this.gameId}`;
      navigator.clipboard.writeText(joinUrl);
    }
  }

  removePlayer(playerToRemove: string): void {
    if (!this.gameId || !this.isHost) return;

    const dialogData: ConfirmDialogData = {
      title: 'Remove Player',
      message: `Are you sure you want to remove "${playerToRemove}" from the game?`,
      confirmText: 'Remove',
      cancelText: 'Cancel',
      confirmColor: 'warn',
      icon: 'person_remove',
    };

    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: dialogData,
    });

    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (confirmed && this.gameId) {
        this.websocketService.sendMessage('/app/removePlayer', {
          sender: this.playerName,
          content: `${this.gameId}:${playerToRemove}`,
        });
      }
    });
  }

  isRoundWinner(playerName: string): boolean {
    return this.game?.currentRoundInfo?.winner === playerName;
  }
}
