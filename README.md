# 🎉 Fun Friday

Real-time multiplayer party games for teams. Create a room, share the five-letter code, play together
in the browser — no accounts, no installs. The host plays too, late arrivals can watch, and every
game ends on a podium with awards and a running **Friday Cup** for the room.

| Game                                                      |                                                     | Players |
| --------------------------------------------------------- | --------------------------------------------------- | ------- |
| 🔢 [Number Guess](games/number-guess/README.md)           | Find the secret number with higher/lower hints      | 2–16    |
| 🔤 [Word Scramble](games/word-scramble/README.md)         | Unscramble the word before everyone else            | 2–20    |
| 🎨 [Pictionary](games/pictionary/README.md)               | One draws, everyone guesses                         | 3–10    |
| ⌨️ [Speed Typing](games/speed-typing/README.md)           | Type the same text fastest and cleanest             | 2–20    |
| ⚡ [Reaction Showdown](games/reaction-showdown/README.md) | Wait for GO… and don't fall for the fake-outs       | 2–20    |
| 🧩 [Sliding Puzzle](games/sliding-puzzle/README.md)       | Same scramble for all, fastest solve wins           | 2–20    |
| ❓ [Trivia Blitz](games/trivia-blitz/README.md)           | Quick-fire multiple choice, speed and streaks count | 2–30    |
| 🃏 [Memory Match](games/memory-match/README.md)           | One shared board, a match means you go again        | 2–10    |
| 🐍 [Snake Arena](games/snake-arena/README.md)             | Real-time Snake in one arena with your coworkers    | 2–12    |
| 🚌 [Backseat Drivers](games/backseat-drivers/README.md)   | One bus, one map reader, everyone else one control  | 3–10    |

Each game lives in its own folder under [`games/`](games/) — see [Adding a game](docs/adding-a-game.md).

## Quick start

**With Docker** (one command, everything included):

```bash
docker compose up --build
# open http://localhost:8080 — colleagues on your network use http://<your-ip>:8080
```

**For development** (hot reload; JDK 25 and Node ≥ 20.19 required):

```bash
npm install
npm run dev:backend    # Spring Boot on :8080 (profile "dev", API docs at /swagger-ui.html)
npm run dev:frontend   # Angular on http://localhost:4200, proxies /api and /ws to :8080
```

On Windows the same npm scripts work (they call `gradlew.bat` through `scripts/gradle.mjs`).

## Project layout

```text
backend/              Gradle multi-project (Java 25, Spring Boot 4)
  sdk/                game SDK: GameDefinition, GameLogic, GameContext, settings, views
  runtime/            runs one game: rounds, turns, timers, scoring (no Spring, no I/O)
  testkit/            GameHarness for scenario tests with virtual time
  app/                the server: rooms, STOMP/REST API, presence, history (H2 + Flyway)
frontend/             Angular 21 app (zoneless, signals, Material 3)
  src/app/core/       realtime connection, room store, clock sync, REST
  src/app/sdk/        frontend game SDK (@ff/sdk)
  src/app/pages/      home, host, join, room (lobby → game → podium), hall of fame
games/<id>/           one folder per game: backend/, frontend/, e2e/, README.md
games/_template/      starting point for `npm run new-game`
e2e/                  Playwright: platform specs + helpers (game specs live in games/*/e2e)
docs/                 architecture, ADRs, adding a game, OpenAPI + AsyncAPI contracts
scripts/              cross-platform helpers (Gradle launcher, game registry, scaffolding)
```

## How it works (short version)

- The browser keeps **one WebSocket** (STOMP over SockJS). Every command is a request with a
  `requestId` and gets a reply; the server pushes a **personal snapshot** of the room after every
  change, so each player only ever receives what they may see (no answers leak).
- Identity is bound to the connection on the server; a secret token in `sessionStorage` lets a
  refreshed or reconnected tab **resume its seat**. Players who vanish are removed after a grace
  period, and the host role moves on automatically.
- The **runtime** drives every game the same way (rounds → turns → reveal → podium) and contains
  game errors; games only implement their rules (`GameLogic`) and their screen.
- Finished games are stored in an embedded **H2** database (Flyway migrations) for the hall of
  fame and leaderboard.

Details: [docs/architecture.md](docs/architecture.md) · decisions: [docs/adr/](docs/adr/) ·
contracts: [OpenAPI](docs/openapi.json) (REST + message schemas), [AsyncAPI](docs/asyncapi.yaml)
(STOMP channels).

## Testing

```bash
npm run test:backend   # scenario tests per game + STOMP integration tests (Gradle)
npm run build          # frontend, then the jar with the frontend bundled
npm run e2e            # Playwright: every platform and game spec against the packaged app
npm run lint           # ESLint + Spotless + Checkstyle
npm run format         # Prettier + Spotless (google-java-format, AOSP style)
```

Only functional tests: game rules are tested through the real engine (`GameHarness`), the server
through real STOMP clients, and the whole product with Playwright driving several browsers.

## Configuration

| Variable               | Default                 | Meaning                                                                          |
| ---------------------- | ----------------------- | -------------------------------------------------------------------------------- |
| `PORT`                 | `8080`                  | HTTP port                                                                        |
| `FF_DATA_DIR`          | `./data`                | where the H2 database (game history) is stored                                   |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | extra origins allowed to call `/api` (comma-separated); same-origin always works |
| `WS_ALLOWED_ORIGINS`   | `http://localhost:4200` | extra origins allowed to open the WebSocket                                      |
| `API_DOCS_ENABLED`     | `false`                 | expose `/v3/api-docs` and Swagger UI in production                               |
| `JAVA_TOOL_OPTIONS`    | –                       | JVM flags, e.g. `-XX:MaxRAMPercentage=75`                                        |

Timings (countdown, reveal, reconnect grace), room limits and rate limits are in
[`application.yml`](backend/app/src/main/resources/application.yml) under `app.*`.

## Deployment

- **Docker**: the [Dockerfile](Dockerfile) builds the frontend (Node 24), the jar (JDK 25) and a
  small non-root JRE image with a health check. Mount `/app/data` to keep the history.
- **Render**: [`render.yaml`](render.yaml) is the Blueprint of the live service `fun-friday-game`
  (Docker, free plan, Oregon). Render deploys a commit once its CI checks pass and switches to it
  once `/actuator/health` answers (within 15 minutes). Keep `name` and `region` equal to the
  existing service; if the service isn't linked to the Blueprint, set the same health check path
  in its dashboard settings.
- **CI**: [GitHub Actions](.github/workflows/ci.yml) — backend build and tests, frontend lint and
  build, a game freshly scaffolded with `npm run new-game` through every check, Playwright e2e,
  Docker build plus a smoke test (the image must answer `render.yaml`'s health check path with
  512 MB on Render's `$PORT`).

## Contributing

- Formatting is enforced: Prettier (TS/HTML/SCSS/JSON/Markdown), Spotless with google-java-format
  AOSP (Java), ESLint and Checkstyle. A pre-commit hook (husky + lint-staged) formats staged files.
- Line endings are LF everywhere (see `.gitattributes`).
- New games: `npm run new-game -- my-game "My Game" "🎯"`, then follow
  [docs/adding-a-game.md](docs/adding-a-game.md).
