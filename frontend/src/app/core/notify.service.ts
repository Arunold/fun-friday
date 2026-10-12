import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

import { RequestError } from './realtime.service';

/** Turns any error into a message that is safe and useful to show. */
export function errorMessage(error: unknown): string {
  if (error instanceof RequestError) {
    return error.message;
  }
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'The server is unreachable. Check your connection.';
    }
    const detail = (error.error as { detail?: string } | null)?.detail;
    return detail ?? `The server answered with an error (${error.status}).`;
  }
  return 'Something went wrong.';
}

/** Short toasts at the bottom of the screen. Identical messages in quick succession are merged. */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snackBar = inject(MatSnackBar);
  private last = { message: '', at: 0 };

  info(message: string): void {
    this.show(message, 'ff-snack-info', 3500);
  }

  success(message: string): void {
    this.show(message, 'ff-snack-success', 2500);
  }

  error(error: unknown): void {
    this.show(typeof error === 'string' ? error : errorMessage(error), 'ff-snack-error', 5000);
  }

  private show(message: string, panelClass: string, duration: number): void {
    const now = Date.now();
    if (message === this.last.message && now - this.last.at < 2000) {
      return;
    }
    this.last = { message, at: now };
    this.snackBar.open(message, 'OK', { duration, panelClass });
  }
}
