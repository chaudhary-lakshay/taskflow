# Testing

## Strategy (R11)
- Backend: JUnit 5 + MockK for services; Spring context + Testcontainers PostgreSQL 17 for integration tests (real Flyway migrations).
- Frontend: Jest 30 + React Testing Library + jsdom, Babel transform; ESLint and `tsc -b` as separate gates.
- Owner-only scenarios in docs/requirements.md are the minimum security tests; each needs a two-user integration test.
- Tests accompany each slice, not only at the end.

## Commands
```bash
scripts/verify.sh                          # everything below, from the repo root
cd backend && ./gradlew test               # needs Docker running
cd frontend && npm ci && npm run lint && npm run typecheck && npm test -- --runInBand && npm run build
```

## T00 baseline (2026-11-24)
| Check | Result |
|---|---|
| MockkBaselineTest | 1 test passed |
| PostgresSmokeIT (Testcontainers postgres:17, Flyway, Spring context) | 1 test passed |
| Frontend Jest + RTL (App.test.tsx) | 1 test passed |
| ESLint, tsc -b, vite build | exit 0 |

Backend XML reports are in `backend/build/test-results/test/` (gitignored). Exact commands, exit codes and commit SHAs are recorded in the Notion plan (page 05).

## T01 persistence tests
| Suite | Tests | What it proves |
|---|---|---|
| SchemaIT | 8 | V1 on real Postgres: defaults, unique + normalised email, title 120/blank, enum CHECKs, FKs and NOT NULL, no delete from under references, planned indexes |
| RepositoryIT | 8 | entity mapping and enum round-trip, email normalisation, updatedAt on update, lazy relations, owner-scoped project and task queries (assignee gets no access) |
| DemoSeedIT | 2 | demo profile seeds 2 users (BCrypt), 3 projects, varied tasks incl. overdue/unassigned/assigned-to-other; second run is a no-op |
| DemoDataSeederTest (MockK) | 5 | missing/blank or policy-violating DEMO_PASSWORD (short, 73 bytes, multibyte over 72) fails fast, writes nothing, never echoes the password; bounds accepted; one BCrypt hash per account |

All integration suites share one Testcontainers PostgreSQL 17 container (`support/PostgresIT`).

## T02 auth and security tests
| Suite | Tests | What it proves |
|---|---|---|
| AuthServiceTest (JUnit 5 + MockK) | 11 | email normalised, hash stored not password; duplicate-email classification from typed data only (PSQLException 23505 + constraint `uq_users_email`, or Hibernate `constraintName`); other constraints, other SQLStates, missing constraint name and message-text-only are NOT mapped; login: exactly one `matches()` per attempt, dummy hash built at construction |
| JwtTokenTest (no Spring, no Docker) | 10 | claims and lifetime; expired, wrong secret, wrong issuer, bad/non-positive subject, missing exp, HS512, garbage all rejected; missing/short/non-base64 secret and bad TTL stop startup |
| AuthRaceIT (MockMvc + real Postgres, 2 threads) | 1 | Concurrent registration of the same new email: a test-only `RaceGate` holds both requests after their `existsByEmail` check so both see "free" and both INSERT; the unique constraint decides: exactly one 201, one 409 `EMAIL_TAKEN`, one row. Proves the typed-violation path through real pgjdbc + Hibernate + Spring translation for 2 callers. Does not prove behaviour for other drivers/versions or >2 callers, and the barrier forces the interleaving rather than finding it |
| PasswordPolicyTest | 2 | the single password rule (8 chars, 72 bytes) used by registration and the demo seeder |
| AuthApiIT (MockMvc, full filter chain, real Postgres) | 13 | register 201 with safe body and BCrypt hash in DB; duplicate email any case 409; field-level 400s; password 8-char and 72-byte bounds (incl. multibyte); malformed JSON; login token and no password; same 401 for wrong password and unknown email; 401 JSON for no/garbage/expired/forged/wrong-issuer/bad-subject tokens; owner id from token not request; only register/login/health public |

Mutation checks (one change each, restored afterwards): removing the issuer validator fails JwtTokenTest, neutering the 72-byte validator fails AuthApiIT, dropping the dummy-hash comparison fails AuthServiceTest. `support/WhoAmIController` is a test-only protected endpoint.

