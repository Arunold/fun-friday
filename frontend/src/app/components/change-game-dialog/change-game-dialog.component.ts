import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
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
    ReactiveFormsModule,
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
  gameTypes = signal<GameType[]>([]);
  selectedGameTypeId = signal('');
  selectedGameType = signal<GameType | null>(null);

  configForm = new FormGroup({
    gameTypeId: new FormControl(''),
    totalRounds: new FormControl(3),
    roundDuration: new FormControl(30),
    wordLength: new FormControl(7),
    difficulty: new FormControl<'EASY' | 'MEDIUM' | 'HARD'>('MEDIUM'),
    minRange: new FormControl(1),
    maxRange: new FormControl(100),
    includeFakeOuts: new FormControl(true),
  });

  private dialogRef = inject(MatDialogRef<ChangeGameDialogComponent>);
  private data: ChangeGameDialogData = inject(MAT_DIALOG_DATA);
  private gameService = inject(GameService);

  ngOnInit(): void {
    this.gameService.getGameTypes().subscribe(types => {
      this.gameTypes.set(types);
      // Pre-select current game type
      const current = types.find(t => t.id === this.data.currentGameTypeId);
      if (current) {
        this.selectGameType(current);
      }
    });
  }

  selectGameType(gameType: GameType): void {
    this.selectedGameTypeId.set(gameType.id);
    this.selectedGameType.set(gameType);
    this.configForm.patchValue({ gameTypeId: gameType.id });
  }

  confirm(): void {
    if (this.selectedGameTypeId()) {
      this.dialogRef.close(this.configForm.value as ChangeGameDialogResult);
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
