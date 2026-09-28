# Changelog

All notable changes to this project are recorded here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
