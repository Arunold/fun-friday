# ❓ Trivia Blitz

Quick-fire multiple choice for the whole team: everyone gets the same question with four answers,
the first pick is final, and the faster you're right, the more you score. Right answers in a row
build a 🔥 streak bonus.

## Rules & scoring

Every round is one question from a curated bank. Each player locks in one answer (click, or press
<kbd>1</kbd>–<kbd>4</kbd> / <kbd>A</kbd>–<kbd>D</kbd>). The turn ends as soon as every connected
player has answered, or when the timer runs out. Until the reveal nobody sees the right answer or
what anyone else picked — only _who_ has answered.

| Situation                 | Points                                                                                                      |
| ------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Right answer              | **1000** for an instant answer, sliding linearly to **500** at the deadline (rounded to 10)                 |
| Streak bonus              | **+100** for every right answer in a row beyond the first: 2nd +100, 3rd +200, 4th and later **+300** (max) |
| Wrong answer or no answer | **0** — and the streak starts over                                                                          |

Examples with a 15 s timer: right after 1.5 s → 950; right after 7.5 s on a 3-answer streak →
750 + 200 = 950; right at the buzzer → 500.

**Reveal:** the right answer glows green, wrong picks turn red, the avatars of everyone who picked an
option appear on it (fastest first, 🔥 for streaks of 2+), plus your result, the fastest correct
player with their time and a one-line fun fact. The turn's points appear in the platform scoreboard.

**Awards:** 🧠 _Brainiac_ — most right answers (ties: faster average) · ⚡ _Quick draw_ — fastest
average right answer · 🔥 _On fire_ — longest streak (at least 2).

## Settings

| Key             | Label                | Values                                                                                                                              | Default |
| --------------- | -------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ------- |
| `rounds`        | Rounds (= questions) | 5, 10, 15, 20                                                                                                                       | 10      |
| `roundDuration` | Round timer          | 10, 15, 20, 30 s                                                                                                                    | 15 s    |
| `category`      | Category             | Mixed, General knowledge, Science, Geography, History, Technology, Food & drink, Sports, Movies & music, Nature & animals, Language | Mixed   |
| `difficulty`    | Difficulty           | Mixed, Easy, Medium, Hard                                                                                                           | Mixed   |

`validate()` rejects filter combinations with fewer questions than rounds, with a concrete hint, e.g.
_"Only 7 questions match History · Easy — play 5 rounds or pick a broader category or difficulty."_
The bank is sized so that any single category supports 20 rounds and any category + difficulty pair
supports 5 rounds.

**Question order:** the whole game is planned up front from the filtered bank, so no question repeats
within a game. Categories take turns (in random order), so a mixed game visits every category before
repeating one. With _Mixed_ difficulty the game gets an even share of easy, medium and hard
questions and asks them in that order — it starts gentle and ramps up. Answer options are shuffled
for every question (the same order for everyone).

## Question bank

`backend/src/main/resources/com/example/funfridaygame/games/triviablitz/questions.txt` — 200
hand-written questions: 10 categories × 20 (7 easy, 7 medium, 6 hard). One question per line:

```text
category | difficulty | question | correct answer | wrong | wrong | wrong | fun fact (optional)
science  | easy       | What is the chemical symbol for gold? | Au | Ag | Fe | Gd | It comes from aurum, the Latin word for gold.
```

The correct answer always comes first in the file; the game shuffles the options. The bank is
validated when it loads (at startup, and in `QuestionBankTest`): known category and difficulty,
exactly four distinct non-blank answers, a valid answer index, no duplicate questions, and lengths
that fit the UI (question ≤ 160, answer ≤ 48, fact ≤ 160 characters). Errors list every broken line
with its line number. Content rules: facts must be accurate and timeless (no "current" records,
populations or champions), family-friendly and office-appropriate.

## Design notes

