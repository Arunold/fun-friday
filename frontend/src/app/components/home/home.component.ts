import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { WebsocketService, GameService } from '../../services';
import { GameType } from '../../models';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule
  ]
})
export class HomeComponent implements OnInit {

  // Form fields
  gameCode = '';
  errorMessage = '';

  // Game selection
  gameTypes: GameType[] = [];
  selectedGameType: GameType | null = null;

  private websocketService = inject(WebsocketService);
  private gameService = inject(GameService);
  private router = inject(Router);

  ngOnInit(): void {
    this.websocketService.connect();
    this.loadGameTypes();
  }

  loadGameTypes(): void {
    this.gameService.getGameTypes().subscribe({
      next: (types: GameType[]) => {
        this.gameTypes = types;
      },
      error: () => {
        this.errorMessage = 'Failed to load game types. Is the backend running?';
      }
    });
  }

  /** Select game type and immediately navigate to host setup */
  selectGameType(gameType: GameType): void {
    this.selectedGameType = gameType;
    this.router.navigate(['/setup/host', gameType.id]);
  }

  goToJoinSetup(): void {
    if (this.gameCode.trim().length >= 6) {
      this.router.navigate(['/setup/join', this.gameCode.toUpperCase()]);
    }
  }
}
