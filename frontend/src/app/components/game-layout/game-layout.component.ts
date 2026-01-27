import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Game } from '../../models';
import { OrderByPipe } from '../../pipes';
import { NavbarComponent } from '../navbar/navbar.component';

/**
 * GameLayoutComponent - Shared layout for all game types.
 * Provides the common structure (header, loading, lobby, countdown, sidebar)
 * and projects game-specific content using ng-content.
 */
@Component({
  selector: 'app-game-layout',
  templateUrl: './game-layout.component.html',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    OrderByPipe,
    NavbarComponent
  ]
})
export class GameLayoutComponent {
  @Input() game!: Game;
  @Input() gameId: string | null = null;
  @Input() playerName = '';
  @Input() isHost = false;
  @Input() playerAvatar = '👤';
  @Input() isLoading = true;
  @Input() countdown = 0;
  @Input() roundTimer = 0;
  @Input() gameIcon = 'casino';
  
  /** Controls whether to show the sidebar during gameplay. Default true. */
  @Input() showSidebar = true;
  
  /** Controls whether to show the round header in main content area. Default false (layout handles it). */
  @Input() showRoundHeader = true;

  @Output() startGame = new EventEmitter<void>();
  @Output() leaveGame = new EventEmitter<void>();
  @Output() exitGame = new EventEmitter<void>();
  @Output() copyCode = new EventEmitter<void>();
  @Output() removePlayer = new EventEmitter<string>();

  onStartGame(): void {
    this.startGame.emit();
  }

  onLeaveGame(): void {
    this.leaveGame.emit();
  }

  onExitGame(): void {
    this.exitGame.emit();
  }

  onCopyCode(): void {
    this.copyCode.emit();
  }

  onRemovePlayer(playerName: string): void {
    this.removePlayer.emit(playerName);
  }
}
