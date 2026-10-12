import { Routes } from '@angular/router';

import { leaveRoomGuard } from './leave-room.guard';
import { RoomPage } from './room.page';

/** The room route (lazy, with its guard, so none of it weighs on the initial bundle). */
export default [{ path: '', component: RoomPage, canDeactivate: [leaveRoomGuard] }] satisfies Routes;
