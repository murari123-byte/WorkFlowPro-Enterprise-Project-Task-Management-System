# Changelog

All notable changes to this project are recorded here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Code review fixes (2026-09-28)
- Security: login now checks the password against a dummy BCrypt hash when the email is unknown, so
  response time no longer reveals which emails have accounts.
- Security: `/actuator/health` shows component details (database, disk) only to authorized callers
  (`show-details: when-authorized`); anonymous callers see only `UP`/`DOWN`.
- Bug: a 401/403 from another service used to become a 500; `ServiceClients.call` now returns 401/403
  (`UnauthorizedException` added to common). Test: `ServiceClientsTest`.
- Performance: `hibernate.default_batch_fetch_size: 50` in auth, project and task services — user roles
  for a page of users load in one query instead of one per user (N+1).
- API: `GET /api/tasks?open=true` (only TODO / IN_PROGRESS / IN_REVIEW); the dashboard "My tasks" uses it.
- Frontend: editing an overdue task no longer fails validation for its unchanged past due date.

### Removed
- `GET /api/auth/me` — duplicate of `GET /api/users/me` (the frontend already used `/api/users/me`).
- `notification-service` module (it only had a ping endpoint; the final architecture has no
  notifications), its gateway route `/api/notifications/**`, and `NOTIFICATION_*` variables in
  `.env.example`, `docker-compose.yml` and the database init script. A database volume created earlier
  still contains an unused `notification_db`; `docker compose down -v` removes it.

### Added — Phase 3 / Step 5: React frontend (2026-09-28)
- `frontend/` created with the Vite `react-ts` template; added `axios` and `react-router-dom`.
- API layer (`src/api`): Axios client with Bearer token and single-flight refresh on 401, token storage
  (access token in memory, refresh token in localStorage), error helpers, one function per endpoint.
- `AuthContext` (login, register, logout, session restore on reload) and `RequireAuth` (protected and
  role-based routes).
- Pages: login, register, dashboard, projects, project details/form, tasks, task details (activity
  timeline)/form, profile, admin users, not found. Plain CSS, responsive (menu toggle, tables as cards).
- `frontend/.env.example` (`VITE_API_BASE_URL`), `docs/frontend.md`; setup guide now includes the frontend.

### Fixed (found by the end-to-end browser run)
- Form hint/error text was inside `<label>`, polluting the field's accessible name; moved outside.
- Task and project edit forms could be overwritten by a late duplicate load (StrictMode / slow network);
  late responses are now ignored.
- Lint: removed synchronous `setState` calls inside effects (AuthContext, task history).

### Removed
- Vite template files: `App.css`, `src/assets/*`, `public/icons.svg`, template `README.md`.

### Docs (2026-09-28)
- `docs/setup.md` rewritten as a tested fresh-machine guide (secrets, bootstrap admin, first API calls,
  Swagger for all services, migration check). README quick start updated. `docs/PROJECT_STATUS.md` added.

