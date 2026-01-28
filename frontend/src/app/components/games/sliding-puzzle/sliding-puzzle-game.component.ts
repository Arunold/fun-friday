import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SlidingPuzzleRoundInfo, PlayerPuzzleInfo } from '../../../models';
import { GameLayoutComponent } from '../../game-layout/game-layout.component';
import { BaseGameComponent } from '../base-game.component';
import { isSlidingPuzzleRoundInfo } from '../../../utils/game-config.utils';

@Component({
  selector: 'app-sliding-puzzle-game',
  templateUrl: './sliding-puzzle-game.component.html',
  styleUrl: './sliding-puzzle-game.component.css',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    GameLayoutComponent,
  ],
})
export class SlidingPuzzleGameComponent extends BaseGameComponent {
  readonly gameIcon = 'extension';

  // Typed getter for round info
  get roundInfo(): SlidingPuzzleRoundInfo | null {
    const info = this.game?.currentRoundInfo;
    return isSlidingPuzzleRoundInfo(info) ? info : null;
  }

  get gridSize(): number {
    return this.roundInfo?.gridSize ?? 4;
  }

  get myPuzzleState(): PlayerPuzzleInfo | null {
    return this.roundInfo?.playerStates?.[this.playerName] ?? null;
  }

  get myPuzzle(): number[] {
    return this.myPuzzleState?.puzzle ?? [];
  }

  get isSolved(): boolean {
    return this.myPuzzleState?.solved ?? false;
  }

  protected onNewRound(): void {
    this.hasGuessed = false;
  }

  protected checkIfPlayerAnswered(): void {
    if (this.myPuzzleState?.solved) {
      this.hasGuessed = true;
    }
  }

  onTileClick(index: number): void {
    if (!this.gameId || this.isSolved) return;

    // Check if this tile is adjacent to the empty space
    const emptyIndex = this.myPuzzle.indexOf(0);
    if (!this.isValidMove(index, emptyIndex)) return;

    this.websocketService.sendMessage('/app/puzzleMove', {
      gameId: this.gameId,
      playerName: this.playerName,
      tileIndex: index,
    });
  }

  private isValidMove(tileIndex: number, emptyIndex: number): boolean {
    const size = this.gridSize;
    const tileRow = Math.floor(tileIndex / size);
    const tileCol = tileIndex % size;
    const emptyRow = Math.floor(emptyIndex / size);
    const emptyCol = emptyIndex % size;

    // Valid move: adjacent horizontally or vertically
    return (
      (Math.abs(tileRow - emptyRow) === 1 && tileCol === emptyCol) ||
      (Math.abs(tileCol - emptyCol) === 1 && tileRow === emptyRow)
    );
  }

  isMovable(index: number): boolean {
    const emptyIndex = this.myPuzzle.indexOf(0);
    return this.isValidMove(index, emptyIndex);
  }

  getTileValue(index: number): number {
    return this.myPuzzle[index] ?? 0;
  }

  isCorrectPosition(index: number): boolean {
    const value = this.myPuzzle[index];
    if (value === 0) {
      return index === this.gridSize * this.gridSize - 1;
    }
    return value === index + 1;
  }

  getPlayerState(playerName: string): PlayerPuzzleInfo | null {
    return this.roundInfo?.playerStates?.[playerName] ?? null;
  }

  getPlayerProgress(playerName: string): number {
    const state = this.getPlayerState(playerName);
    if (!state) return 0;
    const totalTiles = this.gridSize * this.gridSize;
    return Math.round((state.correctTiles / totalTiles) * 100);
  }

  hasPlayerSolved(playerName: string): boolean {
    return this.getPlayerState(playerName)?.solved ?? false;
  }

  getSolvePosition(playerName: string): number {
    const order = this.roundInfo?.solveOrder ?? [];
    const index = order.indexOf(playerName);
    return index >= 0 ? index + 1 : 0;
  }

  getPositionSuffix(position: number): string {
    if (position === 1) return 'st';
    if (position === 2) return 'nd';
    if (position === 3) return 'rd';
    return 'th';
  }

  formatSolveTime(playerName: string): string {
    const state = this.getPlayerState(playerName);
    if (!state || !state.solved) return '-';
    const seconds = (state.solveTime / 1000).toFixed(2);
    return `${seconds}s`;
  }

  getPlayerAvatar(playerName: string): string {
    const player = this.game?.players.find(p => p.name === playerName);
    return player?.avatar || '👤';
  }

  getDifficultyLabel(): string {
    const difficulty = this.roundInfo?.difficulty;
    switch (difficulty) {
      case 'EASY':
        return '🟢 Easy (3x3)';
      case 'MEDIUM':
        return '🟡 Medium (4x4)';
      case 'HARD':
        return '🔴 Hard (5x5)';
      default:
        return '';
    }
  }

  getGridTemplateColumns(): string {
    return `repeat(${this.gridSize}, 1fr)`;
  }
}
