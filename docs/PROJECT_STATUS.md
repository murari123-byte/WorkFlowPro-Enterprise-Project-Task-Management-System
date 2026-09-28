# Project Status

Last updated: 2026-09-28

This page is the honest, current state of WorkFlowPro: what is built and tested, what is only
partly built, and what is still to do. Read this first.

## Summary

| Part | Status |
|---|---|
| API Gateway | ✅ Done and tested |
| Auth Service | ✅ Done and tested |
| Project Service | ✅ Done and tested |
| Task Service | ✅ Done and tested |
| `common` shared library | ✅ Done and tested |
| PostgreSQL + Flyway | ✅ Done (one database per service) |
| Backend tests | ✅ 105 tests, all passing (`mvn clean install`) |
| React frontend | ✅ Done — builds, lint clean, 21-step browser test passed |
| Documentation audit, interview prep, resume section | ❌ Not started |
| notification-service | ⚪ Empty scaffold (ping only). The final architecture does not use it — decide whether to delete it |

## What is done

### Infrastructure
- Maven multi-module project: `common`, `api-gateway`, `auth-service`, `project-service`,
  `task-service`, `notification-service`.
- Java 21, Spring Boot 4.0.8, Spring Cloud 2025.1.3, springdoc-openapi 3.0.3.
- PostgreSQL 16 in Docker (`docker-compose.yml`, port **5440**). On first start the init script
  creates `auth_db`, `project_db`, `task_db`, `notification_db`, each with its own account.
  An account can only connect to its own database.
- Services run on ports **9080–9084** (8080–8084 were already taken on the dev machine).
- All secrets come from `.env` (see `.env.example`). Nothing secret is in the code.

### API Gateway (port 9080)
- Routes: `/api/auth/**` and `/api/users/**` → auth-service, `/api/projects/**` → project-service,
  `/api/tasks/**` → task-service, `/api/notifications/**` → notification-service.
- CORS only at the gateway (`CORS_ALLOWED_ORIGINS`, default `http://localhost:5173`).
- A service that is down returns `503` JSON instead of `500`.

### Auth Service (port 9081, `auth_db`)
- Register, login, refresh, logout, current user.
- JWT access tokens (HS256, 15 min) with `sub`, `email`, `roles` claims.
- Refresh tokens: random, stored only as a SHA-256 hash, single use (rotation). Reusing an old one
  signs the user out everywhere.
- BCrypt password hashing. New users always get `EMPLOYEE`; roles sent by the client are ignored.
- Users API: profile, change password, user search (paged, safe sorting), batch lookup for other
  services, ADMIN role and enable/disable management (an admin cannot lock themselves out).
- First ADMIN is created on startup from `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`.
- Flyway: `V1__init`, `V2__create_users_and_roles` (seeds 4 roles), `V3__create_refresh_tokens`.

### Project Service (port 9082, `project_db`)
- Create, list (search, status, manager filter, sort, paging), view, update, delete.
- Members: add and remove. Manager: ADMIN can reassign. The manager is always a member.
- Status workflow: `PLANNING → ACTIVE ⇄ ON_HOLD → COMPLETED`; any open status → `CANCELLED`.
  Completed and cancelled projects are read-only.
- Rules: ADMIN and PROJECT_MANAGER create projects; members can view (others get 404);
  only the manager or an ADMIN can change a project; a project with tasks cannot be deleted.
- Dashboard numbers: `GET /api/projects/stats`.
- Internal endpoints for task-service: `/api/projects/accessible`, `/api/projects/{id}/membership`.
- Flyway: `V1__init`, `V2__create_projects` (`projects`, `project_members`).

### Task Service (port 9083, `task_db`)
- Create, search (project, text, status, priority, assignee, overdue, sort, paging), view,
  update, delete, assign/unassign, status change.
- Status workflow: `TODO ⇄ IN_PROGRESS ⇄ IN_REVIEW → COMPLETED`, cancel, reopen, restore.
- Rules: project manager, TEAM_LEAD members and ADMIN create/edit/assign; the assignee can only move
  their own task between TODO / IN_PROGRESS / IN_REVIEW; only manager or ADMIN delete;
  assignee must be a project member; tasks only change while the project is PLANNING or ACTIVE.
