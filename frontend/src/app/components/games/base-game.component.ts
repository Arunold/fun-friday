import { Component, OnInit, OnDestroy, inject, NgZone } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { WebsocketService, GameService } from '../../services';
import { Game } from '../../models';
import { ConfirmDialogComponent, ConfirmDialogData } from '../confirm-dialog/confirm-dialog.component';
import { getRoundDuration, getCurrentTurn } from '../../utils/game-config.utils';

/**
 * Base class for all game type components.
 * Provides common functionality for lobby, countdown, timers, and game state management.
 */
@Component({
  template: ''
})
export abstract class BaseGameComponent implements OnInit, OnDestroy {
  game: Game | undefined;
  gameId: string | null = null;
  playerName = '';
  isHost = false;
  playerAvatar = '👤';
  hasGuessed = false;
  countdown = 0;
  roundTimer = 0;

  isLoading = true;

  protected countdownInterval: ReturnType<typeof setInterval> | null = null;
  protected roundTimerInterval: ReturnType<typeof setInterval> | null = null;
  protected dialog = inject(MatDialog);
  protected ngZone = inject(NgZone);

  constructor(
    protected route: ActivatedRoute,
    protected router: Router,
    protected websocketService: WebsocketService,
    protected gameService: GameService
  ) {}

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
      next: (status) => {
        this.isLoading = false;

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
        this.isLoading = false;
        this.router.navigate(['/error', 'not-found']);
      }
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
      this.websocketService.subscribe('/topic/game/' + this.gameId, (game: Game) => {
        this.handleGameUpdate(game);
      });

      this.websocketService.subscribe('/topic/game/' + this.gameId + '/terminated', () => {
        this.clearCountdown();
        this.clearRoundTimer();
        this.router.navigate(['/error', 'host-left']);
      });

      // Subscribe to player removal notifications
      this.websocketService.subscribe('/topic/game/' + this.gameId + '/removed/' + this.playerName, () => {
        this.clearCountdown();
        this.clearRoundTimer();
        this.router.navigate(['/error', 'removed']);
      });

      // If no initial game data (e.g., coming from Play Again), fetch the game details
      if (!this.game) {
        this.gameService.getGameDetails(this.gameId).subscribe({
          next: (game) => {
            this.game = game;
          },
          error: () => {
            // Will rely on WebSocket update
          }
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
    const isNewRoundOrTurn = (!wasPlayingRound && isPlayingRound) ||
      (isPlayingRound && (
        game.currentRoundNumber !== previousRoundNumber ||
        (currentTurn !== 0 && currentTurn !== previousTurn)
      ));
    
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
      this.websocketService.unsubscribe('/topic/game/' + this.gameId + '/removed/' + this.playerName);
    }
  }

  protected startCountdown(seconds: number): void {
    this.clearCountdown();
    this.countdown = seconds;
    this.ngZone.runOutsideAngular(() => {
      this.countdownInterval = setInterval(() => {
        this.ngZone.run(() => {
          this.countdown--;
          if (this.countdown <= 0) {
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
    this.countdown = 0;
  }

  protected startRoundTimer(): void {
    this.clearRoundTimer();
    // Use game's configured round duration from gameConfig
    const duration = getRoundDuration(this.game);
    this.roundTimer = duration;
    this.ngZone.runOutsideAngular(() => {
      this.roundTimerInterval = setInterval(() => {
        this.ngZone.run(() => {
          this.roundTimer--;
          if (this.roundTimer <= 0) {
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
    this.roundTimer = 0;
  }

  startGame(): void {
    if (this.gameId && this.isHost) {
      this.websocketService.sendMessage('/app/start', {
        sender: this.playerName,
        content: this.gameId
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
        content: this.gameId
      });
    }
    this.goHome();
  }

  exitGame(): void {
    if (this.gameId && this.isHost) {
      // Host leaving will terminate the game for all players via the /leave endpoint
      this.websocketService.sendMessage('/app/leave', {
        sender: this.playerName,
        content: this.gameId
      });
    }
    this.goHome();
  }

  copyGameCode(): void {
    if (this.gameId) {
      navigator.clipboard.writeText(this.gameId);
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
      icon: 'person_remove'
    };

    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: dialogData
    });

    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (confirmed && this.gameId) {
        this.websocketService.sendMessage('/app/removePlayer', {
          sender: this.playerName,
          content: `${this.gameId}:${playerToRemove}`
        });
      }
    });
  }

  isRoundWinner(playerName: string): boolean {
    return this.game?.currentRoundInfo?.winner === playerName;
  }
}
