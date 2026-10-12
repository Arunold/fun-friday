import { Clipboard } from '@angular/cdk/clipboard';
import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { NotifyService } from '../../core/notify.service';
import { MemberView } from '../../core/protocol';
import { RoomStore } from '../../core/room.store';
import { ChangeGameData, ChangeGameDialog, ChangeGameResult } from '../../shared/change-game-dialog.component';
import { confirmAction } from '../../shared/confirm-dialog.component';

/** Room commands with their confirmations and feedback, shared by the toolbar, lobby and podium. */
@Injectable()
export class RoomActions {
  private readonly store = inject(RoomStore);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  private readonly clipboard = inject(Clipboard);

  /** The link that opens the join page of this room. */
  inviteLink(): string {
    return `${window.location.origin}/join/${this.store.code() ?? ''}`;
  }

  /** Copies the invite link (works on plain-http LAN addresses too). */
  copyInvite(): void {
    if (this.clipboard.copy(this.inviteLink())) {
      this.notify.success('Invite link copied — paste it in your team chat.');
    } else {
      this.notify.error('Could not copy. Select the link and copy it manually.');
    }
  }

  async leave(): Promise<void> {
    const running = this.store.phase() !== 'LOBBY' && this.store.phase() !== 'FINISHED';
    const confirmed =
      !running ||
      (await confirmAction(this.dialog, {
        title: 'Leave the game?',
        message: "The game continues without you and your score won't count.",
        confirm: 'Leave',
        destructive: true,
      }));
    if (confirmed) {
      await this.store.leave();
      await this.router.navigate(['/']);
    }
  }

  async end(): Promise<void> {
    const confirmed = await confirmAction(this.dialog, {
      title: 'End the room for everyone?',
      message: 'Everybody is sent back to the home page. The Friday Cup of this room is lost.',
      confirm: 'End room',
      destructive: true,
    });
    if (confirmed) {
      await this.run(() => this.store.end());
    }
  }

  async kick(member: MemberView): Promise<void> {
    const confirmed = await confirmAction(this.dialog, {
      title: `Remove ${member.name}?`,
      message: 'They can join again with the room code.',
      confirm: 'Remove',
      destructive: true,
    });
    if (confirmed) {
      await this.run(() => this.store.kick(member.id));
    }
  }

  /** Host picks another game (from the lobby or the podium). */
  async changeGame(): Promise<void> {
    const game = this.store.game();
    if (!game) {
      return;
    }
    const ref = this.dialog.open<ChangeGameDialog, ChangeGameData, ChangeGameResult>(ChangeGameDialog, {
      data: { gameId: game.id, settings: this.store.settings(), playerCount: this.store.players().length },
      width: '880px',
      maxWidth: '95vw',
      maxHeight: '90vh',
    });
    const result = await firstValueFrom(ref.afterClosed());
    if (result) {
      await this.run(() => this.store.updateSettings(result.gameId, result.settings));
    }
  }

  /** Runs a room command and toasts its error, if any. */
  async run(command: () => Promise<unknown>): Promise<boolean> {
    try {
      await command();
      return true;
    } catch (error) {
      this.notify.error(error);
      return false;
    }
  }
}
