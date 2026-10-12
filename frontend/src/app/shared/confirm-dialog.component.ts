import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirm: string;
  cancel?: string;
  /** Styles the confirm button as dangerous (ending a room, kicking someone). */
  destructive?: boolean;
}

/** A yes/no question before something that can't be undone. */
@Component({
  selector: 'ff-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">{{ data.cancel ?? 'Cancel' }}</button>
      <button
        mat-flat-button
        cdkFocusInitial
        [class.danger]="data.destructive"
        [mat-dialog-close]="true"
        data-testid="confirm"
      >
        {{ data.confirm }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .danger {
      --mat-button-filled-container-color: var(--mat-sys-error);
      --mat-button-filled-label-text-color: var(--mat-sys-on-error);
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmOptions>(MAT_DIALOG_DATA);
}

/** Opens a {@link ConfirmDialog}; resolves `true` when confirmed. */
export async function confirmAction(dialog: MatDialog, options: ConfirmOptions): Promise<boolean> {
  const ref = dialog.open<ConfirmDialog, ConfirmOptions, boolean>(ConfirmDialog, {
    data: options,
    width: '420px',
    autoFocus: 'dialog',
  });
  return (await firstValueFrom(ref.afterClosed())) === true;
}