### Added — Phase 3 / Step 4: Task Service (2026-09-28)
- Flyway `V2__create_tasks_and_history.sql`: `tasks` (status/priority checks, generated `priority_rank`,
  `@Version`, indexes on (project_id, status), assignee_id, partial index on open tasks' due_date) and
  `task_history` (identity PK, FK cascade, index on (task_id, created_at DESC)).
- `/api/tasks`: create, search (project, text, status, priority, assignee, overdue, sort incl. priority rank,
  paging), get, update, `PATCH /{id}/status`, `PUT /{id}/assignee`, delete, `/{id}/history`, `/stats`, `/count`.
- Workflow TODO ⇄ IN_PROGRESS ⇄ IN_REVIEW → COMPLETED, cancel from open states, reopen/restore.
- `TaskPermissions`: leads (ADMIN, project manager, TEAM_LEAD member) create/edit/assign/any move;
  assignee may only move own task between TODO/IN_PROGRESS/IN_REVIEW; delete = manager or ADMIN;
  tasks only change while the project is PLANNING or ACTIVE; non-members get 404.
- Overdue = due date before today (UTC) and status still open; history written in the same transaction.
- common `Paging`: API sort name → entity property mapping.
- Tests: `TaskPermissionsTest`, `TaskControllerIntegrationTest`.

### Changed — shared user lookup (2026-09-28)
- `UserClient`, `RemoteUser`, `UserSummary` moved from project-service to `common.client`
  (`UserDirectoryClient`), so task-service can reuse them. project-service creates the bean in `ClientConfig`.

### Added — Phase 3 / Step 3: Project Service (2026-09-28)
- Flyway `V2__create_projects.sql`: `projects` (status check, date check, `@Version` column,
  unique `LOWER(name)`), `project_members` (composite PK, FK with cascade, index on `user_id`).
- Endpoints under `/api/projects`: create (ADMIN, PROJECT_MANAGER), list (search, status, manager filter,
  paging, whitelisted sort), get, update, `PATCH /{id}/status`, `PUT /{id}/manager` (ADMIN),
  `POST/DELETE /{id}/members`, delete, `/stats`, and `/accessible` + `/{id}/membership` for task-service.
- Workflow `PLANNING → ACTIVE ⇄ ON_HOLD → COMPLETED`, any open status → `CANCELLED`; closed projects read-only.
- Rules: members see a project (others get 404), only its manager or ADMIN edits it, manager must be
  PROJECT_MANAGER/ADMIN, manager cannot be removed, project with tasks cannot be deleted (409).
- `UserClient` (auth-service, batch lookups — one call per page, not per row), `TaskClient`;
  common `ServiceClients` (2 s connect / 5 s read timeout, token relay, network errors → 503).
- Swagger UI at `:9082/swagger-ui.html`. Env vars used: `JWT_SECRET`, `JWT_ISSUER`, `AUTH_SERVICE_URL`,
  `TASK_SERVICE_URL`, `SWAGGER_ENABLED`.
- Tests: `ProjectStatusTest`, `ProjectServiceTest` (Mockito), `ProjectControllerIntegrationTest`
  (MockMvc + Testcontainers + `@MockitoBean` clients + real signed test tokens).

### Added — Phase 3 / Step 2: Users API in auth-service (2026-09-28)
- `GET/PUT /api/users/me`, `PUT /api/users/me/password` (revokes all refresh tokens).
- `GET /api/users` search (ADMIN, PROJECT_MANAGER, TEAM_LEAD): text, role filter, paging, whitelisted sort;
  only ADMIN sees disabled users. `GET /api/users/{id}`, `GET /api/users/batch?ids=` for other services.
- `PUT /api/users/{id}/roles`, `PUT /api/users/{id}/status` (ADMIN). An admin cannot remove their own
  ADMIN role or disable themselves. Disabling revokes the user's refresh tokens.
- `AdminBootstrap`: first ADMIN from `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` (created once).
- common: `BadRequestException`, `Paging` helper (page/size limits, sort whitelist).
- Gateway: `/api/users/**` → auth-service.
- Tests: `UserServiceTest`, `UserControllerIntegrationTest`, `PagingTest`; auth tests now use
  `@ActiveProfiles("test")` with `src/test/resources/application-test.yml`.

### Fixed
- Spring Data JPA 4 rejects `null` in `Specification.allOf(...)`; unused filters now return
  `Specification.unrestricted()`.

### Added — Phase 3 / Step 1: shared `common` module (2026-09-28)
- New Maven module `common` (a library, not a service): `ApiException` + `ResourceNotFoundException`,
  `ForbiddenException`, `ConflictException`, `BusinessRuleException` (422), `ServiceUnavailableException`;
  `ErrorResponse`; `GlobalExceptionHandler` (now also handles bad path/query types → 400, optimistic-lock
  conflicts → 409, DB constraint violations → 409); `SecurityErrorHandler`; `JwtProperties` (secret, issuer)
  and `JwtVerificationConfig` (JwtDecoder + roles converter); `AuthenticatedUser`; `PageResponse`;
  `BearerTokenRelayInterceptor` for service-to-service calls.
- Tests: `AuthenticatedUserTest`, `PageResponseTest`.

### Changed
- auth-service uses `common` (scans `com.workflowpro.common`); its duplicate exception/security classes
  were removed. `JwtProperties` in auth-service split: TTLs are now `TokenProperties`. Env vars unchanged.

### Added — Phase 1 / Step 3: API Gateway routing + CORS (2026-09-28)
- Routes `/api/auth/**`, `/api/projects/**`, `/api/tasks/**`, `/api/notifications/**` to the four
  services; target URLs from `AUTH_SERVICE_URL`, `PROJECT_SERVICE_URL`, `TASK_SERVICE_URL`,
  `NOTIFICATION_SERVICE_URL`. Paths forwarded unchanged; `Authorization` header passed through.
- Global CORS at the gateway only, origins from `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`);
  `DedupeResponseHeader` default filter.
