# TaskFlow

Task-management app (take-home): Kotlin + Spring Boot 3.5 backend, React + TypeScript frontend, PostgreSQL 17.
Status: backend complete through T03 (persistence, auth, projects, tasks, summary). The React screens (T04+) are not built yet.

## Prerequisites
- JDK 17+ to launch Gradle (the build downloads Java 21 itself via the Gradle toolchain resolver)
- Node ^24.11.0 (Babel 8 needs it; 24.14.0 tested, see `frontend/.nvmrc`), Docker (Compose v2)

## Run locally
```bash
cp .env.example .env            # set POSTGRES_PASSWORD and DB_PASSWORD to the same local value
openssl rand -base64 32         # put the output in JWT_SECRET (required, the backend refuses to start without it)
docker compose up -d db
cd backend
DB_PASSWORD=<same value> JWT_SECRET=<generated value> ./gradlew bootRun   # Windows: gradlew.bat; health: http://localhost:8080/actuator/health
cd ../frontend
npm ci && npm run dev
```

Demo data (optional): run the backend with `--args='--spring.profiles.active=demo'` and `DEMO_PASSWORD=<your choice>` set. It creates `alice.demo@example.test` and `bob.demo@example.test` (both use that password) with sample projects and tasks. Without `DEMO_PASSWORD` the demo profile refuses to start. You can then log in with `POST /api/auth/login`.

## Auth API (T02)
- `POST /api/auth/register` `{name,email,password}` → 201 `{id,name,email}`. Email is trimmed and lower-cased and must be unique (409 `EMAIL_TAKEN`). Password: 8+ characters, at most 72 UTF-8 bytes (BCrypt limit; longer is rejected, not truncated).
- `POST /api/auth/login` `{email,password}` → 200 `{accessToken,tokenType:"Bearer",expiresIn,user}`; wrong password and unknown email give the same 401.
- Every other endpoint needs `Authorization: Bearer <token>`. Public: register, login, `GET /actuator/health`.
- Errors always look like `{status,code,message,fieldErrors?}`, also for 401 from the security filters.
- Environment: `JWT_SECRET` (base64, 32+ bytes, no default), `JWT_ISSUER` (default `taskflow`), `JWT_TTL_SECONDS` (default 3600). Tokens are HS256, stateless; there is no refresh token or logout on the server.

## Project and task API (T03)
All need `Authorization: Bearer <token>` and are owner-only (someone else's project or task is a 404): `GET/POST /api/projects`, `GET/POST /api/projects/{id}/tasks` (filters `status`, `priority`, `assigneeId`; `sortBy=dueDate|priority`, `direction=asc|desc`, `page`, `size` up to 100), `PUT /api/tasks/{id}`, `PATCH /api/tasks/{id}/status`, `DELETE /api/tasks/{id}`, `GET /api/projects/{id}/summary`. Extra, beyond the assignment: `GET /api/users?query=` (assignee lookup, id and name only). Details: `docs/api-contract.md`.

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
