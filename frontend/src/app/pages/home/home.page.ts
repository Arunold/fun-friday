import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { CatalogService } from '../../core/api.service';
import { RoomStore } from '../../core/room.store';
import { normalizeRoomCode } from '../../core/util';
import { GameCard } from '../../shared/game-card.component';

/** Landing page: join with a code or pick a game to host. */
@Component({
  selector: 'ff-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    GameCard,
  ],
  templateUrl: './home.page.html',
  styleUrl: './home.page.scss',
})
export class HomePage {
  protected readonly catalog = inject(CatalogService);
  protected readonly store = inject(RoomStore);
  private readonly router = inject(Router);

  protected readonly code = signal('');
  protected readonly codeValid = computed(() => /^[A-Z0-9]{5}$/.test(this.code()));

  constructor() {
    void this.catalog.load();
  }

  protected onCodeInput(value: string): void {
    this.code.set(normalizeRoomCode(value).slice(0, 5));
  }

  protected join(): void {
    if (this.codeValid()) {
      void this.router.navigate(['/join', this.code()]);
    }
  }
}
