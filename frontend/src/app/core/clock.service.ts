import { Injectable, inject, signal } from '@angular/core';

import { Pong } from './protocol';
import { RealtimeService } from './realtime.service';

const TICK_MS = 250;
const SAMPLES = 3;

/**
 * Server-synchronized time. Deadlines in snapshots are server epoch milliseconds; comparing them
 * with the local clock would make timers run early or late on machines with a skewed clock.
 *
 * On every (re)connect a few pings estimate the offset (NTP-style, keeping the sample with the
 * lowest round-trip time). Until then the `serverTime` of snapshots gives a rough estimate.
 */
@Injectable({ providedIn: 'root' })
export class ClockService {
  private readonly realtime = inject(RealtimeService);
  private offsetMs = 0;
  private bestRoundTrip = Number.POSITIVE_INFINITY;

  /** Server time, updated four times a second; read it in templates and computed signals. */
  readonly now = signal(Date.now());

  constructor() {
    setInterval(() => this.now.set(this.serverNow()), TICK_MS);
    this.realtime.connected$.subscribe(() => void this.sync());
    if (this.realtime.isConnected()) {
      void this.sync();
    }
  }

  /** The current server time in epoch milliseconds. */
  serverNow(): number {
    return Date.now() + this.offsetMs;
  }

  /** Rough correction from a snapshot's `serverTime`, used until a ping has completed. */
  observe(serverTime: number): void {
    if (this.bestRoundTrip === Number.POSITIVE_INFINITY) {
      this.offsetMs = serverTime - Date.now();
    }
  }

  private async sync(): Promise<void> {
    this.bestRoundTrip = Number.POSITIVE_INFINITY;
    for (let i = 0; i < SAMPLES; i++) {
      const sentAt = Date.now();
      try {
        const pong = await this.realtime.request<Pong>('ping', { clientTime: sentAt }, { requiresSession: false });
        const receivedAt = Date.now();
        const roundTrip = receivedAt - sentAt;
        if (roundTrip < this.bestRoundTrip) {
          this.bestRoundTrip = roundTrip;
          this.offsetMs = pong.serverTime + roundTrip / 2 - receivedAt;
        }
      } catch {
        return; // disconnected again; the next connect re-syncs
      }
    }
    this.now.set(this.serverNow());
  }
}
