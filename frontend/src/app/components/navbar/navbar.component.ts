import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonModule } from '@angular/material/button';
import { WebsocketService } from '../../services';

/**
 * NavbarComponent - Self-contained navigation header.
 * Reads player info from sessionStorage and handles actions directly.
 * Minimal inputs required - only game-specific info that can't be inferred.
 */
@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css'],
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule,
    MatButtonModule
  ]
})
export class NavbarComponent implements OnInit {
  /** Game type name to display in badge */
  @Input() gameTypeName = '';
  
  /** Game ID to display */
  @Input() gameId: string | null = null;
  
  /** Whether to show game code badge */
  @Input() showGameCode = true;
  
  /** Show exit/leave options. Set to false for results page. */
  @Input() showExitOptions = true;
  
  /** Show home button. Set to true for results page. */
  @Input() showHomeButton = false;

  // Player info - read from sessionStorage
  playerName = '';
  playerAvatar = '👤';
  isHost = false;

  private router = inject(Router);
  private websocketService = inject(WebsocketService);

  ngOnInit(): void {
    this.playerName = sessionStorage.getItem('playerName') || '';
    this.playerAvatar = sessionStorage.getItem('playerAvatar') || '👤';
    this.isHost = sessionStorage.getItem('isHost') === 'true';
  }

  onExitGame(): void {
    if (this.gameId && this.isHost) {
      this.websocketService.sendMessage('/app/leave', {
        sender: this.playerName,
        content: this.gameId
      });
    }
    this.goHome();
  }

  onLeaveGame(): void {
    if (this.gameId) {
      this.websocketService.sendMessage('/app/leave', {
        sender: this.playerName,
        content: this.gameId
      });
    }
    this.goHome();
  }

  onGoHome(): void {
    this.goHome();
  }

  private goHome(): void {
    sessionStorage.clear();
    this.router.navigate(['/']);
  }
}