## T03 project and task tests
| Suite | Tests | What it proves |
|---|---|---|
| TaskServiceTest (MockK, fixed Clock 2026-11-24) | 15 | create defaults/trim/foreign project/due date today-future-none-past/unknown assignee/both errors; update D04 (unchanged past kept, changed past rejected, today/future/clear ok), clears assignee, foreign task untouched; status change; overdue flag rule; list of foreign project never queries tasks |
| ProjectServiceTest | 4 | create under caller, owner-scoped list, summary counts with missing statuses and today from the clock, foreign project counts nothing |
| UserLookupServiceTest | 2 | min 2 characters, trimmed, capped at 20, id+name only |
| RepositoryGuardTest | 2 | Project/TaskRepository expose no unscoped finder or delete |
| OwnerScopingIT (real Postgres, real JWTs, two users) | 7 | B gets 404 on A's list/create/update/PATCH/delete/summary even when assigned a task there, nothing changes; foreign and missing give identical bodies; A keeps access; project lists are separate; client ownerId ignored; all new endpoints 401 without token |
| ProjectApiIT | 7 | project create/validation; summary overdue rules (yesterday TODO/IN_PROGRESS yes, DONE no, today no, future no, no date no), empty project, filters do not affect summary, summary overdue equals the overdue flags in the list; user lookup (id+name only, name not email, min 2, max 20, wildcards literal) |
| TaskApiIT | 12 | defaults, title 120/121/blank, dueDate today/future/none/past, unknown assignee, bad enum/date/type, PUT replace/clear, D04 on PUT, validation, updatedAt moves, PATCH transitions, DELETE 204 |
| TaskListIT | 10 | filters alone and combined, no cross-project leakage, priority rank order both ways, due-date order with nulls last both ways, id tie-break, sort with filter, 45 tasks with heavy ties paged for all four sorts: no duplicates and the exact expected order, page metadata/boundaries (exact multiple, beyond last, size 1 and 100, empty), 400 for invalid sortBy/direction/page/size/enums/ids, several errors reported together |

Mutation checks (one change each, restored): `findByIdAndOwnerId` ignoring the owner → OwnerScopingIT fails (3); `findByIdAndProjectOwnerId` ignoring the owner → OwnerScopingIT fails (3); `countOverdue` `<` → `<=` → ProjectApiIT fails (2); `isOverdue` `isBefore` → `!isAfter` → TaskServiceTest fails (1). **Not caught:** removing the owner predicate from `TaskSpecifications.scoped` leaves all tests green, because `TaskService.list` already 404s on a foreign project first; that predicate is defence in depth and is currently untested on its own.

Query plans (EXPLAIN ANALYZE on PostgreSQL 17 with 2,003 projects and 100,007 tasks; one project had 10,000 tasks): every main query used the existing indexes: lists use `idx_tasks_project_status_id` (status filter) or the project index plus a top-N sort (about 5.7 ms worst case for the 10,000-task project), counts and the summary use the same indexes (1 to 2 ms), the overdue count uses `idx_tasks_project_due_date_id`, the owner join uses `idx_projects_owner_id`, task by id uses the primary key. No index was added. The plans are dominated by the per-project row count, so they say little about much larger projects.

## Environment notes
- Docker Desktop must be running for Testcontainers (verified with Docker Engine 29.5.3, Testcontainers 1.21.4).
- Integration tests inject a test-only `taskflow.jwt.secret` (see `PostgresIT`); `bootRun` needs a real `JWT_SECRET` in the environment.
- Tests and bootRun run with `-Duser.timezone=UTC` (Postgres 17 rejects the JVM zone alias `Asia/Calcutta`).
- Frontend needs Node ^24.11.0 (Babel 8 requirement); tested on 24.14.0 (`frontend/.nvmrc`).

## Known dev-tooling advisories
`npm audit` reports 18 moderate findings, all from two advisories in the Jest toolchain: `glob` (AbortSignal cancellation bypass, GHSA-3g3m-2fjr-6pw4) and `minimatch` (globparts DoS, GHSA-g3hv-75wg-m249). They reach us only through Jest/babel-jest dev dependencies, which only process this repo's own files and are not in the production bundle. `npm audit --omit=dev` reports 0. The suggested `audit fix --force` would downgrade Jest to 29, so it was not applied. Re-check when Jest ships a release with patched glob/minimatch.
