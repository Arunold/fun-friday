# 0004 — Live rooms in memory, finished games in H2

- **Status:** Accepted (v2, October 2026)
- **Related:** IMP-48, IMP-14, IMP-30

## Context

`implementation-plan.md` plans H2 + Flyway. Version 1 kept everything in a map and lost it on
restart; there was no history or cross-game ranking.

Live room state changes many times per second (keystrokes, strokes, timers) and is only useful
while the room exists. Finished games are small, append-only facts that are worth keeping.

## Decision

- **Live state stays in memory**, one object graph per room guarded by the room lock. Rooms are
  evicted when empty, idle (10 min without connected members), inactive (2 h) or old (12 h).
- **Finished games are persisted** in an embedded **H2 file database** (`FF_DATA_DIR`) through
  **Spring Data JDBC** (aggregates `GameRecord` → `GameRecordPlayer`), with the schema managed by
  **Flyway** (`db/migration/V1__game_history.sql`). Writes happen asynchronously on a virtual
  thread, outside the room lock.
- Queries: room history, recent games, Friday Cup leaderboard by period (`JdbcClient`).
- Tests and e2e use in-memory H2.

## Consequences

- A restart ends running games (players see "room closed"/"seat expired" and can start again) but
  keeps the hall of fame.
- No external database to operate; the Docker image declares `/app/data` as a volume. Free hosting
  without a disk starts with an empty history after each deploy.
- Switching to PostgreSQL later means a datasource change and possibly SQL dialect tweaks; the
  Spring Data JDBC mapping uses explicit upper-case table/column names to stay portable.
