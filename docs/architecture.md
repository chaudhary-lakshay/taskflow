# Architecture

Status: as built. Diagrams (system, components, use cases, ERD, classes, sequences, state machine, frontend, deployment) are in [`diagrams/architecture.md`](diagrams/architecture.md); this page holds the rules and decisions behind them.

- **Modular monolith.** Kotlin Spring Boot 3.5 backend, feature packages `com.taskflow.{auth,user,project,task,security,common}`, each with controller / service / repository layers and DTOs. React + Vite frontend talks to it over `/api`. PostgreSQL 17 with Flyway migrations (`backend/src/main/resources/db/migration`).
- **Security (T02, implemented).** Stateless Spring Security resource server; `JwtEncoder`/`JwtDecoder` are Nimbus beans (`security/JwtConfig`), HS256 key from `JWT_SECRET` (base64, 32+ bytes, startup fails without it), BCrypt passwords (72-byte bound). CSRF is disabled because no cookie authenticates requests; no CORS rules (same-origin or Vite proxy). Entry point and access-denied handler write the same `ApiError` JSON as the controller advice. Ownership is always derived from the JWT subject.
- **Project/task flow.** `ProjectController / TaskController → ProjectService / TaskService → ProjectRepository / TaskRepository`; `UserLookupController → UserLookupService → UserRepository`. Services return DTOs, mapped inside the transaction.
- **Patterns.** *Repository*: `ProjectRepository` and `TaskRepository` extend Spring's bare `Repository`, not `JpaRepository` and not `JpaSpecificationExecutor`, so `findById`, `findAll`, `deleteById`, `count`, `exists` and the specification-taking variants do not exist on them. `RepositoryGuardTest` pins the exact method signatures (name, parameter types, return type). *Specification*: the filtered task list is the repository fragment `TaskPageQuery.findPage(ownerId, projectId, query)`; its implementation builds `TaskSpecifications.scoped(projectId, ownerId)` plus the optional status/priority/assignee filters itself, so a caller cannot leave the scope out. *Strategy*: `TaskSortStrategy` with `DueDateSort` and `PrioritySort` (rank CASE expression, nulls last, id tie-break for a deterministic order), chosen by `TaskSortField`.
- **Auth flow.** `AuthController → AuthService → UserRepository / PasswordEncoder / TokenService`; errors go through `GlobalExceptionHandler`.
- **Frontend state.** React Router, AuthContext for the session, TanStack Query for server state, local state for filters and modals. No Redux.
- **Persistence.** Flyway `V1__init_schema.sql` (constraints and indexes), JPA entities `User`, `Project`, `Task`, enums `TaskStatus` / `TaskPriority`, and an optional `demo` profile seeder.


## Frontend (T04)
Data flow: see diagram 13.
main.tsx → QueryClientProvider → BrowserRouter → AuthProvider → App (Routes)
App: /login, /register | RequireAuth → /projects, /projects/:id | / and * → /projects
pages → hooks (useProjects, useCreateProject: TanStack Query) → api/projects.ts, api/auth.ts → api/http.ts → fetch('/api/...')
```
- `api/http.ts`: the only `fetch`; adds `Authorization: Bearer`, maps failures to `ApiError {status, code, message, fieldErrors}` (network failure = status 0); a 401 on a request that carried a token calls `onUnauthorized`, a 401 without a token (wrong password) does not.
- `auth/AuthContext`: session in `sessionStorage` (`auth/session.ts`, with an expiry check on load); on logout or 401 it clears storage, state and the query cache (`queryClient.clear()`), and it also clears the cache when a new session starts. After the first 401 the login page sends no request, so there is no redirect loop.
- Query keys: `['user', userId, 'projects']`.
- Reusable components: `Button`, `FormField` (label, aria-invalid, aria-describedby, role=alert errors), `Modal` (focus moves in, Tab wraps, Escape closes, focus returns to the opener), `AppHeader`.
- Client validation mirrors the backend (`auth/validation.ts`); the server stays the authority and its `fieldErrors` are shown next to the fields.

### Board (T05)
Data flow: see diagram 13.
ProjectPage → SummaryBar / FilterBar / BoardColumn ×3 (TODO, IN_PROGRESS, DONE) → TaskCard
BoardColumn → useColumnTasks → api/tasks.listTasks (GET /projects/{id}/tasks?status=…&sortBy=dueDate&direction=…&page=…&size=10)
SummaryBar → useSummary → api/tasks.getSummary;  TaskFormModal / DeleteTaskModal / card status → useCreateTask, useUpdateTask, useDeleteTask, useChangeStatus
```
- Query keys: `['user', uid, 'project', pid, 'summary']`, `['user', uid, 'project', pid, 'tasks', status, priority, assigneeId, direction, page]`, `['user', uid, 'users', term]` (assignee lookup).
- Every successful mutation invalidates the whole `['user', uid, 'project', pid]` prefix: all three columns and the summary. That is a superset of the affected columns, chosen over tracking which ones changed.
- Filters live in the URL (`features/board/filters.ts`); each column is keyed by the filter values, so its page restarts at 0 when a filter changes.
- Overdue is computed on the card from `dueDate`, `status` and the UTC date, mirroring the backend rule (`features/board/dates.ts`).
- The backend has no single-project endpoint, so `/projects/:id` finds the project in the cached list.
