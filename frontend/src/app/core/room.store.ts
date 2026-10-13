import { LiveAnnouncer } from '@angular/cdk/a11y';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, filter } from 'rxjs';

import { ClockService } from './clock.service';
import { NotifyService } from './notify.service';
import { Profile } from './profile.service';
import { Destinations, Joined, RoomSnapshot, ServerEvent, SettingValues, StreamMessage } from './protocol';
import { RealtimeService, RequestError } from './realtime.service';
import { readStorage, shallowArrayEqual, shallowEqual, writeStorage } from './util';

/** What a tab needs to reclaim its seat after a refresh or reconnect. */
export interface RoomSession {
  code: string;
  memberId: string;
  token: string;
}

/** A private game event sent to this player (`GameContext.sendTo` on the server). */
export interface GameEvent<T = unknown> {
  type: string;
  payload: T;
}

/** Outcome of a game action: games show `message` inline when it fails. */
export type ActionResult = { ok: true } | { ok: false; code: string; message: string };

const SESSION_KEY = 'ff.session';
/** Errors after which a stored session can never be resumed. */
const SESSION_GONE = new Set(['SESSION_EXPIRED', 'ROOM_NOT_FOUND', 'NOT_IN_ROOM']);

/**
 * The room this tab is in: the latest personal snapshot from the server plus every room command.
 *
 * The server is the single source of truth — it pushes a complete snapshot after every change and
 * the store simply replaces its copy. The session (room code + secret token) lives in
 * `sessionStorage`, so a refresh resumes the same seat while a second tab can join as someone else.
 */
@Injectable({ providedIn: 'root' })
export class RoomStore {
  private readonly realtime = inject(RealtimeService);
  private readonly clock = inject(ClockService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);
  private readonly announcer = inject(LiveAnnouncer);

  /** The latest snapshot of the room (null when not in a room or not loaded yet). */
  readonly snapshot = signal<RoomSnapshot | null>(null);
  readonly session = signal<RoomSession | null>(readStorage<RoomSession>(globalThis.sessionStorage, SESSION_KEY));
  /** True while the stored session is being resumed after a (re)connect. */
  readonly resuming = signal(false);

  readonly phase = computed(() => this.snapshot()?.phase ?? null);
  readonly code = computed(() => this.snapshot()?.code ?? this.session()?.code ?? null);
  // Snapshots are new objects every time; equality functions keep unchanged parts stable so only
  // what really changed re-renders.
  readonly you = computed(() => this.snapshot()?.you ?? null, { equal: shallowEqual });
  readonly isHost = computed(() => this.snapshot()?.you.host ?? false);
  readonly isPlaying = computed(() => this.snapshot()?.you.playing ?? false);
  readonly players = computed(() => this.snapshot()?.players ?? [], { equal: shallowArrayEqual });
  readonly spectators = computed(() => this.snapshot()?.spectators ?? [], { equal: shallowArrayEqual });
  readonly members = computed(() => [...this.players(), ...this.spectators()]);
  readonly me = computed(() => this.members().find((m) => m.id === this.snapshot()?.you.memberId) ?? null);
  readonly host = computed(() => this.members().find((m) => m.host) ?? null);
  readonly game = computed(() => this.snapshot()?.game ?? null, { equal: shallowEqual });
  readonly settings = computed<SettingValues>(() => this.snapshot()?.settings ?? {}, { equal: shallowEqual });
  readonly progress = computed(() => this.snapshot()?.progress ?? null, { equal: shallowEqual });
  readonly view = computed(() => this.snapshot()?.view ?? null);
  readonly lastTurn = computed(() => this.snapshot()?.lastTurn ?? null);
  readonly results = computed(() => this.snapshot()?.results ?? null);
  /** Server time at which the countdown, turn or reveal ends. */
  readonly phaseEndsAt = computed(() => this.snapshot()?.phaseEndsAt ?? null);
  /** Length of the current timed phase in milliseconds. */
  readonly phaseDurationMs = computed(() => {
    const snapshot = this.snapshot();
    return snapshot?.phaseEndsAt != null && snapshot.phaseStartedAt != null
      ? Math.max(1, snapshot.phaseEndsAt - snapshot.phaseStartedAt)
      : null;
  });
  /** Milliseconds left in the current timed phase (ticks with the server clock). */
  readonly timeLeftMs = computed(() => {
    const endsAt = this.phaseEndsAt();
    return endsAt == null ? null : Math.max(0, endsAt - this.clock.now());
  });

