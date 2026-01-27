import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { WebsocketService, GameService } from '../../../services';
import { WordScrambleRoundInfo } from '../../../models';
import { GameLayoutComponent } from '../../game-layout/game-layout.component';
import { BaseGameComponent } from '../base-game.component';
import { isWordScrambleRoundInfo } from '../../../utils/game-config.utils';

@Component({
  selector: 'app-word-scramble-game',
  templateUrl: './word-scramble-game.component.html',
  styleUrl: './word-scramble-game.component.css',
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
export class WordScrambleGameComponent extends BaseGameComponent {
  wordGuessValue = '';
  readonly gameIcon = 'text_rotation_none';

  constructor(
    route: ActivatedRoute,
    router: Router,
    websocketService: WebsocketService,
    gameService: GameService
  ) {
    super(route, router, websocketService, gameService);
  }

  // Typed getter for round info
  get roundInfo(): WordScrambleRoundInfo | null {
    const info = this.game?.currentRoundInfo;
    return isWordScrambleRoundInfo(info) ? info : null;
  }

  protected onNewRound(): void {
    this.wordGuessValue = '';
  }

  protected checkIfPlayerAnswered(): void {
    if (this.roundInfo?.playerWordGuesses[this.playerName] !== undefined) {
      this.hasGuessed = true;
    }
  }

  submitWordGuess(): void {
    if (!this.gameId || !this.wordGuessValue.trim() || this.hasGuessed) return;

    this.websocketService.sendMessage('/app/wordGuess', {
      gameId: this.gameId,
      playerName: this.playerName,
      guess: this.wordGuessValue.trim()
    });
    this.hasGuessed = true;
  }

  getPlayerWordGuess(playerName: string): string | undefined {
    return this.roundInfo?.playerWordGuesses[playerName];
  }

  hasPlayerAnswered(playerName: string): boolean {
    return this.getPlayerWordGuess(playerName) !== undefined;
  }

  isCorrectAnswer(playerName: string): boolean {
    const guess = this.getPlayerWordGuess(playerName);
    const originalWord = this.roundInfo?.originalWord;
    return guess?.toUpperCase() === originalWord?.toUpperCase();
  }

  isIncorrectAnswer(playerName: string): boolean {
    const guess = this.getPlayerWordGuess(playerName);
    if (!guess) return false;
    return !this.isCorrectAnswer(playerName);
  }
}
