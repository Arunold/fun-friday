import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatOptionModule } from '@angular/material/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import { Game, GameType } from '../../models';
import { GameService, WebsocketService } from '../../services';
import { AvatarDialogComponent } from '../avatar-dialog/avatar-dialog.component';

export interface GameConfig {
  totalRounds: number;
  wordLength: number;
  roundDuration: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
}

export interface SetupResult {
  playerName: string;
  avatar: string;
  gameConfig?: GameConfig;
}

@Component({
  selector: 'app-game-setup',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatOptionModule,
    MatDialogModule,
  ],
  templateUrl: './game-setup.component.html',
  styleUrl: './game-setup.component.css',
})
export class GameSetupComponent implements OnInit {
  mode: 'host' | 'join' = 'host';
  selectedGameType: GameType | null = null;
  gameCode = '';

  playerName = '';
  selectedAvatarId = '';
  selectedAvatarEmoji = '👨';
  errorMessage = '';
  isLoading = false;

  gameConfig: GameConfig = {
    totalRounds: 3,
    wordLength: 7,
    roundDuration: 30,
    difficulty: 'MEDIUM',
  };

  private dialog = inject(MatDialog);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private websocketService = inject(WebsocketService);
  private gameService = inject(GameService);

  ngOnInit(): void {
    // Determine mode based on route
    const url = this.router.url;

    if (url.includes('/setup/host/')) {
      this.mode = 'host';
      const gameTypeId = this.route.snapshot.paramMap.get('gameTypeId');
      if (gameTypeId) {
        this.loadGameType(gameTypeId);
      }
    } else if (url.includes('/setup/join/')) {
      this.mode = 'join';
      this.gameCode = this.route.snapshot.paramMap.get('gameCode') || '';
    }

    // Connect WebSocket
    this.websocketService.connect();

    // Set default avatar
    this.selectedAvatarId = 'm1';
    this.selectedAvatarEmoji = '👨';
  }

  loadGameType(gameTypeId: string): void {
    this.gameService.getGameTypes().subscribe({
      next: (types: GameType[]) => {
        this.selectedGameType = types.find(t => t.id === gameTypeId) || null;
        if (!this.selectedGameType) {
          this.router.navigate(['/']);
        }
      },
      error: () => {
        this.errorMessage = 'Failed to load game type';
      },
    });
  }

  openAvatarDialog(): void {
    const dialogRef = this.dialog.open(AvatarDialogComponent, {
      data: { selectedAvatar: this.selectedAvatarId },
      panelClass: 'avatar-dialog-panel',
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.selectedAvatarId = result.avatarId;
        this.selectedAvatarEmoji = result.emoji;
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/']);
  }

  onSubmit(): void {
    if (!this.playerName.trim() || !this.selectedAvatarId) return;

    this.isLoading = true;
    this.errorMessage = '';

    if (this.mode === 'host') {
      this.createGame();
    } else {
      this.joinGame();
    }
  }

  private createGame(): void {
    if (!this.selectedGameType) return;

    const hostName = this.playerName.trim();
    const gameTypeId = this.selectedGameType.id;

    this.websocketService.subscribe('/topic/created/' + hostName, (message: unknown) => {
      const game = message as Game;
      sessionStorage.setItem('playerName', hostName);
      sessionStorage.setItem('isHost', 'true');
      sessionStorage.setItem('playerAvatar', this.selectedAvatarEmoji);
      sessionStorage.setItem('initialGame', JSON.stringify(game));
      this.websocketService.unsubscribe('/topic/created/' + hostName);
      this.websocketService.unsubscribe('/topic/error/' + hostName);
      this.router.navigate(['/game', game.gameTypeId, game.gameId]);
    });

    this.websocketService.subscribe('/topic/error/' + hostName, (message: unknown) => {
      const error = message as { error?: string };
      this.isLoading = false;
      this.errorMessage = error.error || 'Failed to create game';
    });

    this.websocketService.sendMessage('/app/create', {
      hostName,
      gameTypeId,
      avatar: this.selectedAvatarEmoji,
      totalRounds: this.gameConfig.totalRounds,
      wordLength: this.gameConfig.wordLength,
      roundDuration: this.gameConfig.roundDuration,
      difficulty: this.gameConfig.difficulty,
    });
  }

  private joinGame(): void {
    const playerName = this.playerName.trim();
    const gameId = this.gameCode.toUpperCase();

    this.websocketService.subscribe('/topic/game/' + gameId, (message: unknown) => {
      const game = message as Game;
      sessionStorage.setItem('playerName', playerName);
      sessionStorage.setItem('isHost', 'false');
      sessionStorage.setItem('playerAvatar', this.selectedAvatarEmoji);
      sessionStorage.setItem('initialGame', JSON.stringify(game));
      this.websocketService.unsubscribe('/topic/game/' + gameId);
      this.websocketService.unsubscribe('/topic/error/' + playerName);
      this.router.navigate(['/game', game.gameTypeId, gameId]);
    });

    this.websocketService.subscribe('/topic/error/' + playerName, (message: unknown) => {
      const error = message as { error?: string };
      this.isLoading = false;
      this.errorMessage = error.error || 'Failed to join game';
    });

    this.websocketService.sendMessage('/app/joinWithProfile', {
      playerName,
      gameCode: gameId,
      avatar: this.selectedAvatarEmoji,
    });
  }
}
