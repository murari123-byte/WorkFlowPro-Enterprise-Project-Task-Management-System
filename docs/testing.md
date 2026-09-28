# Testing

## Summary

| Module | Tests | Kinds |
|---|---|---|
| common | 12 | unit |
| api-gateway | 11 | Spring Boot tests with a fake backend |
| auth-service | 33 | unit (Mockito) + integration (MockMvc + Testcontainers) |
| project-service | 32 | unit (JUnit, Mockito) + integration |
| task-service | 17 | unit + integration |
| **Total backend** | **105** | all passing (`mvn clean install`) |
| Frontend | build + lint | `npm run build` (TypeScript type check) and `npm run lint` (oxlint) — no unit tests |
| End to end | 21 browser steps | run manually with Playwright during development (see below) |

## Run the tests

```bash
mvn clean install                         # everything (Docker must be running)
mvn -pl auth-service -am test             # one service (+ the modules it depends on)
mvn -pl task-service -am test -Dtest=TaskPermissionsTest     # one test class
mvn clean install -DskipTests             # build without tests
cd frontend && npm run build && npm run lint
```
Reports: `<module>/target/surefire-reports/`.

## Tools

| Tool | Used for |
|---|---|
| JUnit 6 (Jupiter) | all tests (`@Test`, `@ParameterizedTest` with `@CsvSource` / `@ValueSource`) |
| Mockito | unit tests of services with mocked repositories/clients (`@ExtendWith(MockitoExtension.class)`, `@Mock`, `ArgumentCaptor`) |
| AssertJ | readable assertions (`assertThat`, `assertThatThrownBy`) |
| Spring Boot Test | `@SpringBootTest` starts the real application context |
| MockMvc | calls controllers through the real security filter chain without a real HTTP port (`spring-boot-starter-webmvc-test` in Boot 4) |
| Testcontainers (2.x) | starts a **real PostgreSQL 16** in Docker for each test context; `@ServiceConnection` points the datasource at it — tests never touch your dev database or `.env` |
| `@MockitoBean` | replaces a Spring bean (e.g. the client to another service) with a Mockito mock in integration tests |
| `TestJwts` (test helper) | creates real HS256-signed tokens with a test secret, so security is tested for real |
| WebTestClient + JDK `HttpServer` | gateway tests against a tiny fake backend |

Test-only settings live in `src/test/resources/application-test.yml` (activated with `@ActiveProfiles("test")`):
a test JWT secret and, in auth-service, a bootstrap admin (`admin@test.local`).

## What is tested

### common (12)
| Class | Tests | Covers |
|---|---|---|
| `AuthenticatedUserTest` | 2 | user id/email/roles read from JWT claims; token without roles |
| `PageResponseTest` | 1 | page → JSON shape |
| `PagingTest` | 5 | default sort, field + direction, API name → entity property, unknown sort field (e.g. `passwordHash`) rejected, bad direction/size/page |
| `ServiceClientsTest` | 4 | service down / 5xx → 503, 401 → 401, 403 → 403, 404 left to caller |

### api-gateway (11)
| Class | Tests | Covers |
|---|---|---|
| `GatewayRoutingTest` | 9 | each route forwards the path unchanged (5 paths), `Authorization` header forwarded, unknown path 404, CORS preflight allowed for the React origin, rejected for another origin, CORS header on normal requests |
| `GatewayServiceDownTest` | 1 | unreachable service → 503 JSON |
| `ApiGatewayApplicationTests` | 1 | context starts |

