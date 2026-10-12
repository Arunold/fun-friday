/**
 * Types of the server protocol (REST + STOMP), derived from the generated OpenAPI schema.
 *
 * Regenerate `api/schema.generated.ts` after changing a Java protocol record:
 * `./gradlew :app:test --tests '*ApiContractTest*'` (writes docs/openapi.json), then
 * `npm run generate:api`.
 */
import type { components } from './api/schema.generated';

type Schemas = components['schemas'];

// ------------------------------------------------------------------ room state (STOMP)
export type RoomSnapshot = Schemas['RoomSnapshot'];
export type RoomPhase = RoomSnapshot['phase'];
export type You = Schemas['You'];
export type MemberView = Schemas['MemberView'];
export type MemberRole = MemberView['role'];
export type GameRef = Schemas['GameRef'];
export type Progress = Schemas['Progress'];
export type TurnView = Schemas['TurnView'];
export type Results = Schemas['Results'];
export type StandingView = Schemas['StandingView'];
export type AwardView = Schemas['AwardView'];
export type CupEntryView = Schemas['CupEntryView'];

// ------------------------------------------------------------------ messages (STOMP)
export type Reply = Schemas['Reply'];
export type ErrorCode = NonNullable<Reply['error']>;
export type Joined = Schemas['Joined'];
export type Pong = Schemas['Pong'];
export type ServerEvent = Schemas['ServerEvent'];
export type StreamMessage = Schemas['StreamMessage'];

// ------------------------------------------------------------------ REST
export type GameSummary = Schemas['GameSummary'];
export type SettingDef = Schemas['SettingDef'];
export type SettingOption = Schemas['Option'];
export type RoomSummary = Schemas['RoomSummary'];
export type GameHistoryEntry = Schemas['GameHistoryEntry'];
export type GameRecordPlayer = Schemas['GameRecordPlayer'];
export type LeaderboardEntry = Schemas['LeaderboardEntry'];

/** Setting values by key, as sent to and received from the server. */
export type SettingValues = Record<string, unknown>;

/** Platform events on `/user/queue/events` with scope "room". */
export type RoomEventType = 'KICKED' | 'ROOM_CLOSED' | 'REPLACED' | 'HOST_CHANGED';

/** STOMP destinations. */
export const Destinations = {
  /** Personal room snapshots. */
  room: '/user/queue/room',
  /** Answers to requests. */
  reply: '/user/queue/reply',
  /** Personal events (kicked, room closed, private game events). */
  events: '/user/queue/events',
  /** Public game events of a room (e.g. drawing strokes). */
  stream: (code: string) => `/topic/rooms/${code}/stream`,
  /** Requests: `/app/rooms/{endpoint}`. */
  request: (endpoint: string) => `/app/rooms/${endpoint}`,
} as const;
