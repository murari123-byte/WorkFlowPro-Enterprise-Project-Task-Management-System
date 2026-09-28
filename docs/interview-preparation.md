# Interview Preparation — WorkFlowPro

Everything here matches the real code. Numbers used (37 endpoints, 105 tests, 3 databases, 7 migrations,
4 roles) can be checked in [api.md](api.md), [testing.md](testing.md) and [migrations.md](migrations.md).

---

## 2-minute explanation

"WorkFlowPro is a project and task management system I built with Spring Boot microservices and React.

A company can create projects, add members, create tasks, assign them and move them through a workflow —
To do, In progress, In review, Completed. There is a dashboard with project and task numbers, including
overdue tasks.

The backend has three services — Auth, Project and Task — behind a Spring Cloud API Gateway. Each service
has its own PostgreSQL database, and Flyway creates all the tables. Login uses JWT: auth-service creates the
token, and every service checks it by itself. There are four roles — Admin, Project Manager, Team Lead and
Employee — and the rules are enforced on the server, not just hidden in the UI.

The frontend is React with TypeScript. It talks only to the gateway, and an Axios interceptor adds the
token and refreshes it automatically.

I wrote 105 backend tests with JUnit, Mockito and Testcontainers, so the integration tests run against a
real PostgreSQL."

## 5-minute explanation

"**The problem.** I wanted a realistic business app, not a to-do list: several user roles, rules about who
can do what, and data that belongs to different parts of the system.

**The architecture.** The browser talks only to an API Gateway on port 9080. The gateway routes
`/api/auth` and `/api/users` to auth-service, `/api/projects` to project-service and `/api/tasks` to
task-service, and it handles CORS. Behind it are three Spring Boot services, each with its own database.
The auth database login can't even connect to the task database — I set that up in the Postgres init
script. Because the data is split, there are no foreign keys between services. For example, a task only
stores the project id, and task-service asks project-service over REST whether the user is a member.

**Security.** auth-service stores passwords with BCrypt. At login it returns a 15-minute JWT access token,
with the user id, email and roles, plus a refresh token. The refresh token is a random value; I only store
its SHA-256 hash, and each one can be used once. If an old refresh token is used again, I treat it as
stolen and sign the user out everywhere. Every service verifies the JWT with Spring Security's OAuth2
Resource Server, so a request isn't trusted just because it came through the gateway. When one service calls
another, it forwards the user's own token, so the called service applies its rules to that same user.

**Business rules.** Projects have a workflow: Planning, Active, On hold, Completed, Cancelled. Tasks have
To do, In progress, In review, Completed, Cancelled. The assignee can start a task and send it for review,
but only a lead — the project manager, a team lead in the project, or an admin — can mark it completed.
I put the task permission rules in one plain Java class, `TaskPermissions`, which is easy to unit-test. Every
change to a task writes a history row in the same database transaction. People who aren't members of a
project get a 404, not a 403, so they can't even tell it exists.

**Data and APIs.** I use Spring Data JPA with Specifications for search and filtering, and a shared paging
helper that limits page size and only allows known sort fields. Flyway owns the schema; Hibernate only
validates it. I added indexes for the common queries, and a generated column so priority sorts as
LOW < MEDIUM < HIGH < URGENT instead of alphabetically. All errors come back in one JSON format from a global
exception handler in a small shared `common` module.

**Frontend.** React 19, TypeScript and React Router, with protected and role-based routes. The Axios
interceptor adds the token, and on a 401 it refreshes once and retries. The pages have loading, error and
empty states, form validation that matches the backend, and a responsive layout in plain CSS.

**Testing.** 105 backend tests: unit tests with Mockito for the rules, and integration tests with MockMvc,
real signed tokens and a Testcontainers PostgreSQL. I also ran a 21-step browser test with Playwright. It
found two real bugs, which I fixed.

**What I'd improve next:** RS256 keys instead of a shared secret, the refresh token in an HttpOnly cookie,
Docker images for all services, and frontend unit tests."

