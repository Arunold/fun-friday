import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { errorMessage } from './notify.service';
import { GameHistoryEntry, GameSummary, LeaderboardEntry, RoomSummary } from './protocol';

/** REST calls (read-only; everything that changes state goes over the WebSocket). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  games(): Promise<GameSummary[]> {
    return firstValueFrom(this.http.get<GameSummary[]>('/api/games'));
  }

  /** Public facts about a room, or `null` if there is no such room. */
  async room(code: string): Promise<RoomSummary | null> {
    try {
      return await firstValueFrom(this.http.get<RoomSummary>(`/api/rooms/${encodeURIComponent(code)}`));
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        return null;
      }
      throw error;
    }
  }

  roomHistory(code: string): Promise<GameHistoryEntry[]> {
    return firstValueFrom(this.http.get<GameHistoryEntry[]>(`/api/rooms/${encodeURIComponent(code)}/history`));
  }

  recentGames(): Promise<GameHistoryEntry[]> {
    return firstValueFrom(this.http.get<GameHistoryEntry[]>('/api/history'));
  }

  leaderboard(days = 30): Promise<LeaderboardEntry[]> {
    return firstValueFrom(this.http.get<LeaderboardEntry[]>('/api/leaderboard', { params: { days } }));
  }
}

/** The installed games, loaded once and shared by every page. */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly api = inject(ApiService);
  private loading: Promise<void> | null = null;

  readonly games = signal<GameSummary[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly byId = computed(() => new Map((this.games() ?? []).map((game) => [game.id, game])));

  /** Loads the catalog (once; call again after an error to retry). */
  load(): Promise<void> {
    if (!this.loading) {
      this.error.set(null);
      this.loading = this.api.games().then(
        (games) => this.games.set(games),
        (error: unknown) => {
          this.loading = null;
          this.error.set(errorMessage(error));
        },
      );
    }
    return this.loading;
  }

  find(id: string | null | undefined): GameSummary | undefined {
    return id ? this.byId().get(id) : undefined;
  }
}
