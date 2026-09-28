# WorkFlowPro — Enterprise Project & Task Management System

A full-stack project and task management system built as **Java Spring Boot microservices** with a
**React + TypeScript** frontend. Teams create projects, add members, create and assign tasks, move them
through a review workflow, and follow progress on a dashboard — with JWT login and role-based access.

> Status: complete and verified end to end — see [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md).

## Features

- **Accounts & security:** register, login, logout, profile, change password. JWT access tokens (15 min) +
  single-use refresh tokens (7 days) with reuse detection. BCrypt passwords.
- **Roles (RBAC):** ADMIN, PROJECT_MANAGER, TEAM_LEAD, EMPLOYEE. Admin user management (roles, enable/disable).
  First admin created from environment variables.
- **Projects:** create, edit, delete, list; members; project manager; start/end dates;
  workflow PLANNING → ACTIVE ⇄ ON_HOLD → COMPLETED / CANCELLED.
- **Tasks:** create, edit, delete; assign to a project member; priority (LOW–URGENT); due date;
  workflow TODO → IN_PROGRESS → IN_REVIEW → COMPLETED (+ cancel, reopen); **overdue detection**;
  **activity history** of every change.
- **Search, filter, sort, pagination** for projects, tasks and users.
- **Dashboard:** total/active/completed projects, total/pending/completed/overdue tasks, my open tasks,
  tasks by status and priority, projects by status.
- **Swagger UI** for every service. **105 automated backend tests.**

## Architecture

```
React + TypeScript (Vite, :5173)
            │  JSON + Authorization: Bearer <JWT>
            ▼
     API Gateway (:9080)          Spring Cloud Gateway — routing, CORS
   ┌────────┼──────────┐
   ▼        ▼          ▼
 Auth     Project     Task        Spring Boot services (:9081, :9082, :9083)
 Service  Service     Service     each verifies the JWT itself; they call each other over REST
   │        │          │
   ▼        ▼          ▼
 auth_db  project_db  task_db     PostgreSQL 16 (Docker, :5440) — one database per service, Flyway
```

| Service | Port | Owns | Responsibility |
|---|---|---|---|
| api-gateway | 9080 | — | single entry point, routes `/api/**`, CORS, 503 when a service is down |
| auth-service | 9081 | `auth_db` | users, roles, login, JWT + refresh tokens, user management |
| project-service | 9082 | `project_db` | projects, members, manager, project workflow, project stats |
| task-service | 9083 | `task_db` | tasks, assignment, task workflow, history, search, overdue, task stats |
| common | — | — | shared library: error format, exception handler, JWT verification, paging, REST clients |

Details: [architecture](docs/architecture.md) (with a full request flow) · [microservices](docs/microservices.md).

## Technology stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.0.8, Spring MVC, Spring Cloud Gateway 5 (Spring Cloud 2025.1.3) |
| Security | Spring Security 7, OAuth2 Resource Server (JWT, HS256), BCrypt |
| Data | Spring Data JPA, Hibernate 7, PostgreSQL 16, Flyway 11 |
| API docs | springdoc-openapi 3 (Swagger UI) |
| Tests | JUnit 6 (Jupiter), Mockito, AssertJ, MockMvc, Testcontainers 2 |
| Frontend | React 19, TypeScript 6, Vite 8, React Router 7, Axios, plain CSS |
| Build / run | Maven (multi-module), npm, Docker Compose |

Why each one: [docs/dependencies.md](docs/dependencies.md).

## Folder structure

```
WorkFlowPro/
├── pom.xml                     parent POM: versions + modules
├── common/                     shared library (no business logic)
├── api-gateway/                Spring Cloud Gateway
├── auth-service/               users, roles, JWT
├── project-service/            projects, members
├── task-service/               tasks, history
│   └── src/main/java/com/workflowpro/<service>/
│       controller/ service/ repository/ entity/ dto/ client/ config/
│   └── src/main/resources/
│       application.yml, db/migration/V*.sql
├── frontend/                   React app (src/api, auth, components, hooks, pages)
├── docker-compose.yml          PostgreSQL
├── docker/postgres/init/       creates the 3 databases + logins (first start)
├── docs/                       all documentation
├── .env.example                every environment variable (no real secrets)
└── CHANGELOG.md
```

## Database design

Three databases on one PostgreSQL server; each service can only connect to its own.

| Database | Tables | Relationships |
|---|---|---|
| `auth_db` | `users`, `roles`, `user_roles`, `refresh_tokens` | users ↔ roles many-to-many; user → refresh tokens one-to-many |
| `project_db` | `projects`, `project_members` | project → members one-to-many (composite PK) |
| `task_db` | `tasks`, `task_history` | task → history one-to-many |

Ids from another service (e.g. `tasks.project_id`, `projects.manager_id`) have **no foreign key** — the
owning service is asked over REST. UUID keys, `CHECK` constraints for enums, `@Version` optimistic locking,
indexes for the common queries. Details: [docs/database.md](docs/database.md).

## Authentication flow

1. `POST /api/auth/login` → auth-service checks the BCrypt password → returns an **access token** (JWT with
   user id, email, roles; 15 min) and a **refresh token** (random, stored hashed, 7 days).
2. React sends `Authorization: Bearer <access token>` on every call. **Every service verifies the token
   itself** (signature, expiry, issuer) and reads roles only from it.
3. On `401` React calls `/api/auth/refresh` once, gets a new pair (the old refresh token is dead) and retries.
4. Logout revokes the refresh token.