---

## Architecture explanation

- **Gateway** (Spring Cloud Gateway, WebFlux): one URL for the browser, path-based routing, CORS, and a
  503 answer when a service is down. It holds no business logic and doesn't check tokens.
- **auth-service:** users, roles, login, tokens, user administration. **project-service:** projects,
  members, managers, project workflow. **task-service:** tasks, assignment, task workflow, history, stats.
- **Database per service** on one PostgreSQL server. The services are independent and talk only over REST.
- **common:** a small shared library for cross-cutting code (error format, exception handler, JWT
  verification, paging, REST client helpers). It has no business logic, so the services stay independent.
- **Layers in each service:** Controller → Service → Repository → Database, with DTOs at the edge.

## Complete request flow (example: employee moves a task to "In review")

1. React sends `PATCH /api/tasks/{id}/status` to the gateway with the JWT in the `Authorization` header.
2. The gateway checks CORS and forwards the request to task-service.
3. The task-service security filter verifies the JWT signature, expiry and issuer (401 if invalid) and turns the
   `roles` claim into Spring authorities.
4. The controller validates the body (400 if the status value is wrong).
5. The service loads the task (404 if missing), then calls project-service `/membership` with the same token.
   project-service returns 404 if the user isn't a member, and I turn that into "Task not found".
6. Rules: the project must be Planning or Active, the move must follow the workflow, and this user must be allowed
   to make it. Those failures come back as 422 and 403.
7. In one transaction: update the task and insert a history row. `@Version` prevents a lost update (409).
8. The response includes names (one batch call to auth-service) and what this user may do next.

## JWT authentication flow

Login → BCrypt check → access token (HS256, 15 min, claims `sub`, `email`, `roles`, `iss`, `exp`) +
refresh token (random, stored hashed, 7 days) → the frontend keeps the access token in memory and the refresh
token in localStorage → every request sends `Bearer <token>` → each service verifies it locally →
on 401 the frontend calls `/refresh` once, gets a new pair and retries → logout revokes the refresh token.

## RBAC explanation

Four roles, stored in a `user_roles` many-to-many table and copied into the JWT at login.
Two levels of checks:
1. **Role checks** with `@PreAuthorize`, e.g. only ADMIN or PROJECT_MANAGER can create a project, and only ADMIN
   changes roles.
2. **Data rules** in the service layer, which depend on the specific project: only *this* project's manager
   can edit it, only members can see it, and only the assignee or a lead can move a task.

Roles are never taken from the request body. Register always gives EMPLOYEE, and I have a test that sends
`"roles":["ADMIN"]` and checks that it's ignored.

## Microservices explanation

I split by business area (identity, projects, tasks). Each service can be built, tested, deployed and
changed on its own. The trade-offs I dealt with: no joins across databases (so I use REST calls and batch
lookups), and no cross-service transactions (the project delete checks the task count first; that's a
known limitation). I kept communication synchronous REST because it's simple and easy to test at this size.

## API Gateway explanation

Spring Cloud Gateway 5 on WebFlux. Routes are configured in YAML under
`spring.cloud.gateway.server.webflux.routes`, with the target URLs from environment variables. CORS is set up
only there, so the services don't need CORS config. A custom `ErrorWebExceptionHandler` returns 503 JSON
when a service can't be reached. I tested routing with a fake backend built on the JDK's `HttpServer`.

## PostgreSQL / database design

Three databases. auth_db: users, roles, user_roles (many-to-many), refresh_tokens.
project_db: projects, project_members (composite primary key). task_db: tasks, task_history.
UUID keys, CHECK constraints for enum columns, a unique index on `LOWER(name)` for project names,
a partial index on due dates of open tasks for the overdue queries, and a generated `priority_rank`
column for sorting. Ids from other services have no foreign keys.

## JPA / Hibernate explanation

