import { Component, Inject } from '@angular/core';
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
    imports: [
        CommonModule,
        MatDialogModule,
        MatTabsModule,
        MatButtonModule,
        MatIconModule
    ],
    template: `
    <div class="avatar-dialog">
      <button mat-icon-button class="close-btn" (click)="onCancel()">
        <mat-icon>close</mat-icon>
      </button>
      <h2 mat-dialog-title>Choose Your Avatar</h2>
      <mat-dialog-content>
        <mat-tab-group>
          <mat-tab label="Male">
            <div class="avatar-grid">
              @for (avatar of maleAvatars; track avatar.id) {
                <button class="avatar-btn"
                        [class.selected]="selectedAvatar === avatar.id"
                        (click)="selectAndClose(avatar.id)">
                  {{ avatar.emoji }}
                </button>
              }
            </div>
          </mat-tab>
          <mat-tab label="Female">
            <div class="avatar-grid">
              @for (avatar of femaleAvatars; track avatar.id) {
                <button class="avatar-btn"
                        [class.selected]="selectedAvatar === avatar.id"
                        (click)="selectAndClose(avatar.id)">
                  {{ avatar.emoji }}
                </button>
              }
            </div>
          </mat-tab>
          <mat-tab label="Other">
            <div class="avatar-grid">
              @for (avatar of othersAvatars; track avatar.id) {
                <button class="avatar-btn"
                        [class.selected]="selectedAvatar === avatar.id"
                        (click)="selectAndClose(avatar.id)">
                  {{ avatar.emoji }}
                </button>
              }
            </div>
          </mat-tab>
        </mat-tab-group>
      </mat-dialog-content>
    </div>
  `,
    styles: [`
    .avatar-dialog {
      min-width: 400px;
      position: relative;
    }
    
    .close-btn {
      position: absolute;
      top: 8px;
      right: 8px;
      z-index: 10;
    }
    
    h2[mat-dialog-title] {
      color: var(--spotify-black);
      text-align: center;
      margin-bottom: 20px;
      font-weight: 700;
    }
    
    .avatar-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 15px;
      padding: 20px;
      max-height: 300px;
      overflow-y: auto;
    }
    
    .avatar-btn {
      width: 70px;
      height: 70px;
      font-size: 2.5rem;
      border: 3px solid transparent;
      border-radius: 50%;
      background: var(--spotify-light-gray);
      cursor: pointer;
      transition: all 0.2s ease;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    
    .avatar-btn:hover {
      background: var(--spotify-gray);
      transform: scale(1.1);
      border-color: var(--spotify-green);
    }
    
    .avatar-btn.selected {
      border-color: var(--spotify-green);
      background: rgba(29, 185, 84, 0.2);
      transform: scale(1.15);
      box-shadow: 0 0 20px rgba(29, 185, 84, 0.4);
    }
    
    mat-dialog-actions {
      padding: 16px;
    }
  `]
})
export class AvatarDialogComponent {
    selectedAvatar: string = '';

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
        { id: 'm12', emoji: '🥷' }
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
        { id: 'f12', emoji: '🧚‍♀️' }
    ];

    othersAvatars: Avatar[] = [...this.maleAvatars, ...this.femaleAvatars].sort(() => Math.random() - 0.5);

    constructor(
        public dialogRef: MatDialogRef<AvatarDialogComponent>,
        @Inject(MAT_DIALOG_DATA) public data: AvatarDialogData
    ) {
        if (data?.selectedAvatar) {
            this.selectedAvatar = data.selectedAvatar;
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
