import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatOptionModule } from '@angular/material/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ActivatedRoute, Router } from '@angular/router';
import { Game, GameType } from '../../models';
import { GameService, WebsocketService } from '../../services';
import { AvatarDialogComponent } from '../avatar-dialog/avatar-dialog.component';

export interface GameConfig {
  totalRounds: number;
  wordLength: number;
  roundDuration: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  // Number Guess settings
  minRange: number;
  maxRange: number;
  // Reaction Showdown settings
  includeFakeOuts: boolean;
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
    ReactiveFormsModule,
    MatCardModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatOptionModule,
    MatDialogModule,
    MatSlideToggleModule,
  ],
  templateUrl: './game-setup.component.html',
  styleUrl: './game-setup.component.css',
})
export class GameSetupComponent implements OnInit {
  mode = signal<'host' | 'join'>('host');
  selectedGameType = signal<GameType | null>(null);
  gameCode = signal('');

  playerNameControl = new FormControl('');
  selectedAvatarId = signal('');
  selectedAvatarEmoji = signal('👨');
  errorMessage = signal('');
  isLoading = signal(false);

  configForm = new FormGroup({
    totalRounds: new FormControl(3),
    wordLength: new FormControl(7),
    roundDuration: new FormControl(30),
    difficulty: new FormControl<'EASY' | 'MEDIUM' | 'HARD'>('MEDIUM'),
    minRange: new FormControl(1),
    maxRange: new FormControl(100),
    includeFakeOuts: new FormControl(true),
  });

  private dialog = inject(MatDialog);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private websocketService = inject(WebsocketService);
  private gameService = inject(GameService);

  get isSubmitDisabled(): boolean {
    return !this.playerNameControl.value?.trim() || !this.selectedAvatarId() || this.isLoading();
  }

  ngOnInit(): void {
    // Determine mode based on route
    const url = this.router.url;

    if (url.includes('/setup/host/')) {
      this.mode.set('host');
      const gameTypeId = this.route.snapshot.paramMap.get('gameTypeId');
      if (gameTypeId) {
        this.loadGameType(gameTypeId);
      }
    } else if (url.includes('/setup/join/')) {
      this.mode.set('join');
      this.gameCode.set(this.route.snapshot.paramMap.get('gameCode') || '');
    }

    // Connect WebSocket
    this.websocketService.connect();

    // Set default avatar
    this.selectedAvatarId.set('m1');
    this.selectedAvatarEmoji.set('👨');
  }

  loadGameType(gameTypeId: string): void {
    this.gameService.getGameTypes().subscribe({
      next: (types: GameType[]) => {
        this.selectedGameType.set(types.find(t => t.id === gameTypeId) || null);
        if (!this.selectedGameType()) {
          this.router.navigate(['/']);
        }
      },
      error: () => {
        this.errorMessage.set('Failed to load game type');
      },
    });
  }

  openAvatarDialog(): void {
    const dialogRef = this.dialog.open(AvatarDialogComponent, {
      data: { selectedAvatar: this.selectedAvatarId() },
      panelClass: 'avatar-dialog-panel',
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.selectedAvatarId.set(result.avatarId);
        this.selectedAvatarEmoji.set(result.emoji);
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/']);
  }

  onSubmit(): void {
    if (!this.playerNameControl.value?.trim() || !this.selectedAvatarId()) return;

    this.isLoading.set(true);
    this.errorMessage.set('');

    if (this.mode() === 'host') {
      this.createGame();
    } else {
      this.joinGame();
    }
  }

  private createGame(): void {
    if (!this.selectedGameType()) return;

    const hostName = this.playerNameControl.value!.trim();
    const gameTypeId = this.selectedGameType()!.id;
    const config = this.configForm.value;

    this.websocketService.subscribe('/topic/created/' + hostName, (message: unknown) => {
      const game = message as Game;
      sessionStorage.setItem('playerName', hostName);
      sessionStorage.setItem('isHost', 'true');
      sessionStorage.setItem('playerAvatar', this.selectedAvatarEmoji());
      sessionStorage.setItem('initialGame', JSON.stringify(game));
      this.websocketService.unsubscribe('/topic/created/' + hostName);
      this.websocketService.unsubscribe('/topic/error/' + hostName);
      this.router.navigate(['/game', game.gameTypeId, game.gameId]);
    });

    this.websocketService.subscribe('/topic/error/' + hostName, (message: unknown) => {
      const error = message as { error?: string };
      this.isLoading.set(false);
      this.errorMessage.set(error.error || 'Failed to create game');
    });

    this.websocketService.sendMessage('/app/create', {
      hostName,
      gameTypeId,
      avatar: this.selectedAvatarEmoji(),
      totalRounds: config.totalRounds,
      wordLength: config.wordLength,
      roundDuration: config.roundDuration,
      difficulty: config.difficulty,
      minRange: config.minRange,
      maxRange: config.maxRange,
      includeFakeOuts: config.includeFakeOuts,
    });
  }

  private joinGame(): void {
    const playerName = this.playerNameControl.value!.trim();
    const gameId = this.gameCode().toUpperCase();

    this.websocketService.subscribe('/topic/game/' + gameId, (message: unknown) => {
      const game = message as Game;
      sessionStorage.setItem('playerName', playerName);
      sessionStorage.setItem('isHost', 'false');
      sessionStorage.setItem('playerAvatar', this.selectedAvatarEmoji());
      sessionStorage.setItem('initialGame', JSON.stringify(game));
      this.websocketService.unsubscribe('/topic/game/' + gameId);
      this.websocketService.unsubscribe('/topic/error/' + playerName);
      this.router.navigate(['/game', game.gameTypeId, gameId]);
    });

    this.websocketService.subscribe('/topic/error/' + playerName, (message: unknown) => {
      const error = message as { error?: string };
      this.isLoading.set(false);
      this.errorMessage.set(error.error || 'Failed to join game');
    });

    this.websocketService.sendMessage('/app/joinWithProfile', {
      playerName,
      gameCode: gameId,
      avatar: this.selectedAvatarEmoji(),
    });
  }
}