Entities map the tables. I used `@ManyToMany` for user roles (EAGER, because they're needed at every login),
`@ElementCollection` with an `@Embeddable` for project members (they have no life outside a project),
and `@Version` for optimistic locking. Search uses Spring Data **Specifications**, so optional
filters combine cleanly. Dashboard numbers use JPQL `GROUP BY` queries instead of loading rows.
`open-in-view` is off, `ddl-auto` is `validate`, and `default_batch_fetch_size` avoids N+1 queries
when a list loads a lazy collection.

## Flyway explanation

Every schema change is a versioned SQL file (`V1__init.sql`, `V2__create_projects.sql`, …) in each service.
It runs automatically at startup and is recorded in `flyway_schema_history` with a checksum. I never edit an applied
file; I add a new version. A test in each service runs all migrations on a fresh Testcontainers database
and checks the version.

## React ↔ Spring Boot communication

React calls functions in `api/endpoints.ts`. Axios adds the base URL (the gateway) and the Bearer token.
The response interceptor handles 401 with a single shared refresh call, because refresh tokens are single-use
and two parallel refreshes would break the session. Error messages come from the backend's `ErrorResponse`,
and field errors are shown under the matching inputs. The backend also returns permission hints such as
`allowedStatuses` and `permissions.canEdit`, so the UI shows only the buttons that will work.

## Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) in `common` turns everything into one JSON shape.
The business errors are `ApiException` subclasses with their own status: 400, 401, 403, 404, 409, 422 and 503.
Validation errors become 400 with `fieldErrors`. Optimistic lock failures become 409. Unknown errors become 500
with a generic message, and the details go only to the log. Security errors (401/403) from the filters use the
same JSON through `SecurityErrorHandler`.

## Validation

Bean Validation on request records: `@NotBlank`, `@Email`, `@Size`, `@FutureOrPresent` and an
`@AssertTrue` method for "end date not before start date". Business validation, like workflow moves or the
assignee being a member, lives in the service layer and returns 422. The frontend repeats the simple rules so users
get quick feedback, but the backend is always the real check.

## Pagination

Lists return `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`). I don't return Spring's
`Page` directly, because its JSON can change between versions. The `Paging` helper rejects a size over 100
and allows only whitelisted sort fields. So nobody can sort by `passwordHash`, and a typo gives a 400 instead of a 500.
For priority, the API name maps to the `priorityRank` column.

## Testing

Unit tests with Mockito for services and rules (`AuthServiceTest`, `ProjectServiceTest`,
`TaskPermissionsTest`, …). Integration tests with `@SpringBootTest` and MockMvc against a real PostgreSQL from
Testcontainers, with real signed JWTs from a test helper. The calls to other services are replaced with
`@MockitoBean`. These tests cover full workflows: 401, 403 and 404 cases, validation, paging and sorting.
105 tests in total. A manual 21-step Playwright run covered the UI.

---

## Interview questions and answers (36)

**1. What is WorkFlowPro?**
A project and task management system. Users work in projects, tasks move through a review workflow, and
a dashboard shows progress. I built it with Spring Boot microservices and React.

**2. Why microservices and not one application?**
To learn and show real service boundaries. Identity, projects and tasks change for different reasons, so
each one has its own service and database. For a small team, a monolith would honestly be simpler; I chose
microservices on purpose and handled the trade-offs, like the cross-service calls.

**3. How do the services communicate?**
Synchronous REST with Spring's `RestClient`. I set 2-second connect and 5-second read timeouts and
forward the user's token. Network errors turn into a 503.

**4. Why doesn't the gateway check the JWT?**
Every service verifies the token itself anyway, so nothing trusts a request just because it passed the
gateway. That also means a service is still secure if it's called directly.

**5. What's inside your JWT?**
`sub` (the user id), `email`, `roles`, `iss`, `iat` and `exp`. It's signed with HS256 using a secret from an
environment variable, and the service won't start if that secret is shorter than 32 characters.

**6. How does logout work with stateless JWTs?**
Logout revokes the refresh token in the database. The access token stays valid until it expires, at most
15 minutes, which is why I keep it short. That's a known trade-off of stateless tokens.

