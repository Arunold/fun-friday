import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
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
        MatDialogModule
    ],
    template: `
    <div class="setup-page">
      <div class="setup-card">
        <button mat-button class="back-btn" (click)="goBack()">
          <mat-icon>arrow_back</mat-icon> Back
        </button>

        <!-- Badge showing what we're doing -->
        @if (mode === 'host' && selectedGameType) {
          <div class="badge host-badge">
            <span class="game-icon">{{ selectedGameType.icon }}</span>
            <span>{{ selectedGameType.name }}</span>
          </div>
        }
        @if (mode === 'join') {
          <div class="badge join-badge">
            <mat-icon>vpn_key</mat-icon>
            <span>Joining: {{ gameCode }}</span>
          </div>
        }

        <h2>{{ mode === 'host' ? 'Setup Your Game' : 'Setup Your Profile' }}</h2>

        <!-- Avatar Selection (clickable) -->
        <div class="avatar-selector" (click)="openAvatarDialog()">
          <div class="avatar-display">
            <span class="avatar-emoji">{{ selectedAvatarEmoji }}</span>
          </div>
          <span class="avatar-hint">Click to change avatar</span>
        </div>

        <!-- Player Name -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Your Name</mat-label>
          <input matInput placeholder="Enter your name" [(ngModel)]="playerName" (keyup.enter)="onSubmit()">
        </mat-form-field>

        <!-- Game Config (Host only) -->
        @if (mode === 'host') {
          <div class="config-section">
            <h3>Game Settings</h3>
            <div class="config-row">
              <mat-form-field appearance="outline">
                <mat-label>Number of Rounds</mat-label>
                <mat-select [(ngModel)]="gameConfig.totalRounds">
                  <mat-option [value]="2">2 Rounds</mat-option>
                  <mat-option [value]="3">3 Rounds</mat-option>
                  <mat-option [value]="5">5 Rounds</mat-option>
                  <mat-option [value]="7">7 Rounds</mat-option>
                  <mat-option [value]="10">10 Rounds</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Round Timer</mat-label>
                <mat-select [(ngModel)]="gameConfig.roundDuration">
                  <mat-option [value]="15">15 seconds</mat-option>
                  <mat-option [value]="30">30 seconds</mat-option>
                  <mat-option [value]="45">45 seconds</mat-option>
                  <mat-option [value]="60">60 seconds</mat-option>
                  <mat-option [value]="90">90 seconds</mat-option>
                  <mat-option [value]="120">120 seconds</mat-option>
                  <mat-option [value]="150">150 seconds</mat-option>
                </mat-select>
              </mat-form-field>

              @if (selectedGameType?.id === 'word-scramble') {
                <mat-form-field appearance="outline">
                  <mat-label>Word Length</mat-label>
                  <mat-select [(ngModel)]="gameConfig.wordLength">
                    <mat-option [value]="3">3 Letters</mat-option>
                    <mat-option [value]="4">4 Letters</mat-option>
                    <mat-option [value]="5">5 Letters</mat-option>
                    <mat-option [value]="6">6 Letters</mat-option>
                    <mat-option [value]="7">7 Letters</mat-option>
                    <mat-option [value]="8">8 Letters</mat-option>
                    <mat-option [value]="9">9 Letters</mat-option>
                    <mat-option [value]="10">10 Letters</mat-option>
                  </mat-select>
                </mat-form-field>
              }
            </div>
          </div>
        }

        @if (errorMessage) {
          <div class="error-message">
            <mat-icon>error</mat-icon>
            {{ errorMessage }}
          </div>
        }

        <button mat-raised-button [color]="mode === 'host' ? 'primary' : 'accent'" 
                class="submit-btn"
                [disabled]="!playerName.trim() || !selectedAvatarId || isLoading"
                (click)="onSubmit()">
          <ng-container>
            @if (isLoading) {
              <mat-icon class="spinning">refresh</mat-icon>
              {{ mode === 'host' ? 'Creating...' : 'Joining...' }}
            } @else {
              <mat-icon>{{ mode === 'host' ? 'add_circle' : 'login' }}</mat-icon>
              {{ mode === 'host' ? 'Create Game' : 'Join Game' }}
            }
          </ng-container>
        </button>
      </div>
    </div>
  `,
    styles: [`
    .setup-page {
      min-height: 100vh;
      background: var(--spotify-black);
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 20px;
    }

    .setup-card {
      background: var(--spotify-dark-gray);
      border-radius: 16px;
      padding: 40px;
      max-width: 500px;
      width: 100%;
      position: relative;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
      text-align: center;
    }

    .setup-card h2 {
      color: var(--spotify-white);
      font-size: 1.8rem;
      margin-bottom: 30px;
    }

    .back-btn {
      position: absolute;
      top: 20px;
      left: 20px;
      color: var(--spotify-text-gray) !important;
    }

    .back-btn:hover {
      color: var(--spotify-white) !important;
    }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      border-radius: 30px;
      padding: 10px 20px;
      margin-bottom: 20px;
      font-weight: 600;
    }

    .host-badge {
      background: rgba(29, 185, 84, 0.15);
      border: 1px solid var(--spotify-green);
      color: var(--spotify-green);
    }

    .host-badge .game-icon {
      font-size: 1.5rem;
    }

    .join-badge {
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid var(--spotify-white);
      color: var(--spotify-white);
    }

    .join-badge mat-icon {
      font-size: 1.2rem;
      width: 20px;
      height: 20px;
    }

    /* Avatar Selector */
    .avatar-selector {
      cursor: pointer;
      margin-bottom: 25px;
      transition: transform 0.2s ease;
    }

    .avatar-selector:hover {
      transform: scale(1.05);
    }

    .avatar-selector:hover .avatar-display {
      border-color: var(--spotify-green);
      box-shadow: 0 0 30px rgba(29, 185, 84, 0.4);
    }

    .avatar-display {
      width: 120px;
      height: 120px;
      border-radius: 50%;
      background: var(--spotify-gray);
      border: 4px solid var(--spotify-light-gray);
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 10px;
      transition: all 0.2s ease;
    }

    .avatar-emoji {
      font-size: 4rem;
    }

    .avatar-hint {
      color: var(--spotify-text-gray);
      font-size: 0.85rem;
    }

    .avatar-selector:hover .avatar-hint {
      color: var(--spotify-green);
    }

    .full-width {
      width: 100%;
    }

    .config-section {
      margin: 25px 0;
      padding: 20px;
      background: var(--spotify-gray);
      border-radius: 12px;
      text-align: left;
    }

    .config-section h3 {
      color: var(--spotify-white);
      margin-bottom: 15px;
      font-size: 1rem;
    }

    .config-row {
      display: flex;
      gap: 15px;
      flex-wrap: wrap;
    }

    .config-row mat-form-field {
      flex: 1;
      min-width: 150px;
    }

    .error-message {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      color: #f44336;
      background: rgba(244, 67, 54, 0.1);
      padding: 12px;
      border-radius: 8px;
      margin-bottom: 15px;
    }

    .submit-btn {
      width: 100%;
      margin-top: 20px;
      padding: 16px 32px !important;
      font-size: 1rem !important;
    }

    .spinning {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
  `]
})
export class GameSetupComponent implements OnInit {
    mode: 'host' | 'join' = 'host';
    selectedGameType: GameType | null = null;
    gameCode: string = '';

