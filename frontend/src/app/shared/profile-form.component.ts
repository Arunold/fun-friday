import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { NAME_MAX_LENGTH, NAME_PATTERN, Profile } from '../core/profile.service';
import { AvatarPicker } from './avatar-picker.component';

/** Validates a display name like the server does; returns an error message or `null`. */
export function nameError(name: string): string | null {
  const clean = name.trim().replace(/\s+/g, ' ');
  if (!clean) {
    return 'Please enter a name.';
  }
  if (clean.length > NAME_MAX_LENGTH) {
    return `At most ${NAME_MAX_LENGTH} characters.`;
  }
  if (!NAME_PATTERN.test(clean)) {
    return "Letters, digits, spaces and . _ ' - only.";
  }
  return null;
}

/** Name + avatar, two-way bound with `[(profile)]`. */
@Component({
  selector: 'ff-profile-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, AvatarPicker],
  template: `
    <div class="identity">
      <span class="preview" aria-hidden="true">{{ profile().avatar }}</span>
      <mat-form-field class="name">
        <mat-label>Your name</mat-label>
        <input
          matInput
          name="playerName"
          autocomplete="nickname"
          [maxlength]="maxLength"
          [ngModel]="profile().name"
          (ngModelChange)="profile.set({ name: $event, avatar: profile().avatar })"
          (blur)="touched.set(true)"
          data-testid="player-name"
          required
        />
        @if (visibleError(); as message) {
          <mat-hint class="error" role="alert">{{ message }}</mat-hint>
        } @else {
          <mat-hint align="end">{{ profile().name.length }}/{{ maxLength }}</mat-hint>
        }
      </mat-form-field>
    </div>
    <ff-avatar-picker
      [value]="profile().avatar"
      (valueChange)="profile.set({ name: profile().name, avatar: $event })"
    />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .identity {
      display: flex;
      align-items: flex-start;
      gap: 16px;
    }
    .preview {
      flex: none;
      display: grid;
      place-items: center;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: var(--mat-sys-surface-container-highest);
      font-size: 32px;
    }
    .name {
      flex: 1;
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class ProfileForm {
  readonly profile = model.required<Profile>();
  /** An error from the server (e.g. the name is taken) shown under the field. */
  readonly serverError = input<string | null>(null);

  protected readonly maxLength = NAME_MAX_LENGTH;
  protected readonly touched = signal(false);
  protected readonly visibleError = computed(() => {
    const local = nameError(this.profile().name);
    return this.serverError() ?? (this.profile().name || this.touched() ? local : null);
  });
}