**7. How do you store refresh tokens?**
Only as a SHA-256 hash. If the database leaks, the tokens can't be used. Each token works once; using it
returns a new one.

**8. What is refresh token reuse detection?**
If someone presents a refresh token that was already used, either the user or an attacker has an old copy. I revoke
all of that user's refresh tokens, so both have to log in again.

**9. How are passwords stored?**
With BCrypt at strength 10. I limit passwords to 72 characters because BCrypt ignores anything longer.

**10. How do you stop user enumeration at login?**
The same error message for "unknown email" and "wrong password". I also run BCrypt against a dummy hash
when the email doesn't exist, so both cases take the same time. I measured about 0.09 seconds for both.

**11. How does RBAC work in your project?**
Roles are in the token. `@PreAuthorize` handles role-only rules, like who can create a project. The service
layer handles rules about specific data, like being the manager of this project or a member of it.

**12. Why return 404 instead of 403 for non-members?**
A 403 would confirm the project exists. A 404 reveals nothing.

**13. Can a user make themselves an admin?**
No. Registration ignores any roles in the body (I have a test for that), and only an ADMIN can call the
role endpoint. An admin also can't remove their own ADMIN role, so the system can't be locked out.

**14. How is the first admin created?**
`AdminBootstrap` runs at startup. If `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` are set and no user
has that email, it creates an ADMIN. It never changes an existing user.

**15. What happens when a role changes while the user is logged in?**
The new role shows up in their next access token, after the next refresh or login. It takes at most 15 minutes.

**16. Explain the task workflow and who can move a task.**
To do, In progress, In review, Completed, plus Cancelled, reopen and restore. The assignee can move their own task
between To do, In progress and In review. Leads — the project manager, a team lead in the project, or an admin —
can make any allowed move, including Completed.

**17. Where did you put the permission logic, and why?**
In `TaskPermissions`, a plain Java class with static methods. It doesn't need Spring or a database, so it's easy to
read and to unit-test. The same logic also works out `allowedStatuses` for the UI.

**18. How does task-service know if the user is a project member?**
It calls project-service `/api/projects/{id}/membership` with the user's token. project-service owns
membership, so task-service never keeps a copy that could go out of date.

**19. What is token relay?**
When a service calls another service, it forwards the same Bearer token it received. The called service then
applies its normal rules to the original user, so a service never has more rights than the user.

**20. How do you avoid calling auth-service once per row?**
Batch lookup: `GET /api/users/batch?ids=...` with up to 100 ids. A page of 10 tasks needs one call
for all the assignee names.

**21. How do you keep data consistent without cross-service foreign keys?**
The owning service checks ids through its API. For example, project-service refuses to delete a project while
task-service still has tasks for it. It isn't atomic, so I documented that; a real system might use soft delete or events.

**22. Why a separate database per service?**
So no service can depend on another's tables. Each service's database login can only connect to its own database.

**23. How is search and filtering implemented?**
Spring Data JPA Specifications. Each filter is a small method, and filters that aren't used return "no condition".
They combine with `Specification.allOf`. The text search is a case-insensitive `LIKE` on title and description.

**24. How do you sort by priority correctly?**
PostgreSQL computes a `priority_rank` column (1–4) from the priority. The API sort name `priority`
maps to that column, so URGENT comes first instead of sorting alphabetically.

**25. How do you detect overdue tasks?**
A task is overdue when its due date is before today (UTC) and it isn't completed or cancelled. A partial index on
open tasks' due dates supports the query, and the response includes an `overdue` flag.

**26. How is task history implemented?**
A `task_history` table. Every change inserts a row in the same transaction: the action, the field, the old
value, the new value, who did it and when. It stores display values such as the assignee's name at that moment,
because history should show what was true then.

**27. What is optimistic locking and where do you use it?**
A `version` column with `@Version` on projects and tasks. If two people save the same row at the same time, the second
save fails and the user gets a 409 ("changed by someone else") instead of silently overwriting the first.

