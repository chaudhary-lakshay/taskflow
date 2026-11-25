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
| RepositoryGuardTest | 3 | exact reviewed method signatures (name, parameter types, return type) of ProjectRepository and TaskRepository (any extra method or overload, including inherited Spring Data or Specification ones, fails); `findPage` takes owner id first |
| TaskRepositoryScopeIT (real Postgres) | 4 | with a foreign owner id, for an existing project: `findPage` returns 0 rows and totalElements 0 under every filter/sort/page combination (even for a user assigned a task), `findByIdAndProjectOwnerId` null, list/count/overdue queries empty; owner and missing/foreign project ids behave the same |
| OwnerScopingIT (real Postgres, real JWTs, two users) | 7 | B gets 404 on A's list/create/update/PATCH/delete/summary even when assigned a task there, nothing changes; foreign and missing give identical bodies; A keeps access; project lists are separate; client ownerId ignored; all new endpoints 401 without token |
| ProjectApiIT | 8 | project create/validation; summary overdue rules (yesterday TODO/IN_PROGRESS yes, DONE no, today no, future no, no date no), empty project, filters do not affect summary, summary overdue equals the overdue flags in the list; user lookup (id+name only, name not email, min 2, max 20, wildcards literal) |
| TaskApiIT | 12 | defaults, title 120/121/blank, dueDate today/future/none/past, unknown assignee, bad enum/date/type, PUT replace/clear, D04 on PUT, validation, updatedAt moves, PATCH transitions, DELETE 204 |
| TaskListIT | 12 | filters alone and combined, no cross-project leakage, priority rank order both ways, due-date order with nulls last both ways, id tie-break, sort with filter, 45 tasks with heavy ties paged for all four sorts: no duplicates and the exact expected order, page metadata/boundaries (exact multiple, beyond last, size 1 and 100, empty), 400 for invalid sortBy/direction/page/size/enums/ids, 400 for a page whose offset overflows Int (21,474,837 at size 100; page 21,474,836 is an empty 200), several errors reported together |

Mutation checks (one change each, restored): `findByIdAndOwnerId` ignoring the owner → OwnerScopingIT fails (3); `findByIdAndProjectOwnerId` ignoring the owner → OwnerScopingIT fails (3); `countOverdue` `<` → `<=` → ProjectApiIT fails (2); `isOverdue` `isBefore` → `!isAfter` → TaskServiceTest fails (1). The owner predicate in `TaskSpecifications.scoped` was originally covered by no test (the service 404 hid it). After the T03 re-review it is: removing it fails 3 `TaskRepositoryScopeIT` tests; removing the project predicate fails `TaskRepositoryScopeIT` and 2 `TaskListIT` tests; adding `JpaSpecificationExecutor` back fails `RepositoryGuardTest`. The guard checks the repository interfaces used by services, not the whole codebase: code that injects `EntityManager` directly could still bypass the scope.

Query plans (EXPLAIN ANALYZE on PostgreSQL 17 with 2,003 projects and 100,007 tasks; one project had 10,000 tasks): every main query used the existing indexes: lists use `idx_tasks_project_status_id` (status filter) or the project index plus a top-N sort (about 5.7 ms worst case for the 10,000-task project), counts and the summary use the same indexes (1 to 2 ms), the overdue count uses `idx_tasks_project_due_date_id`, the owner join uses `idx_projects_owner_id`, task by id uses the primary key. No index was added. The plans are dominated by the per-project row count, so they say little about much larger projects.

