import { inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { CanDeactivateFn } from '@angular/router';

import { RoomStore } from '../../core/room.store';
import { normalizeRoomCode } from '../../core/util';
import { confirmAction } from '../../shared/confirm-dialog.component';

/**
 * Navigating away from the room (browser Back, a link, a typed URL inside the app) leaves the room
 * after a confirmation, so nobody stays behind as a ghost player. Exits the app makes itself —
 * leave, kick, room closed, seat taken over — clear the session first and pass straight through.
 * Refreshing or closing the tab is not a navigation: the seat survives a refresh, and a closed tab
 * frees it after the presence grace period.
 */
export const leaveRoomGuard: CanDeactivateFn<unknown> = async (_component, route) => {
  const store = inject(RoomStore);
  const dialog = inject(MatDialog);
  if (!store.hasSessionFor(normalizeRoomCode(route.paramMap.get('code') ?? ''))) {
    return true;
  }
  const running = store.phase() !== 'LOBBY' && store.phase() !== 'FINISHED';
  const confirmed = await confirmAction(dialog, {
    title: 'Leave the room?',
    message: running
      ? "The game continues without you and your score won't count."
      : 'You can come back any time with the room code.',
    confirm: 'Leave',
    destructive: true,
  });
  if (confirmed) {
    await store.leave();
  }
  return confirmed;
};
