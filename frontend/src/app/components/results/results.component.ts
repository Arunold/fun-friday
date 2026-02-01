import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';
import { Game } from '../../models';
import { OrderByPipe } from '../../pipes';
import { GameService, WebsocketService } from '../../services';
import {
  ChangeGameDialogComponent,
  ChangeGameDialogResult,
} from '../change-game-dialog/change-game-dialog.component';
import { NavbarComponent } from '../navbar/navbar.component';

@Component({
  selector: 'app-results',
  templateUrl: './results.component.html',
  styleUrls: ['./results.component.css'],
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    OrderByPipe,
    NavbarComponent,
  ],
})
export class ResultsComponent implements OnInit, OnDestroy {
  game = signal<Game | undefined>(undefined);
  gameId: string | null = null;
  playerName = '';
  playerAvatar = '👤';
  isHost = false;
  isLoading = signal(true);

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private dialog = inject(MatDialog);
  private websocketService = inject(WebsocketService);
  private gameService = inject(GameService);

  ngOnInit(): void {
    this.gameId = this.route.snapshot.paramMap.get('id');
    this.playerName = sessionStorage.getItem('playerName') || '';
    this.playerAvatar = sessionStorage.getItem('playerAvatar') || '👤';
    this.isHost = sessionStorage.getItem('isHost') === 'true';

    // Try to get game data from navigation state first
    const navState = history.state;
    if (navState?.game) {
      this.game.set(navState.game);
      this.isLoading.set(false);
      this.subscribeToGameUpdates();
      return;
    }

    // Otherwise fetch from server
    if (this.gameId) {
      this.loadGameData();
    } else {
      this.router.navigate(['/']);
    }
  }

  private loadGameData(): void {
    if (!this.gameId) return;

    this.gameService.getGameStatus(this.gameId).subscribe({
      next: status => {
        this.isLoading.set(false);
        if (!status.exists) {
          this.router.navigate(['/error', 'game-ended']);
          return;
        }
        this.subscribeToGameUpdates();
      },
      error: () => {
        this.isLoading.set(false);
        this.router.navigate(['/error', 'game-ended']);
      },
    });
  }

  private subscribeToGameUpdates(): void {
    if (!this.gameId) return;

    this.websocketService.connect();
    this.websocketService.subscribe('/topic/game/' + this.gameId, (message: unknown) => {
      const game = message as Game;
      this.game.set(game);

      // If host started a new game (play again), redirect to game
      if (game.gameState === 'LOBBY') {
        this.router.navigate(['/game', game.gameTypeId, this.gameId]);
      }
      // If game is starting, redirect to the game
      if (game.gameState === 'STARTING') {
        this.router.navigate(['/game', game.gameTypeId, this.gameId]);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.gameId) {
      this.websocketService.unsubscribe('/topic/game/' + this.gameId);
    }
  }

  getWinner(): string {
    const game = this.game();
    if (!game || game.players.length === 0) return '';
    const sorted = [...game.players].sort((a, b) => b.score - a.score);
    return sorted[0].name;
  }

  getWinnerScore(): number {
    const game = this.game();
    if (!game || game.players.length === 0) return 0;
    const sorted = [...game.players].sort((a, b) => b.score - a.score);
    return sorted[0].score;
  }

  playAgain(): void {
    if (this.gameId && this.isHost) {
      this.websocketService.sendMessage('/app/playAgain', {
        sender: this.playerName,
        content: this.gameId,
      });
    }
  }

  changeGame(): void {
    const game = this.game();
    if (!this.gameId || !this.isHost || !game) return;

    const dialogRef = this.dialog.open(ChangeGameDialogComponent, {
      data: { currentGameTypeId: game.gameTypeId },
      panelClass: 'change-game-dialog-panel',
    });

    dialogRef.afterClosed().subscribe((result: ChangeGameDialogResult) => {
      if (result && this.gameId) {
        this.websocketService.sendMessage('/app/changeGame', {
          sender: this.playerName,
          content: JSON.stringify({
            gameId: this.gameId,
            gameTypeId: result.gameTypeId,
            totalRounds: result.totalRounds,
            roundDuration: result.roundDuration,
            wordLength: result.wordLength,
            difficulty: result.difficulty,
            minRange: result.minRange,
            maxRange: result.maxRange,
            includeFakeOuts: result.includeFakeOuts,
          }),
        });
      }
    });
  }

  getRankIcon(index: number): string {
    switch (index) {
      case 0:
        return '🥇';
      case 1:
        return '🥈';
      case 2:
        return '🥉';
      default:
        return '';
    }
  }

  goHome(): void {
    sessionStorage.clear();
    this.router.navigate(['/']);
  }
}