## T04 frontend tests (Jest 30 + RTL + user-event, isolated QueryClient with retries off)
| Suite | Tests | What it proves |
|---|---|---|
| validation.test.ts | 4 | rules match the backend: name 100, email 254, password 8 characters and 72 bytes (é x37 is rejected), login minimal, project 120/2000 |
| api/http.test.ts | 7 | relative /api path, bearer header, error normalisation with fieldErrors, 401 with token ends the session once, a late 401 for a no-longer-current token only rejects that request, 401 without token does not, 204 / non-JSON error / network failure |
| LoginPage.test.tsx | 4 | validation without API call, trimmed submit, session in sessionStorage (not localStorage), generic invalid-credentials message, server field errors |
| RegisterPage.test.tsx | 5 | validation incl. 72 bytes, register then login with the same credentials, failed auto-login goes to /login and registers once, duplicate email shown on the field |
| routing.test.tsx | 7 | protected redirect without calling the API, / redirect, return to the wanted page after login, signed-in user skips /login, unknown project, logout clears session and query cache, expired stored session ignored |
| unauthorized.test.tsx (real http layer, fake fetch) | 3 | a 401 clears session and cache, redirects to /login once with a notice, and no second request is sent (no loop); a delayed 401 for an old token (request sent as A, logout, login as B, then the 401 arrives) leaves session B, its cache and the page untouched; a 500 shows an error with Retry and keeps the session |
| ProjectsPage.test.tsx | 9 | loading, list, empty, error with working Retry; create modal: focus on first field, required name, 120 limit, success refreshes the list and returns focus, server field errors keep it open, Escape/Cancel/Tab wrap |

Browser smoke (24 Nov, headless Chromium via the installed `playwright-cli`, real backend through the Vite proxy; manual run, not an automated suite): redirect of /projects to /login when logged out; register with empty fields (three field errors), then register, which signs in and lands on /projects with the empty state; create a project through the modal (focus on Name when it opens, Escape closes and returns focus to "New project"); the list survives a reload; the project page shows the name and back link; logout lands on /login and `/projects` redirects back, with sessionStorage and localStorage empty; a wrong password shows "Invalid email or password" and stays on /login. At 375 px: no horizontal overflow (scrollWidth 375), one-column list, modal 343 px wide. At 1280 px: no overflow, three-column grid. The only console error was the expected 401 of the wrong-password login. Screenshots were saved outside the repo. Not covered: other browsers, real expiry of a token in the browser, screen readers.

## T05 board tests (Jest 30 + RTL + user-event, mocked `api/tasks`, `api/users`, `api/projects`; frontend total 76 tests / 10 suites)
| Suite | Tests | What it proves |
|---|---|---|
| validation.test.ts (+4) | 8 | validateTask: title required and 120, 2000 description, past date refused on create, today accepted, edit keeps an unchanged past date (D04) but not a changed one |
| TaskCard.test.tsx | 9 | overdue label and class for an open past-due task; not overdue when due today, in the future, no date, or DONE; priority/due/assignee text; status dropdown callback; error with Retry; Edit/Delete callbacks |
| TaskForm.test.tsx | 8 | create: empty title, 121 chars, past date block the API call; trimmed payload with picked assignee; server field errors on the field; edit: prefill, unchanged past date allowed, full PUT body, changed past date refused, clearing the assignee sends null |
| Board.test.tsx | 16 | loading, empty, column error with Retry (other columns unaffected), summary error with Retry; each column queries its own status with size 10; Next/Previous over 12 tasks; priority filter refetches all three columns and leaves the summary alone; filters read from the URL; sort desc; assignee filter via the lookup and Clear; status success moves the task and refetches columns and summary; failure keeps the old status, shows the error and Retry works; overdue only on open tasks; delete confirm / Cancel / failure with retry; all board query keys start with `['user', 1, 'project', 5]` |

The backend fake in Board.test.tsx filters, sorts and pages like the real endpoint, so these tests prove the UI's requests and reactions, not the backend (the backend tests cover that).

