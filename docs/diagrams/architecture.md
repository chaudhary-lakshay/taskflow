# Architecture & UML

[README](../../README.md) | **Architecture & UML**

Mermaid source of truth for the as-built system, reviewed against the code on 25 Nov 2026 (after the page-offset fix). GitHub renders them. The original design sketches are in the Notion plan (page 02); where they differ, these win.

## 1. System (HLD)
```mermaid
flowchart LR
  Browser -->|"http://localhost:8081"| Nginx["frontend: nginx-unprivileged\nstatic React build"]
  Nginx -->|"/api/* proxy, backend:8080"| API["backend: Spring Boot 3.5, Kotlin"]
  API -->|"JDBC, Flyway at startup"| DB[("PostgreSQL 17")]
```
One origin for the browser, so no CORS rules. Details in [Deployment](#14-deployment).

## 2. Backend components
```mermaid
flowchart TB
  subgraph web["Web layer"]
    SEC["Security filter chain\nJWT resource server"]
    AC[AuthController]
    PC[ProjectController]
    TC[TaskController]
    UC[UserLookupController]
    EX["GlobalExceptionHandler\nApiError JSON"]
  end
  subgraph svc["Service layer: rules, ownership, Clock"]
    AS[AuthService]
    PS[ProjectService]
    TS[TaskService]
    US[UserLookupService]
    TK[TokenService]
  end
  subgraph data["Persistence"]
    UR[UserRepository]
    PR[ProjectRepository]
    TR["TaskRepository + TaskPageQuery"]
    SP["TaskSpecifications\nTaskSortStrategy"]
  end
  DB[("PostgreSQL")]
  SEC --> AC & PC & TC & UC
  AC --> AS --> UR
  AS --> TK
  PC --> PS --> PR
  PS --> TR
  TC --> TS --> PR
  TS --> TR
  TS --> UR
  UC --> US --> UR
  TR --> SP
  UR & PR & TR --> DB
  EX -.-> web
```
Controllers take the user id only from the JWT. Services return DTOs, mapped inside the transaction.

## 3. Use cases
```mermaid
flowchart LR
  V(["Visitor"])
  O(["Signed-in user (owner)"])
  V --> Reg[Register]
  V --> Login[Log in]
  O --> CP[Create project]
  O --> LP[List own projects]
  O --> CT[Create task]
  O --> BR["View board: filter, sort, page"]
  O --> ST[Change task status]
  O --> ET[Edit task]
  O --> DT[Delete task]
  O --> SM[See project summary]
  O --> FU["Find a user by name (assignee)"]
  CT -.->|optional| FU
  ET -.->|optional| FU
```
There is no separate owner role: the signed-in user may use only projects and tasks they own. An assignee gets no access (404 for everyone else, D06).

## 4. Data model (ERD)
```mermaid
erDiagram
  USERS ||--o{ PROJECTS : owns
  PROJECTS ||--o{ TASKS : contains
  USERS |o--o{ TASKS : "assigned (optional)"
  USERS {
    bigint id PK "identity"
    varchar name "100, not blank"
    varchar email UK "254, trimmed lower case (CHECK)"
    varchar password_hash "100, BCrypt"
    timestamptz created_at
  }
  PROJECTS {
    bigint id PK "identity"
    varchar name "120, not blank"
    text description "nullable"
    bigint owner_id FK "not null"
    timestamptz created_at
  }
  TASKS {
    bigint id PK "identity"
    varchar title "120, not blank"
    text description "nullable"
    varchar status "TODO, IN_PROGRESS, DONE (CHECK)"
    varchar priority "LOW, MEDIUM, HIGH (CHECK)"
    date due_date "nullable, UTC day"
    bigint assignee_id FK "nullable"
    bigint project_id FK "not null"
    timestamptz created_at
    timestamptz updated_at
  }
```
Indexes (V1 migration): `projects(owner_id, id)`; `tasks(project_id, status, id)`, `tasks(project_id, due_date, id)`, `tasks(project_id, assignee_id)`. Foreign keys have no cascade, and there is no delete for projects or users.

## 5. Classes: auth and security
```mermaid
classDiagram
  class AuthController {
    +register(RegisterRequest) UserDto
    +login(LoginRequest) AuthResponse
  }
  class AuthService {
    +register(name, email, password) User
    +login(email, password) LoginResult
    -dummyHash String
  }
  class TokenService {
    +issue(userId) IssuedToken
  }
  class JwtConfig {
    +jwtSecretKey(props) SecretKey
    +jwtEncoder(key) JwtEncoder
    +jwtDecoder(key, props, clock) JwtDecoder
  }
  class SecurityConfig {
    +securityFilterChain(http) SecurityFilterChain
  }
  class RequiredClaimsValidator {
    +validate(jwt) OAuth2TokenValidatorResult
  }
  class JwtProperties {
    secret
    issuer
    ttlSeconds
  }
  class UserRepository {
    <<interface>>
    +findByEmail(email) User
    +existsByEmail(email) Boolean
    +findByNameContainingIgnoreCase(name, pageable) List~User~
  }
  class User {
    id
    name
    email
    passwordHash
    createdAt
  }
  class UserLookupService {
    +search(query) List~UserOption~
  }
  AuthController --> AuthService
  AuthService --> UserRepository
  AuthService --> TokenService
  AuthService ..> PasswordEncoder : BCrypt
  TokenService --> JwtEncoder
  TokenService --> JwtProperties
  JwtConfig ..> RequiredClaimsValidator : adds to decoder
  JwtConfig --> JwtProperties
  SecurityConfig ..> JwtDecoder : resource server
  UserRepository --> User
  UserLookupService --> UserRepository
```

## 6. Classes: project, task and persistence
```mermaid
classDiagram
  class ProjectController
  class TaskController {
    +parseQuery(...) TaskListQuery
  }
  class ProjectService {
    +listOwned(ownerId)
    +create(ownerId, req)
    +summary(projectId, ownerId)
  }
  class TaskService {
    +list(projectId, ownerId, query) PageDto
    +create(projectId, ownerId, req)
    +update(taskId, ownerId, req)
    +changeStatus(taskId, ownerId, status)
    +delete(taskId, ownerId)
  }
  class TaskRules {
    +isOverdue(dueDate, status, today) Boolean
  }
  class ProjectRepository {
    <<interface>>
    +save(project)
    +findAllByOwnerIdOrderById(ownerId)
    +findByIdAndOwnerId(id, ownerId)
  }
  class TaskRepository {
    <<interface>>
    +save(task)
    +delete(task)
    +findByIdAndProjectOwnerId(id, ownerId)
    +countByStatus(projectId, ownerId)
    +countOverdue(projectId, ownerId, today)
  }
  class TaskPageQuery {
    <<interface>>
    +findPage(ownerId, projectId, query) Page
  }
  class TaskSpecifications {
    +scoped(projectId, ownerId)
    +hasStatus(status)
    +hasPriority(priority)
    +assignedTo(userId)
  }
  class TaskSortStrategy {
    <<interface>>
    +orders(root, cb, ascending) List~Order~
  }
  class Project {
    id
    name
    description
    owner
  }
  class Task {
    id
    title
    status
    priority
    dueDate
    assignee
    project
  }
  class TaskStatus {
    <<enumeration>>
    TODO
    IN_PROGRESS
    DONE
  }
  class TaskPriority {
    <<enumeration>>
    LOW
    MEDIUM
    HIGH
  }
  ProjectController --> ProjectService
  TaskController --> TaskService
  ProjectService --> ProjectRepository
  ProjectService --> TaskRepository
  TaskService --> ProjectRepository
  TaskService --> TaskRepository
  TaskService ..> TaskRules
  TaskRepository --|> TaskPageQuery : fragment
  TaskPageQuery ..> TaskSpecifications : Specification pattern
  TaskPageQuery ..> TaskSortStrategy : Strategy pattern
  TaskSortStrategy <|.. DueDateSort
  TaskSortStrategy <|.. PrioritySort
  ProjectRepository --> Project
  TaskRepository --> Task
  Project "*" --> "1" User : owner
  Task "*" --> "1" Project
  Task "*" --> "0..1" User : assignee
  Task --> TaskStatus
  Task --> TaskPriority
```
`ProjectRepository` and `TaskRepository` extend Spring Data's bare `Repository`: every lookup and query takes the owner id. Mutations act on entities loaded through scoped lookups or new entities attached to the authenticated owner or an owned project. `UserRepository` is a `JpaRepository` (not owner-scoped).

## 7. Sequence: register
```mermaid
sequenceDiagram
  participant C as Client
  participant AC as AuthController
  participant S as AuthService
  participant R as UserRepository
  participant DB as PostgreSQL
  C->>AC: POST /api/auth/register
  AC->>AC: @Valid (name, email, password rules)
  AC->>S: register(name, email, password)
  S->>R: existsByEmail(normalised email)
  alt already used
    S-->>C: 409 EMAIL_TAKEN
  else free
    S->>S: BCrypt hash
    S->>R: saveAndFlush(User)
    R->>DB: INSERT
    alt unique violation uq_users_email (race)
      DB-->>S: SQLState 23505
      S-->>C: 409 EMAIL_TAKEN
    else saved
      S-->>C: 201 {id, name, email}
    end
  end
```

## 8. Sequence: login
```mermaid
sequenceDiagram
  participant C as Client
  participant AC as AuthController
  participant S as AuthService
  participant R as UserRepository
  participant T as TokenService
  C->>AC: POST /api/auth/login
  AC->>S: login(email, password)
  S->>R: findByEmail(normalised email)
  S->>S: BCrypt matches(password, user hash or dummy hash)
  alt unknown email or wrong password
    S-->>C: 401 INVALID_CREDENTIALS (same body for both)
  else ok
    S->>T: issue(userId)
    T-->>S: HS256 JWT (iss, sub=userId, iat, exp)
    S-->>C: 200 {accessToken, tokenType, expiresIn, user}
  end
```

## 9. Sequence: JWT filter and authorization
```mermaid
sequenceDiagram
  participant C as Client
  participant F as BearerTokenAuthenticationFilter
  participant D as JwtDecoder (HS256)
  participant A as AuthorizationFilter
  participant K as Controller
  C->>F: request (Authorization: Bearer token, or none)
  alt token presented
    F->>D: decode and validate
    Note over D: signature, algorithm, exp (30 s skew), issuer, exp present, sub = positive number
    alt invalid token
      D-->>F: validation failure
      F-->>C: 401 UNAUTHORIZED + WWW-Authenticate: Bearer
    else valid token
      D-->>F: validated JWT
      F->>A: authenticated principal
      A->>K: permitted request
      Note over K: protected controllers derive ownerId from jwt.userId()
    end
  else no token
    F->>A: anonymous request
    alt POST register or login, GET /actuator/health
      A->>K: permitted request
    else protected path
      A-->>C: 401 UNAUTHORIZED
    end
  end
```
There is no per-request user lookup: a valid token is trusted until it expires. Ownership is enforced later, in the service (diagram 10).

## 10. Sequence: task create, update and status change (ownership)
```mermaid
sequenceDiagram
  participant C as Client
  participant K as TaskController
  participant S as TaskService
  participant PR as ProjectRepository
  participant TR as TaskRepository
  participant DB as PostgreSQL
  C->>K: POST /projects/{id}/tasks, PUT /tasks/{id}, PATCH /tasks/{id}/status
  K->>K: @Valid body, ownerId = jwt.userId()
  alt create
    K->>S: create(projectId, ownerId, req)
    S->>PR: findByIdAndOwnerId(projectId, ownerId)
  else update or status
    K->>S: update / changeStatus(taskId, ownerId, ...)
    S->>TR: findByIdAndProjectOwnerId(taskId, ownerId)
  end
  alt missing or owned by someone else
    S-->>C: 404 NOT_FOUND (same body for both)
  else owned and status change
    S->>TR: set status, flush
    S-->>C: 200 TaskDto
  else owned and create or update
    S->>S: due date rule (past date refused on create, on update only if changed), assignee exists
    alt rule broken
      S-->>C: 400 VALIDATION_ERROR with fieldErrors
    else ok
      S->>TR: save / flush
      TR->>DB: INSERT or UPDATE
      S-->>C: 201 or 200 TaskDto (overdue computed from the injected Clock)
    end
  end
```

## 11. State machine: task status
```mermaid
stateDiagram-v2
  [*] --> TODO : create (default)
  [*] --> IN_PROGRESS : create with status
  [*] --> DONE : create with status
  TODO --> IN_PROGRESS
  IN_PROGRESS --> TODO
  IN_PROGRESS --> DONE
  DONE --> IN_PROGRESS
  TODO --> DONE
  DONE --> TODO
```
Every transition is allowed (D05); the same status is a no-op that still returns 200. "Overdue" is not a state: it is derived (due before today UTC and status not DONE).

## 12. Sequence: filter, sort and page
```mermaid
sequenceDiagram
  participant UI as BoardColumn (one per status)
  participant K as TaskController
  participant S as TaskService
  participant Q as TaskPageQueryImpl
  participant DB as PostgreSQL
  UI->>K: GET /projects/{id}/tasks?status&priority&assigneeId&sortBy&direction&page&size=10
  K->>K: parseQuery: sortBy, direction, page >= 0, size 1..100, page*size <= Int.MAX
  alt invalid
    K-->>UI: 400 VALIDATION_ERROR (one field error per parameter)
  else valid
    K->>S: list(projectId, ownerId, query)
    S->>S: owned project, else 404
    S->>Q: findPage(ownerId, projectId, query)
    Q->>Q: Specification: scoped(project, owner) AND optional filters
    Q->>DB: SELECT page: ORDER BY strategy then id, OFFSET page*size, LIMIT size
    Q->>DB: SELECT count(*) with the same predicate
    Q-->>S: Page
    S-->>UI: 200 {content, page, size, totalElements, totalPages}
  end
```
The two selects run in one read-only transaction, but under Read Committed each sees its own snapshot. Ordering is deterministic for a fixed result set; offset paging can repeat or skip a row if tasks change between page requests (see [backend audit](../backend-audit.md)).

## 13. Frontend data flow
```mermaid
flowchart LR
  Main["main.tsx\nQueryClientProvider, BrowserRouter, AuthProvider"] --> App["App routes\nRequireAuth"]
  App --> PP["ProjectsPage"] --> UP["useProjects, useCreateProject"]
  App --> BP["ProjectPage\nFilterBar, SummaryBar, BoardColumn x3"]
  BP --> UB["useBoard.ts\nuseColumnTasks, useSummary,\ncreate, update, delete, changeStatus"]
  BP -. "filters in URL" .-> URL["filters.ts"]
  UP --> API["api/projects.ts, auth.ts"]
  UB --> API2["api/tasks.ts, users.ts"]
  API --> H["api/http.ts (only fetch, bearer header)"]
  API2 --> H
  H --> Nginx["/api via nginx"]
  AC["AuthContext (sessionStorage)"] --> H
  H -. "401 with token: clear session and cache" .-> AC
```
Query keys start with the user id (`['user', uid, 'project', pid, ...]`). Each mutation invalidates the whole project prefix, so all three columns and the summary refetch.

## 14. Deployment
```mermaid
flowchart LR
  subgraph host["Docker host"]
    P8081["127.0.0.1:8081"]
    P5432["127.0.0.1:5432 (dev only)"]
  end
  subgraph compose["Compose network"]
    FE["frontend\nnginx-unprivileged, uid 101\nlistens 8080"]
    BE["backend\nSpring Boot, uid 10001\n8080, not published"]
    DB[("db\npostgres 17, volume taskflow-db")]
  end
  P8081 --> FE
  FE -->|"/api"| BE
  BE --> DB
  P5432 --> DB
```
Start order: db healthy, then backend (`/actuator/health`), then frontend. `JWT_SECRET` and `POSTGRES_PASSWORD` come from `.env` and are required.
