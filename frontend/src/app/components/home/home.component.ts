import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
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
  styleUrls: ['./home.component.css'],
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
})
export class HomeComponent implements OnInit {
  // Form control for game code
  gameCodeControl = new FormControl('');

  // Signals for reactive state
  gameTypes = signal<GameType[]>([]);
  errorMessage = signal('');
  selectedGameType = signal<GameType | null>(null);

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
        this.gameTypes.set(types);
      },
      error: () => {
        this.errorMessage.set('Failed to load game types. Is the backend running?');
      },
    });
  }

  /** Select game type and immediately navigate to host setup */
  selectGameType(gameType: GameType): void {
    this.selectedGameType.set(gameType);
    this.router.navigate(['/setup/host', gameType.id]);
  }

  goToJoinSetup(): void {
    const code = this.gameCodeControl.value?.trim() || '';
    if (code.length >= 6) {
      this.router.navigate(['/setup/join', code.toUpperCase()]);
    }
  }

  get isJoinDisabled(): boolean {
    const code = this.gameCodeControl.value?.trim() || '';
    return code.length < 6;
  }
}