- Overdue detection: due date before today (UTC) and task still open.
- Task history (activity timeline): created, updated, assigned, status/priority/due date changed.
- Dashboard numbers: `GET /api/tasks/stats` (total, pending, completed, overdue, my open tasks,
  by status, by priority).
- Flyway: `V1__init`, `V2__create_tasks_and_history` (`tasks`, `task_history`).

### Shared `common` module
- One error format (`ErrorResponse`) and one `GlobalExceptionHandler` for every service.
- JWT verification config, JSON 401/403 handler, `AuthenticatedUser`.
- `PageResponse` (paged JSON shape) and `Paging` (page size limit, sort whitelist).
- `ServiceClients` (timeouts, caller's token passed on, network errors → 503) and `UserDirectoryClient`.

### Tests (all passing)
| Module | Tests |
|---|---|
| common | 8 |
| api-gateway | 12 |
| auth-service | 33 |
| project-service | 32 |
| task-service | 17 |
| notification-service | 3 |

Unit tests use JUnit 5 + Mockito. Integration tests use MockMvc with a real PostgreSQL started by
Testcontainers, and real signed JWTs.

## Frontend (`frontend/`) — done

Vite + React 19 + TypeScript + Axios + React Router 7, plain CSS. Full description: [frontend.md](frontend.md).

- Pages: login, register, dashboard (8 stat cards, 3 bar charts, my tasks), projects list, project
  details (status, members, manager, tasks), project form, tasks list, task details (status, assign,
  activity timeline), task form, profile (name, password), admin users (roles, enable/disable), not found.
- Axios client adds the token and refreshes it once on 401; the session survives a page reload.
- Protected and role-based routes; buttons follow the permissions the API returns.
- Search, filters, sorting and pagination on projects, tasks and users; debounced search boxes.
- Loading, error (with retry), empty states; form validation matching the backend rules.
- Responsive: collapsible menu, tables become cards on phones.
- Verified: `npm run build` and `npm run lint` pass; a Playwright browser run of 21 steps against the real
  backend passed with no console errors. Two bugs found by it were fixed (see frontend.md).

## What remains to finish the project

1. ~~Frontend pages~~ — done.
2. ~~Frontend wiring and styles~~ — done.
3. ~~End-to-end check~~ — done (21-step browser run, see frontend.md).
4. **Final code review:** bugs, security, validation, duplicate code, indexes, API consistency.
5. **Documentation audit:** create `docs/microservices.md`, `docs/database.md`,
   `docs/testing.md`, `docs/interview-preparation.md` (with 30–40 Q&A and a resume section);
   rename `docs/database-migrations.md` to `docs/migrations.md`; bring README, `docs/api.md`,
   `docs/authentication.md` and `docs/architecture.md` up to date with the project, task and users APIs.
6. **Decide on notification-service:** delete it (the final architecture has no notifications) or keep it.
7. **Not built (requested earlier, later marked out of scope):** Kanban board, task comments,
   organizations/teams.

## How to run it

```bash
git clone https://github.com/murari123-byte/WorkFlowPro-Enterprise-Project-Task-Management-System.git WorkFlowPro
cd WorkFlowPro
cp .env.example .env          # replace every change-me value (openssl rand -base64 48 for JWT_SECRET)
docker compose up -d          # PostgreSQL on port 5440
mvn clean install             # builds everything and runs all tests (Docker must be running)

# in one terminal per service:
set -a; source .env; set +a
java -jar api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar
java -jar auth-service/target/auth-service-0.1.0-SNAPSHOT.jar
java -jar project-service/target/project-service-0.1.0-SNAPSHOT.jar
java -jar task-service/target/task-service-0.1.0-SNAPSHOT.jar
```

Swagger UI: `http://localhost:9081/swagger-ui.html`, `:9082/swagger-ui.html`, `:9083/swagger-ui.html`.

Frontend (new terminal): `cd frontend && npm install && npm run dev` → http://localhost:5173.
Sign in as the bootstrap admin from your `.env`. Full guide: [setup.md](setup.md).
