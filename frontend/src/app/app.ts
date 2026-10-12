import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { RoomStore } from './core/room.store';
import { ConnectionBanner } from './shared/connection-banner.component';

@Component({
  selector: 'ff-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, ConnectionBanner],
  template: `
    <ff-connection-banner />
    <router-outlet />
    <p class="small-screen" role="note">Fun Friday is made for desktop screens — some games may not fit here.</p>
  `,
  styles: `
    .small-screen {
      display: none;
    }
    @media (max-width: 899px) {
      .small-screen {
        display: block;
        position: fixed;
        inset: auto 12px 12px;
        z-index: 900;
        padding: 10px 14px;
        border-radius: var(--ff-radius-sm);
        background: var(--mat-sys-tertiary-container);
        color: var(--mat-sys-on-tertiary-container);
        font-size: 13px;
        text-align: center;
      }
    }
  `,
})
export class App {
  constructor() {
    // Creating the store at startup connects the socket and resumes a seat after a refresh.
    inject(RoomStore);
  }
}
