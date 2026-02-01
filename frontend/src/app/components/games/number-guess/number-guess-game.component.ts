import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
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
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatInputModule,
    MatIconModule,
    GameLayoutComponent,
  ],
})
export class NumberGuessGameComponent extends BaseGameComponent {
  guessControl = new FormControl<number | null>(null);
  readonly gameIcon = 'casino';

  // Typed getter for round info
  get roundInfo(): NumberGuessRoundInfo | null {
    const info = this.game?.currentRoundInfo;
    return isNumberGuessRoundInfo(info) ? info : null;
  }

  protected onNewRound(): void {
    this.guessControl.setValue(null);
  }

  protected checkIfPlayerAnswered(): void {
    if (this.roundInfo?.playerGuesses[this.playerName] !== undefined) {
      this.hasGuessed = true;
    }
  }

  submitGuess(): void {
    if (!this.gameId || this.guessControl.value === null || this.hasGuessed) return;

    this.websocketService.sendMessage('/app/guess', {
      gameId: this.gameId,
      playerName: this.playerName,
      guess: this.guessControl.value,
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