Browser smoke (24 Nov, headless Chromium via `playwright-cli`, real backend through the Vite proxy, freshly registered user; manual run): empty title shows "Title is required"; four tasks created through the form sorted by due date with the dateless one last; two past-dated rows inserted by SQL (the API refuses past dates on create) showed Overdue = 1 and an "Overdue" label only on the open one, not on the DONE one; priority filter High showed only High tasks in the columns while the summary stayed at 6 and the URL held `?priority=HIGH`; sort "Latest first" reversed the order; changing a status moved the card and updated the summary and Overdue at once (Overdue 1 → 0 after the past-due task became Done); edit changed priorities; delete asked first and Total fell from 6 to 5; after a reload the board and summary were the same. At 375 px: one column, scrollWidth 375 = innerWidth. At 1280 px: three columns, no overflow. Console: 0 errors. Screenshots are in `%TEMP%/taskflow-smoke`, not in the repo. A first pass used a wrong `playwright-cli` command (`select-option`, which does not exist; the command is `select`), so the priorities of the first four tasks were set afterwards through the edit form. Not covered in the browser: the status-change failure path and paging beyond 10 tasks per column (both are covered by unit tests only); other browsers; screen readers.

## T06 traceability (page-06 test matrix and assignment testing requirements)
Test names are the backtick names in the source. Backend: `backend/src/test/kotlin/com/taskflow/` (abbreviated here to `…/`); frontend: `frontend/src/`. "Manual" means the browser or curl runs described below, not an automated suite.

