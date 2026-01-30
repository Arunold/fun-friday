import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface Avatar {
  id: string;
  emoji: string;
}

export interface AvatarDialogData {
  selectedAvatar: string;
}

@Component({
  selector: 'app-avatar-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatTabsModule, MatButtonModule, MatIconModule],
  templateUrl: './avatar-dialog.component.html',
  styleUrls: ['./avatar-dialog.component.css'],
})
export class AvatarDialogComponent {
  selectedAvatar = '';

  maleAvatars: Avatar[] = [
    { id: 'm1', emoji: '👨' },
    { id: 'm2', emoji: '👨‍💻' },
    { id: 'm3', emoji: '👨‍🎨' },
    { id: 'm4', emoji: '👨‍🚀' },
    { id: 'm5', emoji: '🧔' },
    { id: 'm6', emoji: '👨‍🔬' },
    { id: 'm7', emoji: '🤴' },
    { id: 'm8', emoji: '🧙‍♂️' },
    { id: 'm9', emoji: '🦸‍♂️' },
    { id: 'm10', emoji: '👨‍🎤' },
    { id: 'm11', emoji: '👷‍♂️' },
    { id: 'm12', emoji: '🥷' },
  ];

  femaleAvatars: Avatar[] = [
    { id: 'f1', emoji: '👩' },
    { id: 'f2', emoji: '👩‍💻' },
    { id: 'f3', emoji: '👩‍🎨' },
    { id: 'f4', emoji: '👩‍🚀' },
    { id: 'f5', emoji: '👩‍🔬' },
    { id: 'f6', emoji: '👸' },
    { id: 'f7', emoji: '🧙‍♀️' },
    { id: 'f8', emoji: '🦸‍♀️' },
    { id: 'f9', emoji: '👩‍🎤' },
    { id: 'f10', emoji: '👷‍♀️' },
    { id: 'f11', emoji: '💃' },
    { id: 'f12', emoji: '🧚‍♀️' },
  ];

  othersAvatars: Avatar[] = [...this.maleAvatars, ...this.femaleAvatars].sort(
    () => Math.random() - 0.5
  );

  public dialogRef = inject(MatDialogRef<AvatarDialogComponent>);
  public data = inject<AvatarDialogData>(MAT_DIALOG_DATA);

  constructor() {
    if (this.data?.selectedAvatar) {
      this.selectedAvatar = this.data.selectedAvatar;
    }
  }

  getAvatarEmoji(avatarId: string): string {
    const allAvatars = [...this.maleAvatars, ...this.femaleAvatars];
    const avatar = allAvatars.find(a => a.id === avatarId);
    return avatar?.emoji || '🎮';
  }

  /** Select avatar and immediately close the dialog */
  selectAndClose(avatarId: string): void {
    this.selectedAvatar = avatarId;
    const emoji = this.getAvatarEmoji(avatarId);
    this.dialogRef.close({ avatarId, emoji });
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
