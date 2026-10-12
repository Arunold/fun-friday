# 🎨 Pictionary

One player draws, everyone else races to guess the word.

Every round each player draws once. The drawer picks one of three words and sketches it on a shared
canvas; the others type guesses into a live feed. Correct guesses are announced without giving the
word away, a near miss earns a private _"So close!"_, and letter hints appear as time runs out.

## Rules & scoring

| Who                       | Points                                                     |
| ------------------------- | ---------------------------------------------------------- |
| Correct guesser           | 100, falling linearly to 40 at the end of the drawing time |
| First correct guesser     | +20                                                        |
| Drawer                    | 25 per player who guessed the word, at most 150            |
| Wrong guess, no guess     | 0 (no partial credit — only exact answers score)           |
| Turn voided (drawer left) | 0 for everyone                                             |

- **3–10 players.** The drawer order is the join order, fixed when the round starts. Drawers who left
  or are offline are skipped.
- **Word choice:** 10 s to pick one of three words (only the drawer sees them; keys 1–3 work too).
  When time runs out a word is picked at random. Words never repeat within a game.
- **Drawing:** the round timer starts once the word is picked. The turn ends early when every
  connected guesser has the word. If the drawer leaves or loses the connection, the turn ends at
  once and nobody scores ("… left — no points this turn").
- **Guessing:** case, accents, extra spaces and hyphens don't matter (`Ice-Cream` = `ice cream`).
  Up to 30 guesses per player per turn; the drawer can't guess and correct guessers can't guess
  again (they see the word instead).
- **So close!** One typo in a word of at least 5 letters, or the word inside a longer guess, is a
  near miss: only its author sees it (in the feed and as a note), so it can't give the word away.
- **Letter hints** (setting): at half and at three quarters of the drawing time a few letters of the
  masked word (`_ a _ _ e`) are revealed to the guessers.

## Awards

| Award          | For                                                    |
| -------------- | ------------------------------------------------------ |
| 🎨 Picasso     | the drawer whose drawings were guessed by most players |
| 🔮 Mind reader | the most first correct guesses                         |
| 👀 Quick eye   | the fastest correct guess                              |

## Settings

| Key             | Label        | Values                                          | Default |
| --------------- | ------------ | ----------------------------------------------- | ------- |
| `rounds`        | Rounds       | 1, 2, 3 (everyone draws once per round)         | 2       |
| `roundDuration` | Round timer  | 60, 80, 100, 120 s (drawing time)               | 80 s    |
| `difficulty`    | Words        | `easy`, `medium`, `hard`, `mixed` (one of each) | `mixed` |
| `hints`         | Letter hints | on / off                                        | on      |

## Drawing

- The canvas is a logical **1000 × 750** sheet scaled to fit the stage (4:3, as large as possible).
- Tools for the drawer: 12 colours, 3 brush sizes, eraser, fill, undo, redo and clear (clear is
  undoable). Shortcuts: <kbd>Ctrl</kbd>+<kbd>Z</kbd> undo, <kbd>Ctrl</kbd>+<kbd>Y</kbd> /
  <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>Z</kbd> redo, <kbd>B</kbd> pen, <kbd>E</kbd> eraser,
  <kbd>F</kbd> fill, <kbd>1</kbd>–<kbd>3</kbd> brush size.
- The drawer's browser renders immediately and sends the stroke in batches about every 80 ms
  (`sendFast('stroke', {id, tool, color, size, points})`). The server appends every batch to the
  turn's authoritative drawing log (`Drawing`), relays it on the room stream and skips the state
  update (`ctx.suppressUpdate()`), so strokes don't flood the room with snapshots.
- Fill, undo, redo and clear are server actions on the same log, relayed as `edit` events. Fills are
  flood fills replayed in log order at the logical size, so every canvas ends up identical.
- **Consistency (BUG-18):** every snapshot carries the whole log with a version, so guesses, hints
  and phase changes re-sync every canvas — including after a refresh, a reconnect or for spectators.
  Between snapshots the browsers apply stream events in order (deduplicated by stroke id); on a gap
  they ask for the log (`sync`) or wait for the next snapshot.
- Limits keep payloads small and stop abuse: 256 points per batch, 2 000 per stroke, 25 000 per
  picture, 2 000 operations per turn; colours must be `#rrggbb`.

## Report items

| Item                                  | How it is resolved                                                                                                                                                                                                               |
| ------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| BUG-04 — freeze when a player leaves  | `turnsInRound` fixes the drawer order per round; `skipTurn` skips drawers who are gone or offline; a drawer leaving mid-turn ends the turn without points. The runtime contains errors in game code, so nothing can hang a room. |
| BUG-07 — word sent to every player    | `view()` is per viewer: only the drawer (and guessers who already got it) receive `word`; everybody else gets the mask. The REST API exposes no game state.                                                                      |
| BUG-08 — answers leak between turns   | Every turn starts from a fresh `Turn`; the word is in views only for the drawer, correct guessers and after the reveal.                                                                                                          |
| BUG-09 — no live feedback             | Every guess updates the feed for everyone at once (masked: "🎉 Bob guessed it!"); near misses send a private `close` event.                                                                                                      |
| BUG-18 — canvases drift apart         | Authoritative, versioned drawing log in every snapshot + ordered stream events (see _Drawing_).                                                                                                                                  |
| BUG-19 — wrong points in turn results | The reveal lists every correct guesser with their time and the points actually awarded, plus the drawer's points.                                                                                                                |
| BUG-32 — Ctrl+Z / Ctrl+Y missing      | Keyboard shortcuts for undo/redo (and tools) on the drawer's side; covered by the e2e spec.                                                                                                                                      |
| BUG-05 — 30 s default for every game  | Own drawing time: 80 s by default (60–120 s), plus a separate 10 s word choice.                                                                                                                                                  |
| IMP-20 — partial-credit exploits      | No partial credit at all; guesses are capped at 30 per turn and rate-limited by the platform.                                                                                                                                    |
| IMP-25 — a livelier Pictionary        | Streaming strokes, server log with replay, letter hints, 1-of-3 word choice, no repeated words, eraser/fill/sizes, shortcuts.                                                                                                    |

## Files

| Path                                                          | What                                                                              |
| ------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `backend/…/PictionaryGame.java`                               | metadata, rules, settings, `createLogic`                                          |
| `backend/…/PictionaryLogic.java`                              | turns, word choice, guesses, hints, scoring, views                                |
| `backend/…/Drawing.java`                                      | the authoritative drawing log (strokes, fills, undo/redo/clear, limits)           |
| `backend/…/GuessBook.java`                                    | guesses, the per-viewer feed and correct guessers                                 |
| `backend/…/WordMatcher.java`                                  | normalisation, correct / close / wrong                                            |
| `backend/…/WordList.java`, `WordPicker.java`, `HintMask.java` | curated word lists, choices without repeats, letter hints                         |
| `backend/…/Scoring.java`, `GameStats.java`                    | points and awards                                                                 |
| `backend/src/main/resources/…/words-*.txt`                    | ~120 drawable, family-friendly words per difficulty                               |
| `backend/src/test/…`                                          | scenario tests (`GameHarness`), drawing log and word/rule tests                   |
| `frontend/`                                                   | the stage (`pictionary.component`), canvas + tools (`sketch-board`), renderer     |
| `e2e/pictionary.spec.ts`                                      | Playwright: a full three-player round; keyboard undo/redo and private near misses |
