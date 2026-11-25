# TaskFlow

A small team task tracker: a **Kotlin + Spring Boot 3.5** REST API, a **React 19 + TypeScript** frontend and **PostgreSQL 17**. Users register, create projects, and manage tasks on a three-column board (To Do, In Progress, Done) with filters, sorting, paging and an overdue summary. Every project and task is private to its owner.

Built as a take-home assignment. All mandatory requirements are implemented; of the bonus items, Docker Compose, Flyway migrations and a GitHub Actions workflow are in, **OpenAPI/Swagger and the task activity log are not implemented** (see [Known limitations](#known-limitations-and-next-steps)).

## Quick start (Docker Compose)
Prerequisites: Docker with Compose v2. Nothing else is needed (the images build Java 21 and Node 24 themselves).
```bash
cp .env.example .env                 # Windows PowerShell: Copy-Item .env.example .env
# edit .env: set POSTGRES_PASSWORD, and JWT_SECRET (openssl rand -base64 32; Git for Windows ships openssl)
docker compose up --build            # first build takes a few minutes
```
Open **http://localhost:8081** (change with `FRONTEND_PORT`) and register. `docker compose up` stops with a clear message if `JWT_SECRET` or `POSTGRES_PASSWORD` is empty. Start order is enforced: the database must be healthy, then the backend (`/actuator/health`), then the frontend. Stop with `docker compose down` (the data stays in the `taskflow-db` volume; `down -v` would delete it).

Only the frontend is published, on `127.0.0.1`. It is unprivileged nginx (image `nginxinc/nginx-unprivileged`, runs as the non-root user `nginx`, listens on 8080 inside the container) and serves the React build and forwards `/api` to the backend, so the browser talks to one origin and no CORS setup is needed. The backend has no host port. The database is published on `127.0.0.1:5432` for development only; remove that line from `compose.yaml` if you do not run the backend outside Docker.

### Demo login (demo data only)
To get sample data, set both in `.env` before `docker compose up`: `SPRING_PROFILES_ACTIVE=demo` and `DEMO_PASSWORD=<a password you choose, 8+ characters>`. This creates two **demo-only** users, `alice.demo@example.test` and `bob.demo@example.test` (both use your `DEMO_PASSWORD`), with sample projects and tasks: all three statuses, an overdue task and an unassigned task. The seeder does nothing if those users already exist, so on an old database use a fresh volume. No password is stored in the repository; without `DEMO_PASSWORD` the demo profile refuses to start.

## Local development (without Docker for the app)
Prerequisites: JDK 17+ to start Gradle (the build fetches Java 21 itself through the Gradle toolchain resolver), Node ^24.11.0 (`frontend/.nvmrc` has 24.14.0), Docker for PostgreSQL and the tests.
```bash
cp .env.example .env
docker compose up -d db                       # PostgreSQL only
cd backend
export DB_PASSWORD=<POSTGRES_PASSWORD> JWT_SECRET=$(openssl rand -base64 32)
./gradlew bootRun                             # http://localhost:8080/actuator/health
# add --args='--spring.profiles.active=demo' and DEMO_PASSWORD=... for demo data
cd ../frontend && npm ci && npm run dev       # http://localhost:5173, /api proxied to :8080
```
Windows PowerShell:
```powershell
docker compose up -d db
cd backend
$env:DB_PASSWORD = "<POSTGRES_PASSWORD>"; $env:JWT_SECRET = "<output of: openssl rand -base64 32>"
.\gradlew.bat bootRun
cd ..\frontend; npm ci; npm run dev
```
Gradle does not read `.env`; export the variables in the shell that runs it.

## Tests
```bash
scripts/verify.sh                      # everything: backend tests + frontend lint, typecheck, Jest, build
cd backend && ./gradlew test           # 129 tests; needs Docker (Testcontainers starts PostgreSQL 17); writes a JaCoCo report to build/reports/jacoco/test/html
cd frontend && npm run lint && npm run typecheck && npm test && npm run build   # 76 Jest tests
```
- Backend: JUnit 5 + MockK service tests with an injected `Clock`; `@SpringBootTest` + MockMvc integration tests on **real PostgreSQL** with real signed JWTs and two users (ownership); JWT, repository, schema and query-ordering tests. Line coverage 97 % (information, not a gate).
- Frontend: Jest + React Testing Library + user-event with a mocked API layer and an isolated query client (TaskCard, TaskForm, board states, filters, status change, auth, routing).
- `docs/testing.md` maps every scenario to the test that covers it, lists mutation checks, and states what was **not** tested (no automated browser E2E, one manual pass instead).
- CI: `.github/workflows/ci.yml` runs the backend and frontend checks on push and pull request. **It has not run yet** (it needs a GitHub repository); only its YAML syntax was checked locally.

## Environment variables
| Variable | Used by | Meaning |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER` | Compose | database name and user (default `taskflow`) |
| `POSTGRES_PASSWORD` | Compose | database password; **required** |
| `JWT_SECRET` | backend | base64, at least 32 bytes, no default; **required** (the backend will not start without it) |
| `JWT_ISSUER`, `JWT_TTL_SECONDS` | backend | token issuer (`taskflow`) and lifetime (3600) |
| `FRONTEND_PORT` | Compose | host port of the web UI (8081) |
| `SPRING_PROFILES_ACTIVE`, `DEMO_PASSWORD` | backend | `demo` + a password enables the demo seed; both empty by default |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | backend outside Docker | JDBC URL and credentials (Compose sets them itself) |

`.env` is gitignored; `.env.example` has placeholders only.

## Architecture
```
Browser → nginx (static React build, /api proxy) → Spring Boot API → PostgreSQL
```
Diagrams (system, components, use cases, ERD, class diagrams, register/login/JWT/task/paging sequences, state machine, frontend data flow, deployment) are in [`docs/diagrams/architecture.md`](docs/diagrams/architecture.md); written decisions in `docs/decisions.md`, the API in `docs/api-contract.md`.

- **Backend**: modular monolith, feature packages (`auth`, `project`, `task`, `user`, `security`, `common`), each controller → service → repository. DTOs only; entities are never returned. A `@RestControllerAdvice` gives every error the same shape `{status, code, message, fieldErrors?}`, including 401 from the security filters. Flyway owns the schema (`ddl-auto: validate`).
- **Frontend**: React functional components and hooks, a separate API layer (`src/api`, one `fetch` wrapper), reusable components (`TaskCard`, `Modal`, `Button`, `FormField`, `SelectField`).

### Design patterns
| Pattern | Where | Why |
|---|---|---|
| Repository | Spring Data repositories. `ProjectRepository` and `TaskRepository` are the guarded ones: `RepositoryGuardTest` keeps their surface closed. `UserRepository` is separate and not owner-scoped: email lookup for registration and login, and a name-only directory search (id and name) for the assignee lookup | no way to load a project or task without naming its owner |
| Specification | `task/TaskSpecifications.kt`, composed in `TaskPageQuery` | optional filters (status, priority, assignee) on top of a mandatory project + owner scope |
| Strategy | `task/TaskSortStrategy.kt` (`DueDateSort`, `PrioritySort`) | each sort is one rule; priority sorts by rank HIGH > MEDIUM > LOW, undated tasks last, id as final tie-break so pages never repeat or skip |
| DTO mapping | `from(...)` factories in `*Dtos.kt` | the API shape is separate from the entities |

### State management (frontend)
Server data (projects, tasks, summary) lives in **TanStack Query**: caching, loading and error states and refetching after a mutation come for free, and every query key starts with the user id, so one user never sees another's cache. The signed-in session is small, global and rarely changes, so plain **React Context** (`AuthContext`) is enough; no Redux. Form and modal state stay local. The session and the whole query cache are cleared on logout and on any 401. Board filters live in the URL, so reload and shared links keep them.

## Assumptions and trade-offs
- **Spring Boot 3.5.16 is out of free support** (since 2026-06-30). The assignment asks for Boot 3.x, so I stayed on the last 3.5 release and would move to a supported line first when extending.
- **Token in `sessionStorage`** (D09): survives a reload in the tab, gone when the tab closes, but readable by JavaScript if an XSS bug exists. No refresh token and no cookie; the token lasts one hour and the app returns to login on a 401.
- **Dates are UTC calendar days** (D03, a `LocalDate`, no time zone). Overdue means due before today **and** not DONE; no date is never overdue. The frontend uses the same rule and the same UTC day.
- **Past due dates** (D04): refused on create; on edit an unchanged past date is kept, a changed past date is refused, so overdue tasks stay editable.
- **404, not 403**, for someone else's project or task (D06): the API does not reveal that it exists. Missing or invalid token: 401.
- **Extra endpoint** (D02): `GET /api/users?query=` (name search, at least 2 characters, at most 20 results, id and name only) so a task can be assigned to a registered user. Assigning someone grants them no access to the project.
- **Paging per column** (D07): each board column pages on its own (10 per page); the summary counts the whole project and ignores the filters.
- All status transitions are allowed (D05). Defaults: TODO, MEDIUM, no assignee.
- The backend runs with `-Duser.timezone=UTC` (set in Gradle and in the Docker image): pgjdbc sends the JVM zone and PostgreSQL 17 rejects some legacy aliases such as `Asia/Calcutta`.
- `npm audit` reports moderate advisories in the Jest toolchain (dev only, not in the build output); see `docs/testing.md`.

## Known limitations and next steps
- **Not implemented (bonus):** OpenAPI/Swagger documentation and the task activity log (who changed a status, and when). The API is documented by hand in `docs/api-contract.md`.
- CI is written but has never run; the Docker images were built and smoke-tested locally only.
- No refresh tokens, logout on the server, password reset, e-mail verification or rate limiting on login (next: rate limiting and refresh tokens).
- No project editing or deleting, no team membership, no real-time updates, no drag-and-drop (a status dropdown is used instead).
- No automated browser end-to-end tests; the manual runs are recorded in `docs/testing.md`.
- nginx and the backend run plain HTTP; put a TLS-terminating proxy in front for any real deployment.

## API summary
### Auth
- `POST /api/auth/register` `{name,email,password}` → 201 `{id,name,email}`. Email is trimmed and lower-cased and must be unique (409 `EMAIL_TAKEN`). Password: 8+ characters, at most 72 UTF-8 bytes (BCrypt limit; longer is rejected, not truncated).
- `POST /api/auth/login` `{email,password}` → 200 `{accessToken,tokenType:"Bearer",expiresIn,user}`; wrong password and unknown email give the same 401.
- Every other endpoint needs `Authorization: Bearer <token>`. Public: register, login, `GET /actuator/health`.
- Errors always look like `{status,code,message,fieldErrors?}`, also for 401 from the security filters.
- Environment: `JWT_SECRET` (base64, 32+ bytes, no default), `JWT_ISSUER` (default `taskflow`), `JWT_TTL_SECONDS` (default 3600). Tokens are HS256, stateless; there is no refresh token or logout on the server.

### Projects, tasks and summary
All need `Authorization: Bearer <token>` and are owner-only (someone else's project or task is a 404): `GET/POST /api/projects`, `GET/POST /api/projects/{id}/tasks` (filters `status`, `priority`, `assigneeId`; `sortBy=dueDate|priority`, `direction=asc|desc`, `page`, `size` up to 100), `PUT /api/tasks/{id}`, `PATCH /api/tasks/{id}/status`, `DELETE /api/tasks/{id}`, `GET /api/projects/{id}/summary`. Extra, beyond the assignment: `GET /api/users?query=` (assignee lookup, id and name only). Details: `docs/api-contract.md`.


## Docs
`docs/`: requirements, API contract, architecture, [diagrams](docs/diagrams/architecture.md), decisions (D01–D12 and per-task notes), testing and traceability, and a short [backend audit](docs/backend-audit.md).
