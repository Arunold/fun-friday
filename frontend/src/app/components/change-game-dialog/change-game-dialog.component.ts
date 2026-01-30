import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatOptionModule } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { GameType } from '../../models';
import { GameService } from '../../services';

export interface ChangeGameDialogData {
  currentGameTypeId: string;
}

export interface ChangeGameDialogResult {
  gameTypeId: string;
  totalRounds: number;
  roundDuration: number;
  wordLength: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  minRange: number;
  maxRange: number;
  includeFakeOuts: boolean;
}

@Component({
  selector: 'app-change-game-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatOptionModule,
    MatSlideToggleModule,
  ],
  templateUrl: './change-game-dialog.component.html',
  styleUrl: './change-game-dialog.component.scss',
})
export class ChangeGameDialogComponent implements OnInit {
  gameTypes: GameType[] = [];
  selectedGameTypeId = '';
  selectedGameType: GameType | null = null;

  config: ChangeGameDialogResult = {
    gameTypeId: '',
    totalRounds: 3,
    roundDuration: 30,
    wordLength: 7,
    difficulty: 'MEDIUM',
    minRange: 1,
    maxRange: 100,
    includeFakeOuts: true,
  };

  private dialogRef = inject(MatDialogRef<ChangeGameDialogComponent>);
  private data: ChangeGameDialogData = inject(MAT_DIALOG_DATA);
  private gameService = inject(GameService);

  ngOnInit(): void {
    this.gameService.getGameTypes().subscribe(types => {
      this.gameTypes = types;
      // Pre-select current game type
      const current = types.find(t => t.id === this.data.currentGameTypeId);
      if (current) {
        this.selectGameType(current);
      }
    });
  }

  selectGameType(gameType: GameType): void {
    this.selectedGameTypeId = gameType.id;
    this.selectedGameType = gameType;
    this.config.gameTypeId = gameType.id;
  }

  confirm(): void {
    if (this.selectedGameTypeId) {
      this.dialogRef.close(this.config);
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
