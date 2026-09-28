# Changelog

All notable changes to this project are recorded here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
