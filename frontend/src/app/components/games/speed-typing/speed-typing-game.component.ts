import { Component, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { WebsocketService, GameService } from '../../../services';
import { SpeedTypingRoundInfo, PlayerProgressInfo } from '../../../models';
import { GameLayoutComponent } from '../../game-layout/game-layout.component';
import { BaseGameComponent } from '../base-game.component';
import { isSpeedTypingRoundInfo } from '../../../utils/game-config.utils';

@Component({
  selector: 'app-speed-typing-game',
  templateUrl: './speed-typing-game.component.html',
  styleUrl: './speed-typing-game.component.css',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatInputModule,
    MatIconModule,
    MatProgressBarModule,
    GameLayoutComponent
  ]
})
export class SpeedTypingGameComponent extends BaseGameComponent implements AfterViewInit {
  typedText = '';
  readonly gameIcon = 'keyboard';

  @ViewChild('typingInput') typingInput!: ElementRef<HTMLInputElement>;

  constructor(
    route: ActivatedRoute,
    router: Router,
    websocketService: WebsocketService,
    gameService: GameService
  ) {
    super(route, router, websocketService, gameService);
  }

  ngAfterViewInit(): void {
    // Focus will be set in onNewRound when game state changes
  }

  // Typed getter for round info
  get roundInfo(): SpeedTypingRoundInfo | null {
    const info = this.game?.currentRoundInfo;
    return isSpeedTypingRoundInfo(info) ? info : null;
  }

  get targetText(): string {
    return this.roundInfo?.targetText ?? '';
  }

  get myProgress(): PlayerProgressInfo | null {
    return this.roundInfo?.playerProgress?.[this.playerName] ?? null;
  }

  get isFinished(): boolean {
    return this.myProgress?.finished ?? false;
  }

  protected onNewRound(): void {
    this.typedText = '';
    this.hasGuessed = false;
    // Focus the typing input after a short delay to ensure DOM is ready
    setTimeout(() => {
      this.typingInput?.nativeElement?.focus();
    }, 100);
  }

  protected checkIfPlayerAnswered(): void {
    if (this.myProgress?.finished) {
      this.hasGuessed = true;
    }
  }

  onTyping(): void {
    if (!this.gameId || this.isFinished) return;

    this.websocketService.sendMessage('/app/typingProgress', {
      gameId: this.gameId,
      playerName: this.playerName,
      typedText: this.typedText
    });
  }

  getPlayerProgress(playerName: string): PlayerProgressInfo | null {
    return this.roundInfo?.playerProgress?.[playerName] ?? null;
  }

  getProgressPercentage(playerName: string): number {
    return this.getPlayerProgress(playerName)?.percentage ?? 0;
  }

  getProgressAccuracy(playerName: string): number {
    return this.getPlayerProgress(playerName)?.accuracy ?? 100;
  }

  hasPlayerFinished(playerName: string): boolean {
    return this.getPlayerProgress(playerName)?.finished ?? false;
  }

  getFinishPosition(playerName: string): number {
    const rankings = this.roundInfo?.rankings ?? [];
    const index = rankings.indexOf(playerName);
    return index >= 0 ? index + 1 : 0;
  }

  getPositionSuffix(position: number): string {
    if (position === 1) return 'st';
    if (position === 2) return 'nd';
    if (position === 3) return 'rd';
    return 'th';
  }

  formatFinishTime(playerName: string): string {
    const progress = this.getPlayerProgress(playerName);
    if (!progress || !progress.finished) return '-';
    const seconds = (progress.finishTime / 1000).toFixed(2);
    return `${seconds}s`;
  }

  getPlayerAvatar(playerName: string): string {
    const player = this.game?.players.find(p => p.name === playerName);
    return player?.avatar || '👤';
  }

  getDifficultyLabel(): string {
    const difficulty = this.roundInfo?.difficulty;
    switch (difficulty) {
      case 'EASY': return '🟢 Easy';
      case 'MEDIUM': return '🟡 Medium';
      case 'HARD': return '🔴 Hard';
      default: return '';
    }
  }

  getTypedCharClass(index: number): string {
    if (index >= this.typedText.length) return 'untyped';
    if (this.typedText[index] === this.targetText[index]) return 'correct';
    return 'incorrect';
  }

  getRemainingText(): string {
    return this.targetText.substring(this.typedText.length);
  }

  getTypedTextCorrect(): string {
    let result = '';
    for (let i = 0; i < this.typedText.length && i < this.targetText.length; i++) {
      if (this.typedText[i] === this.targetText[i]) {
        result += this.typedText[i];
      } else {
        break;
      }
    }
    return result;
  }
}
