import { Injectable, signal } from '@angular/core';
import { Client, IMessage, ReconnectionTimeMode, StompSubscription } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import SockJS from 'sockjs-client';

import { Destinations, ErrorCode, Reply } from './protocol';
import { newId } from './util';

/** `connecting` before the first connection, `reconnecting` after losing it. */
export type ConnectionState = 'connecting' | 'connected' | 'reconnecting';

/** Why a request failed: a server error code or a client-side condition. */
export type RequestErrorCode = ErrorCode | 'TIMEOUT' | 'OFFLINE' | 'DISCONNECTED';

/** A failed request. `message` is human-readable and safe to show. */
export class RequestError extends Error {
  constructor(
    readonly code: RequestErrorCode,
    message: string,
  ) {
    super(message);
    this.name = 'RequestError';
  }
}

export interface RequestOptions {
  /**
   * Whether the request needs the room session to be re-established first (default `true`).
   * Only `resume` and `ping` run before that.
   */
  requiresSession?: boolean;
  timeoutMs?: number;
}

export interface SubscribeOptions {
  /** Subscribe only once the room session is re-established (default `true`). */
  requiresSession?: boolean;
}

interface PendingRequest {
  resolve(data: unknown): void;
  reject(error: RequestError): void;
  timer: ReturnType<typeof setTimeout>;
}

interface Registration {
  destination: string;
  handler(body: unknown): void;
  requiresSession: boolean;
  active?: StompSubscription;
}

const REQUEST_TIMEOUT_MS = 8000;

