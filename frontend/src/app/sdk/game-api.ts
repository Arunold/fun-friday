import { InjectionToken, Signal, Type, inject } from '@angular/core';
import { Observable } from 'rxjs';

import type { MemberView, Progress, RoomPhase, TurnView } from '../core/protocol';
import type { ActionResult, GameEvent } from '../core/room.store';

export type { ActionResult, GameEvent };

/** A player as games see them: id, name, avatar, colour, score, online state. */
export type GamePlayer = MemberView;
/** Round/turn counters. */
export type GameProgress = Progress;
/** Points of a finished turn and its headline. */
export type TurnSummary = TurnView;

/** A public game event from `GameContext.stream(type, payload)` on the server. */
export interface GameStreamMessage<T = unknown> {
  /** Increases by one per room; a gap means a message was missed (re-sync from the view). */
  seq: number;
  type: string;
  payload: T;
}

export interface SendOptions {
  /** Don't show a toast when the game rejects the action (handle `ActionResult` yourself). */
  quiet?: boolean;
}

/**
 * Everything a game's UI needs, injected with {@link injectGameApi}. All state is exposed as
 * signals and always reflects the latest server snapshot; actions go back to the game's
 * `GameLogic.onAction` on the server.
 *
 * @typeParam V the game's view type (what `GameLogic.view(ctx, viewer)` returns on the server)
 */
export interface GameApi<V = unknown> {
  /** The game's personalised view for this browser; `null` until the first turn starts. */
  readonly view: Signal<V | null>;
  /** `PLAYING` during a turn, `REVEAL` while the turn's results are shown. */
  readonly phase: Signal<RoomPhase | null>;
  /** Shortcut for `phase() === 'REVEAL'`. */
  readonly revealing: Signal<boolean>;
  /** This browser's member id (equals the player id in views). */
  readonly meId: Signal<string | null>;
  /** This browser's player entry (null for spectators who aren't listed as players). */
  readonly me: Signal<GamePlayer | null>;
  /** `false` for spectators: they see the view but can't act. */
  readonly isPlaying: Signal<boolean>;
  /** Players in the current game, in join order, with live scores. */
  readonly players: Signal<readonly GamePlayer[]>;
  readonly progress: Signal<GameProgress | null>;
  /** The game's settings as chosen by the host (values by setting key). */
  readonly settings: Signal<Readonly<Record<string, unknown>>>;
  /** Milliseconds left in the current turn or reveal (server-synchronised; ticks 4×/s). */
  readonly timeLeftMs: Signal<number | null>;
  /** Length of the current timed phase in ms (for progress rings). */
  readonly phaseDurationMs: Signal<number | null>;
  /** The turn whose results are being revealed. */
  readonly lastTurn: Signal<TurnSummary | null>;
  /** Public game events of the room (e.g. drawing strokes). */
  readonly stream$: Observable<GameStreamMessage>;
  /** Private events sent only to this player. */
  readonly events$: Observable<GameEvent>;

  /**
   * Sends an action to the game and resolves once the server accepted or rejected it. A rejected
   * action shows a toast with the game's message unless `quiet` is set.
   */
  send(type: string, payload?: unknown, options?: SendOptions): Promise<ActionResult>;
  /**
   * Fire-and-forget variant for high-frequency actions (strokes, typing progress, key presses).
   * Rejections — including actions that arrive just after the turn ended — are dropped silently.
   */
  sendFast(type: string, payload?: unknown): void;
  /** Looks up a player of this game by id. */
  player(id: string | null | undefined): GamePlayer | undefined;
  /** The current server time (epoch ms), e.g. to measure reaction times. */
  now(): number;
  /**
   * The network round trip to the server in ms (measured on every (re)connect; `null` until then),
   * e.g. for how far ahead a real-time game draws the player's own moves.
   */
  readonly roundTripMs: Signal<number | null>;
}

/** Injection token of the {@link GameApi}; provided by the platform for every game component. */
export const GAME_API = new InjectionToken<GameApi>('GAME_API');

/** Injects the {@link GameApi} typed with the game's view. Call in a field initializer. */
export function injectGameApi<V>(): GameApi<V> {
  return inject(GAME_API) as GameApi<V>;
}

/** What a game's `frontend/index.ts` exports as default. */
export interface GameUi {
  /**
   * Rendered on the stage while the game runs (PLAYING and REVEAL). It should fill its host
   * element and never scroll: the stage has a fixed size that fits the window.
   */
  readonly component: Type<unknown>;
}

/** Lazy loader of a game's UI chunk (see the generated registry). */
export type GameUiLoader = () => Promise<GameUi>;

/** Declares a game's UI: `export default defineGameUi({ component: MyGameComponent });` */
export function defineGameUi(ui: GameUi): GameUi {
  return ui;
}