| Area | Scenario | Covered by | Status |
|---|---|---|---|
| Auth | register valid | `…/auth/AuthApiIT` register returns 201 with a safe user, normalised email and a BCrypt hash; `AuthServiceTest` register normalises email, trims name and stores only the hash | covered |
| Auth | duplicate normalised email | `AuthApiIT` duplicate email, in any letter case, is 409; `AuthServiceTest` (5 constraint-classification tests); `AuthRaceIT` two concurrent registrations | covered |
| Auth | invalid fields | `AuthApiIT` invalid input gives 400 with field-level errors, password bounds (8 chars / 72 bytes), email and name limits, malformed JSON; `PasswordPolicyTest` | covered |
| Auth | wrong password | `AuthApiIT` wrong password and unknown email give the same 401; `AuthServiceTest` login with a wrong password / unknown email | covered |
| Auth | hash, not raw | `AuthApiIT` register … BCrypt hash in the database; `AuthServiceTest` stores only the hash; `DemoDataSeederTest` | covered |
| Auth | valid / expired / malformed token | `AuthApiIT` garbage, expired, forged, wrong-issuer and bad-subject tokens are all 401 in the standard shape, no token is 401 with a Bearer challenge; `JwtTokenTest` (10 cases, real signed JWTs) | covered |
| Projects | list own only | `ProjectApiIT` create … the list shows it; `OwnerScopingIT` project lists show only the caller's projects; `RepositoryIT` project list is owner scoped | covered |
| Projects | create uses principal | `OwnerScopingIT` owner comes from the token - a client supplied ownerId is ignored; `ProjectServiceTest` create stores the project under the caller | covered |
| Projects | foreign access rejected | `OwnerScopingIT` B gets 404 for … A's project; `ProjectServiceTest` summary of a foreign or missing project is NotFound | covered |
| Tasks | required title, 120 / 121 | `TaskApiIT` title is required, trimmed, and at most 120 characters; `SchemaIT` task title is limited to 120 characters | covered |
| Tasks | due today / past / null | `TaskApiIT` dueDate on create may be today, future or absent, but not in the past; update keeps an unchanged past due date…; `TaskServiceTest` create due date…, update keeps/rejects… | covered |
| Tasks | unknown assignee | `TaskApiIT` an unknown assignee is a 400 field error and nothing is saved; `TaskServiceTest` | covered |
| Tasks | status and priority enum | `TaskApiIT` bad enum values, bad dates and bad types are 400 field errors; `SchemaIT` status and priority accept only the enum values | covered |
| Tasks | create / update / delete / PATCH | `TaskApiIT` create applies defaults…, update replaces all editable fields…, patch changes only the status - every transition is allowed, delete returns 204… and a second delete is 404 | covered |
| Ownership | B tries A's list / create / update / PATCH / delete / summary | `OwnerScopingIT` B gets 404 for list, create, update, patch, delete and summary (two real tokens, DB unchanged); same 404 body for foreign and missing; B's project invisible to A | covered |
| Ownership | assigned B still cannot access | `OwnerScopingIT` fixture assigns A's task to B; `TaskRepositoryScopeIT` … even when assigned; `RepositoryIT` task lookup requires the project owner, even for the assignee | covered |
| Ownership | every endpoint needs a token | `OwnerScopingIT` every new endpoint needs a token; `AuthApiIT` only register, login and health are public | covered |
| Query | combined filters, empty result | `TaskListIT` status, priority and assignee filters work alone and combined; **new (T06)** combined filters with paging return each matching task once, in order, and never a non-matching one | covered (gap closed) |
| Query | page boundaries, size limit | `TaskListIT` page metadata and boundaries (size 1/100, page beyond last, empty project); invalid sort, direction, page and size are 400 field errors (size 0/101/-5/"many") | covered |
| Query | stable tie-break, high priority first, null dates last | `TaskListIT` priority sorts by rank HIGH, MEDIUM, LOW; due date … undated tasks always last; ties are broken by id; paging 45 tasks with many ties returns every task exactly once | covered |
| Query | invalid sort | `TaskListIT` invalid sort, direction, page and size are 400 field errors; several invalid parameters are all reported together | covered |
| Summary | all statuses, overdue not DONE, today not overdue, null date | `ProjectApiIT` summary counts statuses and overdue by the rule; empty project and filters ignored; overdue equals the flags in the list; `ProjectServiceTest`; `TaskServiceTest` overdue flag … | covered |
| Summary | status transitions | **new (T06)** `ProjectApiIT` summary follows status transitions and deletes, including the overdue count (TODO → IN_PROGRESS → DONE → TODO → delete) | covered (gap closed) |
| UI | task card overdue | `features/board/TaskCard.test.tsx` overdue label and class; not overdue when due today / future / no date / DONE | covered |
| UI | form validation and submission | `features/board/TaskForm.test.tsx` (8 tests: create and edit, D04, assignee clear, server field errors); `auth/validation.test.ts` validateTask | covered |
| UI | status update error | `features/board/Board.test.tsx` on failure keeps the old status, shows an error, and Retry works | covered |
| UI | loading / empty / error | `Board.test.tsx` board states (loading, empty, column error + Retry, summary error + Retry); `ProjectsPage.test.tsx` | covered |
| UI | login and token expiry | `features/auth/LoginPage.test.tsx`; `auth/unauthorized.test.tsx` a 401 clears session and cache and redirects to /login once with no second request, a late 401 for an old token changes nothing; `auth/routing.test.tsx` expired stored session ignored | covered |
| UI | browser smoke | T04, T05 and T06 manual runs (below) | manual |
| E2E | register A/B, A creates project and tasks, filter/sort, status, reload, delete, logout, mobile | T06 manual pass (below) | manual, not automated |
| Setup | clean clone, compose starts, migration applies, seed works, docs match commands | `bash scripts/verify.sh` in a fresh clone (T00, T05, T06); `PostgresSmokeIT`, `SchemaIT`; `DemoSeedIT` (two users, owned projects, varied tasks, idempotent) | covered for verify, migrations and seed; README commands are re-read, not executed by a test |