/**
 * The single STOMP-over-SockJS connection to the server.
 *
 * - Reconnects automatically with exponential back-off and restores every subscription.
 * - `request()` correlates each request with its reply (`requestId`) and times out.
 * - After a reconnect, requests and session-bound subscriptions wait until the room session is
 *   re-established ({@link markReady}, called by the room store after `resume`), so nothing is
 *   sent "as a stranger" and lost.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  /** Connection state for the UI. */
  readonly state = signal<ConnectionState>('connecting');
  /** Emits after every (re)connect, before the session is ready. */
  readonly connected$ = new Subject<void>();
  /** Failures of fire-and-forget requests (sent without a request id). */
  readonly errors$ = new Subject<RequestError>();

  private readonly pending = new Map<string, PendingRequest>();
  private readonly registrations = new Set<Registration>();
  private readonly connectWaiters = new Set<() => void>();
  private readonly readyWaiters = new Set<() => void>();
  private ready = false;
  private everConnected = false;

  private readonly client = new Client({
    webSocketFactory: () => new SockJS(new URL('/ws', window.location.origin).toString()),
    reconnectDelay: 500,
    maxReconnectDelay: 8000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    debug: () => undefined,
    onConnect: () => this.handleConnect(),
    onWebSocketClose: () => this.handleClose(),
    onStompError: (frame) => console.warn('STOMP error:', frame.headers['message']),
  });

  constructor() {
    this.subscribe<Reply>(Destinations.reply, (reply) => this.handleReply(reply), {
      requiresSession: false,
    });
    this.client.activate();
  }

  /** Whether the socket is connected right now. */
  isConnected(): boolean {
    return this.client.connected;
  }

  /**
   * Sends a request to `/app/rooms/{endpoint}` and resolves with the reply's data.
   *
   * @throws RequestError when the server refuses, doesn't answer in time or the connection drops
   */
  async request<T = void>(endpoint: string, body: object = {}, options: RequestOptions = {}): Promise<T> {
    const timeoutMs = options.timeoutMs ?? REQUEST_TIMEOUT_MS;
    const startedAt = Date.now();
    await (options.requiresSession === false ? this.whenConnected(timeoutMs) : this.whenReady(timeoutMs));
    return new Promise<T>((resolve, reject) => {
      const requestId = newId();
      const remaining = Math.max(1000, timeoutMs - (Date.now() - startedAt));
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new RequestError('TIMEOUT', 'The server did not answer in time. Please try again.'));
      }, remaining);
      this.pending.set(requestId, { resolve: resolve as (data: unknown) => void, reject, timer });
      this.client.publish({
        destination: Destinations.request(endpoint),
        body: JSON.stringify({ ...body, requestId }),
      });
    });
  }

  /**
   * Fire-and-forget request without a reply on success (for high-frequency game actions). Failures
   * are reported on {@link errors$}. Returns `false` (and drops the message) while not ready.
   */
  send(endpoint: string, body: object = {}): boolean {
    if (!this.client.connected || !this.ready) {
      return false;
    }
    this.client.publish({ destination: Destinations.request(endpoint), body: JSON.stringify(body) });
    return true;
  }

  /**
   * Subscribes to a destination for as long as the returned function isn't called; survives
   * reconnects. Message bodies are parsed as JSON.
   */
  subscribe<T>(destination: string, handler: (body: T) => void, options: SubscribeOptions = {}): () => void {
    const registration: Registration = {
      destination,
      handler: handler as (body: unknown) => void,
      requiresSession: options.requiresSession ?? true,
    };
    this.registrations.add(registration);
    this.activate(registration);
    return () => {
      this.registrations.delete(registration);
      if (registration.active && this.client.connected) {
        registration.active.unsubscribe();
      }
      registration.active = undefined;
    };
  }

  /** Called once the room session is re-established (or there is none) after a (re)connect. */
  markReady(): void {
    if (this.ready || !this.client.connected) {
      return;
    }
    this.ready = true;
    this.registrations.forEach((registration) => this.activate(registration));
    this.readyWaiters.forEach((wake) => wake());
    this.readyWaiters.clear();
  }

  // ------------------------------------------------------------------ internals

  private activate(registration: Registration): void {
    if (registration.active || !this.client.connected || (registration.requiresSession && !this.ready)) {
      return;
    }
    registration.active = this.client.subscribe(registration.destination, (message: IMessage) => {
      let body: unknown;
      try {
        body = JSON.parse(message.body);
      } catch {
        console.warn('Ignoring a message that is not JSON on', registration.destination);
        return;
      }
      registration.handler(body);
    });
  }

  private handleConnect(): void {
    this.everConnected = true;
    this.ready = false;
    this.state.set('connected');
    this.registrations.forEach((registration) => {
      registration.active = undefined;
      this.activate(registration);
    });
    this.connectWaiters.forEach((wake) => wake());
    this.connectWaiters.clear();
    this.connected$.next();
  }

  private handleClose(): void {
    this.ready = false;
    this.registrations.forEach((registration) => (registration.active = undefined));
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(new RequestError('DISCONNECTED', 'The connection was interrupted. Please try again.'));
    }
    this.pending.clear();
    this.state.set(this.everConnected ? 'reconnecting' : 'connecting');
  }

  private handleReply(reply: Reply): void {
    const error = reply.ok
      ? null
      : new RequestError(reply.error ?? 'INTERNAL', reply.message ?? 'Something went wrong.');
    if (!reply.requestId) {
      if (error) {
        this.errors$.next(error);
      }
      return;
    }
    const pending = this.pending.get(reply.requestId);
    if (!pending) {
      return; // timed out already
    }
    this.pending.delete(reply.requestId);
    clearTimeout(pending.timer);
    if (error) {
      pending.reject(error);
    } else {
      pending.resolve(reply.data);
    }
  }

  private whenConnected(timeoutMs: number): Promise<void> {
    return this.client.connected ? Promise.resolve() : this.wait(this.connectWaiters, timeoutMs);
  }

  private whenReady(timeoutMs: number): Promise<void> {
    return this.client.connected && this.ready ? Promise.resolve() : this.wait(this.readyWaiters, timeoutMs);
  }

  private wait(waiters: Set<() => void>, timeoutMs: number): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const wake = () => {
        clearTimeout(timer);
        resolve();
      };
      const timer = setTimeout(() => {
        waiters.delete(wake);
        reject(new RequestError('OFFLINE', 'No connection to the server — still trying to reconnect.'));
      }, timeoutMs);
      waiters.add(wake);
    });
  }
}
