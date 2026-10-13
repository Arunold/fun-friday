import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { ChipPlayer, PlayerChip, injectGameApi } from '@ff/sdk';

import { BackseatDriversView, LevelResult, VoteView } from './backseat-drivers.types';

/** A teammate one can vote for. */
interface Option {
  id: string;
  player: ChipPlayer;
  votes: number;
  winner: boolean;
  picked: boolean;
  me: boolean;
}

const STATUS_EMOJI: Record<LevelResult['status'], string> = { PARKED: '🅿️', CRASHED: '💥', TIME_UP: '⏱️' };

/**
 * The MVP vote after the last level: a grid of teammates (never yourself), how many votes are in
 * (not for whom), and the winners once the vote is over. Next to it, the team's road trip so far.
 */
@Component({
  selector: 'ff-bd-mvp-vote',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlayerChip],
  templateUrl: './mvp-vote.component.html',
  styleUrl: './mvp-vote.component.scss',
  host: { 'data-testid': 'mvp-vote', '[attr.data-revealed]': 'vote().revealed' },
})
export class MvpVote {
  readonly vote = input.required<VoteView>();
  readonly levels = input.required<LevelResult[]>();

  protected readonly api = injectGameApi<BackseatDriversView>();
  protected readonly error = signal<string | null>(null);
  protected readonly statusEmoji = STATUS_EMOJI;

  protected readonly canVote = computed(
    () => this.api.isPlaying() && this.api.phase() === 'PLAYING' && !this.vote().revealed,
  );

  protected readonly options = computed<Option[]>(() => {
    const vote = this.vote();
    const me = this.api.meId();
    const hideMe = this.api.isPlaying() && !vote.revealed; // nobody votes for themselves
    return vote.candidates
      .filter((id) => id !== me || !hideMe)
      .map((id) => ({
        id,
        player: this.chip(id),
        votes: vote.tally?.[id] ?? 0,
        winner: !!vote.winners?.includes(id),
        picked: vote.myVote === id,
        me: id === me,
      }));
  });

  protected readonly voted = computed(() => this.vote().voted.map((id) => ({ id, player: this.chip(id) })));
  protected readonly total = computed(() => this.levels().reduce((sum, level) => sum + level.score, 0));
  protected readonly status = computed(() => {
    const vote = this.vote();
    if (vote.revealed) {
      return vote.winners?.length ? 'The votes are in!' : 'No votes this time — the whole bus wins!';
    }
    if (!this.api.isPlaying()) {
      return 'The team is voting…';
    }
    return vote.myVote ? 'Vote cast — you can still change it.' : 'Tap a teammate. Votes stay secret until the end.';
  });

  protected async cast(option: Option): Promise<void> {
    if (!this.canVote() || option.picked) {
      return;
    }
    this.error.set(null);
    const result = await this.api.send('vote', { playerId: option.id }, { quiet: true });
    if (!result.ok) {
      this.error.set(result.message);
    }
  }

  private chip(id: string): ChipPlayer {
    return this.api.player(id) ?? { name: '?', avatar: '🙂', color: 'var(--mat-sys-outline)' };
  }
}
