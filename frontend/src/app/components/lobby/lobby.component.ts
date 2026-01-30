import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Game } from '../../models';

/**
 * LobbyComponent - Displays the game lobby where players wait before the game starts.
 * Shows game code, player list, and controls for the host.
 */
@Component({
  selector: 'app-lobby',
  templateUrl: './lobby.component.html',
  styleUrls: ['./lobby.component.css'],
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule, MatTooltipModule],
})
export class LobbyComponent {
  @Input() game!: Game;
  @Input() gameId: string | null = null;
  @Input() gameIcon = 'casino';
  @Input() playerName = '';
  @Input() isHost = false;

  @Output() startGame = new EventEmitter<void>();
  @Output() copyCode = new EventEmitter<void>();
  @Output() copyUrl = new EventEmitter<void>();
  @Output() removePlayer = new EventEmitter<string>();

  onStartGame(): void {
    this.startGame.emit();
  }

  onCopyCode(): void {
    this.copyCode.emit();
  }

  onCopyUrl(): void {
    this.copyUrl.emit();
  }

  onRemovePlayer(playerName: string): void {
    this.removePlayer.emit(playerName);
  }
}
