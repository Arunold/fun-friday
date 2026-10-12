# 0005 — Functional tests only

- **Status:** Accepted (v2, October 2026)
- **Related:** IMP-38, IMP-39, `implementation-plan.md` ("only functional tests, unit tests not
  needed for now")

## Context

Version 1 had no tests; most reported bugs would have been caught by playing a game end to end.
The plan asks for functional tests rather than unit tests.

## Decision

Three layers, all exercising real behaviour through public interfaces:

1. **Game scenario tests** (`games/<id>/backend/src/test`): `GameHarness` plays a game through the
   real `GameRun` with virtual time and a seeded random generator — players act, time advances,
   assertions on views, scores, phases and awards.
2. **Server integration tests** (`backend/app/src/test`): the Spring Boot app on a random port with
   real STOMP clients — create/join/resume, presence grace and removal, host migration, input
   validation, spectators, the OpenAPI contract (which also exports `docs/openapi.json`).
3. **End-to-end tests** (`e2e/tests`, `games/<id>/e2e`): Playwright drives several isolated browser
   contexts against the packaged jar in the `e2e` profile (short countdown/reveal, a test-only
   endpoint exposing each game's `debugState()` so tests can play deterministically).

CI runs all three on every pull request.

## Consequences

- Tests describe behaviour players care about and survive refactors of internals.
- Some edge cases are cheaper to cover in scenario tests than in e2e; each game's checklist asks for
  both.
- The e2e endpoint is only registered when `app.e2e.enabled=true` (never in production).