Roles and the full permission table: [docs/authentication.md](docs/authentication.md).

## API communication

- The browser only calls the **gateway** (`http://localhost:9080`). Routes: `/api/auth/**` and `/api/users/**`
  → auth, `/api/projects/**` → project, `/api/tasks/**` → task.
- Services call each other **directly over REST** with the caller's own token (token relay):
  project → auth (user checks), project → task (task count before delete),
  task → project (membership), task → auth (assignee names).
- One JSON error format everywhere: `{ timestamp, status, error, message, path, fieldErrors? }`.
- All 37 endpoints with examples: [docs/api.md](docs/api.md).

## Setup and run

Full guide with every command: **[docs/setup.md](docs/setup.md)**.

**Prerequisites:** Java 21, Maven 3.9+, Docker + Compose (without sudo), Node 20+, openssl.

```bash
# 1. Clone
git clone https://github.com/murari123-byte/WorkFlowPro-Enterprise-Project-Task-Management-System.git WorkFlowPro
cd WorkFlowPro

# 2. Environment variables: copy, then replace every change-me value
cp .env.example .env
#    JWT_SECRET:  openssl rand -base64 48      passwords: openssl rand -hex 16
#    BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD = your first admin login

# 3. PostgreSQL (port 5440) — creates auth_db, project_db, task_db on first start
docker compose up -d

# 4. Build + run all 105 tests (tests use their own throwaway PostgreSQL)
mvn clean install

# 5. Start each service in its own terminal (Flyway migrations run automatically on startup)
set -a; source .env; set +a
java -jar auth-service/target/auth-service-0.1.0-SNAPSHOT.jar         # 9081
java -jar project-service/target/project-service-0.1.0-SNAPSHOT.jar   # 9082
java -jar task-service/target/task-service-0.1.0-SNAPSHOT.jar         # 9083
java -jar api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar           # 9080

# 6. Frontend (new terminal)
cd frontend && npm install && npm run dev                             # http://localhost:5173
```

Open **http://localhost:5173** and sign in with `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`.

## Environment variables

All 30 are listed with defaults in [.env.example](.env.example) and explained in
[docs/configuration.md](docs/configuration.md). The important ones:

| Variable | Meaning |
|---|---|
| `JWT_SECRET` | signs/verifies tokens — **same value for all services**, min 32 characters |
| `AUTH_DB_PASSWORD`, `PROJECT_DB_PASSWORD`, `TASK_DB_PASSWORD`, `POSTGRES_ADMIN_PASSWORD` | database passwords (no defaults) |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | first ADMIN, created once at startup |
| `CORS_ALLOWED_ORIGINS` | browser origins allowed by the gateway (default `http://localhost:5173`) |
| `*_SERVICE_URL`, `*_SERVICE_PORT`, `DB_HOST`, `DB_PORT` | where things run |

## Flyway migrations

Each service migrates its own database at startup — nothing to run by hand, and the database is never
changed manually. Current versions: **auth_db V3, project_db V2, task_db V2** (7 files).
List, verification commands and how to add one: [docs/migrations.md](docs/migrations.md).

## Testing

```bash
mvn clean install                           # 105 backend tests (Docker must be running)
cd frontend && npm run build && npm run lint
```
Unit tests (JUnit + Mockito) for business rules; integration tests (MockMvc + real PostgreSQL via
Testcontainers + real signed JWTs) for every API's security and workflow; a 21-step browser run was done
during development. Details: [docs/testing.md](docs/testing.md).

## Swagger

| Service | URL |
|---|---|
| auth-service | http://localhost:9081/swagger-ui.html |
| project-service | http://localhost:9082/swagger-ui.html |
| task-service | http://localhost:9083/swagger-ui.html |

Log in with `POST /api/auth/login`, click **Authorize**, paste the `accessToken`.

## Documentation

| File | Contents |
|---|---|
| [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) | what is done, verification checklist, what remains |
| [docs/setup.md](docs/setup.md) | step-by-step local setup, first login, tour |
| [docs/architecture.md](docs/architecture.md) | design decisions, request flow, layers, limitations |
| [docs/microservices.md](docs/microservices.md) | each service: features, rules, classes, tests |
| [docs/database.md](docs/database.md) | tables, columns, constraints, indexes, relationships |
| [docs/migrations.md](docs/migrations.md) | every Flyway migration, order, verification |
| [docs/authentication.md](docs/authentication.md) | JWT, refresh tokens, roles, permission matrix |
| [docs/api.md](docs/api.md) | every endpoint with request/response examples and errors |
| [docs/frontend.md](docs/frontend.md) | React structure, pages, API calls, tokens, states |
| [docs/configuration.md](docs/configuration.md) | every env var and config file |
| [docs/dependencies.md](docs/dependencies.md) | every dependency and why |
| [docs/testing.md](docs/testing.md) | test classes and what they cover |
| [docs/troubleshooting.md](docs/troubleshooting.md) | common problems and fixes |
| [docs/interview-preparation.md](docs/interview-preparation.md) | explanations, Q&A, resume section |
| [CHANGELOG.md](CHANGELOG.md) | development history |

## Known limitations

Project delete is not atomic across services; logout does not cancel the current access token (≤ 15 min);
one shared HS256 secret; refresh token in `localStorage`; no frontend unit tests. Full list and future
improvements: [docs/architecture.md](docs/architecture.md#known-limitations).
