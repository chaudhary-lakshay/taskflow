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
## T03 decisions
- `ProjectRepository` and `TaskRepository` are bare `Repository` interfaces with a closed method list (resolves the T02 review note). `TaskRepository` does not extend `JpaSpecificationExecutor` (T03 re-review): the filtered list is `TaskPageQuery.findPage(ownerId, projectId, query)`, a custom fragment that always ANDs the project and owner predicates (Specification pattern kept, used internally). `RepositoryGuardTest` fails if any other method appears; `save`, `saveAll`, `delete`, `flush` act on entities already fetched through a scoped query.
- `GET /api/users` without `query` returns the same field-level 400 (`fieldErrors.query`) as a too-short query.
- Task list: the service checks project ownership first (404), then the spec also scopes by owner. Omitted `sortBy` = dueDate. Undated tasks always last. Ties by id ascending. **[ASSUMPTION]** sort default.
- PUT requires title, status and priority; description, dueDate, assigneeId may be null to clear. **[ASSUMPTION]**
- Description limit 2000 characters for projects and tasks. **[ASSUMPTION]**
- Overdue is defined once in `TaskRules.isOverdue` (DTO flag) and mirrored in the `countOverdue` query; a test asserts the summary count equals the number of overdue flags in the list.
- Assignment accepts any existing user (D02); there is no project membership, so assigning someone grants nothing.
- Update returns the stored `updatedAt` by flushing before mapping.
- Demo data only under profile `demo`; the shared demo password comes from `DEMO_PASSWORD` with no default, and startup fails if it is missing. BCrypt via `spring-security-crypto` only (Spring Security itself arrives in T02).

## T04 frontend decisions
- State: Context for the session, TanStack Query for server state, local state for forms and modals (see README).
- Token in `sessionStorage` (D09), cleared on logout and any 401; stored with an expiry time, ignored after that.
- After registration the app logs in with the same credentials; if that login fails it goes to `/login` with a notice and never registers again. **[ASSUMPTION]** (page 03 says the same).
- Login does not enforce the 8-character minimum on the client (old passwords must still work); registration does, plus the 72-byte limit.
- The HTTP layer is configured while the provider renders, not in an effect: child effects run before a parent's, so an effect would let the first request after a reload go out without the token (found by the 401 test).
- Dependencies pinned exactly: react-router 8.5.3 (peer react >=19.2.7, node >=22.22: installed react 19.3.1 and Node 24.14.0), @tanstack/react-query 5.108.4 (peer react ^18 || ^19). `@testing-library/react` 16.3.3 needs `@testing-library/dom` ^10, which was already installed (10.4.2). The plan listed react-router 8.5.2; 8.5.3 was current on 24 November.
- A 401 ends the session only if the token the request was sent with is still the current session token at response time. A late 401 for an old token (logout, then login as someone else while the request was in flight) just rejects that request (T04 re-review).
