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
| DemoDataSeederTest (MockK) | 1 | no DEMO_PASSWORD → fails fast, writes nothing |

All integration suites share one Testcontainers PostgreSQL 17 container (`support/PostgresIT`).

## Environment notes
- Docker Desktop must be running for Testcontainers (verified with Docker Engine 29.5.3, Testcontainers 1.21.4).
- Tests and bootRun run with `-Duser.timezone=UTC` (Postgres 17 rejects the JVM zone alias `Asia/Calcutta`).
- Frontend needs Node ^24.11.0 (Babel 8 requirement); tested on 24.14.0 (`frontend/.nvmrc`).

## Known dev-tooling advisories
`npm audit` reports 18 moderate findings, all from two advisories in the Jest toolchain: `glob` (AbortSignal cancellation bypass, GHSA-3g3m-2fjr-6pw4) and `minimatch` (globparts DoS, GHSA-g3hv-75wg-m249). They reach us only through Jest/babel-jest dev dependencies, which only process this repo's own files and are not in the production bundle. `npm audit --omit=dev` reports 0. The suggested `audit fix --force` would downgrade Jest to 29, so it was not applied. Re-check when Jest ships a release with patched glob/minimatch.
