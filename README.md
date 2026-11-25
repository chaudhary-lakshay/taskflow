# TaskFlow

**README** | [Architecture & UML](docs/diagrams/architecture.md) | [API reference](docs/api-contract.md)

TaskFlow is a task management application built with Kotlin and Spring Boot. Users can create projects, organise tasks on a Kanban board, assign tasks and track progress.

## Features

- Register and sign in using JWT authentication.
- Create projects and manage tasks across To Do, In Progress and Done.
- Filter by status, priority or assignee; sort by due date or priority through the API.
- Track task counts and overdue tasks.
- Access only projects and tasks you own.

## Tech stack

| Backend | Frontend | Database |
| --- | --- | --- |
| Kotlin, Java 21, Spring Boot 3.5 | React, TypeScript, Vite | PostgreSQL 17, Flyway |
| Spring Security, Spring Data JPA | React Router, TanStack Query | |

## Getting started

Requires Docker with Compose v2.

```bash
git clone https://github.com/chaudhary-lakshay/taskflow.git
cd taskflow
cp .env.example .env
```

Set `POSTGRES_PASSWORD` and `JWT_SECRET` in `.env`. Generate the JWT secret using `openssl rand -base64 32` (or another secure random generator). On Windows, `Copy-Item .env.example .env` also works.

```bash
docker compose up --build
```

Open [http://localhost:8081](http://localhost:8081) and register an account. To stop the application, run `docker compose down`. Database data is retained.

### Sample data

Before the first startup, set these variables in `.env`:

```dotenv
SPRING_PROFILES_ACTIVE=demo
DEMO_PASSWORD=your-chosen-password
```

Sign in as `alice.demo@example.test` or `bob.demo@example.test` using the password you chose (at least 8 characters and at most 72 UTF-8 bytes). Existing demo accounts keep their original passwords.

For running the backend and frontend separately, see [Local development](docs/local-development.md).

## Architecture

```text
Browser → nginx → Spring Boot → PostgreSQL
                   Controller
                       ↓
                     Service
                       ↓
                    Repository
```

The backend is a modular monolith with packages for authentication, users, projects and tasks. Spring Security validates bearer tokens; services enforce ownership, and DTOs keep persistence entities out of API responses.

| Pattern | Purpose |
| --- | --- |
| Repository | Owner-scoped data access for projects and tasks. |
| Specification | Combines optional task filters with mandatory ownership predicates. |
| Strategy | Separates due-date and priority sorting rules. |

React Context holds the session; TanStack Query handles server data and cache refreshes.

[View class diagrams, ERD and sequence diagrams →](docs/diagrams/architecture.md)

## Testing

With Docker running, JDK 17+ and Node 24.14 installed:

```bash
bash scripts/verify.sh
```

| Backend | Frontend |
| --- | --- |
| 129 tests — JUnit 5, MockK, Testcontainers | 76 tests — Jest, React Testing Library |

Integration tests use PostgreSQL and cover authentication, ownership, validation and pagination. See [test details](docs/testing.md) and [GitHub Actions](https://github.com/chaudhary-lakshay/taskflow/actions).

## Trade-offs

- Assigning a task does not grant access to its project. Due dates use UTC calendar days.
- JWTs expire after one hour by default; no refresh tokens or server-side logout.
- Offset pagination can shift when tasks change between requests.
- Swagger and task activity history are not implemented. Spring Boot 3.x was selected for the assignment; this release is outside free support.
- Browser flows were tested manually; automated browser end-to-end tests are not included.

[Design decisions](docs/decisions.md) · [Backend audit](docs/backend-audit.md)
