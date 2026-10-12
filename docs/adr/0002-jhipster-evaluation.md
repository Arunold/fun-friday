# 0002 — Keep the hand-built stack; don't adopt JHipster

- **Status:** Accepted (v2, October 2026)
- **Related:** IMP-49 (time-boxed JHipster decision spike), IMP-01–IMP-04, IMP-48

## Context

`implementation-plan.md` mentions JHipster. The report asked for a time-boxed spike before the
platform work, because JHipster brings its own conventions that overlap with the planned game
platform (IMP-01–IMP-04) and persistence (IMP-48).

What JHipster (8.x) would give us: a generated Spring Boot + Angular monolith, JDL entity
generation with CRUD screens, Liquibase, Spring Security with JWT/OAuth2 and user management,
Micrometer/Prometheus, Cypress tests, Docker/Kubernetes descriptors.

What Fun Friday needs: no accounts (a name and an emoji), no CRUD screens, two small history
tables, a custom real-time protocol (STOMP request/reply, personal snapshots, room streams), a game
SDK with per-viewer views, and a frontend that hosts lazily loaded game components.

## Options

| Option                                        | For                                                                                                                                                                | Against                                                                                                                                                                                                                                                                                    |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Generate a JHipster app and port the games    | Batteries included (security, metrics, CI, Docker)                                                                                                                 | ~90% of the generated code is unused (users, auth, entities, admin); its WebSocket sample is a tracker, not a game protocol; Liquibase + JPA replace two tables we can create with one Flyway script; upgrades go through the generator; the game platform still has to be written by hand |
| JHipster only for tooling ideas               | Proven conventions                                                                                                                                                 | Nothing to install                                                                                                                                                                                                                                                                         |
| **Hand-built Spring Boot + Angular (chosen)** | Small, readable code focused on the game platform; current versions (Spring Boot 4, Angular 21 zoneless) without waiting for a generator release; one-folder games | We write CI, Docker and config ourselves (a few small files)                                                                                                                                                                                                                               |

## Decision

Keep the hand-built stack and borrow the useful conventions: Spring profiles (`dev`, `test`,
`e2e`, prod defaults), Actuator health/metrics, Flyway for schema changes, Docker multi-stage build,
CI with lint + tests + e2e, ESLint/Prettier/Spotless.

## Consequences

- The codebase stays small and every file is about Fun Friday.
- No authentication layer to maintain; identity is connection-bound (ADR 0001). If accounts are
  ever needed, Spring Security can be added to the existing app.
- Upgrades are ordinary dependency bumps.
