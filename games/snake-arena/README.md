# 🐍 Snake Arena

The classic Snake, except the other snakes are your coworkers. Everyone steers a snake in one
shared arena: eat to grow, make the others crash into you, and don't crash yourself.

The platform's first real-time game: the server simulates the arena at 10 ticks per second and
streams a compact frame every tick; browsers draw it on a canvas and glide between frames.

## Rules & scoring

Snakes never stop: each tick they move one cell. You can only turn — never reverse into yourself.
Each round opens with a one-second **Get ready…** (snakes visible, frozen), then **Go!**.

- **🍎 Food** makes you one segment longer. There are always about `2 + 1.5 × players` apples.
- **Crash** into a wall, any snake's body (yours included) or another head and your snake becomes
  food for the others: every second segment turns into an apple (at most 30). You come back after
  **3 seconds** as a 4-segment snake on a safe spot. Your points stay.
- **Head-on** (or two heads swapping cells): both snakes crash, nobody is credited.
- **Power-ups** appear every 10–15 s (at most two at a time, each vanishes after 15 s):

| Power-up  | Effect                                                                  |
| --------- | ----------------------------------------------------------------------- |
| ⚡ Boost  | two cells per tick for 3 s                                              |
| 👻 Ghost  | for 3 s you pass through other snakes' bodies and they through yours    |
| 🧲 Magnet | for 5 s every apple within 5 cells slides one cell towards you per tick |

| Situation                                                         | Points  |
| ----------------------------------------------------------------- | ------- |
| Eat an apple                                                      | **+1**  |
| Another snake crashes into your body                              | **+5**  |
| Longest snake alive when the round ends (ties: all of them)       | **+5**  |
| _Last snake standing:_ still alive when the round ends (everyone) | **+10** |

The reveal shows the frozen final frame with the round table (points, apples, snakes trapped,
crashes, longest length) and a headline: _"Dev ruled the arena with 31 points"_, _"Ana and Ben
shared the arena with 12 points each"_ or, in last snake standing, _"Ana survived!"_.

Awards: **🐍 Biggest snake** (longest length reached), **🕸️ Trapper** (most snakes that ran into
them), **🍎 Glutton** (most apples) and **🛡️ Survivor** (fewest crashes). Ties go to the higher
total score, then to whoever joined first.

## Controls

| Key                    | Action                                                 |
| ---------------------- | ------------------------------------------------------ |
| `←` `↑` `↓` `→` / WASD | turn — a quick double turn is queued (up to two turns) |

Your snake turns at the next cell its head reaches on screen — its eyes look the new way at once —
and that is where the server turns it too, network delay or not (see _Latency_ below). Key
auto-repeat is ignored and typing in text fields is never hijacked. Spectators watch the whole
arena and the live leaderboard. The kill feed reads _"Ana ⚔️ Dev"_ (Dev ran into Ana), 💥 for a
head-on crash, 🧱 for a wall, 🌀 for biting your own tail and 👋 for a player who left.

## Settings

| Key             | Label       | Values                                        | Default |
| --------------- | ----------- | --------------------------------------------- | ------- |
| `rounds`        | Rounds      | 1, 2, 3, 5                                    | 3       |
| `roundDuration` | Round timer | 60, 90, 120, 180 s                            | 90 s    |
| `arena`         | Arena       | Auto, Small 36×24, Medium 44×28, Large 52×32  | Auto    |
| `variation`     | Variation   | Classic, Shrinking arena, Last snake standing | Classic |
| `powerUps`      | Power-ups   | on / off                                      | on      |

**Auto** picks small for up to 3 players, medium for up to 6 and large for more (2–12 players).

- **Shrinking arena:** during the last 30 s the walls move in by one cell on every side every 3 s
  (down to 12×8). The next ring pulses before it moves; a snake caught by a wall crashes.
- **Last snake standing:** no respawns. The round ends as soon as at most one snake is left (or
  with the timer); everyone still alive gets +10.

## Design notes

