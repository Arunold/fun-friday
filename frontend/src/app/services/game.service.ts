import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GameType, Game } from '../models';
import { environment } from '../../environments/environment';

export interface GameStatus {
  exists: boolean;
  message?: string;
  gameState?: string;
  gameTypeId?: string;
  gameTypeName?: string;
}

@Injectable({
  providedIn: 'root',
})
export class GameService {
  private readonly apiUrl = environment.apiUrl;

  private http = inject(HttpClient);

  getGameTypes(): Observable<GameType[]> {
    return this.http.get<GameType[]>(`${this.apiUrl}/game-types`);
  }

  getGameStatus(gameId: string): Observable<GameStatus> {
    return this.http.get<GameStatus>(`${this.apiUrl}/games/${gameId}/status`);
  }

  getGameDetails(gameId: string): Observable<Game> {
    return this.http.get<Game>(`${this.apiUrl}/games/${gameId}`);
  }
}