  /** Public game events of the room (`GameContext.stream`). */
  readonly stream$ = new Subject<StreamMessage>();
  /** Private game events for this player (`GameContext.sendTo`). */
  readonly gameEvents$ = new Subject<GameEvent>();

  private unsubscribeStream: (() => void) | null = null;
  private streamCode: string | null = null;
  /** While creating/joining: the room whose first snapshot may arrive before the reply. */
  private expecting: string | null = null;
  private early: RoomSnapshot | null = null;
  /** A session taken over by another tab; "Play here" resumes it. */
  private parked: RoomSession | null = null;
  /** Set while this host ends the room, so the closing event doesn't look like an accident. */
  private endingRoom = false;

  constructor() {
    this.realtime.subscribe<RoomSnapshot>(Destinations.room, (s) => this.applySnapshot(s), { requiresSession: false });
    this.realtime.subscribe<ServerEvent>(Destinations.events, (e) => this.handleEvent(e), { requiresSession: false });
    // Failures of fire-and-forget game actions (`sendFast`). A stroke or key press that arrives just
    // after a turn ended, or that the game turns down, is expected in real-time play and not worth a
    // toast; rate limiting and malformed requests still are.
    this.realtime.errors$
      .pipe(filter((error) => error.code !== 'INVALID_ACTION' && error.code !== 'WRONG_PHASE'))
      .subscribe((error) => this.notify.error(error));
    this.realtime.connected$.subscribe(() => void this.restoreSession());
    if (this.realtime.isConnected()) {
      void this.restoreSession();
    }
  }

  // ================================================================== joining

  /** Creates a room and joins it as host. Resolves with the room code. */
  async create(profile: Profile, gameId: string, settings: SettingValues): Promise<string> {
    this.expect('*');
    try {
      const joined = await this.realtime.request<Joined>('create', { ...profile, gameId, settings });
      this.enter(joined);
      return joined.code;
    } finally {
      this.expect(null);
    }
  }

  /** Joins a room as player (or spectator). */
  async join(code: string, profile: Profile, spectator: boolean): Promise<Joined> {
    this.expect(code);
    try {
      const joined = await this.realtime.request<Joined>('join', { code, ...profile, spectator });
      this.enter(joined);
      return joined;
    } finally {
      this.expect(null);
    }
  }

  /** Whether this tab has a seat in the given room. */
  hasSessionFor(code: string): boolean {
    return this.session()?.code === code;
  }

  /** After another tab took over the seat: take it back in this tab. */
  async playHere(): Promise<string | null> {
    const parked = this.parked;
    if (!parked) {
      return null;
    }
    this.parked = null;
    this.saveSession(parked);
    await this.restoreSession();
    return this.session()?.code ?? null;
  }

  // ================================================================== room commands

  /** Leaves the room for good. */
  async leave(): Promise<void> {
    try {
      await this.realtime.request('leave', {}, { timeoutMs: 4000 });
    } catch {
      // Leaving locally is what matters; the server drops the seat after the grace period anyway.
    } finally {
      this.clear();
    }
  }

  kick(memberId: string): Promise<void> {
    return this.realtime.request('kick', { memberId });
  }

  updateSettings(gameId: string, settings: SettingValues): Promise<void> {
    return this.realtime.request('settings', { gameId, settings });
  }

  setReady(ready: boolean): Promise<void> {
    return this.realtime.request('ready', { ready });
  }

  becomePlayer(): Promise<void> {
    return this.realtime.request('become-player');
  }

  start(): Promise<void> {
    return this.realtime.request('start');
  }

  playAgain(): Promise<void> {
    return this.realtime.request('play-again');
  }

  /** Closes the room for everyone (host only). */
  async end(): Promise<void> {
    this.endingRoom = true;
    try {
      await this.realtime.request('end');
    } catch (error) {
      this.endingRoom = false;
      throw error;
    }
  }

  // ================================================================== game actions

  /** Sends a game action and reports whether the game accepted it. */
  async act(type: string, payload?: unknown): Promise<ActionResult> {
    try {
      await this.realtime.request('action', { type, payload: payload ?? null });
      return { ok: true };
    } catch (error) {
      if (error instanceof RequestError) {
        return { ok: false, code: error.code, message: error.message };
      }
      return { ok: false, code: 'INTERNAL', message: 'Something went wrong.' };
    }
  }