- **Architecture.** `SnakeArenaLogic` is the only class that talks to the SDK (tick timer, actions,
  views, scoring); the engine underneath is plain Java: `Simulation` (one round, tick by tick),
  `MoveResolver` (simultaneous moves and collisions), `Spawner` (start slots, respawn spots),
  `Board`, `Snake`, `RoundScoring`, `GameStats` and `KillFeed`.
- **Tick loop.** A `tick` timer every 100 ms, aligned to `ctx.turnStartedAt()` so it never drifts.
  Each tick applies the queued turns, simulates, streams a `frame` and calls `ctx.suppressUpdate()` —
  ticks never cause snapshots. The view always carries the latest frame, so a (re)connecting browser
  draws at once; browsers keep whichever frame is newer (stream or snapshot, by `round` + `tick`).
- **Frames** are compact and complete: snakes as cell-index arrays (`y · cols + x`, head first),
  food, power-ups, the wall bounds and the last five kill-feed entries — about 2–4 KB with a full
  room. Respawn and power-up times are tick numbers, so countdowns need no clock sync.
- **Collisions** are resolved simultaneously each tick: tails move out of the way first (unless that
  snake eats), then walls → own body → two heads swapping cells → other bodies (the owner gets +5)
  → heads meeting on one cell. A boosted snake moves its extra cell in a first sub-step on its own;
  hitting a snake that hasn't moved yet in that sub-step counts as a body hit.
- **Fairness.** Start slots sit along the four walls, three cells in, filled left, right, top,
  bottom and spread evenly; opposite walls interleave so nobody starts head-on, and the order
  rotates by one seat every round. A respawn needs an empty 5×5 area at least one cell inside the
  walls that is not within four cells in front of another head (preferably without food); the new
  snake heads for the side with the longest free run and doesn't move on its first tick. If there
  is no such area, it waits and tries again every tick.
- **Input.** `turn {dir, at}` with Bean Validation: unknown actions and directions are rejected;
  harmless no-ops — a turn into the current heading, a reversal, a turn while crashed or with a full
  queue — are ignored silently, so no error toast appears. `at` (optional) is the tick whose head
  position the turn starts from — the cell where the player's screen shows the snake turning. The
  server carries the turn out with the first move after that tick: an early turn waits (at most
  four ticks), a late one goes with the next move; without `at` the next move takes it.
- **Latency.** The server decides everything; the browser hides the network delay without ever
  showing a move the server won't make (`arena-playback.ts`):
  - _Other snakes_ glide between the two newest frames against a smoothed clock of the server's
    ticks (`frame-clock.ts`), held back a little more while frames arrive unevenly — they only show
    moves the server has made.
  - _Your snake_ runs ahead along your pending turns (client-side prediction, `steering.ts`). A key
    turns it at the next cell centre its drawn head reaches — its eyes look the new way at once —
    and the turn names that cell's tick. Your snake is drawn a round trip minus one tick (plus
    30 ms) past the frames' clock (`lead.ts`), so the turn always reaches the server before that
    tick has passed and the server turns the snake in exactly the same cell: the head never jumps,
    near or far from the server (round trips up to ~370 ms). The round trip comes from the
    platform's ping and from the answer to every turn (the second longest of the last eight
    counts); a change is eased in, so your snake runs a little faster or slower for a moment
    instead of jumping. Close to the server that puts your snake at about the others' moment; far
    away, the others lag behind it by the round trip.
  - What still needs correcting — a frame later than ever, a boost that starts — glides out within
    ~100 ms. A head guessed ahead runs up to a wall and touches it; the crash is the server's to
    tell (waiting at the last cell would look as if a turn could still save it).
- **Leaving and connections.** A player who leaves turns into food (feed: 👋) and drops out of the
  round table; a disconnected snake keeps going straight and respawns as usual.