    playerName: string = '';
    selectedAvatarId: string = '';
    selectedAvatarEmoji: string = '👨';
    errorMessage: string = '';
    isLoading: boolean = false;

    gameConfig: GameConfig = {
        totalRounds: 3,
        wordLength: 7,
        roundDuration: 30
    };

    constructor(
        private dialog: MatDialog,
        private router: Router,
        private route: ActivatedRoute,
        private websocketService: WebsocketService,
        private gameService: GameService
    ) { }

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
            }
        });
    }

    openAvatarDialog(): void {
        const dialogRef = this.dialog.open(AvatarDialogComponent, {
            data: { selectedAvatar: this.selectedAvatarId },
            panelClass: 'avatar-dialog-panel'
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

        this.websocketService.subscribe('/topic/created/' + hostName, (game: Game) => {
            sessionStorage.setItem('playerName', hostName);
            sessionStorage.setItem('isHost', 'true');
            sessionStorage.setItem('playerAvatar', this.selectedAvatarEmoji);
            sessionStorage.setItem('initialGame', JSON.stringify(game));
            this.websocketService.unsubscribe('/topic/created/' + hostName);
            this.websocketService.unsubscribe('/topic/error/' + hostName);
            this.router.navigate(['/game', game.gameTypeId, game.gameId]);
        });

        this.websocketService.subscribe('/topic/error/' + hostName, (error: any) => {
            this.isLoading = false;
            this.errorMessage = error.error || 'Failed to create game';
        });

        this.websocketService.sendMessage('/app/create', {
            hostName,
            gameTypeId,
            avatar: this.selectedAvatarEmoji,
            totalRounds: this.gameConfig.totalRounds,
            wordLength: this.gameConfig.wordLength,
            roundDuration: this.gameConfig.roundDuration
        });
    }

    private joinGame(): void {
        const playerName = this.playerName.trim();
        const gameId = this.gameCode.toUpperCase();

        this.websocketService.subscribe('/topic/game/' + gameId, (game: Game) => {
            sessionStorage.setItem('playerName', playerName);
            sessionStorage.setItem('isHost', 'false');
            sessionStorage.setItem('playerAvatar', this.selectedAvatarEmoji);
            sessionStorage.setItem('initialGame', JSON.stringify(game));
            this.websocketService.unsubscribe('/topic/game/' + gameId);
            this.websocketService.unsubscribe('/topic/error/' + playerName);
            this.router.navigate(['/game', game.gameTypeId, gameId]);
        });

        this.websocketService.subscribe('/topic/error/' + playerName, (error: any) => {
            this.isLoading = false;
            this.errorMessage = error.error || 'Failed to join game';
        });

        this.websocketService.sendMessage('/app/joinWithProfile', {
            playerName,
            gameCode: gameId,
            avatar: this.selectedAvatarEmoji
        });
    }
}
