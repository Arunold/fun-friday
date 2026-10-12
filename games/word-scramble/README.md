# 🔤 Word Scramble

Everyone gets the same scrambled letters and races to type the word they spell. Guess as often as
you like — wrong guesses just shake the input — and the faster you crack it, the more you score.

## Rules & scoring

Every round has one word (never repeated within a game) shown as the same scrambled tiles to
everybody. A correct guess locks you in with your solve order and time while the others keep going.

| Situation                   | Points                                                           |
| --------------------------- | ---------------------------------------------------------------- |
| 1st to solve                | 100                                                              |
| 2nd to solve                | 80                                                               |
| 3rd to solve                | 65                                                               |
| 4th and later               | 50                                                               |
| Speed bonus (every solver)  | + up to 20: `20 × share of the round still left` when you solved |
| Each hint you took (max. 2) | −25 % of your points for that round                              |
| Not solved                  | 0                                                                |

Example (45 s round): Ann solves first after 9 s → 100 + 16 = **116**. Bob solves second after 18 s
→ 80 + 12 = **92**. Had Ann taken one hint she'd score (100 + 16) × 75 % = **87**.

- **Wrong guesses** (a different arrangement of the tiles) shake the input with a "Not quite" note
  and stay listed — for the guesser only. Repeating the same wrong guess is refused.
- **Refused without counting:** an empty guess, the wrong number of letters, or letters that aren't
  on the tiles (each with a short explanation).
- **Anti brute force:** at most 40 wrong guesses per round, and never more than a quarter of all
  possible letter orders (4 letters: 6, 5 letters: 30). A player who is out of guesses is done.
- **Hints** are personal and opt-in: the first reveals the first letter, the second one more letter,
  each in its place (`P _ _ _ E _`). Nobody else sees your hint letters.
- **The round ends** when every connected player has solved it (or used up their guesses) or the
  timer runs out. The reveal flips the tiles into the word and lists the solve order with times.

## Settings

| Key             | Label       | Values                                              | Default |
| --------------- | ----------- | --------------------------------------------------- | ------- |
| `rounds`        | Rounds      | 3, 5, 7, 10                                         | 5       |
| `roundDuration` | Round timer | 30, 45, 60, 90 s                                    | 45 s    |
| `wordLength`    | Word length | `4` … `8` letters, `mixed` (5–7, drawn every round) | `6`     |
| `hints`         | Hints       | on / off                                            | on      |

## Awards

| Award            | For                                                        |
| ---------------- | ---------------------------------------------------------- |
| ⚡ Speed reader  | the fastest single solve of the game                       |
| 📚 Wordsmith     | the most first places                                      |
| 💪 Never give up | the most wrong guesses before cracking a word (at least 3) |

## Design notes

| Item       | How it is solved                                                                                                                                                                                                                                                                                                                                                                             |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **BUG-20** | Unlimited guesses (up to the brute-force cap) with immediate feedback; a correct guess only locks _that_ player in. `isTurnComplete` waits for every `ctx.activePlayers()` player to be solved or out of guesses; scoring is by solve order.                                                                                                                                                 |
| **BUG-21** | No network at all: curated lists in `backend/src/main/resources/word-scramble/words/<length>.txt` (137–182 common, family-friendly words per length), read once through a lazy holder (`WordBank.Holder`) and validated at load: A–Z only, right length, no duplicates, no two words that are anagrams of each other, at least 60 words. A broken list fails fast with every problem listed. |
| **BUG-30** | `GuessText.normalize` runs once when a guess arrives: trim, upper case, accents dropped, letters only — `" plan-et "` is `PLANET`. Only the normalised form is stored and compared.                                                                                                                                                                                                          |
| **BUG-08** | `view()` sends `word` only when `revealed`; `startTurn` resets `revealed` before picking the next word. Hint letters and wrong guesses live in `me` (the viewer's own status); `players` carries only solved/rank/time (hints and miss counts after the reveal). A scenario test checks every view (players and spectator) of every round for the answer.                                    |
| **BUG-05** | Own default timer of 45 s (choices 30–90 s), declared with `CommonSettings.roundDuration`.                                                                                                                                                                                                                                                                                                   |
| **IMP-24** | All of the above plus per-player hints (first letter, then one more), points by solve order with a speed bonus, curated offline lists per length.                                                                                                                                                                                                                                            |

More decisions:

- **Hints: per player, opt-in** (rather than automatic shared hints): taking one is a choice with a
  visible price (−25 % per hint), so a stuck player can still score without making the round easier
  for everyone.
- **One answer per scramble.** Every listed word was checked against a standard English dictionary
  to have no anagram twin (no `LEMON`/`MELON`, `HEART`/`EARTH`), so a valid word typed from the
  tiles is never "wrong"; the loader rejects twins within the lists.
- **Scrambles are real scrambles** (`Scrambler`): never the word, a rotation of it or the word
  backwards, at most a third of the letters in place, and never showing a blocked letter sequence
  (`word-scramble/blocked.txt`, ROT13-encoded) — an innocent word can't turn into something rude on
  screen. A test scrambles every curated word.
- **Resources are namespaced** by the game id (`/word-scramble/…`) so they can't clash with other
  games' files on the shared classpath.
- **UI:** tiles drop in at the start of a round, can be shuffled locally (button or <kbd>Space</kbd>,
  animated with FLIP) and clicked to type; typed letters dim their tiles. A wrong guess shakes the
  input, empties it and joins the list of your misses. Typing anywhere on the page goes to the
  input. When you solve, your tiles flip into the word right away (from your own guess, never from
  the server); at the reveal everybody's tiles flip. Tiles scale with the stage (container query
  units), so the game fits 1366×768 as well as 1920×1080 without scrolling.

## Files

| Path                                        | What                                                        |
| ------------------------------------------- | ----------------------------------------------------------- |
| `backend/…/WordScrambleGame.java`           | metadata, settings, `createLogic`                           |
| `backend/…/WordScrambleLogic.java`          | the rules: rounds, guesses, hints, turn end, views, awards  |
| `backend/…/WordScrambleView.java`           | what browsers receive                                       |
| `backend/…/WordBank.java`                   | loads and validates the word lists, draws unused words      |
| `backend/…/Scrambler.java`                  | scramble rules and blocked sequences                        |
| `backend/…/GuessText.java`                  | guess normalisation                                         |
| `backend/…/Scoring.java`                    | points formula                                              |
| `backend/…/PlayerRound.java`                | one player's progress in a round                            |
| `backend/…/Highlights.java`                 | solves of the game → awards                                 |
| `backend/…/WordLength.java`                 | the word length setting (fixed or mixed)                    |
| `backend/…/ResourceLines.java`              | reads the bundled text files                                |
| `backend/src/main/resources/word-scramble/` | word lists per length, blocked sequences                    |
| `backend/src/test/…`                        | scenario tests (`GameHarness`) and unit tests               |
| `frontend/`                                 | the game screen (`index.ts` exports it)                     |
| `e2e/word-scramble.spec.ts`                 | Playwright: full game, wrong guess + retry, hints/spectator |

```bash
cd backend && ./gradlew :games:word-scramble:test -PffGames=word-scramble
FF_GAMES=word-scramble npm run build -w frontend
cd backend && ./gradlew :app:bootJar -PffGames=word-scramble
cd e2e && npx playwright test ../games/word-scramble
```

See [docs/adding-a-game.md](../../docs/adding-a-game.md).