| Assignment requirement (R11) | Evidence |
|---|---|
| JUnit 5 + MockK service tests | `AuthServiceTest` (11), `ProjectServiceTest` (4), `TaskServiceTest` (15), `UserLookupServiceTest` (2), `DemoDataSeederTest` (5), `MockkBaselineTest`; injected `Clock` in `TaskServiceTest` |
| At least one Spring integration test | 12 classes extending `PostgresIT` / `ApiIT` (`@SpringBootTest` + MockMvc + real PostgreSQL 17 in Testcontainers), e.g. `OwnerScopingIT`, `TaskApiIT`, `TaskListIT` |
| At least two Jest + RTL component tests | `TaskCard.test.tsx`, `TaskForm.test.tsx`, `Board.test.tsx`, `ProjectsPage.test.tsx`, `LoginPage.test.tsx`, `RegisterPage.test.tsx` (user-event, accessible roles, isolated QueryClient, mocked API) |

Gaps and limits, honestly: no automated browser E2E (manual only); frontend tests mock the API, so UI-to-server contract drift is caught only by the manual pass; no test of a real token expiring in the browser (the expiry paths are unit tested with a 401); the status-change failure path and paging beyond 10 tasks per column were not exercised in the browser; README command text is not executed by any test; other browsers, screen readers and keyboard-only use were not tested.

### T06 mutation checks (one rule each, file copied first, one change, run, restore from the copy, `git diff` clean afterwards)
| Rule | One-line change | Tests that failed |
|---|---|---|
| Owner scoping of the task page query | removed the owner predicate in `TaskSpecifications.scoped` | `TaskRepositoryScopeIT` (3 of 19 run: foreign owner empty with every filter, foreign project, existing project) |
| Overdue rule, backend | `TaskRules.isOverdue`: `isBefore(today)` → `!isAfter(today)` (today counted overdue) | `TaskServiceTest` overdue flag…; `ProjectApiIT` summary overdue count equals the number of overdue flags… (2 of 46 run) |
| Overdue rule, frontend | `dates.ts`: `dueDate < today` → `dueDate <= today` | `TaskCard.test.tsx` (1 of 25 run: not overdue when due today) |
| Due date not in the past on create | `TaskService.create`: `isBefore(today)` → `isBefore(today.minusDays(1))` | `TaskServiceTest` create due date… yesterday is rejected; `TaskApiIT` dueDate on create… (2 of 27 run) |

Observation: the first relay hook treated these deliberate failures as real ones and escalated; nothing was broken and no production file changed.

### T06 coverage (information, not a gate)
`./gradlew test` now also writes a JaCoCo report (`backend/build/reports/jacoco/test/html/index.html`, XML next to it). Backend line coverage: **457 of 471 lines (97.0 %)** with 128 tests. The number says what the tests execute, not that they assert the right thing; the mutation checks above are the better evidence for the three key rules. No frontend coverage was measured.

### T06 end-to-end manual pass (24 Nov, headless Chromium via `playwright-cli`, real backend + Vite proxy, curl for the second user)
- User A registered in the browser and created a project and tasks through the form (priorities Low/Medium/High, with and without a due date, one unassigned task); statuses changed through the card dropdown to cover To Do, In Progress and Done; one overdue task seeded by SQL (the API refuses past dates on create). Summary showed To Do 3, In Progress 1, Done 1, Overdue 1, Total 5; moving the overdue task to Done made Overdue 0.
- Priority filter High showed only the two High tasks while the summary stayed at 4 total; "Latest first" reversed the order; delete asked first (Total 5 → 4); reload kept the same board and summary; logout went to /login and the board URL redirected back to /login.
- 375 px: scrollWidth 375 = innerWidth, one column; 1280 px: three columns. Console: 0 errors.
- User B (registered by curl, then also logged in through the browser): `GET /api/projects` → `[]`; with A's project and task: list, summary, create, PUT, PATCH and DELETE all returned **404**; no token and a garbage token returned **401**; A's summary afterwards was unchanged. In the browser B saw the empty project list and "Project not found" on A's board URL.
- Screenshots in `%TEMP%/taskflow-smoke`, not in the repo. All processes, the Gradle daemon and the db container were stopped afterwards (no listener on 5173/8080).