### auth-service (33)
| Class | Tests | Covers |
|---|---|---|
| `AuthServiceTest` (Mockito) | 5 | register lower-cases email, hashes password, gives EMPLOYEE; duplicate email; wrong password; unknown email → same error **and** password still checked (timing); disabled account |
| `RefreshTokenServiceTest` (Mockito) | 5 | only the hash is stored + 7-day expiry; valid token rotated; expired; unknown; **reuse revokes all sessions** |
| `UserServiceTest` (Mockito) | 6 | admin cannot remove own ADMIN / disable self; disabling revokes sessions; wrong current password; password change stores new hash and signs out everywhere; unknown user 404 |
| `AuthControllerIntegrationTest` | 7 | full flow register → me → login (case-insensitive) → refresh → old token rejected → rotated token also revoked → logout; **`"roles":["ADMIN"]` ignored**; duplicate 409; validation 400 with fieldErrors; wrong password 401; no token 401; **tampered token 401** |
| `UserControllerIntegrationTest` | 7 | EMPLOYEE gets 403 on user admin; admin changes roles and the next token carries them; paging + sort whitelist + search/role filter; disabled user cannot log in or refresh; profile + password change; lookup by id/batch, 404, bad UUID 400; bootstrap admin exists |
| `AuthServiceApplicationTests` | 2 | context starts; Flyway at V3, nothing pending |
| `PingControllerTest` | 1 | ping |

### project-service (32)
| Class | Tests | Covers |
|---|---|---|
| `ProjectStatusTest` | 10 | every allowed/forbidden workflow move (parameterized); which statuses are closed |
| `ProjectServiceTest` (Mockito) | 11 | PM creates and becomes manager + member; PM cannot create for someone else; manager role required; duplicate name; non-member gets 404; member cannot edit; invalid status move; closed project read-only; manager cannot be removed; project with tasks cannot be deleted; ADMIN sees all |
| `ProjectControllerIntegrationTest` | 8 | no token 401; EMPLOYEE cannot create (403); validation incl. date range; **full workflow** (404 for non-member → add member → member can view but not manage → invalid move 422 → valid move → membership view → remove manager 422 → remove member → delete blocked 409 while tasks exist → delete); only ADMIN reassigns manager; list shows only own projects + paging + status filter + bad sort 400; stats count only visible projects; duplicate name ignoring case 409 |
| `ProjectServiceApplicationTests` | 2 | context; Flyway at V2 |
| `PingControllerTest` | 1 | ping |

### task-service (17)
| Class | Tests | Covers |
|---|---|---|
| `TaskPermissionsTest` | 7 | who is a lead (TEAM_LEAD role alone is not enough); only manager/ADMIN delete; assignee may start and submit but not complete; other employees cannot move; workflow still applies to ADMIN; nothing changes while the project is ON_HOLD; overdue rule |
| `TaskControllerIntegrationTest` | 7 | **full task workflow with history** (employee cannot assign → assign → non-member assignee 422 → assignee moves TODO → IN_PROGRESS → IN_REVIEW, cannot complete → manager completes → invalid move 422 → 5 history entries newest first); edit records priority/due date/title changes; outsider gets 404 and an empty list; search + priority sort by rank + paging + priority filter + `open=true` + bad sort/enum 400; validation (blank title, past due date) and EMPLOYEE cannot create; delete permissions; stats and count; no token 401 |
| `TaskServiceApplicationTests` | 2 | context; Flyway at V2 |
| `PingControllerTest` | 1 | ping |

In `TaskControllerIntegrationTest` the mocked project-service answers **depending on who is calling**
(it reads the current user from the security context), like the real service does with token relay.

## End-to-end browser run (manual)

During development a Playwright script (Chromium, headless) drove the real React app against the real
gateway, services and database. It passed all 21 steps (latest run after the code review):
protected route redirect, login validation, wrong password, register with validation, employee sees no
"New project"/"Users", employee blocked from `/admin/users`, session survives reload, admin creates a
project (date validation), project to ACTIVE, add member, create task with assignee, edit priority,
employee starts and submits the task but cannot complete it, admin completes it, task filters, dashboard,
admin changes a user's roles, profile update, not-found page, mobile menu and stacked tables without
horizontal scroll. No browser console errors.

The script is **not in the repository** (Playwright is not a project dependency). Adding it as
`frontend/e2e` with `@playwright/test` is listed under future improvements.

## Not covered by automated tests

- React components (no Vitest/React Testing Library tests).
- Real service-to-service HTTP between two running services (integration tests mock the other service;
  it was checked manually with all services running).
- Editing an overdue task in the UI (would need a task whose due date has passed; checked by code review and the TypeScript build).
- Load/performance tests.
