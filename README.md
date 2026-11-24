# TaskFlow

Task-management app (take-home): Kotlin + Spring Boot 3.5 backend, React + TypeScript frontend, PostgreSQL 17.
Status: T00 baseline only, no features yet.

## Prerequisites
- JDK 17+ to launch Gradle (the build downloads Java 21 itself via the Gradle toolchain resolver)
- Node ^24.11.0 (Babel 8 needs it; 24.14.0 tested, see `frontend/.nvmrc`), Docker (Compose v2)

## Run locally
```bash
cp .env.example .env            # set POSTGRES_PASSWORD and DB_PASSWORD to the same local value
docker compose up -d db
cd backend
DB_PASSWORD=<same value> ./gradlew bootRun   # Windows: gradlew.bat; health: http://localhost:8080/actuator/health
cd ../frontend
npm ci && npm run dev
```

## Verify
```bash
scripts/verify.sh   # backend tests (Testcontainers) + frontend lint, typecheck, Jest, build
```

## Docs
Scope, API contract, architecture, decisions and test strategy: `docs/`. Items marked ASSUMPTION there are unconfirmed design defaults.

## Notes
- Spring Boot 3.5 is out of free support (since 2026-06-30); kept because the assignment requires Boot 3.x.
- Backend runs with `-Duser.timezone=UTC` (set in Gradle): pgjdbc sends the JVM zone, and Postgres 17 rejects the legacy alias `Asia/Calcutta`. If you run the jar directly in such a zone, pass the same flag.
- TypeScript is pinned to 5.9 (create-vite ships 7.x, which typescript-eslint does not support yet).
- Secrets: `.env` is gitignored; `JWT_SECRET` comes from the environment only.