  /**
   * Sends a high-frequency game action without waiting for an answer. Rejections by the game (or
   * because the turn just ended) are dropped silently; rate limiting is still toasted.
   */
  actFast(type: string, payload?: unknown): boolean {
    return this.realtime.send('action', { type, payload: payload ?? null });
  }

  // ================================================================== internals

  private async restoreSession(): Promise<void> {
    const session = this.session();
    if (!session) {
      this.realtime.markReady();
      return;
    }
    this.resuming.set(true);
    try {
      const joined = await this.realtime.request<Joined>(
        'resume',
        { code: session.code, token: session.token },
        { requiresSession: false },
      );
      this.saveSession({ code: joined.code, memberId: joined.memberId, token: joined.token });
      this.subscribeStream(joined.code);
    } catch (error) {
      if (error instanceof RequestError && SESSION_GONE.has(error.code)) {
        this.clear();
        if (this.router.url.startsWith('/room/')) {
          void this.router.navigate(['/oops', 'expired']);
        }
      }
      // Otherwise (timeout, connection lost) the next reconnect tries again.
    } finally {
      this.resuming.set(false);
      this.realtime.markReady();
    }
  }

  private enter(joined: Joined): void {
    this.parked = null;
    this.saveSession({ code: joined.code, memberId: joined.memberId, token: joined.token });
    if (this.early?.code === joined.code) {
      this.applySnapshot(this.early);
    }
    this.early = null;
    this.subscribeStream(joined.code);
  }

  private expect(code: string | null): void {
    this.expecting = code;
    if (code === null) {
      this.early = null;
    }
  }

  private applySnapshot(snapshot: RoomSnapshot): void {
    if (this.session()?.code !== snapshot.code) {
      // The first snapshot of a room we're joining can overtake the join reply.
      if (this.expecting === '*' || this.expecting === snapshot.code) {
        this.early = snapshot;
      }
      return;
    }
    this.clock.observe(snapshot.serverTime);
    const previous = this.snapshot();
    this.snapshot.set(snapshot);
    this.announce(previous, snapshot);
  }

  private handleEvent(event: ServerEvent): void {
    if (event.scope === 'game') {
      this.gameEvents$.next({ type: event.type, payload: event.payload });
      return;
    }
    switch (event.type) {
      case 'KICKED':
        this.exit('kicked');
        break;
      case 'ROOM_CLOSED':
        if (this.endingRoom) {
          this.endingRoom = false;
          this.clear();
          this.notify.info('Room closed.');
          void this.router.navigate(['/']);
        } else {
          this.exit('closed', event.message);
        }
        break;
      case 'REPLACED':
        this.parked = this.session();
        this.exit('replaced');
        break;
      default:
        if (event.message) {
          this.notify.info(event.message);
        }
    }
  }

  private exit(reason: string, message?: string): void {
    this.clear();
    void this.router.navigate(['/oops', reason], message ? { queryParams: { message } } : {});
  }

  private subscribeStream(code: string): void {
    if (this.streamCode === code) {
      return;
    }
    this.unsubscribeStream?.();
    this.streamCode = code;
    this.unsubscribeStream = this.realtime.subscribe<StreamMessage>(Destinations.stream(code), (message) =>
      this.stream$.next(message),
    );
  }

  private clear(): void {
    this.unsubscribeStream?.();
    this.unsubscribeStream = null;
    this.streamCode = null;
    this.snapshot.set(null);
    this.saveSession(null);
  }

  private saveSession(session: RoomSession | null): void {
    this.session.set(session);
    writeStorage(globalThis.sessionStorage, SESSION_KEY, session);
  }

  /** Screen-reader announcements for the big moments of a game. */
  private announce(previous: RoomSnapshot | null, next: RoomSnapshot): void {
    if (previous?.phase === next.phase && previous.progress?.round === next.progress?.round) {
      return;
    }
    const progress = next.progress;
    switch (next.phase) {
      case 'STARTING':
        void this.announcer.announce(`${next.game.name} is about to start.`);
        break;
      case 'PLAYING':
        if (progress) {
          void this.announcer.announce(`Round ${progress.round} of ${progress.totalRounds}.`);
        }
        break;
      case 'REVEAL':
        void this.announcer.announce(next.lastTurn?.headline || 'Time is up.');
        break;
      case 'FINISHED': {
        const winners = next.results?.standings.filter((s) => s.rank === 1).map((s) => s.name) ?? [];
        void this.announcer.announce(winners.length ? `Game over. ${winners.join(' and ')} won!` : 'Game over.');
        break;
      }
      default:
        break;
    }
  }
}
