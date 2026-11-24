# API contract

JSON, camelCase, ISO dates, numeric ids, `Authorization: Bearer <jwt>`. Status: **proposal**, confirmed in OpenAPI when implemented. Implemented so far (T02): `POST /api/auth/register` and `POST /api/auth/login`, the error shape, and bearer-token protection of everything else. The remaining endpoints arrive in T03.

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

## Errors
Same shape everywhere, including 401/403 produced by the security filters:
```json
{"status":400,"code":"VALIDATION_ERROR","message":"Invalid request","fieldErrors":{"title":"Title is required"}}
```
Codes in use: `VALIDATION_ERROR` (400, with `fieldErrors`), `MALFORMED_REQUEST` (400), `UNAUTHORIZED` / `INVALID_CREDENTIALS` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `EMAIL_TAKEN` (409, **[ASSUMPTION: proposed contract]**), `INTERNAL_ERROR` (500, generic message plus `correlationId`, stack trace only in the server log).
