# ⌨️ Speed Typing

Everyone races to type the same text: fast, and without typos.

## Rules & scoring

- Every round, all players get the **same text at the same moment**: one sentence, one or two
  sentences, or a short passage, depending on the text length setting. A text never comes twice in
  a game.
- Type it **exactly**. Typos turn red and have to be fixed: the text must match character for
  character to finish. When the whole length is typed but something is still wrong, the hint says
  _"Fix 3 errors to finish"_.
- **WPM** counts from your first keystroke (5 characters = 1 word). **Accuracy** is correct
  keystrokes ÷ all keystrokes, so a typo you fixed still counts against it.
- The round ends when every connected player has finished, or when the timer runs out.

| Situation                           | Points                                                                    |
| ----------------------------------- | ------------------------------------------------------------------------- |
| Finishing place                     | 1st **100** · 2nd **80** · 3rd **65** · 4th **55** · 5th and later **50** |
| Speed bonus (finishers)             | **+ WPM ÷ 4**, at most 30 (reached at 120 WPM)                            |
| Accuracy (finishers)                | **× accuracy** (96 % → × 0.96)                                            |
| Finished below the minimum accuracy | **× 0.5** on top: the finish still counts, it's worth half                |
| Not finished                        | **up to 40**: 40 × correctly typed prefix ÷ text length                   |

Example: 2nd place at 72 WPM with 97 % accuracy → (80 + 18) × 0.97 = **95** points.

Awards on the podium: ⚡ **Fastest fingers** (best WPM of a finished round), 🎯 **Sharpshooter**
(best average accuracy among players who finished at least once), 🏃 **Marathon** (most characters
typed, corrections included).

## Settings

| Key           | Label            | Values                                                                      |
| ------------- | ---------------- | --------------------------------------------------------------------------- |
| `rounds`      | Rounds           | 1, **3**, 5                                                                 |
| `difficulty`  | Text length      | Short (one sentence, 40–70 characters), **Medium** (80–140), Long (150–260) |
| `roundTime`   | Round timer      | **Auto** (45 s short · 75 s medium · 120 s long), 30, 60, 90, 120 s         |
| `minAccuracy` | Minimum accuracy | 80, **90**, 95 %                                                            |

Long texts need a timer of at least 60 s (or Auto); the server rejects 30 s with long texts.

## Design notes

**The server is authoritative for progress.** The browser sends what its player typed
(`progress {typed, keystrokes, errors}`) about every 150 ms with `sendFast`, and once more with
`send` when the text matches. The server (`Racer`) derives everything that counts from the typed
text: progress is the **correct prefix** only, the finish is an **exact match** and the finishing
order is the order in which those arrive. Keystroke and error counts can only be measured in the
browser; the server accepts them but clamps them to what is possible (they never shrink, every
currently wrong character was an error, nobody types faster than 25 characters per second).

**Anti-cheat (IMP-20).** The input blocks paste, drop, cut, undo/redo and autocorrect
(`beforeinput`, `paste`/`drop` handlers, `autocomplete`/`autocorrect`/`spellcheck` off, password
manager and Grammarly opt-outs) and keeps the caret at the end. Behind that, the server ignores
updates whose text grows faster than 25 characters per second (a token bucket with a burst of 15,
measured on both the length and the correct prefix, so replacing wrong characters can't skip it
either). Ignored updates are silent: the browser keeps re-sending its full text, so a legitimate
update delayed by a network hiccup lands a moment later — and after a longer outage the browser
catches up in steps of 12 characters, which the bucket always accepts — while a pasted text never
does.

**Live feedback (IMP-27).** WPM, accuracy and progress update with every keystroke in the
browser (`typing.ts` mirrors the server's rules); every character shows its state (typed right,
wrong in red with an underline, not typed yet, extra characters struck through) and a caret glides
to the next one. _"Fix N errors to finish"_ appears once the full length is typed with mistakes, and
the accuracy turns amber with a note when it drops below the minimum. Finishing below the minimum is
accepted but worth half — the finish card explains why. This is simpler than refusing the finish:
nobody gets stuck at the end of the text without knowing what to do.

**A coherent model (BUG-31).** `speed-typing.types.ts` mirrors `SpeedTypingView.java` field by
field. The typed text is part of the player's own `me` (it is only ever sent to its owner) and the
browser uses it: after a refresh or reconnect the input, keystrokes, errors and the WPM clock are
restored from it.

**Per-difficulty timer (BUG-05).** The game has its own `roundTime` setting with _Auto_ as the
default; `startTurn` sets the deadline with `ctx.setDeadline` (45 / 75 / 120 s by text length). It
deliberately doesn't use `CommonSettings.roundDuration`, whose single platform default would ignore
the text length.

**No leaks between rounds (BUG-08).** All round state is rebuilt in `startTurn` before the first
view of a round is rendered; results exist only once the round is revealed; other players' typed
texts are never in any view, and their speed shows once they finish. A scenario test checks that
round 2 starts with an empty view.

**Network etiquette.** Updates that change nothing visible call `ctx.suppressUpdate()`; no updates
are sent in the last 250 ms of a round (they would arrive after the end and bounce off with a
"wait for the next turn" toast); the finishing update is always attempted (quietly).

## Files

| Path                                                               | What                                                                  |
| ------------------------------------------------------------------ | --------------------------------------------------------------------- |
| `backend/…/SpeedTypingGame.java`                                   | metadata, settings, validation, `createLogic`                         |
| `backend/…/SpeedTypingLogic.java`                                  | the rules: texts per round, progress action, scoring, views           |
| `backend/…/Racer.java`                                             | one player's race: progress, finish, speed check, clamped counts, WPM |
| `backend/…/Scoring.java`                                           | the points table                                                      |
| `backend/…/GameStats.java`                                         | totals over the game and the awards                                   |
| `backend/…/TextLibrary.java`, `TextLength.java`, `RaceConfig.java` | curated texts, lengths, settings                                      |
| `backend/…/SpeedTypingView.java`                                   | what browsers receive                                                 |
| `backend/src/main/resources/…/texts/*.txt`                         | 36+ curated texts per length                                          |
| `backend/src/test/…`                                               | scenario tests (`GameHarness`) and unit tests                         |
| `frontend/speed-typing.component.*`                                | the screen: text, caret, input, stats, hints                          |
| `frontend/race-track.component.*`                                  | everyone's lanes                                                      |
| `frontend/race-results.component.*`                                | the reveal table                                                      |
| `frontend/typing.ts`                                               | character states, keystroke counting, WPM/accuracy, font fitting      |
| `frontend/speed-typing.types.ts`                                   | TypeScript mirror of the view                                         |
| `e2e/speed-typing.spec.ts`                                         | Playwright: full game, paste blocked, typos must be fixed             |

See [docs/adding-a-game.md](../../docs/adding-a-game.md).