- **Browser.** A `requestAnimationFrame` loop asks `ArenaPlayback` where every snake is — plain
  fields, no signals; `motion.ts` places a body between two frames or ahead of the newest one —
  while signals only drive the panel and overlays. It draws on the clock frames arrive on
  (`performance.now()`),
  so an effect born on arrival is never "younger than zero" — that negative age once made a canvas
  call throw and the snakes blink out for a picture whenever food appeared. The canvas is sized by
  a `ResizeObserver`, crisp on HiDPI, and tones down decorative effects (crash bursts, pulses, speed
  lines) under `prefers-reduced-motion`. Your own snake glows and is tagged **YOU** after every
  (re)spawn. The side panel always shows the full leaderboard (denser rows above 8 players); the
  kill feed and the controls/legend give way on short windows.
- **No hidden information:** everything on the board is public, so players and spectators get the
  same view.

### What of the GameIdeas design is (not) implemented

| GameIdeas item                                            | Status                                                                                                           |
| --------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Shared arena, colours, highlighted own snake, steering    | ✅                                                                                                               |
| Food, crashed snakes turn into food, 3 s respawn, head-on | ✅                                                                                                               |
| Scoring (+1 / +5 / +5), 3 × 90 s rounds                   | ✅ (round count and length are settings)                                                                         |
| ⚡ Boost, 👻 Ghost, 🧲 Magnet                             | ✅ (can be switched off)                                                                                         |
| Side scoreboard, kill feed _"Ana ⚔️ Dev"_, countdown      | ✅ — the round timer is the platform's                                                                           |
| Variations: Last snake standing, Shrinking arena          | ✅                                                                                                               |
| Variations: Teams, Light cycles                           | ❌ Teams needs team assignment the platform doesn't have yet; Light cycles is a different game (no food, trails) |
| `respawnDelay`, `gridWidth` / `gridHeight` settings       | ➖ fixed 3 s; three arena presets (plus Auto) instead of free sizes, so the field always suits the group         |
| Small updates (new heads, removed tails) instead of state | ➖ full frames: a few KB per tick is fine for ≤ 12 players and makes dropped messages and reconnects trivial     |

## Files

| Path                                                                                  | What                                                             |
| ------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `backend/…/SnakeArenaGame.java`                                                       | metadata, settings, `createLogic`                                |
| `backend/…/SnakeArenaLogic.java`                                                      | SDK glue: tick timer, actions, views, scoring, `debugState`      |
| `backend/…/Simulation.java`                                                           | one round of the arena, tick by tick                             |
| `backend/…/MoveResolver.java`                                                         | simultaneous moves and collisions                                |
| `backend/…/Spawner.java`                                                              | start slots and respawn spots                                    |
| `backend/…/Board.java`, `Snake.java`                                                  | the grid (food, power-ups, walls) and a snake                    |
| `backend/…/RoundScoring.java`, `GameStats.java`                                       | round bonuses and headline; awards                               |
| `backend/…/SnakeArenaView.java`                                                       | what browsers receive (view and frames)                          |
| `backend/…/Dir`, `Bounds`, `ArenaSize`, `Variation`, `PowerType`, `KillFeed`          | small value types and the kill feed                              |
| `backend/src/test/…/SnakeArena*Test.java`                                             | scenario tests (`GameHarness`; `TestArena` lays out the board)   |
| `frontend/snake-arena.component.*`                                                    | the game screen and overlays; `index.ts` registers it            |
| `frontend/side-panel.component.*`                                                     | leaderboard, kill feed, controls and legend                      |
| `frontend/arena-renderer.ts`, `arena-labels.ts`, `arena-effects.ts`, `arena-theme.ts` | canvas drawing, name labels, effects, colours                    |
| `frontend/arena-playback.ts`                                                          | where each snake is drawn and which tick a key turns at          |
| `frontend/frame-clock.ts`, `lead.ts`, `steering.ts`, `motion.ts`                      | tick clock, own snake's lead, own turns, snake poses             |
| `frontend/arena-input.ts`, `panel-model.ts`                                           | keys, panel data                                                 |
| `frontend/snake-arena.types.ts`                                                       | TypeScript mirror of the view                                    |
| `e2e/snake-arena.spec.ts`                                                             | Playwright: steering, spectator, a full last-snake-standing game |
