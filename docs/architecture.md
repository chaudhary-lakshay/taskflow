# Architecture

Status: T00 baseline only. Design below is the approved plan (D01), not implemented code.

- **Modular monolith.** Kotlin Spring Boot 3.5 backend, feature packages `com.taskflow.{auth,user,project,task,security,common}`, each with controller / service / repository layers and DTOs. React + Vite frontend talks to it over `/api`. PostgreSQL 17 with Flyway migrations (`backend/src/main/resources/db/migration`).
- **Security (T02, implemented).** Stateless Spring Security resource server; `JwtEncoder`/`JwtDecoder` are Nimbus beans (`security/JwtConfig`), HS256 key from `JWT_SECRET` (base64, 32+ bytes, startup fails without it), BCrypt passwords (72-byte bound). CSRF is disabled because no cookie authenticates requests; no CORS rules (same-origin or Vite proxy). Entry point and access-denied handler write the same `ApiError` JSON as the controller advice. Ownership is always derived from the JWT subject.
- **Auth flow.** `AuthController → AuthService → UserRepository / PasswordEncoder / TokenService`; errors go through `GlobalExceptionHandler`.
- **Frontend state.** React Router, AuthContext for the session, TanStack Query for server state, local state for filters and modals. No Redux.
- **Current state (T02).** T01 persistence plus registration, login and JWT protection (no project/task controllers yet). Persistence layer: Flyway `V1__init_schema.sql` (users, projects, tasks with constraints and indexes), JPA entities `User`, `Project`, `Task` with enums `TaskStatus`/`TaskPriority`, Spring Data repositories with owner-scoped lookups, and an optional `demo` profile seeder. Frontend is still the placeholder page.

```mermaid
erDiagram
 USERS ||--o{ PROJECTS : owns
 PROJECTS ||--o{ TASKS : contains
 USERS o|--o{ TASKS : assigned_to
```

HLD/UML and the persistence model live in the Notion plan (page 02) and are mirrored here when T01 starts. Diagrams go in `docs/diagrams/`.
