import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';
import { Game } from '../../models';
import { OrderByPipe } from '../../pipes';
import { GameService, WebsocketService } from '../../services';
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
        OrderByPipe,
        NavbarComponent
    ]
})
export class ResultsComponent implements OnInit, OnDestroy {
    game: Game | undefined;
    gameId: string | null = null;
    playerName = '';
    playerAvatar = '👤';
    isHost = false;
    isLoading = true;

    private route = inject(ActivatedRoute);
    private router = inject(Router);
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
            this.game = navState.game;
            this.isLoading = false;
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
            next: (status) => {
                this.isLoading = false;
                if (!status.exists) {
                    this.router.navigate(['/error', 'game-ended']);
                    return;
                }
                this.subscribeToGameUpdates();
            },
            error: () => {
                this.isLoading = false;
                this.router.navigate(['/error', 'game-ended']);
            }
        });
    }

    private subscribeToGameUpdates(): void {
        if (!this.gameId) return;

        this.websocketService.connect();
        this.websocketService.subscribe('/topic/game/' + this.gameId, (message: unknown) => {
            this.game = message as Game;

            // If host started a new game (play again), redirect to game
            if (this.game.gameState === 'LOBBY') {
                this.router.navigate(['/game', this.game.gameTypeId, this.gameId]);
            }
            // If game is starting, redirect to the game
            if (this.game.gameState === 'STARTING') {
                this.router.navigate(['/game', this.game.gameTypeId, this.gameId]);
            }
        });
    }

    ngOnDestroy(): void {
        if (this.gameId) {
            this.websocketService.unsubscribe('/topic/game/' + this.gameId);
        }
    }

    getWinner(): string {
        if (!this.game || this.game.players.length === 0) return '';
        const sorted = [...this.game.players].sort((a, b) => b.score - a.score);
        return sorted[0].name;
    }

    getWinnerScore(): number {
        if (!this.game || this.game.players.length === 0) return 0;
        const sorted = [...this.game.players].sort((a, b) => b.score - a.score);
        return sorted[0].score;
    }

    playAgain(): void {
        if (this.gameId && this.isHost) {
            this.websocketService.sendMessage('/app/playAgain', {
                sender: this.playerName,
                content: this.gameId
            });
        }
    }

    getRankIcon(index: number): string {
        switch (index) {
            case 0: return '🥇';
            case 1: return '🥈';
            case 2: return '🥉';
            default: return '';
        }
    }

    goHome(): void {
        sessionStorage.clear();
        this.router.navigate(['/']);
    }
}
