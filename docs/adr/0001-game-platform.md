# 0001 — Games are plug-ins on a server-authoritative platform

- **Status:** Accepted (v2, October 2026)
- **Related:** IMP-01–IMP-05, IMP-11–IMP-19, BUG-01–BUG-04, BUG-07, BUG-08, BUG-10, BUG-16

## Context

Version 1 implemented every game as its own controller, strategy, config subclass, round class,
round-info class and Angular component, glued together by `switch` statements and a shared
`Game` object that was broadcast whole to everyone. Adding a game touched ~15 files; identity came
from names in message bodies; answers leaked through shared broadcasts; timers lived in ad-hoc
schedulers that weren't bound to rounds; a refresh or dropped connection broke the client.

The product goal (`implementation-plan.md`): adding a game must be easy and self-contained, with a
clean real-time API.

## Decision

1. **Platform vs. game.** The platform owns rooms, membership, presence, host migration, the room
   phases (lobby → countdown → turns → reveal → podium), timers, scoring totals, ranking with ties,
   awards, history and the Friday Cup. A game implements `GameDefinition` (metadata, settings
   schema, validation) and `GameLogic` (turn callbacks, per-viewer `view`). Everything a game needs
   is in a small SDK (`backend/sdk`, `@ff/sdk`).
2. **One folder per game** (`games/<id>/{backend,frontend,e2e,README.md}`), discovered
   automatically by Gradle, Spring component scanning and a generated frontend registry.
3. **Server-authoritative state with personal snapshots.** After every change each member receives
   their own complete `RoomSnapshot` (`/user/queue/room`) containing the game's view rendered for
   them. Clients replace their copy — no client-side state machines to drift. High-frequency public
   data (drawing strokes) uses a separate room stream topic.
4. **Request/reply over STOMP.** Every command carries a `requestId` and receives a `Reply` with an
   error code and message, so the UI never waits forever and can show precise errors.
5. **Connection-bound identity.** A random principal per WebSocket connection, bound to a member on
   create/join/resume; a secret token resumes the seat after a refresh. Subscriptions and sends are
   authorized by an interceptor.
6. **One lock per room.** Commands, timers and disconnects of a room are serialized; game code needs
   no synchronization. Timers carry a turn token.

## Consequences

- A new game is one folder with ~4 Java classes, one Angular component and one e2e spec; no
  platform file changes (verified by `npm run new-game`).
- Secrets can only leak if a game's own `view` returns them; spectators get a dedicated view.
- Snapshots are larger than diffs; with coalescing (~50 ms) and per-viewer views this is fine for
  party-sized rooms (≤ 30 people). Games with large live data use the stream + `suppressUpdate()`.
- Rooms live in one JVM (no horizontal scaling). Acceptable for the target audience; the room store
  is the seam if that ever changes.
