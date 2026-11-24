# API contract

JSON, camelCase, ISO dates, numeric ids, `Authorization: Bearer <jwt>`. Status: **implemented** (T02 auth, T03 projects/tasks/summary/user lookup). OpenAPI is optional bonus work and not done.

| Method | Path | Request / query | Success |
|---|---|---|---|
| POST | /api/auth/register | name, email, password | 201 UserDto |
| POST | /api/auth/login | email, password | 200 AuthResponse |
| GET | /api/projects | - | 200 ProjectDto[] (owned only) |
| POST | /api/projects | name, description | 201 ProjectDto |
| GET | /api/projects/{id}/tasks | status, priority, assigneeId, sortBy=dueDate\|priority, direction=asc\|desc, page=0, size=20 (max 100) | 200 PageDto |
| POST | /api/projects/{id}/tasks | title, description, priority, dueDate, assigneeId; optional status | 201 TaskDto |
| PUT | /api/tasks/{id} | full editable fields incl. status; null clears optional fields | 200 TaskDto |
| PATCH | /api/tasks/{id}/status | status | 200 TaskDto |
| DELETE | /api/tasks/{id} | - | 204 |
| GET | /api/projects/{id}/summary | - | 200 SummaryDto |
| GET | /api/users?query= | min 2 chars, max 20 results | 200 UserOption[] **[ASSUMPTION: extra endpoint]** |

The first ten rows are the assignment's required endpoints.

## DTOs
- AuthResponse: accessToken, tokenType="Bearer", expiresIn, user. Password is never returned.
- ProjectDto: id, name, description, ownerId, createdAt.
- TaskDto: id, title, description, status, priority, dueDate, assignee {id,name} or null, projectId, createdAt, updatedAt, overdue (computed).
- PageDto: content, page, size, totalElements, totalPages.
- SummaryDto: todo, inProgress, done, overdue, total. Independent of board filters.

## Rules
- Defaults: status TODO, priority MEDIUM, no assignee. All status transitions allowed.
- Title max 120. dueDate in the past is rejected on create; on update an unchanged past date is kept, a changed past date is rejected.
- Sorting: priority rank HIGH > MEDIUM > LOW (not alphabetical); null dates last; id as tie breaker. Default direction asc for date, desc for priority. Unsupported sort values give 400.
- Overdue: dueDate before today (UTC) and status != DONE; null date is never overdue.

## Auth behaviour (implemented, T02)
- Register: `name` max 100, `email` valid and max 254, `password` 8 characters minimum and 72 UTF-8 bytes maximum. Email stored trimmed and lower case; duplicates (any letter case) give 409 `EMAIL_TAKEN` with `fieldErrors.email`. Response is `{id,name,email}`; the password and its hash never appear in any response.
- Login: email is normalised the same way; unknown email and wrong password both return 401 `INVALID_CREDENTIALS` with identical bodies.
- Token: HS256 JWT, claims `iss`, `sub` (numeric user id), `iat`, `exp` (default 3600 s). The resource server checks signature, algorithm (HS256 only), expiry (30 s skew), issuer and a positive numeric subject; `exp` is required.
- Public endpoints: register, login, `GET /actuator/health` (infrastructure exception). Everything else, including unknown paths, returns 401 without a valid token.
- Owner id is taken from the JWT subject only (`Jwt.userId()`); no request field can set it.

## Projects and tasks (implemented, T03)
- Every endpoint below needs a bearer token; the owner is always the token's subject. A project or task that is missing **or owned by someone else** gives the same 404 `NOT_FOUND` body (never 403). Being assigned a task grants no access.
- `POST /api/projects`: `name` required, max 120 (trimmed); `description` optional, max 2000 **[ASSUMPTION: limit]**. A client-sent `ownerId` is ignored.
- `GET /api/projects/{id}/tasks` query: `status`, `priority`, `assigneeId` (filters combine with AND); `sortBy` = `dueDate` (default) or `priority`; `direction` = `asc`/`desc` (case-insensitive; default asc for dueDate, desc for priority); `page` >= 0 (default 0); `size` 1..100 (default 20). Anything else gives 400 `VALIDATION_ERROR` with a field error per bad parameter. Omitted `sortBy` means dueDate **[ASSUMPTION]**.
- Ordering: dueDate ascending or descending with tasks without a date always last; priority by rank HIGH > MEDIUM > LOW; ties always by id ascending, so pages never repeat or skip rows. A page past the end is an empty page, not an error.
- `POST /api/projects/{id}/tasks`: `title` required, max 120 (trimmed); optional `description` (max 2000 **[ASSUMPTION]**), `priority` (default MEDIUM), `status` (default TODO), `dueDate` (today or later), `assigneeId` (must be an existing user, else 400 `assigneeId`).
- `PUT /api/tasks/{id}`: complete replacement; `title`, `status`, `priority` required **[ASSUMPTION: "complete" read as these three required]**; `description`, `dueDate`, `assigneeId` null clear. D04: an unchanged past `dueDate` is kept, a changed one must not be in the past.
- `PATCH /api/tasks/{id}/status` `{status}`: all transitions allowed. `DELETE /api/tasks/{id}`: 204, no body.
- `GET /api/projects/{id}/summary`: `{todo,inProgress,done,overdue,total}` over **all** tasks of the project (no filters). Overdue = `dueDate` before today (UTC, injected clock) and status not DONE; today is not overdue, a missing date is never overdue. The same rule sets `overdue` on each TaskDto.
- Wrong enum values, malformed dates and non-numeric ids give 400 with the field name.
- **Extra endpoint (not in the assignment, D02):** `GET /api/users?query=` (authenticated): query trimmed, at least 2 characters (else 400), matches **name only** (never email), case-insensitive, `%` and `_` are literal, at most 20 results, response items are only `{id,name}`. Privacy trade-off: any signed-in user can search all user names.

## Errors
Same shape everywhere, including 401/403 produced by the security filters:
```json
{"status":400,"code":"VALIDATION_ERROR","message":"Invalid request","fieldErrors":{"title":"Title is required"}}
```
Codes in use: `VALIDATION_ERROR` (400, with `fieldErrors`), `MALFORMED_REQUEST` (400), `UNAUTHORIZED` / `INVALID_CREDENTIALS` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `EMAIL_TAKEN` (409, **[ASSUMPTION: proposed contract]**), `INTERNAL_ERROR` (500, generic message plus `correlationId`, stack trace only in the server log).
