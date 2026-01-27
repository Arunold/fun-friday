import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ReactionShowdownRoundInfo, ReactionInfo } from '../../../models';
import { GameLayoutComponent } from '../../game-layout/game-layout.component';
import { BaseGameComponent } from '../base-game.component';
import { isReactionShowdownRoundInfo } from '../../../utils/game-config.utils';

@Component({
    selector: 'app-reaction-showdown-game',
    templateUrl: './reaction-showdown-game.component.html',
    styleUrl: './reaction-showdown-game.component.css',
    standalone: true,
    imports: [
        CommonModule,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        GameLayoutComponent
    ]
})
export class ReactionShowdownGameComponent extends BaseGameComponent {
    readonly gameIcon = 'flash_on';
    hasTapped = false;

    get roundInfo(): ReactionShowdownRoundInfo | null {
        const info = this.game?.currentRoundInfo;
        return isReactionShowdownRoundInfo(info) ? info : null;
    }

    get phase(): string {
        return this.roundInfo?.phase ?? 'WAITING';
    }

    get isFakeOut(): boolean {
        return this.roundInfo?.isFakeOut ?? false;
    }

    get myReaction(): ReactionInfo | null {
        return this.roundInfo?.playerReactions?.[this.playerName] ?? null;
    }

    get fastestPlayer(): string | null {
        return this.roundInfo?.fastestPlayer ?? null;
    }

    get fastestTime(): number {
        return this.roundInfo?.fastestTime ?? 0;
    }

    protected override onNewRound(): void {
        this.hasGuessed = false;
        this.hasTapped = false;
    }

    protected checkIfPlayerAnswered(): void {
        if (this.myReaction) {
            this.hasTapped = true;
        }
    }

    onTap(): void {
        if (!this.gameId || this.hasTapped) return;

        this.hasTapped = true;

        this.websocketService.sendMessage('/app/reactionTap', {
            gameId: this.gameId,
            playerName: this.playerName,
            tapTime: Date.now()
        });
    }

    getReactionClass(): string {
        switch (this.phase) {
            case 'WAITING':
                return 'waiting';
            case 'REACT':
                return 'go';
            case 'FAKEOUT':
                return 'fakeout';
            default:
                return '';
        }
    }

    getPhaseText(): string {
        switch (this.phase) {
            case 'WAITING':
                return 'Wait for it...';
            case 'REACT':
                return 'TAP NOW!';
            case 'FAKEOUT':
                return 'FAKE OUT!';
            default:
                return '';
        }
    }

    getPhaseIcon(): string {
        switch (this.phase) {
            case 'WAITING':
                return 'hourglass_empty';
            case 'REACT':
                return 'flash_on';
            case 'FAKEOUT':
                return 'warning';
            default:
                return '';
        }
    }

    getPlayerReaction(playerName: string): ReactionInfo | null {
        return this.roundInfo?.playerReactions?.[playerName] ?? null;
    }

    getPlayerAvatar(playerName: string): string {
        const player = this.game?.players.find(p => p.name === playerName);
        return player?.avatar || '👤';
    }

    formatReactionTime(reactionTime: number): string {
        if (reactionTime < 0) return '-';
        return `${reactionTime}ms`;
    }

    getReactionStatus(reaction: ReactionInfo | null): string {
        if (!reaction) return 'Waiting...';
        if (reaction.falseStart) return '❌ False Start!';
        if (reaction.tappedFakeOut) return '⚠️ Fell for fake!';
        if (reaction.reactionTime > 0) return `⚡ ${reaction.reactionTime}ms`;
        return 'Waiting...';
    }

    isFastest(playerName: string): boolean {
        return this.fastestPlayer === playerName;
    }
}
