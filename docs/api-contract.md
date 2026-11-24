# API contract

JSON, camelCase, ISO dates, numeric ids, `Authorization: Bearer <jwt>`. Status: **proposal**, confirmed in OpenAPI when implemented (T02/T03). Nothing below is implemented yet.

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

## Errors
Same shape everywhere, including 401/403 produced by the security filters:
```json
{"status":400,"code":"VALIDATION_ERROR","message":"Invalid request","fieldErrors":{"title":"Title is required"}}
```
400 invalid input; 401 bad credentials or token; 404 missing or foreign resource; 409 duplicate email **[ASSUMPTION: proposed contract]**; 500 generic message plus correlation id, no stack trace.
