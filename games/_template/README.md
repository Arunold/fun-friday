# __GAME_ICON__ __GAME_NAME__

> Created from `games/_template` with `npm run new-game`. Replace this text with your game.

Everyone secretly picks a number; then the lucky number is drawn.

## Rules & scoring

| Situation | Points |
|---|---|
| Exact hit | 20 |
| Closest pick (ties share) | 10 |

A turn ends when everyone picked or when the timer runs out. Picks stay secret until the reveal.

## Settings

| Key | Label | Values |
|---|---|---|
| `rounds` | Rounds | 1, 3, 5 |
| `roundDuration` | Round timer | 10, 15, 30 s |
| `highest` | Highest number | 10, 20, 50 |

## Files

| Path | What |
|---|---|
| `backend/…/__CLASS__Game.java` | metadata, settings, `createLogic` |
| `backend/…/__CLASS__Logic.java` | the rules |
| `backend/…/__CLASS__View.java` | what browsers receive |
| `backend/src/test/…` | scenario tests (`GameHarness`) |
| `frontend/` | the game screen (`index.ts` exports it) |
| `e2e/` | Playwright spec |

See [docs/adding-a-game.md](../../docs/adding-a-game.md).