- `ServiceUnavailableHandler`: unreachable service → `503` JSON error instead of a generic `500`.
- Tests: `GatewayRoutingTest` (fake backend via JDK `HttpServer`: routing, headers, 404, CORS allow/deny),
  `GatewayServiceDownTest` (503). No new dependencies.

### Added — Phase 2 / Step 1: JWT authentication, registration, login (2026-09-28)
- auth-service endpoints: `POST /api/auth/register`, `/login`, `/refresh`, `/logout`, `GET /api/auth/me`.
- Spring Security (stateless) + OAuth2 Resource Server: HS256 JWT access tokens (15 min) with
  `sub`, `email`, `roles` claims; roles mapped to `ROLE_*` authorities; `@EnableMethodSecurity`.
- Refresh tokens: random, SHA-256 hashed in DB, single-use rotation, reuse detection revokes all sessions.
- BCrypt password hashing; case-insensitive unique emails; registration always assigns `EMPLOYEE`.
- Global exception handler + JSON 401/403 handler with one `ErrorResponse` format.
- Swagger UI (springdoc-openapi 3.0.3) at `/swagger-ui.html`, toggled by `SWAGGER_ENABLED`.
- Flyway: `V2__create_users_and_roles.sql` (seeds 4 roles), `V3__create_refresh_tokens.sql`.
- Env vars: `JWT_SECRET`, `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`, `JWT_REFRESH_TOKEN_TTL`, `SWAGGER_ENABLED`.
- Tests: `AuthServiceTest`, `RefreshTokenServiceTest` (Mockito), `AuthControllerIntegrationTest`
  (MockMvc + Testcontainers, full register → me → refresh → reuse → logout flow).
- Docs: `docs/authentication.md`, `docs/api.md`.

### Added — Phase 1 / Step 2: PostgreSQL + Flyway (2026-09-28)
- `docker-compose.yml`: PostgreSQL 16 (`postgres:16-alpine`) as container `workflowpro-postgres` on port 5440,
  data in the named volume `workflowpro-pgdata`.
- `docker/postgres/init/01-create-service-databases.sh`: on first start creates `auth_db`, `project_db`,
  `task_db`, `notification_db`, each owned by its own account; `CONNECT` revoked from `PUBLIC`.
- Services (not the gateway): `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`,
  `flyway-database-postgresql`, `postgresql` driver; datasource config from env vars; `ddl-auto: validate`.
- Flyway migration `V1__init.sql` (baseline, no tables) in each service.
- Tests: Testcontainers PostgreSQL via `@ServiceConnection`; new test checks Flyway is at version 1.
- `.env.example`: DB host/port, admin account, per-service DB name/user/password.

### Added — Phase 1 / Step 1: project structure and basic services (2026-09-28)
- Maven multi-module parent `pom.xml` (Spring Boot 4.0.8, Spring Cloud 2025.1.3, Java 21).
- Modules: `api-gateway`, `auth-service`, `project-service`, `task-service`, `notification-service`.
- Each service: main class, `application.yml` with port from env var, `/actuator/health`.
- Business services: `GET /api/<service>/ping` endpoint + unit test + context-load test.
- `api-gateway`: Spring Cloud Gateway (WebFlux) skeleton, no routes yet.
- `.env.example`, `.gitignore`, `README.md`, `docs/` folder.

### Changed
- Default ports moved from 8080–8084 to **9080–9084** because 8080–8084 were already used
  by other applications on the development machine.