## T07 release checks (24 Nov)
- **Compose smoke** (`docker compose up --build -d`, JWT secret supplied from the shell): db healthy, then backend healthy, then frontend. Through the frontend port: `/` 200, `/projects/5` 200 (SPA fallback), `/api/projects` without a token 401, register 201, login, create project and task 201, summary correct; the backend port 8080 is not reachable from the host. One Playwright login and board screenshot (outside the repo). Container logs contain neither the JWT secret, the DB password nor an issued token (checked by searching the logs for the real values). `docker compose down` without `-v`.
- **Demo profile** in Compose, on a fresh volume (a second compose project, removed afterwards): login as `alice.demo@example.test` works; two projects, summary 2 To Do, 1 In Progress, 1 Done, 1 Overdue. On an older database the idempotent seeder does nothing, so old demo users keep their old password.
- **Fresh clone** following the README quick start: `scripts/verify.sh` exit 0 (backend 128 tests, frontend 76), `.env` created from `.env.example`, empty `JWT_SECRET` stops `docker compose` with the message "set JWT_SECRET in .env", `docker compose up --build -d` healthy, register / login / create 201 on a fresh database, then `down -v` of the clone's own project only.
- **CI**: `.github/workflows/ci.yml` is **not run** (no GitHub remote). Its YAML parses (js-yaml), permissions are `contents: read`, and the actions are pinned to the latest release tags found on the official GitHub release pages with curl: checkout v7.0.1, setup-java v6.3.0, setup-node v7.1.0, upload-artifact v7.0.2, gradle/actions wrapper-validation v6.4.1 (each `action.yml` fetched at that tag, HTTP 200). Whether the ubuntu-latest runner, Node from `.nvmrc` and the Testcontainers run behave as locally is unverified until the first push.
- **Secret and history scan** (43 commits, `git log -p --all` piped to grep; no tools installed): private keys, provider tokens (gh*_, github_pat_, sk-, AKIA, xox, AIza, glpat), JWT-shaped strings, base64 literals of 32+ bytes outside the lock file, `.env` files and build outputs ever tracked, `.docx`, IDE and agent files, employer or personal names and absolute paths: no hits. Credential-like assignments on added lines were reviewed by hand: test fixtures (`correct-horse`, `password1`, `race-pass-123`), the placeholder `change-me`, and `TEST_JWT_SECRET` in the JWT test, which decodes to the text "test-only-jwt-secret-32-bytes!!!!" and is used only by tests. The only tracked binary is `gradle-wrapper.jar` (sha256 ad16bca4…37e0; compare with the official Gradle 8.14.8 checksum, which the CI wrapper-validation step also does). All 43 commits have one author, whose e-mail is the personal GitHub address; that is commit metadata and becomes public with the repository.

## Environment notes
- Docker Desktop must be running for Testcontainers (verified with Docker Engine 29.5.3, Testcontainers 1.21.4).
- Integration tests inject a test-only `taskflow.jwt.secret` (see `PostgresIT`); `bootRun` needs a real `JWT_SECRET` in the environment.
- Tests and bootRun run with `-Duser.timezone=UTC` (Postgres 17 rejects the JVM zone alias `Asia/Calcutta`).
- Frontend needs Node ^24.11.0 (Babel 8 requirement); tested on 24.14.0 (`frontend/.nvmrc`).

## Known dev-tooling advisories
`npm audit` reports 18 moderate findings, all from two advisories in the Jest toolchain: `glob` (AbortSignal cancellation bypass, GHSA-3g3m-2fjr-6pw4) and `minimatch` (globparts DoS, GHSA-g3hv-75wg-m249). They reach us only through Jest/babel-jest dev dependencies, which only process this repo's own files and are not in the production bundle. `npm audit --omit=dev` reports 0. The suggested `audit fix --force` would downgrade Jest to 29, so it was not applied. Re-check when Jest ships a release with patched glob/minimatch.
