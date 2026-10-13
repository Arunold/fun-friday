# 🚌 Backseat Drivers

_One bus. Eight drivers. One button each._ The whole team drives one office shuttle to the office
parking lot. Everybody controls just one thing — and only the **navigator** can see the map.
Everyone must be able to talk: same room or a call.

The platform's first **real-time** game: the server simulates the bus 20 times a second and streams
compact frames to every browser, which draws them on a canvas.

## Rules

- **The navigator 🧭** sees the whole map (route, turns, hazards, the bus) but has no button: they
  shout directions — _"Sharp left in 3… 2… 1… NOW!"_.
- **Everybody else** holds one or two controls and sees only the road around the bus (≈ 6 tiles,
  fog beyond). Spectators see the same fogged road — no cheating.
- **SEAT SHUFFLE!** Every 30 s (setting) a siren flashes for 2 s, then every control is released and
  all seats are dealt again — the navigator always changes.
- **Goal:** reach the 🅿️ office parking lot (the bus's centre on a parking tile) before the level
  timer runs out. A level also ends when the bus touches the CEO's sports car.

| Hazard           | What happens                                                            |
| ---------------- | ----------------------------------------------------------------------- |
| 🚧 Cone          | −5 s on the level timer, −25 points; the cone flies away                |
| ☕ Coffee cup    | +5 s on the level timer, +50 points                                     |
| 🦢 Geese         | cross when the bus comes within 5 tiles and block the road for 3 s      |
| 🌧️ Rain          | the drivers' view blurs unless the wipers were tapped in the last 1.5 s |
| 🚇 Tunnel        | pitch black (≈ 1.5 tiles of sight) unless the headlights are on         |
| 🏎️ The CEO's car | touching it ends the level, with no time bonus                          |

A honk within 6 tiles scatters geese at once (also before they start crossing). Every level starts
with the CEO's car parked **right behind the bus** — reversing first is instant failure, so the
navigator must warn the team.

The three levels are always played in this order (Levels = 1, 2 or 3): **Campus Loop** (wide roads,
cones, coffee, geese), **Downtown** (tighter turns, rain and a tunnel) and **Business Park**
(everything, two flocks of geese and a second CEO car next to the parking lot).

## Controls

| Control              | Keys    | How                                                          |
| -------------------- | ------- | ------------------------------------------------------------ |
| Steer left           | `←` `A` | hold (turns only while moving; mirrored when reversing)      |
| Steer right          | `→` `D` | hold — left + right together cancel out                      |
| Gas                  | `↑` `W` | hold                                                         |
| Brake                | `↓` `S` | hold; reverses slowly once stopped — gas + brake: brake wins |
| Horn                 | `H`     | tap                                                          |
| Wipers               | `E`     | tap: one wipe                                                |
| Headlights           | `L`     | tap: on / off                                                |
| _your first control_ | `Space` | so with one control each, everyone simply uses Space         |

The banner also shows your controls as big buttons (press and hold with the mouse). `W` `A` `S` `D`
also work by key position on other keyboard layouts. A key that isn't yours tells you whose it is
(_"Gas is Cleo's job — shout at them!"_), and steering while the bus stands still says it needs
speed. In a **silent-navigator** game the navigator gets signal buttons instead — `←` `↑` `→` `↓`
`H` `E` `L` send _Left, Straight, Right, Stop, Honk, Wipe, Lights_, which flash big on everybody's
screen.

Who holds what (one navigator, the rest share the controls; tap controls without a seat of their own
are added to other seats, spread evenly, so every hazard can always be handled):

| Players | Seats besides the navigator                                                       |
| ------- | --------------------------------------------------------------------------------- |
| 3       | Left + Right + Wipers · Gas + Brake + Horn + Headlights                           |
| 4       | Left + Wipers · Right + Horn · Gas + Brake + Headlights                           |
| 5       | Left · Right + Headlights · Gas + Wipers · Brake + Horn                           |
| 6       | Left · Right · Gas · Brake + Headlights · Horn + Wipers                           |
| 7       | Left · Right · Gas · Brake · Horn + Headlights · Wipers                           |
| 8       | Left · Right · Gas · Brake · Horn · Wipers · Headlights                           |
| 9       | **Power steering:** Left ×2 · Right ×2 · Gas · Brake · Horn · Wipers + Headlights |
| 10      | **Power steering:** Left ×2 · Right ×2 · Gas · Brake · Horn · Wipers · Headlights |

With power steering a side only steers while both of its holders press.

## Scoring

Scoring is team-based: **every player still in the game gets the level score**, so the podium shows
the whole team tied — that's intended (co-op).

| Per level             | Points                            |
| --------------------- | --------------------------------- |
| Each second left      | +10 (0 if out of time or CEO car) |
| Each coffee collected | +50                               |
| Each cone hit         | −25                               |
| Level score           | never below 0                     |

After the last level comes the **MVP vote** (20 s, ends early once every connected player voted, you
can't vote for yourself, spectators watch, 0 points). Awards: **🏆 Team MVP** (most votes; ties share
it), **🧭 Best navigator** (guided the bus through the most new tiles, at least 10), **📯 Horn
hero** (most flocks of geese scattered) and **🦶 Lead foot** (longest on the gas, at least 3 s).

## Settings

| Key             | Label        | Values                                 | Default |
| --------------- | ------------ | -------------------------------------- | ------- |
| `rounds`        | Levels       | 1, 2, 3                                | 2       |
| `roundDuration` | Level timer  | 90, 120, 150, 180 s                    | 120 s   |
| `shuffle`       | Seat shuffle | 20, 30, 45 s (siren 2 s before)        | 30 s    |
| `variation`     | Variation    | Classic · Rush hour · Silent navigator | Classic |

**Rush hour:** the top speed rises from 6 to 8 tiles/s over the level timer while the brakes weaken
from 9 to 4.5 tiles/s². **Silent navigator:** the navigator may not talk (honour system) and signals
instead (at most two signals per second).

## Design notes

- **Architecture.** `BackseatDriversLogic` handles turns, actions and scoring; a `LevelRun` runs one
  level: the tick loop (`ctx.schedule("tick")` every 50 ms, aligned to `turnStartedAt`), the SEAT
  SHUFFLE clock, the roster and the deadline (`extendDeadline(±5 s)` for coffee and cones). The
  engine knows nothing about the platform: `Simulation` (hazards, effects, trail) moves a `Bus`
  with `BusPhysics` (circle of radius 0.45 against the tile map: a bump stops the bus, a glancing
  touch slides along the wall, the CEO's car ends the level), `Seating` deals the seats and
  `InputBook` turns key changes into pedals and taps. Maps are curated ASCII files
  (`level-1..3.txt`, 64–76 × 40–46 tiles) validated by `TrackLoader` when a game starts
  (rectangular, known characters, one start, a parking lot and a CEO car reachable by road).
- **Streams, not snapshots.** Every tick streams a compact `frame` (tick, bus ×1000 as ints,
  pressed controls per seat, effect bits, counters) and calls `suppressUpdate()`; a snapshot goes
  out only when the views change (seats, the siren, hazards, the deadline). The view always holds
  the latest frame, so a reconnecting browser draws at once; the browser keeps whichever frame is
  newer, renders ~75 ms behind the newest frame and interpolates (heading along the short way).
- **No cheating.** Only the navigator's view contains the map. Drivers and spectators get a
  `window` (cells within 7.5 tiles of the bus's cell, everything else `?`) — streamed whenever the
  bus enters another cell or a hazard changes, and part of their view. The browser fogs the road
  from 4.6 tiles and is opaque at 6.1, so unknown cells never show. Frames carry no map data.
  Everyone sees the whole map with the bus's trail once the level is over.
- **Input.** The browser sends changes only (`input {control, pressed}`): a key it already holds is
  never sent again, but an auto-repeat counts as a press when the key isn't held yet — so a key held
  since before the level started, or since before a SEAT SHUFFLE gave you its control, works
  without pressing it again. Everything is released when the window loses focus, and typing is
  never hijacked. Inputs from someone who doesn't hold that control (e.g. a key still down after a
  shuffle) are ignored silently; a key held through a shuffle keeps working if you kept that
  control. Key changes go out as _quiet_ requests (`api.send(…, { quiet: true })`) rather than
  `sendFast`, so the browser learns when the rate limit dropped one: a dropped hold change is sent
  again, so no pedal stays pressed.
- **SEAT SHUFFLE fairness.** 24 random deals, keeping the one where people get the fewest controls
  they had before; the navigator must change and goes preferably to someone who navigated least.
  If a seated player drops out, the seats are repaired at once (the navigator stays if possible).
  A player who reconnects waits for the next shuffle (their seat went to others).
- **Canvas.** The road view is drawn in device pixels (crisp on HiDPI), north up with the bus in
  the middle: kerbs, trees, puddles, the CEO's red sports car, a yellow shuttle with windows,
  brake and reverse lights and headlight beams; rain streaks and blur, tunnel darkness with a cone
  of light, a wiper sweep, honk rings, bump shake, sparks, flying cones and "+5 s". Decorative
  motion is toned down with `prefers-reduced-motion`. The navigator's map pre-renders the ground
  once per level and highlights hazards within 9 tiles of the bus and the drivers' circle of sight.
- **GameIdeas.md coverage.** Implemented: the shared bus, one control each with the documented
  seat table and power steering, the navigator-only map, fog of war for drivers and spectators,
  all six hazards, SEAT SHUFFLE with a 2 s siren, 2–3 levels of about 2 minutes that add hazards,
  team scoring with the exact formula, the MVP vote, the Silent Navigator and Rush Hour variations.
  **Not implemented:** _Saboteur_ (needs secret roles plus a second vote — a game of its own) and
  _Versus_ (two buses = teams, which the platform doesn't have), the **Hall of Fame** against last
  Friday's team (needs persistent per-team records — a platform feature; the Friday Cup covers
  history), and sound (the siren is visual; no audio assets).

## Files

| Path                                                     | What                                                 |
| -------------------------------------------------------- | ---------------------------------------------------- |
| `backend/…/BackseatDriversGame.java`                     | metadata, settings, `createLogic`                    |
| `backend/…/BackseatDriversLogic.java`                    | turns (levels + MVP vote), actions, scoring, awards  |
| `backend/…/LevelRun.java`                                | one level: tick loop, shuffle clock, roster, streams |
| `backend/…/{Simulation,BusPhysics,Bus}.java`             | the engine: hazards, effects, physics                |
| `backend/…/{Seating,InputBook,Control}.java`             | seats and inputs                                     |
| `backend/…/{TrackLoader,TrackMap,Tile,Areas}.java`       | maps, validation, the drivers' window                |
| `backend/…/{Scoring,MvpVote,GameStats}.java`             | level score, MVP vote, awards                        |
| `backend/…/{Rules,Variation,SignalKind,DebugState}.java` | settings, signals, state for e2e tests               |
| `backend/…/BackseatDriversView.java`                     | what browsers receive (views, frames, windows)       |
| `backend/src/main/resources/…/level-*.txt`               | the three curated maps (legend below)                |
| `backend/src/test/…`                                     | scenario tests via `GameHarness` (+ `Autopilot`)     |
| `frontend/backseat-drivers.component.*`                  | layout, keyboard, input; `index.ts` registers it     |
| `frontend/role-banner.component.*`                       | your controls / the navigator's call, shuffle ring   |
| `frontend/road-view.component.*`, `road-renderer.ts`     | the drivers' fogged canvas                           |
| `frontend/map-view.component.*`, `map-renderer.ts`       | the navigator's map, the reveal with the trail       |
| `frontend/side-panel.component.*`                        | coffee, cones, score preview, roster, level result   |
| `frontend/mvp-vote.component.*`                          | the MVP vote                                         |
| `frontend/signal-flash.component.ts`                     | silent-navigator signals on everybody's screen       |
| `frontend/{bus-feed,controls,paint,canvas-loop}.ts`      | frames + interpolation, keys, painters, canvas loop  |
| `frontend/backseat-drivers.types.ts`                     | TypeScript mirror of the view                        |
| `e2e/backseat-drivers.spec.ts`                           | Playwright: full game, signals + spectators, shuffle |

Map legend: `.` grass · `#` building · `T` tree · `w` water · `r` road · `~` road in rain · `t`
road in a tunnel · `P` parking lot · `S` start (heading in the header) · `C` cone · `K` coffee ·
`G` geese crossing · `E` the CEO's car.
