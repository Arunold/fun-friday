import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';

import { PlayerChip, injectGameApi } from '@ff/sdk';

import { __CLASS__View } from './__GAME_ID__.types';

/** __GAME_NAME__: a grid of numbers to pick from; everyone's picks appear at the reveal. */
@Component({
  selector: 'ff-__GAME_ID__',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip],
  templateUrl: './__GAME_ID__.component.html',
  styleUrl: './__GAME_ID__.component.scss',
})
export class __CLASS__Component {
  protected readonly api = injectGameApi<__CLASS__View>();
  protected readonly view = this.api.view;
  protected readonly error = signal<string | null>(null);

  protected readonly numbers = computed(() => Array.from({ length: this.view()?.highest ?? 0 }, (_, i) => i + 1));
  protected readonly canPick = computed(
    () => this.api.isPlaying() && this.api.phase() === 'PLAYING' && this.view()?.myPick == null,
  );
  protected readonly statuses = computed(() =>
    (this.view()?.players ?? [])
      .map((status) => ({ status, player: this.api.player(status.playerId) }))
      .filter((entry) => !!entry.player),
  );

  protected async pick(value: number): Promise<void> {
    const result = await this.api.send('pick', { value }, { quiet: true });
    this.error.set(result.ok ? null : result.message);
  }
}
