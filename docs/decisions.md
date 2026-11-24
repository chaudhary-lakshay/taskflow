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
## T02 auth decisions
- Password policy: 8 characters minimum, 72 UTF-8 bytes maximum (BCrypt reads only 72 bytes; we reject instead of truncating). No composition rules. **[ASSUMPTION]** the assignment gives no password policy.
- Login does not reveal whether an email exists: same 401 body, and a dummy BCrypt comparison runs for unknown emails.
- JWT: HS256 symmetric secret from the environment; TTL 3600 s (plan says 30–60 min, so at the top of that range); 30 s clock skew; no refresh token, no server-side logout (D09 trade-off).
- Spring Security's library validators accept a missing `exp`, so a small validator additionally requires `exp` and a positive numeric `sub`.
- Register returns the user (201) and does not log in; the frontend logs in afterwards (page 03). **[ASSUMPTION]**
- `GET /actuator/health` is the only public non-auth endpoint; it exposes status only.
- No inherited unscoped repository methods are used for user-owned resources: T03 services must call the owner-scoped finders from T01 (`findByIdAndOwnerId`, `findByIdAndProjectOwnerId`). Note `JpaRepository.findById/findAll/deleteById` on Project and Task remain callable and must not be used by services.
- A duplicate email is recognised by SQLState 23505 plus the constraint name `uq_users_email` (PostgreSQL server error or Hibernate `constraintName`), never by message text; any other integrity violation is rethrown (500).
- The password rule lives in one place (`PasswordPolicy`, used by `@ValidPassword` on registration and by the demo seeder). Login only bounds the length (72 bytes) and does not enforce the minimum.
- The dummy hash for unknown-email logins is built when `AuthService` is constructed; every login performs exactly one `matches()`. Timing equality is not claimed: one local sample showed medians of 67.8 ms (wrong password) and 63.8 ms (unknown email), not a proof.
- Demo data only under profile `demo`; the shared demo password comes from `DEMO_PASSWORD` with no default, and startup fails if it is missing. BCrypt via `spring-security-crypto` only (Spring Security itself arrives in T02).
