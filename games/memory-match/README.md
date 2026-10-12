# 🃏 Memory Match

Take turns flipping cards on one shared board — every match means you go again.

Everyone plays on the **same** board of face-down emoji cards. On your move you flip two cards: a
pair stays face up in your colour and you move again; otherwise both cards stay visible for a
moment (everyone can memorise them), flip back, and the next player moves. A round ends when every
pair has been found.

## Rules & scoring

| Situation                                                                 | Points |
| ------------------------------------------------------------------------- | ------ |
| Each pair found                                                           | 100    |
| Each further pair in the same streak (consecutive matches without a miss) | +50    |
| Most pairs in the round (ties all get it)                                 | +100   |

- Players move in join order; the starting seat rotates by one every round.
- A move = two flips within the **time per move**. If the clock runs out, the move is forfeited
  (a single face-up card turns back) and the next player moves.
- Players who are disconnected or have left are skipped immediately; they rejoin the rotation when
  they reconnect. With a single connected player the game continues solo; with nobody connected
  the round ends.
- The round also has a generous safety-net deadline: per pair twice the move time but at least
  20 s (e.g. 8 pairs × 20 s = 2:40), capped at 10 minutes. When it passes, the remaining cards are
  revealed and the pairs found so far count.

Awards on the podium:

| Award              | For                                                                    |
| ------------------ | ---------------------------------------------------------------------- |
| 🐘 Elephant memory | the longest streak (at least 2 pairs in a row)                         |
| 🃏 Pair collector  | the most pairs over the whole game                                     |
| 🍀 Lucky first try | the most "lucky" pairs — the second card had never been face up before |

Ties go to whoever got there first.

## Settings

| Key        | Label         | Values                                                               | Default |
| ---------- | ------------- | -------------------------------------------------------------------- | ------- |
| `rounds`   | Rounds        | 1, 2, 3 (a new board each round)                                     | 1       |
| `board`    | Board         | `4x4` (8 pairs), `4x5` (10), `4x6` (12), `6x6` (18) — rows × columns | `4x6`   |
| `moveTime` | Time per move | 5, 10, 15 s                                                          | 10 s    |
| `theme`    | Cards         | `mixed`, `animals`, `food`, `objects`, `nature`                      | `mixed` |

## Design notes

- **One platform turn = one board.** `turnsInRound` stays 1; the game runs its own moves inside the
  turn with turn-scoped timers: `move` (the time per move, restarted after every pair and every
  hand-over) and `flip-back` (1.2 s after a miss, during which flips are rejected).
- **No secrets in the view (BUG-08).** The faces live only in `Board`; `Board.cardViews()` adds the
  emoji to a card only while it is `UP` or `MATCHED` (and to every card once the round is revealed).
  There is no shuffled list, no "pairs" hint, nothing per viewer — players and spectators get the
  very same view. `startTurn` deals a fresh board and resets `revealed`, the last result and the
  round tallies before the first view of the round is rendered, so the start of round 2+ never
  shows the previous answers. Tests check that a face whose two cards are both face down appears
  nowhere in any view, including at the start of round 2.
- **The game owns its timing (BUG-05).** No generic round timer: the host picks the time per move;
  the round deadline is derived from the board and the move time in `startTurn`
  (`ctx.setDeadline`), so a 6×6 board isn't squeezed into a 30/60 s default.
- **One folder (IMP-50).** Backend module, scenario tests, UI, e2e spec and this README live in
  `games/memory-match/`; the platform discovers it automatically.
- **Absent players never block the board.** `onPlayerLeft` hands the move on. The SDK has no
  callback for disconnects, but the engine calls `isTurnComplete` after every roster change, so
  that is where a disconnected mover is skipped (`TurnOrder.pass` skips anyone not in
  `ctx.activePlayers()`). The move result `AWAY` lets the UI say so.
- **Flip animation without leaking.** The server stops sending a face the moment a card turns face
  down, so the client keeps the last seen emoji for the ~0.5 s flip-back animation and then drops
  it. Cards flip with a 3D `rotateY` and `backface-visibility`; `prefers-reduced-motion` is
  handled globally.
- **Fits every stage.** The card size is computed in CSS from the rows/columns and the available
  space (container query units, 3:4 cards, capped at 168 px wide), so 4×4 to 6×6 fit 1366×768 and
  1920×1080 without scrolling.
- **Content.** Curated emoji lists in `CardTheme` (27–32 per theme): single code points with
  default emoji presentation (no flags, ZWJ sequences, skin tones or variation selectors, Unicode
  ≤ 11), no look-alikes on the same theme, office-friendly.

## Files

| Path                                         | What                                                                     |
| -------------------------------------------- | ------------------------------------------------------------------------ |
| `backend/…/MemoryMatchGame.java`             | metadata, rules, settings, `createLogic`                                 |
| `backend/…/MemoryMatchLogic.java`            | the rules: moves, timers, hand-overs, scoring, view                      |
| `backend/…/Board.java`                       | one round's cards (secret faces, states, finders) and the safe card view |
| `backend/…/TurnOrder.java`                   | order of play per round (rotating start, skipping absent players)        |
| `backend/…/Scorebook.java`                   | pairs/points per round, streaks and tallies for the awards               |
| `backend/…/BoardSize.java`, `CardTheme.java` | board layouts and curated emoji themes                                   |
| `backend/…/MemoryMatchView.java`             | what browsers receive                                                    |
| `backend/src/test/…`                         | scenario tests (`GameHarness`) and content/settings tests                |
| `frontend/`                                  | the board: turn-order strip, turn banner with move timer, 3D card grid   |
| `e2e/memory-match.spec.ts`                   | Playwright: a full two-player game and "not your turn"                   |

See [docs/adding-a-game.md](../../docs/adding-a-game.md).
