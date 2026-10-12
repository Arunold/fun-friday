import { Injectable, Injector, Type, computed, inject } from '@angular/core';
import { map } from 'rxjs';

import { ClockService } from '../core/clock.service';
import { NotifyService } from '../core/notify.service';
import { RoomStore } from '../core/room.store';
import { GAME_API, GameApi, GameStreamMessage, SendOptions } from '../sdk';
import { GAME_UI_LOADERS } from './registry.generated';

/**
 * Loads game UIs on demand (one lazy chunk per game, see `registry.generated.ts`) and builds the
 * {@link GameApi} they receive.
 */
@Injectable({ providedIn: 'root' })
export class GameUiService {
  private readonly store = inject(RoomStore);
  private readonly clock = inject(ClockService);
  private readonly notify = inject(NotifyService);
  private readonly cache = new Map<string, Promise<Type<unknown> | null>>();

  /** Whether a UI exists for the game (the backend may have games without a UI in this build). */
  has(gameId: string): boolean {
    return gameId in GAME_UI_LOADERS;
  }

  /** The game's stage component, or `null` if the game has no UI in this build. */
  load(gameId: string): Promise<Type<unknown> | null> {
    let loading = this.cache.get(gameId);
    if (!loading) {
      const loader = GAME_UI_LOADERS[gameId];
      loading = loader
        ? loader().then(
            (ui) => ui.component,
            (error: unknown) => {
              console.error(`Could not load the UI of ${gameId}`, error);
              this.cache.delete(gameId);
              return null;
            },
          )
        : Promise.resolve(null);
      this.cache.set(gameId, loading);
    }
    return loading;
  }

  /** An injector that provides {@link GAME_API} to a game component. */
  injectorFor(parent: Injector): Injector {
    return Injector.create({ providers: [{ provide: GAME_API, useValue: this.createApi() }], parent });
  }

  private createApi(): GameApi {
    const store = this.store;
    const notify = this.notify;
    const players = store.players;
    return {
      view: store.view,
      phase: store.phase,
      revealing: computed(() => store.phase() === 'REVEAL'),
      meId: computed(() => store.you()?.memberId ?? null),
      me: computed(() => players().find((p) => p.id === store.you()?.memberId) ?? null),
      isPlaying: store.isPlaying,
      players,
      progress: store.progress,
      settings: store.settings,
      timeLeftMs: store.timeLeftMs,
      phaseDurationMs: store.phaseDurationMs,
      lastTurn: store.lastTurn,
      stream$: store.stream$.pipe(
        map((message): GameStreamMessage => ({ seq: message.seq, type: message.type, payload: message.payload })),
      ),
      events$: store.gameEvents$.asObservable(),
      async send(type: string, payload?: unknown, options?: SendOptions) {
        const result = await store.act(type, payload);
        if (!result.ok && !options?.quiet) {
          notify.error(result.message);
        }
        return result;
      },
      sendFast(type: string, payload?: unknown) {
        store.actFast(type, payload);
      },
      player: (id) => (id ? players().find((p) => p.id === id) : undefined),
      now: () => this.clock.serverNow(),
    };
  }
}