- **IMP-50 — one folder:** the whole game (rules, views, question bank, UI, tests, e2e) lives in
  `games/trivia-blitz/`; nothing outside it was changed.
- **BUG-08 — no answer leaks:** `view()` only adds the correct index, every pick, answer times and
  the fun fact once `endTurn()` has run (`revealed`); `startTurn()` resets that state before the
  platform renders the first view of a new round, so the first snapshot of round 2+ carries nothing
  from the previous reveal (`theNextQuestionStartsWithoutTheAnswer`). During a turn other players
  only learn _whether_ someone answered (`answered`, `answeredCount`), never what; spectators get no
  `myAnswer`/`myStreak`. No question id is sent that could be looked up. The correct answer is only
  exposed through `debugState()`, which the platform serves only with the `e2e` profile.
- **BUG-05 — game-specific timing:** the game declares its own `roundDuration` (default 15 s, choices
  10–30 s) instead of a generic 30 s, and turns end the moment every _connected_ player has
  answered (`ctx.activePlayers()`), so disconnected players never hold up a question.
- **Fair timing:** answer time is measured on the server (`ctx.now() - ctx.turnStartedAt()`) against
  the turn's real length; one answer per player, rejected afterwards with _"Your answer is already
  locked in"_. The payload is a record with Bean Validation (`option` 0–3).
- **Points live in the platform:** the view never duplicates points; the stage scoreboard shows the
  turn's points during the reveal. The headline names the answer (_"The answer was C) Au"_,
  _"Everyone got it — …"_, _"Nobody got it — …"_, _"Time's up — …"_).
- **Players coming and going:** departed players vanish from pickers, the fastest answer and awards;
  a reconnecting player can still answer the running question.
- **UI:** question card with category/difficulty pills and your streak, a 2×2 grid of big answer
  buttons with A–D badges, keyboard shortcuts (ignored while typing in a field or when a dialog is
  open), instant optimistic lock-in, avatars of who has answered, staggered entrance animations,
  a glowing right answer and a shake on your own wrong pick. Sizes use container-query units so
  the game fits the stage without scrolling from 1366×768 to 1920×1080; tokens only, player colours
  for avatars. Spectators see the same screen without input.

## Files

| Path                                                               | What                                                                        |
| ------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| `backend/…/TriviaBlitzGame.java`                                   | metadata, settings, `validate`, `createLogic`                               |
| `backend/…/TriviaBlitzLogic.java`                                  | the rules: turns, answers, scoring, views, awards, debug state              |
| `backend/…/TriviaBlitzView.java`                                   | what browsers receive (records)                                             |
| `backend/…/Question.java`, `Category.java`, `Difficulty.java`      | the question model and its rules                                            |
| `backend/…/QuestionBank.java`                                      | loads and validates `questions.txt`                                         |
| `backend/…/QuestionFilter.java`                                    | category/difficulty settings → filter                                       |
| `backend/…/QuizPlanner.java`                                       | picks and orders a game's questions                                         |
| `backend/…/Scoring.java`, `PlayerRecord.java`, `TriviaAwards.java` | points, per-player stats, podium awards                                     |
| `backend/src/main/resources/…/questions.txt`                       | the question bank                                                           |
| `backend/src/test/…`                                               | scenario tests (`GameHarness`) and unit tests for bank, planner and scoring |
| `frontend/`                                                        | the game screen (`index.ts` exports it)                                     |
| `e2e/trivia-blitz.spec.ts`                                         | Playwright: a full game to the podium, keyboard answering, spectator view   |

## Testing

```bash
cd backend && ./gradlew :games:trivia-blitz:test -PffGames=trivia-blitz
FF_GAMES=trivia-blitz npm run build -w frontend && (cd backend && ./gradlew :app:bootJar -PffGames=trivia-blitz)
cd e2e && npx playwright test ../games/trivia-blitz
```

See [docs/adding-a-game.md](../../docs/adding-a-game.md).
