# Architecture decision records

Short records of the decisions that shape Fun Friday (context → decision → consequences).

| #                                   | Decision                                                                        | Status   |
| ----------------------------------- | ------------------------------------------------------------------------------- | -------- |
| [0001](0001-game-platform.md)       | Games are plug-ins on a server-authoritative platform with per-viewer snapshots | Accepted |
| [0002](0002-jhipster-evaluation.md) | Keep the hand-built Spring Boot + Angular stack; don't adopt JHipster           | Accepted |
| [0003](0003-toolchain.md)           | Java 25, Spring Boot 4, Gradle 9, Angular 21 (zoneless), npm workspaces         | Accepted |
| [0004](0004-persistence.md)         | Live rooms in memory; finished games in H2 with Flyway and Spring Data JDBC     | Accepted |
| [0005](0005-functional-testing.md)  | Functional tests only: scenario tests, STOMP integration tests, Playwright      | Accepted |

New decision? Copy the structure of an existing record and add it to this table.
