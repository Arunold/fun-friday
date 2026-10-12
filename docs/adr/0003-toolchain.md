# 0003 — Toolchain: Java 25, Spring Boot 4, Gradle 9, Angular 21

- **Status:** Accepted (v2, October 2026)
- **Related:** IMP-40, IMP-22, IMP-36, IMP-41, BUG-35

## Context

Version 1 used Spring Boot 3.2 (out of open-source support), a Gradle 8.10 wrapper that cannot run
on the installed JDKs, Angular 20 with zone.js and a mismatched angular-eslint 21, Windows-only npm
scripts and mixed line endings.

## Decision

- **Backend:** Java 25 (LTS) via Gradle toolchains (foojay resolver provisions it), **Gradle 9.8**
  wrapper, **Spring Boot 4.1** (Spring Framework 7, Jackson 3, JUnit 6), multi-project build
  (`sdk`, `runtime`, `testkit`, `app`, `games:*`), Spotless (google-java-format AOSP) and
  Checkstyle with zero warnings allowed.
- **Frontend:** **Angular 21** — standalone components, signals, **zoneless** change detection,
  new control flow, `animate.enter`/`leave`, Material 3 theming; TypeScript 5.9; angular-eslint 21
  with ESLint 10 flat config; Prettier. Angular 22 needs Node ≥ 22.22 and TypeScript 6; we move once
  those are standard in the team.
- **Repository:** npm workspaces (`frontend`, `e2e`) at the root; cross-platform scripts in Node
  (`scripts/gradle.mjs` calls `gradlew` or `gradlew.bat`); LF line endings enforced by
  `.gitattributes`; husky + lint-staged pre-commit hook in POSIX sh.
- **Runtime:** Node 24 for builds in CI/Docker (≥ 20.19 locally), Temurin 25 JRE in the image.

## Consequences

- Virtual threads, records and pattern matching are available everywhere.
- Jackson 3 (`tools.jackson.*`) is stricter by default; `fail-on-null-for-primitives` is disabled so
  optional flags may be omitted by clients.
- google-java-format needs `--add-exports` flags on JDK 25 (in `backend/gradle.properties`).
- The configuration cache stays off because games are discovered dynamically in
  `settings.gradle`.
