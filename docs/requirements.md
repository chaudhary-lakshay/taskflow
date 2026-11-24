# Requirements

Source: take-home assignment (pp. 1-3) as mapped in the project plan. Items marked **[ASSUMPTION]** are proposed design defaults, not explicit assignment text, and need confirmation before the task that uses them.

## Scope
TaskFlow: users register/login, own projects, and manage tasks in a three-column board (TODO / IN_PROGRESS / DONE). Backend Spring Boot 3.x + Kotlin, frontend React 18+ (TypeScript), PostgreSQL, JWT auth.

Out of scope (D12): team membership, project edit/delete, notifications, real-time sync, drag-and-drop (a status dropdown is accepted). No microservices, Kafka, Redis, GraphQL or cloud deployment.

## Acceptance criteria
| ID | Criterion |
|---|---|
| R01 | Register with valid unique email, password hashed, duplicates rejected safely; login returns a signed JWT. |
| R02 | All business endpoints require a valid bearer JWT; invalid or expired token gives 401. |
| R03 | **Owner-only**: project listing, project creation and every task operation (including summary) are limited to the authenticated owner. Tested with two users. |
| R04 | Task fields as assigned; enums TODO/IN_PROGRESS/DONE and LOW/MEDIUM/HIGH; assignee optional. |
| R05 | Task list supports status, priority and assignee filters, dueDate/priority sorting, pagination. |
| R06 | Create, update, delete task; status-only PATCH; per-status counts and overdue count. |
| R07 | Field-level 400 validation; title max 120; dueDate not in the past on creation; DTOs, global exception handling, layered structure. |
| R08 | Register/login, project list, three-column project board, task create/edit modal; create project; status changes persisted. |
| R09 | Priority/assignee filters, due-date sorting, overdue highlight, loading/empty/error states, auth redirect, responsive layout. |
| R10 | Functional React hooks, reusable components, separate API layer, documented state choice. |
| R11 | At least two meaningful design patterns; JUnit 5 + MockK service tests; at least one backend integration test; at least two Jest + RTL component tests. |
| R12 | Meaningful Git commits, env-based configuration, public GitHub repo with backend/ and frontend/, README, sample login/seed data. |
| B01 | Optional: Docker Compose, OpenAPI, migrations, GitHub Actions, activity history. Only after R01-R12 pass. |

## Owner-only acceptance scenarios (R03)
1. User A creates a project. User B's `GET /api/projects` does not list it.
2. User B calling any of `GET/POST /api/projects/{id}/tasks`, `GET /api/projects/{id}/summary` on A's project gets **404** (not 403).
3. User B calling `PUT/PATCH/DELETE /api/tasks/{id}` on a task in A's project gets **404**, and the task is unchanged.
4. A task in A's project assigned to B does not give B any access to the project or task.
5. Ownership comes from the JWT subject only; `ownerId` in a request body is ignored or rejected.
6. No token, malformed token, wrong signature and expired token all give 401 with the standard error shape.

## Decisions awaiting confirmation
| ID | Decision | Status |
|---|---|---|
| D02 | Assignee is a registered user; extra `GET /api/users?query=` endpoint returns id and name only | **[ASSUMPTION]** extra endpoint, not in assignment |
| D03 | dueDate is a date-only UTC day and optional; overdue = dueDate < today and status != DONE | **[ASSUMPTION]** optionality |
| D09 | Token kept in sessionStorage | **[ASSUMPTION]** XSS trade-off documented |
| - | Submission email, exact deadline time, AI-assistance rules of the recruiter | **Open**, confirm before release |
