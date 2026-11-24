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

## Environment notes
- Docker Desktop must be running for Testcontainers (verified with Docker Engine 29.5.3, Testcontainers 1.21.4).
- Integration tests inject a test-only `taskflow.jwt.secret` (see `PostgresIT`); `bootRun` needs a real `JWT_SECRET` in the environment.
- Tests and bootRun run with `-Duser.timezone=UTC` (Postgres 17 rejects the JVM zone alias `Asia/Calcutta`).
- Frontend needs Node ^24.11.0 (Babel 8 requirement); tested on 24.14.0 (`frontend/.nvmrc`).

## Known dev-tooling advisories
`npm audit` reports 18 moderate findings, all from two advisories in the Jest toolchain: `glob` (AbortSignal cancellation bypass, GHSA-3g3m-2fjr-6pw4) and `minimatch` (globparts DoS, GHSA-g3hv-75wg-m249). They reach us only through Jest/babel-jest dev dependencies, which only process this repo's own files and are not in the production bundle. `npm audit --omit=dev` reports 0. The suggested `audit fix --force` would downgrade Jest to 29, so it was not applied. Re-check when Jest ships a release with patched glob/minimatch.
