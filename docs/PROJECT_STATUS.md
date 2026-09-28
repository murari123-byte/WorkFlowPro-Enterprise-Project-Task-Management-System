# Project Status

Last updated: 2026-09-28 (after the final code review and documentation audit).

## Summary

| Part | Status |
|---|---|
| API Gateway | ✅ done, tested |
| Auth Service | ✅ done, tested |
| Project Service | ✅ done, tested |
| Task Service | ✅ done, tested |
| `common` shared library | ✅ done, tested |
| PostgreSQL + Flyway (3 databases, 7 migrations) | ✅ done, verified |
| React frontend | ✅ done, build + lint clean, 21-step browser run passed |
| Backend tests | ✅ 105 passing |
| Documentation | ✅ audited against the code (see below) |

## Final verification checklist

Every item was checked by actually running it on 2026-09-28, after the code review fixes.

- [x] **Frontend works** — `npm run build` and `npm run lint` clean (also in a fresh clone); 21-step Playwright browser run passed with no console errors
- [x] **API Gateway works** — routes to all 3 services, CORS allow/deny, 503 when a service is down, 404 for removed routes (tests + live curl)
- [x] **Auth Service works** — register, login, refresh, logout, profile, password change, user admin (tests + browser run)
- [x] **Project Service works** — create, edit, status, members, manager, delete guard, stats (tests + browser run)
- [x] **Task Service works** — create, edit, assign, status workflow, history, delete, stats (tests + browser run)
- [x] **PostgreSQL works** — container healthy, 3 databases, each login can only open its own database
- [x] **Flyway migrations work** — auth_db V1–V3, project_db V1–V2, task_db V1–V2, all `success = t` (documented command run as written); version checked in tests on fresh databases
- [x] **JWT/RBAC works** — tampered/missing token 401, wrong role 403, non-member 404, client-sent roles ignored, assignee cannot complete (tests + browser run)
- [x] **Projects work** — see Project Service
- [x] **Tasks work** — see Task Service
- [x] **Search/filter/pagination work** — projects, tasks (incl. priority sort by rank, `open`, `overdue`), users; bad sort → 400 (tests + browser run)
- [x] **Dashboard works** — stat cards and charts load, numbers change after actions (browser run; stats endpoints tested)
- [x] **Tests pass** — `mvn clean install`: 105 tests, 0 failures
- [x] **Swagger works** — UI and `/v3/api-docs` return 200 on 9081, 9082, 9083
- [x] **Documentation complete** — every file below compared with the code; links checked; setup and API walk-through commands run as written

Not verified automatically: editing an **overdue** task in the UI (needs a task whose due date has passed —
checked by code review and the TypeScript build only). See [testing.md](testing.md#not-covered-by-automated-tests).

## What was built

| Area | Contents |
|---|---|
| Infrastructure | Maven multi-module; Docker Compose PostgreSQL 16 on 5440; init script for 3 databases; `.env` for all secrets |
| Gateway | routes, CORS, 503 handler |
| Auth | JWT (HS256) + single-use hashed refresh tokens with reuse detection, BCrypt, 4 roles, profile, user admin, bootstrap admin |
| Projects | CRUD, members, manager, workflow, read-only when closed, delete guard, stats, internal endpoints for task-service |
| Tasks | CRUD, assignment, workflow with assignee/lead rules, overdue, history, search/filter/sort/paging, stats |
| common | error format + handler, JWT verification, `AuthenticatedUser`, `PageResponse`, `Paging`, `ServiceClients`, `UserDirectoryClient` |
| Frontend | 12 pages, Axios client with token refresh, protected/role routes, states, validation, responsive CSS |
| Docs | README + 14 files in `docs/` |

Details per service: [microservices.md](microservices.md). History: [CHANGELOG.md](../CHANGELOG.md).

## Documentation audit (2026-09-28)

Method: listed every controller mapping, env var, migration, dependency and test class from the code and
compared them with the docs; ran the setup, migration-check and API walk-through commands exactly as written;
checked every relative link; built a fresh clone with only the README steps.

| File | Result |
|---|---|
| README.md | rewritten: overview, features, architecture, services, stack, folder structure, DB design, auth flow, API communication, setup, env vars, Flyway, running, testing, Swagger, docs index, limitations |
| CHANGELOG.md | complete history; review fixes and removals recorded |
| .env.example | all 30 variables; each is used by the code, and every variable the code uses is listed |
| docs/setup.md | updated for 3 databases, new health check, migrations loop; commands re-run |
| docs/architecture.md | rewritten: decisions and why, request flow, layers, ports, limitations, future improvements |
| docs/microservices.md | **new**: each service's features, rules, classes, tests; removed module recorded |
| docs/database.md | **new**: every table, column, constraint, index and relationship |
| docs/migrations.md | renamed from `database-migrations.md`; every migration, order, run/verify/add |
| docs/authentication.md | rewritten: flow, JWT, refresh tokens, passwords, roles, full permission matrix, security checklist |
| docs/api.md | rewritten: all 37 endpoints with bodies, responses, errors, paging/sort fields, walk-through |
| docs/frontend.md | updated: open-tasks filter, review fixes |
| docs/testing.md | **new**: every test class and what it covers, tools, gaps |
| docs/troubleshooting.md | updated: removed routes, 404/409/422 meanings, frontend problems, snap Docker |
| docs/configuration.md | rewritten: every env var and config file, yml explained, fixed values |
| docs/dependencies.md | rewritten with versions and "why" |
| docs/interview-preparation.md | **new**: 2- and 5-minute explanations, 15 topic explanations, 36 Q&A, resume section |

Mismatches found and fixed during the audit: docs still described `notification-service`, `/api/auth/me`,
"JUnit 5" (the build uses JUnit Jupiter 6), 40 tests (now 105), health details in the public output,
JWT variables as "auth-service only", and `allowedStatuses` in alphabetical order (it is workflow order).

## What remains (optional, not required for the project to be complete)

- Future improvements listed in [architecture.md](architecture.md#future-improvements) (RS256, HttpOnly
  refresh cookie, token cleanup job, Docker images for all services, circuit breaker).
- Commit the Playwright browser script as `frontend/e2e` and add React component tests.
- Features requested earlier but left out by the final scope: Kanban board with drag and drop, task
  comments, organizations/teams, notifications.

## How to run it

See [setup.md](setup.md) (full) or the README quick start. In short:
```bash
cp .env.example .env            # fill in secrets
docker compose up -d
mvn clean install
set -a; source .env; set +a     # in each service terminal, then start the 4 jars (see setup.md step 6)
cd frontend && npm install && npm run dev    # http://localhost:5173
```