**28. Why Flyway instead of `ddl-auto=update`?**
The schema is versioned in Git, it's the same on every machine, and I can review it. `ddl-auto=update` can't
handle renames or data changes safely. I use `validate`, so a mismatch fails at startup.

**29. What is the N+1 problem, and did you have it?**
It's when loading N rows triggers N extra queries for related data. Listing users loaded each user's roles
separately. I fixed it with `hibernate.default_batch_fetch_size: 50`, which loads them in batches.

**30. How do you handle errors consistently?**
One `@RestControllerAdvice` in the shared `common` module, plus a JSON entry point for security errors. Every
service returns the same `ErrorResponse`, and a 500 never exposes a stack trace.

**31. What is the `common` module and isn't it risky for microservices?**
It's a small library with only cross-cutting code: errors, JWT verification, paging and REST helpers.
There's no business logic and no entities, so it doesn't couple the services' data or rules.

**32. How did you test security?**
Integration tests with real signed tokens: no token gives 401, a tampered token gives 401, the wrong role gives 403,
a non-member gets 404, and a role sent at registration is ignored. The downstream 401/403 mapping has its own unit test.

**33. Why Testcontainers instead of H2?**
H2 isn't PostgreSQL. My migrations use PostgreSQL features like generated columns, partial indexes and
`LOWER()` unique indexes. Testcontainers runs the real database, so the tests prove the migrations work.

**34. How does the frontend handle an expired token?**
An Axios response interceptor catches the 401, calls `/refresh` once (requests that fail at the same time share that
call), saves the new tokens and repeats the request. If the refresh fails, the user goes back to the login page.

**35. Where do you keep tokens in the browser?**
The access token only in memory; the refresh token in localStorage so the session survives a reload. localStorage can be
read by injected scripts, so the safer version would be an HttpOnly cookie. That's on my improvement list.

**36. What bugs did you find while testing, and how?**
The Playwright browser run found two. Edit forms could be overwritten by a late second data load, which I fixed by
ignoring late responses. Error text was inside form labels, which broke the accessible field names. In the code review I found
login timing leaking which emails exist, 401/403 from another service turning into 500, and an N+1 query.
I fixed all of them and added tests.

---

## Resume section

**Project title**
WorkFlowPro — Enterprise Project & Task Management System (Spring Boot Microservices + React)

**Description (2 lines)**
A full-stack project and task management system with a Spring Cloud API Gateway, three Spring Boot
microservices each owning its own PostgreSQL database, JWT-based role access, and a React + TypeScript dashboard.

**Resume bullets**
- Built 3 Spring Boot microservices (Auth, Project, Task) behind a Spring Cloud Gateway, each with its own
  PostgreSQL database and Flyway migrations, communicating over REST with token relay (37 REST endpoints).
- Implemented stateless JWT authentication with Spring Security (BCrypt, 15-minute access tokens,
  single-use hashed refresh tokens with reuse detection) and role-based access for 4 roles, enforced
  with `@PreAuthorize` and service-layer rules.
- Designed project and task workflows with status transition rules, task assignment, overdue detection and an
  audit history, plus search, filtering, sorting and pagination using Spring Data JPA Specifications.
- Built a responsive React 19 + TypeScript frontend with protected routes, an Axios interceptor for
  automatic token refresh, and a dashboard of project and task statistics.
- Wrote 105 automated tests with JUnit, Mockito, MockMvc and Testcontainers (real PostgreSQL), and documented
  every API with Swagger/OpenAPI.

**Technical keywords**
Java 21, Spring Boot, Spring Cloud Gateway, Microservices, REST APIs, Spring Security, JWT, OAuth2 Resource
Server, BCrypt, RBAC, Spring Data JPA, Hibernate, PostgreSQL, Flyway, JUnit, Mockito, Testcontainers, MockMvc,
Swagger/OpenAPI, React, TypeScript, Vite, Axios, React Router, Docker, Docker Compose, Maven, Git.
