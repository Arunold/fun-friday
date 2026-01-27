import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { WebsocketService, GameService } from '../../../services';
import { NumberGuessRoundInfo } from '../../../models';
import { GameLayoutComponent } from '../../game-layout/game-layout.component';
import { BaseGameComponent } from '../base-game.component';
import { isNumberGuessRoundInfo } from '../../../utils/game-config.utils';

@Component({
  selector: 'app-number-guess-game',
  templateUrl: './number-guess-game.component.html',
  styleUrl: './number-guess-game.component.css',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatInputModule,
    MatIconModule,
    GameLayoutComponent
  ]
})
export class NumberGuessGameComponent extends BaseGameComponent {
  guessValue: number | null = null;
  readonly gameIcon = 'casino';

  constructor(
    route: ActivatedRoute,
    router: Router,
    websocketService: WebsocketService,
    gameService: GameService
  ) {
    super(route, router, websocketService, gameService);
  }

  // Typed getter for round info
  get roundInfo(): NumberGuessRoundInfo | null {
    const info = this.game?.currentRoundInfo;
    return isNumberGuessRoundInfo(info) ? info : null;
  }

  protected onNewRound(): void {
    this.guessValue = null;
  }

  protected checkIfPlayerAnswered(): void {
    if (this.roundInfo?.playerGuesses[this.playerName] !== undefined) {
      this.hasGuessed = true;
    }
  }

  submitGuess(): void {
    if (!this.gameId || this.guessValue === null || this.hasGuessed) return;

    this.websocketService.sendMessage('/app/guess', {
      gameId: this.gameId,
      playerName: this.playerName,
      guess: this.guessValue
    });
    this.hasGuessed = true;
  }

  getPlayerGuess(playerName: string): number | undefined {
    return this.roundInfo?.playerGuesses[playerName];
  }

  hasPlayerAnswered(playerName: string): boolean {
    return this.getPlayerGuess(playerName) !== undefined;
  }
}
