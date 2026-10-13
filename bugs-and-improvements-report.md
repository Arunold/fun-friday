# Fun Friday — Bugs & Improvements Report

| | |
| --- | --- |
| **Date** | 2026-10-09 |
| **Scope** | `main` @ `7a85bbe` — backend (Spring Boot + STOMP), frontend (Angular 20), build & deploy files |
| **Method** | Static review of every tracked file, cross-checked against the `@stomp/stompjs` 7.2.1 source and a Prettier 3.2.5 run |
| **Not done** | The app was **not run** (no JDK 21 on the review machine — see IMP-40). Runtime behaviour is inferred from the code; repro steps are given so each item can be confirmed. |
| **Status** | ✅ **Everything is done — all 37 bugs and all 50 improvements** (2026-10-12). The app was rebuilt on a modular game platform; each item below now has a **Status** (✅ + where/how). [§4](#4-resolution-summary) sums up the new layout and how it was verified. |
| **Verified on** | JDK 25 · Gradle 9.8.1 · Spring Boot 4.1.1 · Node 20.19 · Angular 21.2 · Playwright 1.63 (Chrome) · Docker 29 |

Line links point to the code as of `7a85bbe` and will drift as fixes land. IDs are stable — reference them in commits/PRs (e.g. `fix(BUG-08): …`).

## Summary

| Severity | Meaning | Count | Fixed |
| --- | --- | ---: | :---: |
| 🔴 Critical | Breaks a core flow or freezes the game for everyone | 4 | ✅ 4 |
| 🟠 High | Wrong outcome, cheating vector, or a feature that silently doesn't work | 6 | ✅ 6 |
| 🟡 Medium | Visible bug with a workaround or limited blast radius | 13 | ✅ 13 |
| 🔵 Low | Cosmetic, tooling, latent or edge-case issue | 14 | ✅ 14 |
| | **Total bugs** | **37** | ✅ **37** |

In addition, **50 improvements / enhancements** are proposed across architecture, reliability, security, performance, gameplay, UX, developer experience, deployment and product — see [§2](#2-improvements--enhancements).

✅ **All 37 bugs and all 50 improvements are done** — see [§4 Resolution summary](#4-resolution-summary).

### Fix first

1. **Connection handling** — BUG-01, BUG-02. One rewrite of `WebsocketService` fixes refresh and reconnect.
2. **Player lifecycle** — BUG-03, BUG-04, BUG-11. Ghost players and leavers freeze or slow down games.
3. **Round timing** — BUG-05, BUG-06. Wrong default durations; Reaction rounds never end early.
4. **Answer leaks** — BUG-07, BUG-08. The Pictionary word and round answers reach every client.
5. **Pictionary feedback** — BUG-09. Guessers get no live feedback.
6. **Identity** — BUG-10. Anyone can act as anyone (including the host) in any game.

> [!NOTE]
> ✅ All six areas are fixed — see the **Status** line of each item. Links in §1–§2 point to the old code at `7a85bbe`, which has been replaced; the status lines link to the new code.

## Contents

- [1. Bugs](#1-bugs) — [Critical](#11-critical) · [High](#12-high) · [Medium](#13-medium) · [Low](#14-low)
- [2. Improvements & enhancements](#2-improvements--enhancements) — [Architecture](#21-architecture--game-platform) · [Reliability](#22-reliability--real-time) · [Security](#23-security--fair-play) · [Performance](#24-performance) · [Gameplay](#25-gameplay) · [UX](#26-ux--visual-design) · [Developer experience](#27-developer-experience--quality) · [Deployment](#28-deployment) · [Product](#29-product--roadmap-alignment)
- [3. Suggested roadmap](#3-suggested-roadmap)
- [4. Resolution summary](#4-resolution-summary)
- [Appendix: review notes](#appendix-review-notes)

---

## 1. Bugs

Each entry lists **Where**, **Symptom**, **Cause** and **Fix**; critical and high items also include a **Repro**.

### 1.1 Critical

#### 🔴 BUG-01 — Refreshing a game page breaks all live updates

- **Area:** Frontend · real-time
- **Where:** [websocket.service.ts L36–50](frontend/src/app/services/websocket.service.ts#L36-L50), called from [base-game.component.ts L108–113](frontend/src/app/components/games/base-game.component.ts#L108-L113) (same pattern in [results.component.ts L91–92](frontend/src/app/components/results/results.component.ts#L91-L92))
- **Symptom:** After F5 on `/game/:type/:id` the view stays empty and never receives updates. Console: `TypeError: There is no underlying STOMP connection`.
- **Cause:** `connect()` calls `activate()`, which sets `active = true` synchronously while the socket is still connecting. `subscribe()` only queues when `!active`, so it calls `stompClient.subscribe()` immediately — and stompjs 7 throws when not `connected`. The exception aborts `initializeGame()`, so the `/terminated` and `/removed` subscriptions, Pictionary's `/drawing` subscription and the REST fallback (`getGameDetails`) never run. The pending queue is effectively dead code because every caller calls `connect()` first.
- **Repro:** Create a game, join from a second browser, press F5 in either tab (lobby or mid-round).
- **Fix:** Queue whenever `!connected`; keep a registry of `topic → callback` and (re)subscribe everything in `onConnect`; return an unsubscribe handle (or an `Observable`) from `subscribe()`.
- **Status:** ✅ **Fixed.** [`RealtimeService`](frontend/src/app/core/realtime.service.ts) keeps a registry of subscriptions and (re)subscribes all of them on every connect — nothing calls stompjs before it is connected. After F5, [`RoomStore`](frontend/src/app/core/room.store.ts) resumes the seat with the member token kept in `sessionStorage`, and the server answers with the full personal snapshot, so the page lands in the same phase. E2E: *a refresh during the game keeps the seat and the phase*.

#### 🔴 BUG-02 — A dropped connection is never recovered

- **Area:** Frontend · real-time
- **Where:** [websocket.service.ts L14–21](frontend/src/app/services/websocket.service.ts#L14-L21), [L42–44](frontend/src/app/services/websocket.service.ts#L42-L44), [L65–73](frontend/src/app/services/websocket.service.ts#L65-L73)
- **Symptom:** After a Wi-Fi drop, laptop sleep or proxy idle timeout, stompjs reconnects (every 5 s) but the page never receives another game update. Actions sent while disconnected are silently dropped.
- **Cause:** STOMP subscriptions don't survive a reconnect. `onConnect` only flushes `pendingSubscriptions` (normally empty), while `subscriptions` still holds the dead handles — so re-subscribing the same topic returns early at L42–44. `sendMessage()` drops messages while disconnected without telling the caller.
- **Repro:** Join a game, put the laptop to sleep (or cut the network) long enough for the WebSocket to close, then wake it: the client reconnects but no further updates arrive.
- **Fix:** Same registry as BUG-01, re-subscribed on every `onConnect` (clear stale handles in `onWebSocketClose`); expose a connection-state signal for a "Reconnecting…" banner and re-fetch the game via REST after reconnecting (IMP-12).
- **Status:** ✅ **Fixed.** stompjs reconnects automatically (fast first retry, then back-off) with 10 s heart-beats both ways. On every reconnect the subscriptions are restored and a session gate holds requests until the seat has been resumed; the server then re-sends the snapshot. Requests made while offline wait for the connection or fail visibly after a timeout — never silently — and a *Reconnecting…* banner ([connection-banner](frontend/src/app/shared/connection-banner.component.ts)) plus an offline icon in the room toolbar show the state.

#### 🔴 BUG-03 — Players who close the tab stay in the game ("ghost players")

- **Area:** Backend · player lifecycle
- **Where:** [WebSocketConfig.java](backend/src/main/java/com/example/funfridaygame/config/WebSocketConfig.java) (no session tracking or disconnect handling); [GameController.java L323–351](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L323-L351) (only an explicit leave removes a player)
- **Symptom:** Closing the tab, losing the network or pressing browser Back leaves the player in the game. Rounds can no longer end early (not everyone will answer), Pictionary gives drawing turns to ghosts (a blank canvas for the whole timer), and ghosts show up in results. If the **host** disappears, the room lives on but nobody can start, change or end it — and it is never removed from memory.
- **Cause:** Only the `/app/leave` message removes a player. There is no `SessionDisconnectEvent` listener and no mapping from STOMP session to player.
- **Repro:** Three players in Number Guess; one closes the tab; the other two guess → the round still runs the full timer.
- **Fix:** Record `sessionId → (gameId, player)` on create/join; on disconnect mark the player offline and remove them after a grace period (~30 s, so a refresh doesn't kick them); re-check round end; hand host to the next player instead of terminating; evict empty games (IMP-11, IMP-13, IMP-14).
- **Status:** ✅ **Fixed.** Every WebSocket gets a principal ([`StompSecurityInterceptor`](backend/app/src/main/java/com/example/funfridaygame/config/StompSecurityInterceptor.java)) that create/join/resume bind to a member. A closed socket marks the member offline ([`PresenceListener`](backend/app/src/main/java/com/example/funfridaygame/engine/PresenceListener.java)); after the 30 s grace period [`RoomService`](backend/app/src/main/java/com/example/funfridaygame/engine/RoomService.java) removes them, re-checks the turn and hands the host role to the next online player; empty and idle rooms are evicted. Offline players never block a turn (`GameContext.activePlayers()`), and leaving the room page by navigation (e.g. browser Back) asks and then really leaves ([leave-room.guard.ts](frontend/src/app/pages/room/leave-room.guard.ts)). Tests: `RoomFlowIntegrationTest` (seat kept on return, removed after the grace period, host migration) and e2e (*host leaves*, *going back from the room…*).

#### 🔴 BUG-04 — Pictionary freezes when a player leaves mid-round

- **Area:** Backend · Pictionary
- **Where:** [PictionaryStrategy.java L51–62](backend/src/main/java/com/example/funfridaygame/service/game/PictionaryStrategy.java#L51-L62), scheduled from [PictionaryController.java L248–260](backend/src/main/java/com/example/funfridaygame/controller/PictionaryController.java#L248-L260)
- **Symptom:** The game sticks on a turn-result screen forever — no next turn, no final results.
- **Cause:** `totalTurns` is fixed when the round starts. After someone leaves, `game.getPlayers().get(currentTurn - 1)` throws `IndexOutOfBoundsException` on the last turn. It runs inside a `ScheduledExecutorService` task, so the exception is swallowed (nothing logged, nothing recovers). Leaving also shifts indices, so someone may be skipped or draw twice.
- **Repro:** Four players start Pictionary → after turn 2 a non-host clicks **Leave** → turn 3 plays, turn 4 never starts.
- **Fix:** Snapshot the drawer order (names) at round start, skip players who are gone, end the round when the queue is empty; wrap every scheduled task in `try/catch` with logging (IMP-15).
- **Status:** ✅ **Fixed.** [`GameRun`](backend/runtime/src/main/java/com/example/funfridaygame/runtime/GameRun.java) contains every exception thrown by game code and its timers are turn-scoped, so nothing can hang a room any more. In [Pictionary](games/pictionary/README.md) the drawer order is fixed per round (`turnsInRound`), drawers who left or are offline are skipped (`skipTurn`), and a drawer who leaves or disconnects mid-turn ends the turn at once without points. Tests: `PictionaryGameTest.drawersWhoAreOfflineOrGoneAreSkipped`, `theDrawerLeavingEndsTheTurnWithoutPointsAndTheGameGoesOn`, `theGameEndsWhenEverybodyElseLeft`.

### 1.2 High

#### 🟠 BUG-05 — Every game defaults to 30-second rounds

- **Area:** Backend + frontend · settings
- **Where:** [GameController.java L87–89](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L87-L89), [L273–275](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L273-L275), [L432–442](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L432-L442); [game-setup.component.ts L64–72](frontend/src/app/components/game-setup/game-setup.component.ts#L64-L72)
- **Symptom:** Unless the host changes **Round Timer**, Sliding Puzzle *Hard* (5×5) gets 30 s (near-unsolvable), Speed Typing *Hard (long text)* gets 30 s, and every Reaction Showdown round lasts 30 s although the action is over in ~5 s.
- **Cause:** One generic timer control (default 30) for every game, and the server falls back to 30 as well — so `getDurationForDifficulty()` always returns the passed value; its 60/90/120 s branch is unreachable.
- **Repro:** Host Sliding Puzzle, pick *Hard*, keep the default timer → 30 s rounds.
- **Fix:** Per-game defaults owned by each strategy's `createDefaultConfig()`; the client sends `null` for "default"; show timer options per game (IMP-04).
- **Status:** ✅ **Fixed.** Each game declares its own settings and defaults (`SettingDef`), and the host form is generated from them. Games whose time depends on difficulty set their own deadline: [Sliding Puzzle](games/sliding-puzzle/README.md) *Auto* = 60 / 150 / 300 s for 3×3 / 4×4 / 5×5, [Speed Typing](games/speed-typing/README.md) *Auto* = 45 / 75 / 120 s by text length, [Reaction Showdown](games/reaction-showdown/README.md) derives it from its signals (no timer setting), [Memory Match](games/memory-match/README.md) has a time per move plus a board-sized safety net. Other defaults: [Number Guess](games/number-guess/README.md) 30 s, [Word Scramble](games/word-scramble/README.md) 45 s, [Trivia Blitz](games/trivia-blitz/README.md) 15 s, [Pictionary](games/pictionary/README.md) 80 s (+ 10 s word choice). Tests e.g. `theTimeLimitDependsOnTheBoardSize`, `theRoundTimerDependsOnTheTextLength`, `settingsOfferRoundsSignalsAndFakeOutsButNoRoundTimer`.

#### 🟠 BUG-06 — Reaction Showdown rounds never end early

- **Area:** Backend · Reaction Showdown
- **Where:** [ReactionShowdownService.java L49–70](backend/src/main/java/com/example/funfridaygame/service/game/ReactionShowdownService.java#L49-L70)
- **Symptom:** Everyone has tapped, yet all players wait for the full round timer (30 s by default) before results.
- **Cause:** `handleTap()` records the tap and broadcasts but never triggers an early end. (Number Guess and Word Scramble call `checkRoundEnd()`; Speed Typing and Sliding Puzzle reveal directly when everyone is done.)
- **Repro:** Two players, both tap after GO → nothing happens until the timer runs out.
- **Fix:** Call `roundManager.checkRoundEnd(gameId, game)` after `handleTap()` in `ReactionShowdownController` (the strategy's `allPlayersAnswered` already exists).
- **Status:** ✅ **Fixed.** In [Reaction Showdown](games/reaction-showdown/README.md) a signal ends the moment every connected player has tapped or false-started (re-checked when someone leaves), and the turn ends right after the last signal. Tests: `theFastestTapWinsAndTheSignalEndsOnceEveryoneTapped`, `aPlayerLeavingMidSignalEndsItWhenEveryoneElseHasTapped`.

#### 🟠 BUG-07 — Pictionary: the secret word is sent to every player

- **Area:** Backend + frontend · Pictionary
- **Where:** [PictionaryRoundInfo.java L45–51](backend/src/main/java/com/example/funfridaygame/model/roundinfo/PictionaryRoundInfo.java#L45-L51); [pictionary-game.component.ts L354–357](frontend/src/app/components/games/pictionary/pictionary-game.component.ts#L354-L357); also `GET /api/games/{id}`
- **Symptom:** Any guesser can read the word in DevTools (WebSocket frames or the REST response) and win every turn.
- **Cause:** Both branches of the `if` include `wordToDraw` (the comment says "frontend filters"); the client even builds the hint from the word instead of using the server-computed `wordHint`.
- **Repro:** As a guesser, open DevTools → Network → WS and inspect the turn-start frame: `currentRoundInfo.wordToDraw`.
- **Fix:** Send the word only to the drawer (user destination, IMP-05); use `wordHint` for everyone else; strip it from REST responses.
- **Status:** ✅ **Fixed.** Views are per viewer: in [Pictionary](games/pictionary/README.md) only the drawer (and guessers who already got it) receive the word, and only the drawer gets the three choices; everybody else gets the mask with hint letters. No REST endpoint exposes game state. Tests: `theDrawerPicksOneOfThreeWordsThatNobodyElseSees`, `guessesShowUpLiveButTheFeedNeverRevealsTheWord`; the e2e spec checks that guessers have no word on the page.

#### 🟠 BUG-08 — Round answers leak at the start of rounds 2+

- **Area:** Backend · round-based games
- **Where:** [GameRoundManager.java L97–99](backend/src/main/java/com/example/funfridaygame/controller/GameRoundManager.java#L97-L99); [Game.java L57–62](backend/src/main/java/com/example/funfridaygame/model/Game.java#L57-L62)
- **Symptom:** The first broadcast of every round after the first contains the answer — Number Guess `targetNumber`, Word Scramble `originalWord` — until the next update overwrites it. Related leaks: Number Guess shows everyone's guesses during the round, and Reaction Showdown sends the fake-out flag (`fakeOut`) before the signal.
- **Cause:** `setCurrentRound()` rebuilds the round info while `gameState` is still `ROUND_RESULT` from the previous round (`includeTarget = true`); only afterwards is the state set to the active one.
- **Repro:** Play Word Scramble with 2+ rounds; in DevTools inspect the first frame of round 2 → `originalWord` is present.
- **Fix:** Set the state before assigning the round (or pass the state explicitly to the round-info factory); hide other players' answers until reveal with per-viewer views (IMP-05).
- **Status:** ✅ **Fixed at the root.** There is no shared, mutable round info any more: every snapshot is built per viewer by `GameLogic.view(ctx, viewer)` ([GameLogic](backend/sdk/src/main/java/com/example/funfridaygame/sdk/GameLogic.java)) after the runtime has started the turn, and answers are only included once the turn is in REVEAL. Other players' answers stay private during a turn (Number Guess shows only your own guesses; Reaction Showdown never announces a fake-out in advance). The game rule tests check that no view contains the secret before the reveal (e.g. `NumberGuessGameTest.hintsArePrivateUntilTheReveal`).

#### 🟠 BUG-09 — Pictionary guessers get no live feedback

- **Area:** Backend + frontend · Pictionary
- **Where:** [PictionaryController.java L185–189](backend/src/main/java/com/example/funfridaygame/controller/PictionaryController.java#L185-L189) vs [pictionary-game.component.ts L120–126](frontend/src/app/components/games/pictionary/pictionary-game.component.ts#L120-L126) and [L141–151](frontend/src/app/components/games/pictionary/pictionary-game.component.ts#L141-L151)
- **Symptom:** Guesses don't appear in the guess list, a correct guess doesn't switch the guesser to a "you got it" state, and nobody sees a live feed — everything updates only when the turn ends.
- **Cause:** The server publishes guesses to `/topic/game/{id}/guesses`; the client subscribes to `/topic/game/{id}/guess` (and its handler is empty). The game state isn't re-broadcast after a guess, and round info exposes only the *first* `correctGuesser`, so later correct guessers are never recognised by the client.
- **Repro:** Three players; a guesser types the exact word → their UI doesn't change until the timer ends.
- **Fix:** Use one topic name, broadcast a (masked) guess event plus updated round info after each guess, expose `correctGuessers` as a list.
- **Status:** ✅ **Fixed.** One action channel for every game. In [Pictionary](games/pictionary/README.md) each guess updates everybody's feed at once ("🎉 Bob guessed it!" never shows the word), correct guessers switch to *You got it!* and see the word, and a near miss sends a private *So close!* (`ctx.sendTo`). Tests: `guessesShowUpLiveButTheFeedNeverRevealsTheWord`, `nearMissesGetAPrivateSoCloseAndStayHidden`; e2e *a full round…*.

#### 🟠 BUG-10 — The server trusts whatever name the client sends

- **Area:** Backend · security
- **Where:** Every `@MessageMapping`, e.g. [GameController.java L193–206](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L193-L206) (start), [L323–351](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L323-L351) (leave), [NumberGuessController.java L27–47](backend/src/main/java/com/example/funfridaygame/controller/NumberGuessController.java#L27-L47), [WordScrambleController.java L46–49](backend/src/main/java/com/example/funfridaygame/controller/WordScrambleController.java#L46-L49)
- **Symptom:** Anyone who knows a game code (shown in the lobby and in invite links) can act as any player — including the host: start the game, remove players, or terminate the room by sending `/app/leave` with the host's name. Names that aren't in the game are accepted too: an outsider's guess counts toward "everyone answered", and an outsider's correct Word Scramble guess ends the round for everyone.
- **Cause:** Identity comes from the `sender` / `playerName` fields of the message body; handlers don't check that the name belongs to the game, let alone to the connection.
- **Repro:** From any STOMP client connected to `/ws`, publish to `/app/leave` with `{"sender":"<host name>","content":"<CODE>"}` → the game is terminated for everyone.
- **Fix:** Bind identity to the STOMP session at create/join and reject actions from sessions that aren't members (or aren't the host) — IMP-17.
- **Status:** ✅ **Fixed.** Identity is bound to the connection: the server assigns each STOMP connection a random principal on CONNECT, create/join/resume bind it to a member, and every command is resolved from the principal — names in payloads are ignored. Host-only commands check the host role; clients may only subscribe to their own `/user/queue/*` destinations and their room's stream, and only send to `/app/**`. Test: `RoomFlowIntegrationTest.rejectsInvalidRequests`.

### 1.3 Medium

#### 🟡 BUG-11 — Departed players still count as "answered"; leaving never re-checks the round

- **Area:** Backend · round logic
- **Where:** [NumberGuessStrategy.java L60–63](backend/src/main/java/com/example/funfridaygame/service/game/NumberGuessStrategy.java#L60-L63) (same count-vs-`players.size()` pattern in the other strategies); [GameController.java L346–350](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L346-L350)
- **Symptom:** A round can end before a remaining player has answered; conversely, if the only player who hadn't answered leaves, everyone still waits for the full timer.
- **Cause:** Answers are counted against `players.size()` and a leaver's answer stays in the map (3 players: A answers and leaves, B answers → 2 ≥ 2 → round ends while C hasn't answered). `leaveGame()` removes the player but never calls `checkRoundEnd()`.
- **Fix:** Count only answers from current players (`players.stream().allMatch(p -> round.hasPlayerAnswered(p.getName()))`) and call `checkRoundEnd()` after every roster change.
- **Status:** ✅ **Fixed.** Turn completion is `GameLogic.isTurnComplete(ctx)` over `ctx.activePlayers()` (players still in the room and online). [`GameRun`](backend/runtime/src/main/java/com/example/funfridaygame/runtime/GameRun.java) re-checks it after every action, timer and roster or presence change (leave, kick, disconnect, removal). Test: `NumberGuessGameTest.disconnectedOrDepartedPlayersDoNotBlockTheRound`.

#### 🟡 BUG-12 — "Go Home" on the results page doesn't leave the game

- **Area:** Frontend · navigation
- **Where:** [navbar.component.ts L78–85](frontend/src/app/components/navbar/navbar.component.ts#L78-L85); [results.component.html L3–10](frontend/src/app/components/results/results.component.html#L3-L10)
- **Symptom:** When the host clicks **Go Home** on the results screen, the other players are stranded there — only the host can Play Again / Change Game, and the room is never cleaned up. A non-host who goes home stays in the player list and becomes a ghost in the next game (BUG-03).
- **Cause:** Results renders the navbar with `showExitOptions=false, showHomeButton=true`; `onGoHome()` only clears `sessionStorage` and navigates — no `/app/leave`.
- **Fix:** Send `/app/leave` from `onGoHome()` when a game id is present (host → terminate or migrate host, IMP-11).
- **Status:** ✅ **Fixed.** Every way out sends a real command: **Leave room** (podium, room menu) and **End room** (host, with confirmation) go through [`RoomActions`](frontend/src/app/pages/room/room-actions.service.ts), and leaving the room page by navigation asks and leaves ([leave-room.guard.ts](frontend/src/app/pages/room/leave-room.guard.ts)). When the host leaves, the next player becomes host (with a toast), so nobody is stranded; when the host ends the room, everybody else gets an explanation page with a way home. E2E: *when the host leaves, the next player becomes host*, *going back from the room asks first, then really leaves*.

#### 🟡 BUG-13 — Results page is blank after a refresh

- **Area:** Frontend · results
- **Where:** [results.component.ts L61–105](frontend/src/app/components/results/results.component.ts#L61-L105)
- **Symptom:** F5 on `/results/:id` shows an empty page (no podium, no navbar, no Play Again).
- **Cause:** Without router state, `loadGameData()` only checks `/status` and subscribes; it never fetches the game, so `game()` stays `null` until the next broadcast — which only happens on Play Again / Change Game. The subscribe right after `connect()` also throws (BUG-01).
- **Fix:** Fetch `GET /api/games/{id}` when there is no navigation state; keep the subscription for updates.
- **Status:** ✅ **Fixed.** One room page covers every phase. After a refresh the seat is resumed and the server sends the current snapshot, which in `FINISHED` carries the final results (standings, awards, Friday Cup), so the podium renders straight away ([results.component.ts](frontend/src/app/pages/room/results.component.ts)).

#### 🟡 BUG-14 — Production root URL `/` returns 404

- **Area:** Backend · deployment
- **Where:** [application.properties L17–19](backend/src/main/resources/application.properties#L17-L19); [WebConfig.java L35–53](backend/src/main/java/com/example/funfridaygame/config/WebConfig.java#L35-L53)
- **Symptom:** Opening `https://<app>/` shows a Whitelabel 404; the app only works at `/ui/`.
- **Cause:** Static resources are mapped exclusively to `/ui/**` (`spring.mvc.static-path-pattern` + the `/ui/**` resource handler), which also disables Spring Boot's welcome page; nothing serves or redirects `/`.
- **Fix:** Redirect `/` → `/ui/` (one `ViewControllerRegistry` entry), or serve the SPA at `/` and keep `/api` and `/ws` as reserved prefixes.
- **Status:** ✅ **Fixed.** The app is served at `/`; client routes are forwarded to `index.html` and `/ui/…` redirects to `/` ([`WebConfig`](backend/app/src/main/java/com/example/funfridaygame/config/WebConfig.java)); `/api`, `/ws`, `/actuator` are the reserved prefixes. Tests: `ApiContractTest.servesTheCatalogHealthAndLegacyRedirect`; e2e *deep links are served by the single-page app*, *old invite links still work*.

#### 🟡 BUG-15 — Docker image doesn't listen on the default port

- **Area:** Deployment
- **Where:** [Dockerfile L71](Dockerfile#L71); [render.yaml L19–20](render.yaml#L19-L20); [application.properties L2](backend/src/main/resources/application.properties#L2)
- **Symptom:** `docker run -p 8080:8080 <image>` (without `-e PORT=…`) starts but serves nothing, and the container health check fails. `JAVA_OPTS` from `render.yaml` has no effect either.
- **Cause:** The exec-form `ENTRYPOINT` passes the literal `-Dserver.port=${PORT:-8080}` to Java (no shell expansion). Spring then resolves it with its own `${name:default}` syntax, so the default becomes **`-8080`** — and a negative port makes Spring Boot skip starting the HTTP connector. `JAVA_OPTS` is never referenced. (Works on Render only because `PORT` is set there.)
- **Fix:** Drop the `-D` flag (`application.properties` already has `server.port=${PORT:8080}`) and pass memory flags via `JAVA_TOOL_OPTIONS`, which the JVM reads automatically (IMP-47).
- **Status:** ✅ **Fixed.** The new [Dockerfile](Dockerfile) runs plain `java -jar`: the port comes from `server.port: ${PORT:8080}`, memory flags from `JAVA_TOOL_OPTIONS` (`MaxRAMPercentage=75`, also in [render.yaml](render.yaml)); the image runs as a non-root user with an Actuator `HEALTHCHECK`. Verified: `docker run -p 8090:8080` → `/actuator/health` UP in ~6 s, and the whole Playwright suite passes against the container.

#### 🟡 BUG-16 — Name-based reply topics leak between players; Join navigates on any update

- **Area:** Backend + frontend · messaging
- **Where:** [GameController.java L124](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L124), [L396–400](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L396-L400); [game-setup.component.ts L159–209](frontend/src/app/components/game-setup/game-setup.component.ts#L159-L209)
- **Symptom:** Two people with the same name creating/joining at the same time receive each other's "created" and error messages (one can land in the other's game). A player whose join was rejected ("Game already in progress", "name taken") is still navigated into the game as soon as any update for that game is broadcast.
- **Cause:** Replies go to global, name-keyed topics (`/topic/created/{name}`, `/topic/error/{name}`), and the join flow treats *any* message on `/topic/game/{code}` as success.
- **Fix:** Reply on user destinations (`/user/queue/…`, IMP-18) with a correlation id; navigate only after a join acknowledgement that contains your player.
- **Status:** ✅ **Fixed.** Replies, errors and private events go to user destinations (`/user/queue/reply`, `/events`, `/room`) and carry the request's `requestId`; [`RealtimeService`](frontend/src/app/core/realtime.service.ts) correlates them. The join page navigates only after the `Joined` reply, and a rejected join shows the error next to the name field. E2E: *a taken name is rejected next to the name field*.

#### 🟡 BUG-17 — Game rules are enforced only in the UI

- **Area:** Backend · validation
- **Where:** [GameController.java L63–206](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L63-L206); [game-setup.component.html L98–116](frontend/src/app/components/game-setup/game-setup.component.html#L98-L116); [NumberGuessRound.java L31–33](backend/src/main/java/com/example/funfridaygame/model/round/NumberGuessRound.java#L31-L33)
- **Symptom:** A crafted message can start a game with one player, add an 11th player, use empty or very long names, or set huge round counts/durations. Even through the UI, Number Guess allows *Min 100 / Max 50*, which produces targets 52–100 — outside the range shown to players.
- **Cause:** No server-side validation of create/join/start; `generateRandomNumber(min, max)` assumes `min ≤ max`.
- **Fix:** Validate on the server (Bean Validation + rule checks in `startGame`/`joinGame`), constrain the form (max options above the selected min), return errors to the sender (IMP-19).
- **Status:** ✅ **Fixed.** Everything is validated on the server: names ([`NameRules`](backend/app/src/main/java/com/example/funfridaygame/room/NameRules.java): length, characters, unique per room), seats (`ROOM_FULL` → join as spectator), min/max players on start, settings against each game's schema plus the game's own `validate()` (e.g. Number Guess requires min < max) via [`SettingsResolver`](backend/runtime/src/main/java/com/example/funfridaygame/runtime/SettingsResolver.java), and every action payload (Bean Validation + game rules). The form only offers each game's options, and combinations a game can't play (e.g. Number Guess with the highest number not above the lowest) are refused with a clear message. Tests: `RoomFlowIntegrationTest.rejectsInvalidRequests`, `NumberGuessGameTest.validatesTheNumberRange`.

#### 🟡 BUG-18 — Pictionary canvases drift apart

- **Area:** Backend + frontend · Pictionary
- **Where:** [PictionaryController.java L89–128](backend/src/main/java/com/example/funfridaygame/controller/PictionaryController.java#L89-L128); [pictionary-game.component.ts L75–89](frontend/src/app/components/games/pictionary/pictionary-game.component.ts#L75-L89), [L130–139](frontend/src/app/components/games/pictionary/pictionary-game.component.ts#L130-L139)
- **Symptom:** After a refresh or reconnect a guesser's canvas stays blank for the rest of the turn; after a missed stroke, a later Undo removes the wrong stroke, so guessers see a different drawing than the drawer.
- **Cause:** Undo/redo are only relayed — the server's stroke log isn't updated. Round info carries `drawingData`, but the client never replays it.
- **Fix:** Make the server the source of truth (append/remove strokes by id, broadcast `{op, strokeId}`) and replay `drawingData` on init and reconnect.
- **Status:** ✅ **Fixed.** The server keeps an authoritative, versioned drawing log (strokes, fills, undo/redo/clear by id). Every snapshot carries it, so refreshes, reconnects and spectators re-sync; stream events in between are applied in order with gap detection and a private `sync` request ([Pictionary](games/pictionary/README.md)). Tests: `theDrawingIsTheSameForEveryViewer`, `undoRedoClearAndFillAreAppliedOnTheServer`, `lateBatchesOfAnUndoneStrokeAreIgnored`; e2e checks strokes, undo and redo on every canvas.

#### 🟡 BUG-19 — Pictionary turn results show the wrong points

- **Area:** Backend + frontend · Pictionary
- **Where:** [TurnResult.java L24–38](backend/src/main/java/com/example/funfridaygame/model/TurnResult.java#L24-L38); [pictionary-game.component.html L268–283](frontend/src/app/components/games/pictionary/pictionary-game.component.html#L268-L283); [PictionaryStrategy.java L64–67](backend/src/main/java/com/example/funfridaygame/service/game/PictionaryStrategy.java#L64-L67)
- **Symptom:** The turn summary always shows +100 for the guesser and +50 for the drawer, and only one guesser — but real scoring is per player (100 + seconds left, or similarity-based partial credit). The turn-results list only ever holds the current turn, and `currentTurnNumber` is always 1.
- **Cause:** The template hard-codes `+50 pts` / `+100 pts` around the single `correctGuesser`, and `TurnResult` hard-codes the same values; a new `PictionaryRound` is created every turn, so per-round history resets (and `resetForNewTurn()` is never used).
- **Fix:** Build turn results from the points actually awarded (per player) and keep turn history at round/game level.
- **Status:** ✅ **Fixed.** The [Pictionary](games/pictionary/README.md) reveal lists every correct guesser with their time and the points actually awarded (100 → 40 by time, +20 for the first) plus the drawer's 25 per guesser (max 150); per-turn points are kept by the platform for the round-by-round table. Test: `correctGuessesScoreByTimeAndTheDrawerScoresPerGuesser`.

#### 🟡 BUG-20 — Word Scramble allows one guess and ends on the first correct answer

- **Area:** Backend + frontend · Word Scramble
- **Where:** [WordScrambleController.java L36–52](backend/src/main/java/com/example/funfridaygame/controller/WordScrambleController.java#L36-L52); [word-scramble-game.component.ts L48–58](frontend/src/app/components/games/word-scramble/word-scramble-game.component.ts#L48-L58)
- **Symptom:** One typo and you're out for the round; as soon as one player is right the round ends for everyone, so the "+50 for other correct guessers" branch in `WordScrambleStrategy.awardPoints` is practically unreachable. Contradicts the multi-guess example in `GameIdeas.md`.
- **Cause:** The server ignores every guess after the first (`hasPlayerAnswered`), the client locks input after one submit, and a correct guess immediately reveals the round.
- **Fix:** Allow repeated guesses with feedback, end the round when everyone is correct or time is up, score by solve order (IMP-24).
- **Status:** ✅ **Fixed.** In [Word Scramble](games/word-scramble/README.md) you guess as often as you like (cap: 40 wrong guesses and never a quarter of all letter orders); a wrong guess shakes the input with *Not quite*, a correct one locks in only that player, and the round ends when every active player has solved (or is out of guesses) or time is up. Points follow solve order + speed. Tests: `wrongGuessesGiveFeedbackAndAllowRetries`, `solveOrderAndSpeedDecideThePointsAndTheRoundEndsWhenEveryoneSolved`.

#### 🟡 BUG-21 — Word fetching can stall every game on the server

- **Area:** Backend · Word Scramble
- **Where:** [WordService.java L68–85](backend/src/main/java/com/example/funfridaygame/service/WordService.java#L68-L85)
- **Symptom:** When the external word API is slow or down, round starts hang; four slow calls block every game on the server. Fallback words ignore the chosen length, and API words can be obscure.
- **Cause:** Blocking HTTP call without connect/read timeouts, executed on the shared 4-thread round scheduler; the fallback list isn't filtered by length.
- **Fix:** Bundle curated word lists per length (no network in the hot path), or fetch asynchronously with short timeouts and caching; filter fallbacks by length.
- **Status:** ✅ **Fixed.** No network at all: [Word Scramble](games/word-scramble/README.md) ships curated offline lists per length (4–8 letters, 137–182 family-friendly words each), loaded once and validated at start-up (letters only, right length, no duplicates, no anagram pairs). Tests: `everyLengthHasEnoughValidWords`, `validationListsEveryProblem`.

#### 🟡 BUG-22 — Create/Join spinner can spin forever

- **Area:** Frontend · setup
- **Where:** [websocket.service.ts L65–73](frontend/src/app/services/websocket.service.ts#L65-L73); [game-setup.component.ts L139–216](frontend/src/app/components/game-setup/game-setup.component.ts#L139-L216)
- **Symptom:** Clicking Create/Join before the socket is connected (or while reconnecting) leaves the button spinning with no error.
- **Cause:** `sendMessage()` silently drops messages when disconnected, `subscribe()` may throw (BUG-01), and there is no timeout or error path.
- **Fix:** Wait for the connection (or queue the publish), add a ~5 s timeout with a retry message, surface server errors via the user queue.
- **Status:** ✅ **Fixed.** Requests wait for the connection (and the resumed seat) instead of being dropped, and time out with a clear error; buttons show a spinner only while their request is pending and errors appear as toasts or next to the field ([`RealtimeService`](frontend/src/app/core/realtime.service.ts), [host.page.ts](frontend/src/app/pages/host/host.page.ts), [join.page.ts](frontend/src/app/pages/join/join.page.ts)).

#### 🟡 BUG-23 — Reaction "GO" target jumps away exactly when it's time to tap

- **Area:** Frontend · styles
- **Where:** [_common.scss L47–57](frontend/src/styles/_common.scss#L47-L57); [reaction-showdown-game.component.css L28–31](frontend/src/app/components/games/reaction-showdown/reaction-showdown-game.component.css#L28-L31); also [_utility.scss L35–38](frontend/src/styles/_utility.scss#L35-L38) used by [game-layout.component.html L49](frontend/src/app/components/game-layout/game-layout.component.html#L49)
- **Symptom:** In the GO phase the whole reaction area — including the tap button — shifts up-left by half its size and pulses, right when players must hit it. The pre-round icon in the game layout jumps the same way.
- **Cause:** The global `pulse` keyframes include `translate(-50%, -50%)` (written for an absolutely-centred element) but are reused by elements that aren't positioned that way.
- **Fix:** Split into `pulse` (scale/opacity only) and `pulse-centered`; respect `prefers-reduced-motion`.
- **Status:** ✅ **Fixed.** The global `pulse` with `translate(-50%, -50%)` is gone; the shared motion classes only animate scale and opacity ([styles.scss](frontend/src/styles.scss)). In [Reaction Showdown](games/reaction-showdown/README.md) the tap target is a static, full-size field that never moves or scales — only a decorative orb inside it pulses.

### 1.4 Low

| ID | Issue | Where | Fix | Status |
| --- | --- | --- | --- | --- |
| BUG-24 | Client round timers aren't synced: each client counts down from `roundDuration` when it receives the update, and restarts at full time after a refresh | [base-game.component.ts L230–245](frontend/src/app/components/games/base-game.component.ts#L230-L245) | Send `roundEndsAt` + server time (IMP-09) | ✅ Snapshots carry `serverTime`, `phaseStartedAt` and `phaseEndsAt`; [`ClockService`](frontend/src/app/core/clock.service.ts) estimates the clock offset with pings, so every timer shows the same time on every machine and after a refresh. |
| BUG-25 | Reaction signal task isn't bound to its round or cancelled — latent today (delay 2–5 s, no early end), but it would fire into the next round once rounds end early with a shorter result screen; `getSignalDelay()` throws if `minDelay == maxDelay` | [ReactionShowdownService.java L36–46](backend/src/main/java/com/example/funfridaygame/service/game/ReactionShowdownService.java#L36-L46), [L72–90](backend/src/main/java/com/example/funfridaygame/service/game/ReactionShowdownService.java#L72-L90); [ReactionShowdownRound.java L64–66](backend/src/main/java/com/example/funfridaygame/model/round/ReactionShowdownRound.java#L64-L66) | Check the round number before triggering, keep/cancel the `ScheduledFuture`, use `nextInt(max - min + 1)` | ✅ [Reaction Showdown](games/reaction-showdown/README.md): every signal step is a turn-scoped `ctx.schedule` timer keyed by signal id, cancelled by the platform when the turn ends (test `timersOfAFinishedRoundNeverReachTheNextOne`). |
| BUG-26 | Missing `;` after `#ff0000` turns `@include mat.tabs-overrides(…)` into part of a CSS variable's value, so the tab overrides never apply (plus leftover debug colours `orange` / `#ff0000`) | [_theming.scss L67–83](frontend/src/styles/_theming.scss#L67-L83) | Add the semicolon, remove the debug colours | ✅ `_theming.scss` is gone; the new theme is one `mat.theme()` call with design tokens in [styles.scss](frontend/src/styles.scss) — no debug colours. |
| BUG-27 | "Copy code" / "Copy invite link" give no feedback, and throw on non-HTTPS origins (e.g. testing over a LAN IP) where `navigator.clipboard` is undefined | [base-game.component.ts L290–307](frontend/src/app/components/games/base-game.component.ts#L290-L307) | Snackbar "Copied!", fallback copy method, handle the rejected promise | ✅ CDK `Clipboard` (works on plain-http LAN addresses) + snackbar "Invite link copied", or an error toast ([`RoomActions`](frontend/src/app/pages/room/room-actions.service.ts)). |
| BUG-28 | Game codes (first 6 hex chars of a UUID) aren't checked for collisions — a collision silently replaces a live game | [GameController.java L65](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L65) | Regenerate until unused (IMP-14) | ✅ Room codes: 5 characters from an unambiguous alphabet, regenerated until unused ([`RoomStore`](backend/app/src/main/java/com/example/funfridaygame/room/RoomStore.java)). |
| BUG-29 | Host can't remove a player whose name contains `:` (`split(":")` yields 3 parts and the request is ignored) | [GameController.java L355–358](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L355-L358) | `split(":", 2)` or a structured payload | ✅ Kick takes a `memberId` (structured payload), never a name. |
| BUG-30 | Word Scramble stores guesses untrimmed but checks correctness trimmed — a guess with surrounding spaces ends the round with no winner (the UI trims, so latent for the official client) | [WordScrambleRound.java L31–34](backend/src/main/java/com/example/funfridaygame/model/round/WordScrambleRound.java#L31-L34), [L51–72](backend/src/main/java/com/example/funfridaygame/model/round/WordScrambleRound.java#L51-L72) | Normalise once when the guess is received | ✅ [Word Scramble](games/word-scramble/README.md) normalises a guess once on arrival (trim, upper case, accents dropped, letters only) and only stores/compares that form (test `guessesAreNormalisedOnArrival`). |
| BUG-31 | FE/BE model drift: `isFakeOut` is serialised as `fakeOut`; `typedText` declared in the FE model is never sent; `SlidingPuzzleConfig` is missing from `@JsonSubTypes`, so its type id falls back to the class name | [ReactionShowdownRoundInfo.java L17](backend/src/main/java/com/example/funfridaygame/model/roundinfo/ReactionShowdownRoundInfo.java#L17); [BaseGameConfig.java L15–21](backend/src/main/java/com/example/funfridaygame/model/config/BaseGameConfig.java#L15-L21); [game.model.ts L124–158](frontend/src/app/models/game.model.ts#L124-L158) | `@JsonProperty`, register the subtype, generate TS types (IMP-44) | ✅ Protocol types are generated from the Java records (OpenAPI → TypeScript); each game's view types mirror its Java view. [Reaction Showdown](games/reaction-showdown/README.md) never sends upcoming signals (`fakeOut` only in finished results); [Speed Typing](games/speed-typing/README.md)'s typed text is part of your own view and restores your progress after a refresh. |
| BUG-32 | Undo/Redo tooltips advertise Ctrl+Z / Ctrl+Y, but no keyboard handlers exist | [pictionary-game.component.html L73–90](frontend/src/app/components/games/pictionary/pictionary-game.component.html#L73-L90) | Add a `keydown` host listener (drawer only) or drop the hint | ✅ [Pictionary](games/pictionary/README.md): Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z for the drawer (plus B/E/F tools and 1–3 brush sizes), covered by the e2e spec. |
| BUG-33 | Reaction Showdown has no dedicated round-result view (winner/points aren't summarised at reveal), and the tap button stays enabled after the round ends | [reaction-showdown-game.component.html L19–36](frontend/src/app/components/games/reaction-showdown/reaction-showdown-game.component.html#L19-L36) | Add a `ROUND_RESULT` view; disable the button outside `GUESSING` | ✅ [Reaction Showdown](games/reaction-showdown/README.md): a mini-result after every signal and a round table on the reveal; tapping is disabled outside WAITING/FAKE/GO, after your tap and for spectators, and the server rejects late taps. |
| BUG-34 | Setup and Change Game offer different options (timer up to 150 s vs 90 s, word length 3–10 vs 4–8, min range 100 only in Setup) | [game-setup.component.html L59–116](frontend/src/app/components/game-setup/game-setup.component.html#L59-L116) vs [change-game-dialog.component.html L37–104](frontend/src/app/components/change-game-dialog/change-game-dialog.component.html#L37-L104) | One shared settings component driven by a schema (IMP-04) | ✅ One schema-driven [settings-form](frontend/src/app/shared/settings-form.component.ts) for Host, Lobby and Change Game; options come from each game's `SettingDef`s and are validated by the server. |
| BUG-35 | Tooling: root scripts use Windows-only `.\gradlew`; the husky hook uses bash-only `&>` under `sh`; Prettier check fails on 11 files; CRLF/LF mixed | [package.json L9–12](package.json#L9-L12); [.husky/pre-commit L9](.husky/pre-commit#L9) | IMP-41 | ✅ Root scripts call [scripts/gradle.mjs](scripts/gradle.mjs) (picks `gradlew`/`gradlew.bat`); POSIX [pre-commit](.husky/pre-commit) hook; `.gitattributes` (LF, CRLF for `.bat`); `prettier --check .` passes. |
| BUG-36 | Allowed origins aren't trimmed, so `"https://a.com, https://b.com"` silently rejects the second origin | [CorsProperties.java L23–25](backend/src/main/java/com/example/funfridaygame/config/CorsProperties.java#L23-L25); [WebSocketProperties.java L32–34](backend/src/main/java/com/example/funfridaygame/config/WebSocketProperties.java#L32-L34) | Trim and drop empty entries | ✅ Origins are split, trimmed and empty entries dropped ([`AppProperties`](backend/app/src/main/java/com/example/funfridaygame/config/AppProperties.java)); same-origin needs no configuration. |
| BUG-37 | Cosmetics: page title "FunFridayGameUi"; `matRipple` used without `MatRippleModule` (no ripple); `color="secondary"` isn't a Material palette; `showRoundHeader` comment says "Default false" but the value is `true` | [index.html L5](frontend/src/index.html#L5); [home.component.html L41](frontend/src/app/components/home/home.component.html#L41); [lobby.component.html L42](frontend/src/app/components/lobby/lobby.component.html#L42); [game-layout.component.ts L47–48](frontend/src/app/components/game-layout/game-layout.component.ts#L47-L48) | Trivial fixes | ✅ Title "Fun Friday" (+ per-room titles) and an SVG favicon; the old components with the ripple/colour/comment issues were replaced. |

---

## 2. Improvements & enhancements

Effort: **S** ≤ 1 day · **M** 2–4 days · **L** 1–2 weeks. Priority reflects the goals in `implementation-plan.md`: easy game addition, a clean real-time API, an elegant animated UI, functional tests, Docker-ready.

### 2.1 Architecture & game platform

| ID | Improvement | Why / fixes | Effort | Priority | Status |
| --- | --- | --- | :---: | :---: | --- |
| IMP-01 | **Self-contained game modules** — one backend package per game (config, round, round info, strategy, actions) and one frontend folder (component, models, settings), discovered through registries; one lazy route `game/:type/:id` | Adding a game today means editing models, DTOs, controllers, the round-info factory, config subtypes, the setup form, the Change Game dialog, routes and utils across both apps | L | High | ✅ One folder per game: `games/<id>/{backend,frontend,e2e,README.md}`. Gradle includes every `games/*/backend`, the frontend registry lazy-loads every `games/*/frontend`; `npm run new-game` scaffolds a working game ([guide](docs/adding-a-game.md)). |
| IMP-02 | **Generic action channel** — one `/app/game/{id}/action` endpoint carrying `{type, payload}`, dispatched to the game's strategy | Removes a controller + DTO per action and centralises membership/state checks | M | High | ✅ One `/app/rooms/action` endpoint with `{type, payload}`; membership, phase and rate limit are checked once in [`RoomService`](backend/app/src/main/java/com/example/funfridaygame/engine/RoomService.java), then the game's `onAction` converts and validates the payload. |
| IMP-03 | **Unified round engine** with lifecycle hooks (`onRoundStart`, `onAction`, `shouldEnd`, `onRoundEnd`) and first-class turns | Removes the `"reaction-showdown"` special case and the Pictionary branch in [GameRoundManager.java L111–125](backend/src/main/java/com/example/funfridaygame/controller/GameRoundManager.java#L111-L125) plus the `@Lazy` circular dependency; prevents bugs like BUG-04, BUG-06, BUG-25 | M–L | High | ✅ [`GameRun`](backend/runtime/src/main/java/com/example/funfridaygame/runtime/GameRun.java) drives rounds → turns → reveal → finish with hooks (`turnsInRound`, `skipTurn`, `startTurn`, `onAction`, `onTimer`, `isTurnComplete`, `endTurn`, `onPlayerLeft`); no game-specific code in the platform, no circular dependencies. |
| IMP-04 | **Declarative settings schema** per game (key, type, options, default, min/max) rendered by one dynamic form in Setup, Lobby and Change Game, validated by the server | Fixes BUG-05, BUG-17 and BUG-34 at the root; no hand-written `@switch` per game | M | High | ✅ `SettingDef` schema per game (type, options, default, min/max, help) → one dynamic [settings-form](frontend/src/app/shared/settings-form.component.ts); validated by [`SettingsResolver`](backend/runtime/src/main/java/com/example/funfridaygame/runtime/SettingsResolver.java). |
| IMP-05 | **Per-viewer state** — public view on the room topic, private data (drawer's word, own guesses) on user destinations | Fixes BUG-07, BUG-08; enables hidden-information games | M | High | ✅ Personal snapshots on `/user/queue/room` built from `view(ctx, viewer)`; private events via `ctx.sendTo`; public high-frequency data on the room stream. |
| IMP-06 | **`GameStore` service** replacing the static map in [GameTypeController.java L21](backend/src/main/java/com/example/funfridaygame/controller/GameTypeController.java#L21) | Injectable and testable; one place for eviction (IMP-14) and persistence (IMP-48) | S–M | Medium | ✅ Injectable [`RoomStore`](backend/app/src/main/java/com/example/funfridaygame/room/RoomStore.java) (rooms + principal bindings) with eviction; read-only queries in [`RoomQueries`](backend/app/src/main/java/com/example/funfridaygame/engine/RoomQueries.java). |
| IMP-07 | **Serialised per-game execution** (a lock or single-threaded queue per game) | Handlers mutate shared `Game` objects from STOMP threads and three schedulers (4 + 2 + 2 threads); only two methods are `synchronized`, on different monitors | M | Medium | ✅ Each room has its own lock; commands, timers and disconnects of a room run one at a time (virtual threads across rooms). |
| IMP-08 | **Deltas / throttling** for high-frequency events (typing progress, puzzle moves) | Every keystroke or move broadcasts the full `Game` to everyone | M | Medium | ✅ Action-driven snapshots are coalesced (50 ms); high-frequency input uses `sendFast` (no reply), `ctx.stream` for relays and `ctx.suppressUpdate()` to skip snapshots. |
| IMP-09 | **Server-authoritative deadlines** — `roundEndsAt` + server time in round info | Fixes BUG-24; correct timer after a refresh | S | Medium | ✅ `phaseStartedAt` / `phaseEndsAt` + `serverTime` in every snapshot; client clock offset via ping. |
| IMP-10 | **Round numbering owned by the engine** | All six strategies increment `currentRoundNumber` in `createRound()` (Pictionary only on new rounds) — easy to get wrong in a new game | S | Low | ✅ The runtime owns round/turn numbers (`ctx.round()`, `ctx.turn()`); games never count rounds. |

### 2.2 Reliability & real-time

| ID | Improvement | Why / fixes | Effort | Priority | Status |
| --- | --- | --- | :---: | :---: | --- |
| IMP-11 | **Presence, grace period & host migration** — track sessions, mark players offline on disconnect, remove after ~30 s, pass host to the next player | Fixes BUG-03, BUG-11, BUG-12 | M | High | ✅ Presence with 30 s grace, removal, host migration, re-check of the turn (BUG-03, BUG-11, BUG-12). |
| IMP-12 | **Resilient client** — connection-state signal with a "Reconnecting…" banner, resubscribe + REST resync after reconnect, short outbox for actions | Builds on the BUG-01/02 fix so players see what's happening | S–M | High | ✅ Connection-state signal, *Reconnecting…* banner, re-subscription, resume + snapshot after reconnect, request timeouts ([`RealtimeService`](frontend/src/app/core/realtime.service.ts)). |
| IMP-13 | **Rejoin token** — issued on join, kept in `sessionStorage`, used to reclaim the seat after a refresh or reconnect | Today identity is just the display name | M | Medium | ✅ Secret member token (returned once on join, kept in `sessionStorage`) to resume the seat; a second tab gets `REPLACED` with a "Play here" option. |
| IMP-14 | **TTL eviction & friendlier codes** — evict finished/idle games (e.g. after 2 h); generate codes from an unambiguous alphabet (no 0/O/1/I) and check for collisions | Games are only removed when the host explicitly leaves; fixes BUG-28; codes are easier to read aloud | S | Medium | ✅ Idle/inactive/max-age eviction (10 min / 2 h / 12 h), max rooms, unambiguous collision-checked codes. |
| IMP-15 | **Error handling** — `@MessageExceptionHandler` replying to the sender, `try/catch` + logging in every scheduled task, Bean Validation on DTOs | Exceptions in scheduled tasks vanish silently (BUG-04); clients never learn why an action failed | S | Medium | ✅ Errors are replied to the sender (`@MessageExceptionHandler` + error codes); every call into game code and every timer is contained and logged by [`GameRun`](backend/runtime/src/main/java/com/example/funfridaygame/runtime/GameRun.java); Bean Validation on requests. |
| IMP-16 | **Broker heart-beats** — `setHeartbeatValue` + a `TaskScheduler` on the simple broker | The client asks for 4 s heart-beats but the broker has none configured, so they're negotiated off and dead connections linger | S | Low | ✅ Broker heart-beats 10 s/10 s with a dedicated `TaskScheduler` ([WebSocketConfig](backend/app/src/main/java/com/example/funfridaygame/config/WebSocketConfig.java)). |

### 2.3 Security & fair play

| ID | Improvement | Why / fixes | Effort | Priority | Status |
| --- | --- | --- | :---: | :---: | --- |
| IMP-17 | **Session-bound identity** — on create/join store `(gameId, playerName)` in the STOMP session (or set a `Principal` in a `ChannelInterceptor`); ignore names in payloads | Fixes BUG-10 | M | High | ✅ Principal per connection, bound on create/join/resume; payload names ignored (BUG-10). |
| IMP-18 | **User destinations** — enable `/user/queue/*` for replies, errors and private data | Fixes BUG-16; prerequisite for IMP-05 | S | High | ✅ `/user/queue/reply`, `/user/queue/room`, `/user/queue/events` (BUG-16). |
| IMP-19 | **Server-side validation** — names (length, charset, uniqueness, no `:`), 2–10 players, bounds for rounds/duration/word length, `min < max` | Rules are UI-only today (BUG-17) | S | High | ✅ Names (length, charset, uniqueness), min/max players, settings bounds and game rules validated on the server (BUG-17). |
| IMP-20 | **Anti-cheat basics** — rate-limit actions, block paste in Speed Typing, hide other players' guesses until reveal, cap Pictionary partial-credit guesses | Partial credit (≥ 50 % similarity, best guess counts) rewards spamming guesses; pasting finishes Speed Typing instantly | S–M | Medium | ✅ Per-member rate limit on every action; [Speed Typing](games/speed-typing/README.md) blocks paste/drop/autocorrect and the server ignores text growing faster than 25 chars/s; other players' answers stay hidden until the reveal; [Pictionary](games/pictionary/README.md) gives no partial credit and allows 30 guesses per turn; [Word Scramble](games/word-scramble/README.md) caps wrong guesses. |
| IMP-21 | **Fair reaction timing** — measure relative to when each client received the signal (with sanity checks), or schedule the signal at a shared future timestamp | Server-side timing includes each player's network latency | M | Low | ✅ [Reaction Showdown](games/reaction-showdown/README.md) measures in the browser from the frame that showed GO; the server checks every claim (≥ 100 ms, never more than its own elapsed time + tolerance). Tests `networkLatencyDoesNotDecideWhoIsFastest`, `reactionTimesAreCheckedAgainstTheServerClock`. |

### 2.4 Performance

| ID | Improvement | Why / fixes | Effort | Priority | Status |
| --- | --- | --- | :---: | :---: | --- |
| IMP-22 | **Leaner frontend** — lazy-load game routes, `provideZoneChangeDetection({ eventCoalescing: true })`, `OnPush` for game components | Smaller initial bundle; fewer change-detection passes while typing/drawing | S | Low | ✅ Every page and every game UI is a lazy chunk; zoneless change detection (better than event coalescing); `OnPush` everywhere. |
| IMP-23 | **Smaller payloads** — drop bulky fields from routine updates (e.g. Pictionary `drawingData`) and stop re-sending static data (config, avatars) on every event | Every broadcast carries the whole `Game` | S–M | Low | ✅ Snapshots are personal and small (no static catalog data — it comes once from `GET /api/games`); drawings travel as compact stream events, never inside every update. |

### 2.5 Gameplay

| ID | Improvement | Why / fixes | Effort | Priority | Status |
| --- | --- | --- | :---: | :---: | --- |
| IMP-24 | **Word Scramble** — multiple guesses with wrong-answer feedback, progressive hints (e.g. first letter after 15 s), points by solve order, curated offline word lists per length, round ends when everyone is right or time is up | Fixes BUG-20, BUG-21; matches the `GameIdeas.md` example | M | High | ✅ [Word Scramble](games/word-scramble/README.md): unlimited guesses with feedback, personal opt-in hints (−25 % each), solve order + speed scoring, curated offline lists, early round end (BUG-20, BUG-21, BUG-30). |
| IMP-25 | **Pictionary** — stream strokes while drawing (batched ~50 ms; today a stroke is sent only on pointer-up), server stroke log with replay, progressive letter hints, drawer picks 1 of 3 words, no repeated words per game, eraser/fill/brush sizes, keyboard shortcuts | Fixes BUG-18, BUG-19, BUG-32; makes the core loop feel live | L | High | ✅ [Pictionary](games/pictionary/README.md): strokes streamed in 80 ms batches, server log with replay, letter hints at ½ and ¾ of the time, 1-of-3 word choice with auto-pick, no repeated words, 12 colours, 3 sizes, eraser, fill, shortcuts (BUG-18, BUG-19, BUG-32). |
| IMP-26 | **Reaction Showdown** — end the round when everyone has tapped (BUG-06), several signals per round (best of 3/5), a real GO after a fake-out, a round-result view with times (BUG-33), ~10 s default timer | One tap per 30 s round today; fake-out rounds offer no way to score | S | High | ✅ [Reaction Showdown](games/reaction-showdown/README.md): 3 or 5 signals per round, a real GO after every fake-out, per-signal results and a round table, its own short deadline, early ends (BUG-06, BUG-33). |
| IMP-27 | **Speed Typing** — progress from the correct prefix only, live WPM/accuracy, error highlighting, tell players when they've reached full length but are below `minAccuracy` (95 %) | Progress is `typed length / target length`, so mashing keys fills the bar | S–M | Medium | ✅ [Speed Typing](games/speed-typing/README.md): progress from the correct prefix only, live WPM/accuracy, per-character error highlighting, *Fix N errors to finish*; finishing below the minimum accuracy halves the points (explained on the finish card). |
| IMP-28 | **Sliding Puzzle** — optimistic local moves, arrow-key controls, guaranteed minimum scramble difficulty | Moves wait for a server round-trip; a random shuffle can occasionally be almost solved | M | Medium | ✅ [Sliding Puzzle](games/sliding-puzzle/README.md): optimistic moves with 120 ms slides reconciled against the server board, arrow keys/WASD, scrambles guaranteed hard (Manhattan distance ≥ 85 % of a random board, ≥ 70 % of tiles misplaced). |
| IMP-29 | **Number Guess** — higher/lower hints over several attempts, or distance-based points; hide guesses until reveal | One blind guess per round is light on fun | S | Low | ✅ [Number Guess](games/number-guess/README.md): several attempts with private higher/lower hints, closeness points if nobody hits, guesses hidden until the reveal. |
| IMP-30 | **Results** — ties (shared places), per-round breakdown, per-game awards, a cumulative "Friday Cup" across games played in the same room | Results show final totals only and pick a single winner even on ties | M | Medium | ✅ Shared places on ties, round-by-round table, per-game awards, and a Friday Cup across all games of the room ([results](frontend/src/app/pages/room/results.component.html)); the hall of fame keeps history in H2. |

### 2.6 UX & visual design

| ID | Improvement | Why / fixes | Effort | Priority | Status |
| --- | --- | --- | :---: | :---: | --- |
| IMP-31 | **Material 3 theme** — replace the prebuilt `deeppurple-amber` theme and 99 `!important` overrides with `mat.theme()` + CSS variables; unify palettes (Speed Typing uses Catppuccin colours, Sliding Puzzle indigo, the rest Spotify green) | One coherent look, much less CSS, easy dark/light | M | Medium | ✅ M3 `mat.theme()` (dark, violet/orange) + `--ff-*` tokens; one palette for every game; `!important` only in the reduced-motion override (one block, down from 99). |
| IMP-32 | **Feedback & safety** — snackbars for copy/errors/kicks, confirm before the host ends a game, validate the code (REST) before showing the join form | Many actions fail or succeed silently (BUG-22, BUG-27) | S | Medium | ✅ Snackbars for copy/errors/kicks/host changes, confirmations before ending a room or kicking, the join page checks the code via REST first ([`RoomActions`](frontend/src/app/pages/room/room-actions.service.ts)). |
| IMP-33 | **Better lobby** — settings summary, capacity (n/10), ready toggles, host edits settings in place | Players can't see what they're about to play | M | Medium | ✅ Lobby with settings (editable by the host in place, read-only for others), capacity, ready toggles, start hint ("2 of 2 ready", "1 more player") ([lobby](frontend/src/app/pages/room/lobby.component.html)). |
| IMP-34 | **Motion system** — shared, subtle transitions (round start, reveal, score count-up) with `prefers-reduced-motion` support | The "elegant minimal UI with animations" goal; current animations are ad hoc (BUG-23) | M | Medium | ✅ Shared motion classes (`animate.enter`/`leave`, view transitions, count-ups, confetti), all disabled under `prefers-reduced-motion` ([styles.scss](frontend/src/styles.scss)). |
| IMP-35 | **Accessibility** — focus management between phases, `aria-live` for timer and results, contrast check, host badge visible to everyone | Keyboard and screen-reader users can't follow the game | S–M | Medium | ✅ Live announcements for phase changes and results (CDK `LiveAnnouncer`), `role=status/alert` regions, labelled timer ring, autofocus on inputs per turn, visible host badge, high-contrast dark theme. |
| IMP-36 | **Spectator mode** — watch an in-progress game | Late arrivals only get "Game already in progress" | M | Low | ✅ Late arrivals watch as spectators (spectator views) and get a seat in the next game; e2e *spectators watch the running game…*. |
| IMP-37 | **Polish** — real title & favicon, remember name/avatar in `localStorage`, real avatars for the "Other" category | Small touches that add up | S | Low | ✅ Title + favicon, name/avatar remembered in `localStorage` ([`ProfileService`](frontend/src/app/core/profile.service.ts)), emoji avatar picker with categories. |

### 2.7 Developer experience & quality

| ID | Improvement | Why / fixes | Effort | Priority | Status |
| --- | --- | --- | :---: | :---: | --- |
| IMP-38 | **Tests** — Playwright e2e first (create → join → play a round of every game with two browser contexts), then backend rule tests per strategy and a STOMP integration test | No tests exist; most bugs above would be caught by a two-player e2e | M–L | High | ✅ Functional tests only: **371 JUnit tests** (every game through the real runtime with `GameHarness` + STOMP integration and API contract tests) and **49 Playwright tests** (platform, every game end to end, and a [layout check](e2e/tests/layout.spec.ts) that no game makes the page scroll at 1366×700), all green; they also run in CI. |
| IMP-39 | **CI** — GitHub Actions: backend build + Checkstyle/Spotless, frontend lint + Prettier check + build, e2e on pull requests | Nothing enforces formatting today (Prettier fails on 11 files) | S | High | ✅ [ci.yml](.github/workflows/ci.yml): backend build (tests, Checkstyle, Spotless) + OpenAPI drift check, frontend lint/Prettier/build + registry drift check, a freshly scaffolded game through every check, Playwright e2e, Docker build. |
| IMP-40 | **Toolchain upgrade** — newer Gradle wrapper with toolchain auto-provisioning (foojay resolver), move off Spring Boot 3.2.x (out of OSS support), align `angular-eslint` 21 with Angular 20 | Gradle 8.10 can't run on current JDKs (25/26); security fixes | M | Medium | ✅ Gradle 9.8.1 wrapper + foojay toolchains (JDK 25), Spring Boot 4.1.1, Angular 21.2 with matching angular-eslint 21 ([ADR 0003](docs/adr/0003-toolchain.md)). |
| IMP-41 | **Cross-platform scripts & line endings** — `./gradlew` in root scripts, POSIX-sh husky hook, `.gitattributes` (`* text=auto eol=lf`, `*.bat eol=crlf`), one reformat commit | Fixes BUG-35; ends CRLF/LF churn | S | Medium | ✅ Cross-platform scripts, POSIX husky hook + lint-staged, `.gitattributes`, `.editorconfig`, all files LF. |
| IMP-42 | **Remove dead code** (list below) | Less to read, fewer false leads | S | Low | ✅ Removed with the old code (see the list below). |
| IMP-43 | **De-duplicate** — config switch in create vs change game ([GameController.java L92–114](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L92-L114) vs [L278–300](backend/src/main/java/com/example/funfridaygame/controller/GameController.java#L278-L300)), Setup vs Change Game forms, game-component boilerplate, repeated strategy lookups in `PictionaryController` | The copies have already drifted (BUG-34) | S–M | Medium | ✅ One settings form, one action endpoint, one room page and a frontend SDK (`@ff/sdk`) for game boilerplate — nothing left to drift. |
| IMP-44 | **API contract** — AsyncAPI for STOMP, OpenAPI (springdoc) for REST, generated TypeScript types; refresh the "Architecture Notes" in `GameIdeas.md` | FE/BE models drift (BUG-31) | S–M | Low | ✅ OpenAPI from springdoc → [docs/openapi.json](docs/openapi.json) (written by `ApiContractTest`) → generated TS types (`npm run generate:api`); STOMP channels in [asyncapi.yaml](docs/asyncapi.yaml); `GameIdeas.md` architecture notes rewritten. |
| IMP-45 | **Local stack** — `docker-compose.yml`, Spring profiles (`dev` / `prod`), one command to run everything | Goal in `implementation-plan.md` | S | Medium | ✅ [docker-compose.yml](docker-compose.yml) (`docker compose up --build`), profiles `dev` / `e2e` / `test` (production = default), `npm run dev:backend` + `npm run dev:frontend`. |
| IMP-46 | **Observability** — logs with `gameId`/player in MDC, Actuator metrics (active games, players, rounds) | Multiplayer bugs are hard to diagnose from today's logs | S | Low | ✅ Room code in every log line (MDC), Actuator metrics `funfriday.rooms.*`, `funfriday.members.online`, `funfriday.games.*` ([RoomMetrics](backend/app/src/main/java/com/example/funfridaygame/engine/RoomMetrics.java)). |

**Dead code (IMP-42)**

- **Frontend:** `src/app/websocket.ts`; `src/app/app.config.ts` (`main.ts` bootstraps with its own providers); `game-config.utils.ts` helpers `getWordLength`, `getMinRange`, `getMaxRange`, `isSlidingPuzzleConfig`, `isSpeedTypingConfig`, `isReactionShowdownConfig`; `Avatar` in `player.model.ts`; Speed Typing `getRemainingText()` / `getTypedTextCorrect()`; `GameLayoutComponent` `leaveGame` / `exitGame` outputs and `playerAvatar` input (the navbar handles leaving); the unused `font-awesome` 4.7 dependency; CSS `.error-view`, gender-avatar rules, `.winner-display`, `.trophy-display`, `.medal`.
- **Backend:** `/app/join` (superseded by `/app/joinWithProfile`); the `/ws/stomp` endpoint (the client uses `/ws`); `PictionaryRound.resetForNewTurn()`; `BaseGameConfig.onRoundStart()` / `onRoundEnd()` and their overrides (never called); `Game.getPlayerByName()`; `Game.allPlayersAnswered()`.

**Status:** ✅ All of it is gone — the old `backend/src` and `frontend/src/app/{components,services,models,pipes,…}` trees were deleted and replaced by the new modules (see [§4](#4-resolution-summary)). The `font-awesome` dependency and the CSS listed above went with them.

### 2.8 Deployment

| ID | Improvement | Why / fixes | Effort | Priority | Status |
| --- | --- | --- | :---: | :---: | --- |
| IMP-47 | **Production hardening** — remove `-Dserver.port` from the Dockerfile; pass memory flags via `JAVA_TOOL_OPTIONS` or `-XX:MaxRAMPercentage`; set `server.forward-headers-strategy=framework` (correct scheme/host behind Render's proxy, so same-origin WebSocket checks pass); redirect `/` → `/ui/`; use Actuator `/actuator/health` for health checks | Fixes BUG-14, BUG-15; predictable deploys on a 512 MB instance | S | High | ✅ No `-D` flags, `JAVA_TOOL_OPTIONS`, `forward-headers-strategy: framework`, app at `/`, `/actuator/health` for Docker/Render (BUG-14, BUG-15). |

### 2.9 Product & roadmap alignment

| ID | Improvement | Why / fixes | Effort | Priority | Status |
| --- | --- | --- | :---: | :---: | --- |
| IMP-48 | **H2 + Flyway persistence** — finished games and leaderboards, optionally live-state snapshots | Planned in `implementation-plan.md`; a restart wipes everything today | M | Medium | ✅ H2 file DB + Flyway (`V1__game_history.sql`) + Spring Data JDBC: finished games, per-player results, Friday Cup leaderboard (`/history`) ([ADR 0004](docs/adr/0004-persistence.md)). |
| IMP-49 | **JHipster decision spike** (time-boxed) — JHipster brings its own conventions (Liquibase, auth, generated entities) that overlap with IMP-01–04 and IMP-48 | Decide before Phase 2 so the platform work isn't done twice | S | Medium | ✅ Decided: keep Spring Boot + Angular, borrow conventions only ([ADR 0002](docs/adr/0002-jhipster-evaluation.md)). |
| IMP-50 | **New games** from `GameIdeas.md`, once IMP-01–05 exist | Each new game should be one backend package + one frontend folder | L | Low | ✅ Two new games from `GameIdeas.md`, one folder each: [Trivia Blitz](games/trivia-blitz/README.md) (200 curated questions in 10 categories, streaks) and [Memory Match](games/memory-match/README.md) (one shared board, a match means you go again) — and the six existing games were rebuilt the same way. Later the first real-time games followed, also one folder each: [Snake Arena](games/snake-arena/README.md) and [Backseat Drivers](games/backseat-drivers/README.md) (tick loop with streamed frames, see "Real-time games" in the [guide](docs/adding-a-game.md)). |

---

## 3. Suggested roadmap

| Phase | Goal | Items | Size | Status |
| --- | --- | --- | --- | --- |
| **0 — Quick fixes** | Every existing game works and nobody sees the answer | BUG-01, 02, 05, 06, 08, 09, 13, 14, 15, 20, 23, 26, 27, 29, 30, 31, 32, 33, 36, 37 · IMP-42 | ~1–2 days | ✅ Done |
| **1 — Robust & fair** | Survive refreshes, leavers and cheaters | BUG-03, 04, 07, 10, 11, 12, 16, 17, 18, 19, 21, 22, 25, 28 · IMP-05, 11, 12, 13, 14, 15, 16, 17, 18, 19, 47 | ~1 week | ✅ Done |
| **2 — Platform** | Adding a game is a one-folder job, with tests and CI | BUG-24, 34, 35 · IMP-01–04, 06–10, 22, 23, 38–41, 43–46 | ~2–3 weeks | ✅ Done |
| **3 — Experience** | Polish, new features, persistence | IMP-20, 21, 24–37, 48–50 | Ongoing | ✅ Done |

> [!TIP]
> Run the IMP-49 JHipster spike before Phase 2 — it decides whether IMP-01–04 are built on the current stack or on a generated one. Start IMP-38 (Playwright) during Phase 1 so Phase 2 refactors have a safety net.

> [!NOTE]
> ✅ Done in this order: the JHipster decision was taken first ([ADR 0002](docs/adr/0002-jhipster-evaluation.md) — keep Spring Boot + Angular), Playwright came with the platform, and every phase above is complete.

### Quick wins (≤ 30 min each)

- [x] BUG-08 — call `setGameState()` before `setCurrentRound()` in `startNewRound()` → per-viewer views; answers only in REVEAL.
- [x] BUG-09 — align the guess topic (`/guesses` vs `/guess`) and broadcast the game after each guess → one action channel; Pictionary shows every guess live (masked) and sends private "So close!" events.
- [x] BUG-06 — call `checkRoundEnd()` after a Reaction tap → Reaction signals end as soon as every active player tapped.
- [x] BUG-14 — redirect `/` → `/ui/` → app served at `/`, `/ui/` redirects.
- [x] BUG-15 — remove `-Dserver.port=${PORT:-8080}` from the Dockerfile → Dockerfile without `-D` flags, verified with `docker run`.
- [x] BUG-23 / BUG-26 — split the `pulse` keyframes; add the missing `;` in `_theming.scss` → scale-only pulses; new `mat.theme()` styles.
- [x] BUG-36 — trim allowed origins → trimmed origin lists.
- [x] BUG-37 — page title, `MatRippleModule`, button colour, stale comment → title, favicon, old components replaced.
- [x] IMP-42 — delete `frontend/src/app/websocket.ts` and `frontend/src/app/app.config.ts` → all old frontend and backend code removed.

---

## 4. Resolution summary

Everything in this report is done. Rather than patching `7a85bbe` item by item, the app was rebuilt on a small game platform that removes whole classes of bugs (identity, presence, timers, leaks, drift); the old `backend/src` and `frontend/src/app` code is gone. Every item above links to where its fix lives.

### Where things live now

| Path | What |
| --- | --- |
| [backend/sdk](backend/sdk) | The contract for games: `GameDefinition`, `GameLogic`, `GameContext`, `SettingDef`, `TurnOutcome`, `Award` |
| [backend/runtime](backend/runtime) | `GameRun`: rounds → turns → reveal → podium, turn-scoped timers, scores with ties, settings validation, error containment |
| [backend/testkit](backend/testkit) | `GameHarness`: plays a game with virtual time and a seeded random generator |
| [backend/app](backend/app) | Rooms, identity, presence, host migration, STOMP + REST API, per-viewer snapshots, H2 history, metrics |
| [frontend](frontend) | Angular 21 (zoneless, signals): realtime client, room store, pages, game SDK (`@ff/sdk`), Material 3 theme |
| [games/](games) | One folder per game: `backend/`, `frontend/`, `e2e/`, `README.md` — 10 games; `npm run new-game` scaffolds a new one |
| [e2e](e2e) | Playwright platform specs and shared fixtures (game specs live in `games/<id>/e2e`) |
| [docs](docs) | [Architecture](docs/architecture.md), [Adding a game](docs/adding-a-game.md), [ADRs](docs/adr/README.md), `openapi.json`, `asyncapi.yaml` |
| [Dockerfile](Dockerfile), [docker-compose.yml](docker-compose.yml), [render.yaml](render.yaml), [ci.yml](.github/workflows/ci.yml) | One image, one command, CI |

### How it was verified

| Check | Result |
| --- | --- |
| `./gradlew build` (backend, all games) | ✅ 371 tests, Checkstyle (0 warnings) and Spotless clean |
| `npx playwright test` against the packaged jar | ✅ 49 / 49 — platform, all 10 games end to end, and every game fits a 1366×700 window without page scroll for every player |
| `npm run lint` · `npm run format:check` · `npm run build` | ✅ ESLint, Prettier and the production build clean (initial bundle ≈ 650 kB, every page and game lazy) |
| `npm run new-game` | ✅ the scaffolded game passes ESLint, Prettier, Spotless, Checkstyle, its scenario tests, its e2e spec and the layout check (also checked in CI) |
| `docker build` + `docker run` | ✅ health UP in ~6 s; the full Playwright suite also passes against the container |

### Run it

```bash
docker compose up --build                          # everything on http://localhost:8080
npm install && npm run dev:backend                 # or: API on :8080 (dev profile) …
npm run dev:frontend                               # … and the UI with live reload on :4200
```

---

## Appendix: review notes

- **Prettier 3.2.5 `--check`** fails on 11 frontend files: `app.config.ts`, `app.ts`, `pictionary-game.component.ts`, `reaction-showdown-game.component.ts`, `navbar.component.html`, `navbar.component.ts`, `models/index.ts`, `player.model.ts`, `pipes/index.ts`, `order-by.pipe.ts`, `services/index.ts`.
- **Line endings:** all 64 backend Java files are CRLF; the frontend has 9 CRLF and 67 LF files; there is no `.gitattributes` and `core.autocrlf` is unset.
- **Styles:** 99 `!important` across 10 style files, mostly overriding the prebuilt Material theme.
- **stompjs 7.2.1:** `Client.subscribe()` calls `_checkConnection()`, which throws `TypeError('There is no underlying STOMP connection')` unless `connected` is true (`src/client.ts`) — the root of BUG-01.
- **Toolchain on the review machine:** only JDK 25/26 are installed and Gradle 8.10 can't run on them, so the backend wasn't built or started (IMP-40). Node 20.19 / npm 10.8 available; frontend dependencies weren't installed (Prettier was run standalone).

**Status of these notes (2026-10-12):** ✅ `prettier --check .` passes for the whole repository (frontend, games, e2e, docs) · ✅ `.gitattributes` enforces LF (CRLF only for `.bat`) and all files were normalised · ✅ `!important` is used only in the reduced-motion override block (was 99) · ✅ the realtime client never calls stompjs before it is connected · ✅ the build runs on JDK 25 with Gradle 9.8.1 and toolchain auto-provisioning.
