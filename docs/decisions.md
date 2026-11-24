# Decisions

| ID | Decision | Note |
|---|---|---|
| D01 | Modular monolith, feature packages | Layers without distributed overhead |
| D02 | Assignee is a registered user; assignment grants no access | **[ASSUMPTION]** extra `GET /api/users?query=` endpoint |
| D03 | Date-only UTC due date, optional | **[ASSUMPTION]** optionality |
| D04 | Past due date rejected on create; unchanged overdue date kept on edit | |
| D05 | Defaults TODO / MEDIUM / no assignee; all transitions allowed | |
| D06 | 404 for foreign resources, 401 for bad token | Does not leak existence |
| D07 | Per-column pagination; summary independent of filters | |
| D08 | Priority rank HIGH > MEDIUM > LOW; nulls last; id tie breaker | |
| D09 | Token in sessionStorage | **[ASSUMPTION]** XSS exposure documented |
| D10 | Spring Boot 3.5.16 (free support ended 2026-06-30), Java 21 toolchain, Gradle 8.14.8, Kotlin 1.9.25, PostgreSQL 17, React 19 | Assignment requires Boot 3.x |
| D11 | Public repo only after secret/history check | |
| D12 | No teams, project edit/delete, notifications, real-time, drag-drop | |

## T00 implementation decisions
- Kotlin 1.9.25 + MockK 1.13.17 verified together; no Kotlin 2.x override.
- Testcontainers 1.21.4 works with Docker Engine 29.5.3.
- ESLint 9 + typescript-eslint with TypeScript 5.9 (create-vite ships oxlint and TS 7; typescript-eslint supports TS below 6.1).
- Postgres is published on 127.0.0.1 only.

## T01 persistence decisions
- Flyway V1 owns the schema; Hibernate only validates (`ddl-auto: validate`).
- Emails are stored trimmed and lower case (`User.normalizeEmail`); a CHECK constraint refuses anything else, so the plain UNIQUE index is effectively case-insensitive.
- Enums are text columns with CHECK constraints and `@Enumerated(STRING)`; defaults TODO / MEDIUM in both DB and entity.
- Foreign keys have no cascade: a user referenced by a project or task, or a project with tasks, cannot be deleted. No delete feature exists for either (D12).
- Timestamps are `Instant` truncated to microseconds, matching `timestamptz`, so an entity equals what was stored. `updatedAt` moves on `@PreUpdate`.
- Owner scope lives in repository method signatures (`findByIdAndOwnerId`, `findByIdAndProjectOwnerId`): a foreign row and a missing row both return null (D06 → 404).
- **[ASSUMPTION]** Column limits not given by the assignment: user name 100, email 254, project name 120, password hash 100. Only task title 120 is an assignment rule.
- Demo data only under profile `demo`; the shared demo password comes from `DEMO_PASSWORD` with no default, and startup fails if it is missing. BCrypt via `spring-security-crypto` only (Spring Security itself arrives in T02).
