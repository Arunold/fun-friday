# 🧩 Sliding Puzzle

Everyone gets the same scrambled number puzzle and races to slide the tiles home — first solve wins.

## Rules & scoring

Every round deals one scramble (3×3, 4×4 or 5×5); each player solves their own copy. Put the tiles
in order (1, 2, 3, … reading order) with the gap in the bottom-right corner.

| Situation                                      | Points                                                       |
| ---------------------------------------------- | ------------------------------------------------------------ |
| Solved — 1st / 2nd / 3rd / 4th / 5th and later | 100 / 80 / 65 / 55 / 50                                      |
| Efficiency bonus for solvers                   | up to +30: `30 × min(1, 2 × scramble distance ÷ your moves)` |
| Not solved when time is up                     | up to 40: `40 × tiles in place ÷ all tiles`                  |
| Never moved                                    | 0                                                            |

- **Moves** count tiles: a click that pushes three tiles counts three moves, exactly like three
  arrow-key presses. The _scramble distance_ is the scramble's Manhattan distance — a lower bound
  for any solution — so solving in at most twice that many moves earns the full bonus, twice as
  many moves half of it.
- Solvers always outscore non-solvers (50 > 40). Equal solve times share a place; if a solver
  leaves, everyone behind them moves up.
- A round ends when every connected player solved the puzzle or the time is up. Disconnected
  players never hold up a round.
- Other players' boards are never sent to anyone — opponents and spectators only see progress
  (tiles in place), moves, rank and time.

Awards on the podium: **⚡ Speed solver** (fastest solve), **🎯 Efficient** (fewest moves in a
solve), **💪 Persistent** (most moves overall).

## Controls

| Input                                                                                                      | Effect                                                                                                                     |
| ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Click a tile in the gap's row or column                                                                    | slides it and every tile between it and the gap one cell toward the gap                                                    |
| <kbd>←</kbd> <kbd>↑</kbd> <kbd>→</kbd> <kbd>↓</kbd> or <kbd>W</kbd> <kbd>A</kbd> <kbd>S</kbd> <kbd>D</kbd> | moves the tile next to the gap _into_ the gap in that direction — <kbd>←</kbd> moves the tile right of the gap to the left |

WASD uses physical key positions, so it also works on AZERTY/QWERTZ keyboards.

## Settings

| Key         | Label      | Values                      |
| ----------- | ---------- | --------------------------- |
| `rounds`    | Rounds     | 1, **2**, 3                 |
| `size`      | Board size | 3×3, **4×4**, 5×5           |
| `timeLimit` | Time limit | **Auto**, 1, 2, 3, 4, 5 min |

_Auto_ gives 60 s for 3×3, 150 s for 4×4 and 300 s for 5×5. A 5×5 board needs at least 2 minutes
(the settings form rejects 5×5 with 1 min).

## Design notes

- **IMP-28 — optimistic moves.** The client applies a move at once (tiles slide with a 120 ms CSS
  transform transition, tracked by tile number so DOM nodes never move) and sends
  `move {tile}`. Every view carries the player's server board and move count; since the server
  counts every tile it moves, the count tells which local moves a view already contains. The rest
  are replayed on top of the server board (`reconcile()` in `frontend/puzzle.ts`). If a move is
  refused or the replay doesn't fit, the server board wins and the tiles animate back into place.
  Stale views (older than the board we have) are ignored.
- **IMP-28 — arrow keys.** Arrow keys and WASD move the tile on that side of the gap into it;
  the hint under the board says so. Keys are ignored while typing in inputs, with modifier keys or
  when a menu already handled them.
- **IMP-28 — minimum scramble difficulty.** A scramble is a random walk of legal moves from the
  solved board (never undoing the previous move), so it is always solvable. The walk continues
  until the board's Manhattan distance reaches 85% of a random board's average (12 / 32 / 65 for
  3×3 / 4×4 / 5×5) and at least 70% of the tiles are out of place — never the solved board.
- **BUG-05 — time depends on the size.** The game doesn't use `CommonSettings.roundDuration`; it
  sets the deadline itself in `startTurn` (`timeLimitSeconds()`), with "Auto" scaled to the board.
- **BUG-08 — no leaks between rounds.** `startTurn` builds a fresh scramble and fresh attempts and
  clears the `revealed` flag before the platform's first snapshot of the round, so round 2 never
  shows round 1's results. Views contain only the viewer's own board; ranks and times describe
  progress, not positions (scenario test `theSecondRoundStartsWithAFreshPuzzleAndNoResultsOfTheFirst`).
- The component's host claims the stage height itself (`:host { flex: 1 }`): the stage's
  `.game > *` rule doesn't reach a dynamically created host element.
- `debugState()` returns the scramble, its distance, the time limit and every player's board by
  name, so the e2e spec can solve 3×3 boards with a small IDA* solver.

## Files

| Path                                  | What                                                                                |
| ------------------------------------- | ----------------------------------------------------------------------------------- |
| `backend/…/SlidingPuzzleGame.java`    | metadata, settings, validation, time limit (`createLogic`)                          |
| `backend/…/SlidingPuzzleLogic.java`   | the rules: rounds, moves, ranks, scoring, views, awards                             |
| `backend/…/Board.java`                | board model: pushes, tiles in place, Manhattan distance                             |
| `backend/…/Scrambler.java`            | solvable random-walk scrambles with a minimum difficulty                            |
| `backend/…/Attempt.java`              | one player's copy of the puzzle, moves and solve time                               |
| `backend/…/Scoring.java`              | points formulas and time formatting                                                 |
| `backend/…/SolveStats.java`           | game-wide records for the awards                                                    |
| `backend/…/SlidingPuzzleView.java`    | what browsers receive                                                               |
| `backend/src/test/…`                  | board/scrambler/scoring tests, scenario tests (`GameHarness`), a BFS solver for 3×3 |
| `frontend/sliding-puzzle.component.*` | the board, side panel, spectator leader board                                       |
| `frontend/puzzle.ts`                  | pure board helpers and the reconciliation of optimistic moves                       |
| `frontend/sliding-puzzle.types.ts`    | TypeScript mirror of the view                                                       |
| `e2e/sliding-puzzle.spec.ts`          | Playwright: full game with an IDA* solver, arrow keys, multi-tile clicks            |

See [docs/adding-a-game.md](../../docs/adding-a-game.md).
