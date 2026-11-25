# Backend audit
Independent read-only review by Codex (GPT) of the backend at c921d14, with the follow-up decisions. Evidence is code or test lines; "unverified" means no test ran for it. Fixes are in the commits listed at the end.

| ID | Finding | Evidence | Decision |
|---|---|---|---|
| A1 | `page * size` overflows Int: page 21,474,837 at size 100 gave a negative offset, an exception and a 500 | `TaskPageQuery.kt` offset; reproduced by a new `TaskListIT` test (failed before the fix) | **Fixed.** `parseQuery` rejects it with 400 `VALIDATION_ERROR` on `page`; page 21,474,836 still returns an empty 200 |
| A2 | "Pages never repeat or skip" holds only for an unchanged result set | offset paging; `TaskListIT` uses a fixed fixture | **Fixed (wording).** Comment and `api-contract.md` now say "deterministic for a fixed result set". Keyset paging only if cross-request stability is ever needed |
| A3 | Page and count are two selects; Read Committed gives each its own snapshot | `TaskPageQuery.kt` | **Accepted.** Documented in code and API contract. Repeatable Read would fix the metadata only, not later pages |
| A4 | `RepositoryGuardTest` compared method names only, so an unsafe overload passed | test used `it.name` | **Fixed.** It compares name, parameter types and return type. Checked by adding an overload of `findByIdAndOwnerId`: the test failed |
| A5 | `@Size` counts UTF-16 units, so 61 emoji exceed a "120 character" title | Jakarta `@Size` semantics | **Documented, not changed.** The frontend counts the same way (`String.length`), so both sides agree; a code-point validator would make them differ |
| A6 | The demo seeder treats Alice's existence as "already seeded" | `DemoDataSeeder.kt` | **No change.** Seeding is one transaction, so a partial seed cannot come from the seeder. A lone existing demo email means someone registered it, and the seeder must not touch that account |
| A7 | Stale status text and test counts in the docs | `architecture.md`, `testing.md` | **Fixed.** Counts now match the Gradle reports (129 backend tests) |
| A8 | 20 `!!` assertions | listed in the audit | **Partly changed.** The 5 on `encoder.encode(...)` were dropped (the call returns a platform type, so they guarded nothing). The rest stay: request fields are checked by `@NotBlank`/`@NotNull` before the service runs, and generated ids are non-null after `save`. Replacing them would need a command or mapper layer that adds more code than it removes |
| A9 | `spring-boot-starter-security` is also pulled in by the resource-server starter | `build.gradle.kts` | **No change.** Declaring the main starter is harmless and says what the app uses. Unverified: the dependency graph was not compared |
| A10 | Duplicate-email pre-check costs one extra query and cannot prove uniqueness | `AuthService.kt` | **No change.** The unique constraint decides (`AuthRaceIT` covers the race); the pre-check only avoids an exception on the common path |
| A11-A15 | Keep: bare `Repository`, Specification, Strategy, no N+1 on the task page, concrete classes without service interfaces | `TaskRepository`, `TaskPageQuery`, `TaskSortStrategy`, `open-in-view: false` | **Keep.** Each pattern is required by the assignment and small |

## Remaining recommendations
- Add a SQL-count test for the task page (A14 holds today by reading the code, not by a test).
- Use keyset paging if clients ever need stable paging while tasks change.
- A token stays valid until it expires, even if the user row were gone; there is no user delete today, so this is not reachable yet.
