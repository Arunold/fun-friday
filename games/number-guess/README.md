# 🔢 Number Guess

Find the secret number — every guess tells you **higher** or **lower**. Everyone hunts the same
number at the same time; nobody sees the others' guesses until the reveal.

The reference game of the platform: small, but it uses every part of the SDK (settings with
cross-field validation, private hints, hidden state until the reveal, early turn end, awards,
`debugState` for e2e tests).

## Rules & scoring

Each round a number between the lowest and highest value is drawn. Each player has a few guesses;
each guess answers "higher", "lower" or "correct".

| Situation              | Points                                                                        |
| ---------------------- | ----------------------------------------------------------------------------- |
| Exact hit on guess _k_ | `max(40, 100 − 20·(k − 1))` + up to **20** for speed (time left when hitting) |
| No hit                 | up to **50** for the best guess, by closeness: `50 · (1 − distance / range)`  |
| No guess               | 0                                                                             |

The round ends when every connected player hit the number or used all guesses, or when the timer
runs out. The reveal shows the number, everyone's guesses and their closest guess on the range bar.

Awards: **🎯 Bullseye** (most exact hits) and **⚡ Quick draw** (fastest hit).

## Settings

| Key             | Label             | Values               | Default |
| --------------- | ----------------- | -------------------- | ------- |
| `rounds`        | Rounds            | 1, 2, 3, 5, 7, 10    | 3       |
| `roundDuration` | Round timer       | 15, 20, 30, 45, 60 s | 30 s    |
| `minRange`      | Lowest number     | 1, 10, 50, 100       | 1       |
| `maxRange`      | Highest number    | 50, 100, 500, 1000   | 100     |
| `attempts`      | Guesses per round | 1, 3, 5              | 3       |

Validation: the highest number must be at least 10 above the lowest (`validate()`), so e.g.
100–50 is rejected with a clear message.

## Design notes

- **Hidden until the reveal (BUG-08, IMP-29):** the view shows each player only their own guesses
  and hints; others are visible as "guesses used / done". The target and all guesses are added to
  the view only once the turn has ended.
- **Higher/lower over several attempts (IMP-29):** turns the old one-shot guess into a short
  deduction game; the UI narrows a "still possible" interval on the range bar after every hint.
- **Early end:** `isTurnComplete` checks only connected players, so someone who drops out never
  blocks the round.
- **Validation:** guesses outside the range, extra guesses and malformed payloads are rejected with
  a message shown next to the input.

## Files

| Path                                          | What                                          |
| --------------------------------------------- | --------------------------------------------- |
| `backend/…/NumberGuessGame.java`              | metadata, settings, validation, `createLogic` |
| `backend/…/NumberGuessLogic.java`             | rules and scoring                             |
| `backend/…/NumberGuessView.java`              | what browsers receive                         |
| `backend/src/test/…/NumberGuessGameTest.java` | scenario tests (`GameHarness`)                |
| `frontend/number-guess.component.*`           | the game screen; `index.ts` registers it      |
| `frontend/number-guess.types.ts`              | TypeScript mirror of the view                 |
| `e2e/number-guess.spec.ts`                    | Playwright: full game, out-of-range guess     |
