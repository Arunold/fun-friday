# ⚡ Reaction Showdown

Wait for green, then tap faster than everyone — but don't fall for the fake-outs!

## Rules & scoring

A round has 3 or 5 **signals**. For every signal the whole field shows **“Wait for it…”** for a random
1.5–4 s. With fake-outs on, about every third signal then flashes a **decoy colour** for 0.8–1.5 s
(“✋ NOT YET” in red, “🤚 STEADY…” in amber or “🧊 FREEZE!” in violet). Then the field turns
**green: ⚡ TAP!** Tap the field — or press <kbd>Space</kbd>/<kbd>Enter</kbd> — as fast as you can.
One tap per player per signal.

| Situation                                                                                      | Points                               |
| ---------------------------------------------------------------------------------------------- | ------------------------------------ |
| Fastest valid tap                                                                              | 100                                  |
| 2nd · 3rd · 4th · 5th · 6th                                                                    | 80 · 65 · 50 · 40 · 30               |
| 7th and slower                                                                                 | 25, 20, 15, then 10                  |
| Equal times                                                                                    | share the better rank and its points |
| **False start**: tap while waiting, on a decoy, before GO reached your screen, or under 100 ms | −25                                  |
| No tap within 2 s of GO                                                                        | 0                                    |

A round's points are the sum of its signals (totals never drop below 0 — the platform takes care
of that).

A signal ends as soon as every connected player has tapped or jumped the gun, otherwise 2 s after
GO. Its **mini-result** then shows for 1.5 s: your time, rank and points, and who was fastest.
After the last signal the round ends and the reveal shows the **round table**: every player's time
on every signal, best, average and points.

**Awards:** ⚡ _Lightning_ — fastest single reaction · 🎯 _Steady hands_ — best average reaction
(2+ valid taps if anyone has that many) · 🐇 _Jumpy_ — most false starts.

## Settings

| Key        | Label             | Values      |
| ---------- | ----------------- | ----------- |
| `rounds`   | Rounds            | 1, **3**, 5 |
| `signals`  | Signals per round | **3**, 5    |
| `fakeouts` | Fake-outs         | **on**, off |

There is no round timer setting: the game schedules its own signals and sets the turn deadline in
`startTurn` to the worst case of all its signals plus 2 s (29 s for 3 signals with fake-outs). In
practice a round ends right after its last signal — usually after about 15 s.

## Fair timing

Server-side timing would include each player's network latency, so the **browser measures**:

1. When a frame showing GO has rendered (`afterRenderEffect`), the component notes
   `performance.now()` for that signal id (the view carries `signalId` + `state`).
2. A tap sends `tap {signal, ms}` with the milliseconds since that frame. A tap before GO reached
   the screen sends no `ms`.

The server checks every claim:

| Check                                                              | Result                                       |
| ------------------------------------------------------------------ | -------------------------------------------- |
| Wrong/old `signal` id, or the signal is already showing its result | rejected (“That signal is over”, “Too late”) |
| Tap during WAITING or a decoy                                      | false start                                  |
| Tap during GO without `ms`                                         | false start (the player hadn't seen GO)      |
| `ms` larger than the server's own elapsed time since GO + 150 ms   | the server's time is used instead            |
| (Resulting) `ms` under 100                                         | anticipation → false start                   |
| Second tap on the same signal                                      | rejected                                     |

The coloured field snaps into GO and decoys without any CSS transition, so the measured frame is
the frame players actually see.

## Design notes (bug report)

| Item                                                       | How it is solved                                                                                                                                                                                                                                                                                                |
| ---------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| BUG-05 — every game defaulted to 30 s rounds               | No `roundDuration` setting. The game sets its own deadline (a constant worst case) and ends the turn right after its last signal.                                                                                                                                                                               |
| BUG-06 — rounds never ended early                          | A signal ends the moment every _connected_ player has tapped or false-started (also re-checked when someone leaves); the turn ends after the last signal's result. Disconnected players never block.                                                                                                            |
| BUG-08 — answers leaking at the start of a round           | `startTurn` resets all round state before the first view is built; views show only the current state and the results of signals that are over (tested at the start of every round).                                                                                                                             |
| BUG-23 — the GO target jumped away                         | The tap target is a static, full-size field: it never moves or scales. Only a decorative orb inside it pulses, using the `scale` property (`ff-pulse`), and the “Too soon!” label shakes — never the target.                                                                                                    |
| BUG-25 — signal task not bound to its round                | Every step of a signal is one turn-scoped `ctx.schedule` timer keyed `signal-<id>`; the platform cancels them when the turn ends and `onTimer` ignores keys of other signals. Signal ids are unique per game.                                                                                                   |
| BUG-31 — fake-out flag leaked / serialised wrongly         | The drawn plans never leave the server. `fake` is sent (as the decoy's name) only while the decoy is on screen; `fakeOut` appears only in results of finished signals. The turn deadline is a constant, so it can't reveal when the last GO comes. The TypeScript types mirror the Java records field by field. |
| BUG-33 — no round result view; tap enabled after the round | Mini-result after every signal and a round table on the reveal. Tapping is disabled outside WAITING/FAKE/GO, after your tap and for spectators; the server rejects late taps.                                                                                                                                   |
| IMP-21 — fair reaction timing                              | Client-measured reaction times with server-side plausibility checks (see _Fair timing_).                                                                                                                                                                                                                        |
| IMP-26 — a better Reaction Showdown                        | 3 or 5 signals per round, a real GO after every fake-out, results per signal and per round, a short self-set timer, early ends.                                                                                                                                                                                 |

## Files

| Path                                                                  | What                                                                                          |
| --------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `backend/…/ReactionShowdownGame.java`                                 | metadata, settings, `createLogic`                                                             |
| `backend/…/ReactionShowdownLogic.java`                                | the rules: signal state machine, tap judging, early ends, scoring, views, awards, debug state |
| `backend/…/SignalPlan.java` · `FakeOut.java`                          | the secret schedule of a signal (wait, optional decoy)                                        |
| `backend/…/Signal.java` · `SignalState.java` · `Reaction.java`        | the signal being played and the players' reactions                                            |
| `backend/…/SignalScoring.java` · `RoundTable.java` · `GameStats.java` | points per signal, the round table, award statistics                                          |
| `backend/…/ReactionShowdownView.java`                                 | what browsers receive                                                                         |
| `backend/src/test/…`                                                  | scenario tests (`GameHarness`): flow and rules                                                |
| `frontend/`                                                           | the game screen (`index.ts` exports it)                                                       |
| `e2e/reaction-showdown.spec.ts`                                       | Playwright: a full game with a spectator, and a false start                                   |

`debugState()` (e2e profile only) returns the current `signalId`, `signal`, `state`, `nextInMs`
(time to the next step) and `fakeComing` (whether the waiting signal has a decoy planned), plus the
`signals` and `fakeouts` settings.

See [docs/adding-a-game.md](../../docs/adding-a-game.md).
