# Microservices

Four runnable applications plus one shared library. Big picture: [architecture.md](architecture.md).

| Module | Type | Port | Database | Main packages |
|---|---|---|---|---|
| `api-gateway` | Spring Cloud Gateway (WebFlux) | 9080 | none | `exception` |
| `auth-service` | Spring Boot MVC | 9081 | `auth_db` | `controller`, `service`, `repository`, `entity`, `dto`, `config`, `exception` |
| `project-service` | Spring Boot MVC | 9082 | `project_db` | `controller`, `service`, `repository`, `entity`, `dto`, `client`, `config` |
| `task-service` | Spring Boot MVC | 9083 | `task_db` | `controller`, `service`, `repository`, `entity`, `dto`, `client`, `config` |
| `common` | Java library (jar) | — | — | `exception`, `security`, `web`, `client` |

Base package of every module: `com.workflowpro.<module>` (gateway: `com.workflowpro.gateway`).

---

## API Gateway (`api-gateway`)

**Responsibility:** the single entry point for the browser.

- **Routing** (`spring.cloud.gateway.server.webflux.routes` in `application.yml`), paths forwarded unchanged:

  | Path | Target |
  |---|---|
  | `/api/auth/**`, `/api/users/**` | `AUTH_SERVICE_URL` (default `http://localhost:9081`) |
  | `/api/projects/**` | `PROJECT_SERVICE_URL` (default `http://localhost:9082`) |
  | `/api/tasks/**` | `TASK_SERVICE_URL` (default `http://localhost:9083`) |

- **CORS** only here (`globalcors`): origins from `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`),
  methods GET/POST/PUT/PATCH/DELETE/OPTIONS, headers `Authorization`, `Content-Type`, preflight cached 1 hour.
  A `DedupeResponseHeader` default filter keeps only one copy of CORS headers.
- **`ServiceUnavailableHandler`**: when a service cannot be reached, returns `503` JSON instead of `500`.
- Unknown path → `404`.
- It does **not** verify JWTs — each service does that itself.

Key classes: `ApiGatewayApplication`, `exception/ServiceUnavailableHandler`.
Tests: `GatewayRoutingTest` (routing, header forwarding, 404, CORS allowed/denied) with a fake backend
(JDK `HttpServer`), `GatewayServiceDownTest` (503), `ApiGatewayApplicationTests`.

---

## Auth Service (`auth-service`)

**Responsibility:** who you are and what role you have. Owns users, roles and refresh tokens.

| Feature | Where |
|---|---|
| Register (always EMPLOYEE), login, refresh, logout | `AuthController`, `AuthService` |
| Access token creation (HS256 JWT) | `JwtService` |
| Refresh tokens: create, rotate, reuse detection, revoke | `RefreshTokenService` |
| Profile, change password | `UserController` `/api/users/me*`, `UserService` |
| User search (text, role, paging, sort whitelist) | `UserController` `GET /api/users`, `UserSpecifications` |
| Batch/single lookup used by other services | `GET /api/users/batch`, `GET /api/users/{id}` |
| Admin: change roles, enable/disable | `PUT /api/users/{id}/roles`, `PUT /api/users/{id}/status` |
| First ADMIN on startup | `AdminBootstrap` (`BOOTSTRAP_ADMIN_*`) |
| Security config (public endpoints, BCrypt, JwtEncoder) | `config/SecurityConfig` |
| Token lifetimes | `config/TokenProperties` (`JWT_ACCESS_TOKEN_TTL`, `JWT_REFRESH_TOKEN_TTL`) |

Entities: `User` (↔ `Role` many-to-many via `user_roles`), `Role`, `RoleName` enum, `RefreshToken`.
Flyway: V1–V3 ([migrations.md](migrations.md)). Security details: [authentication.md](authentication.md).

Public endpoints: `POST /api/auth/register|login|refresh|logout`, `GET /api/auth/ping`, health/info, Swagger.
Everything else needs a valid access token.

Tests: `AuthServiceTest`, `RefreshTokenServiceTest`, `UserServiceTest` (Mockito);
`AuthControllerIntegrationTest`, `UserControllerIntegrationTest`, `AuthServiceApplicationTests` (Testcontainers).

---

## Project Service (`project-service`)

**Responsibility:** projects, their members and manager, and the project workflow. Owns `project_db`.

| Feature | Where |
|---|---|
| Create / list / get / update / delete | `ProjectController`, `ProjectService` |
| Search, status and manager filter, sort, paging | `ProjectSpecifications`, `Paging` |
| Members add/remove, change manager (ADMIN) | `ProjectService.addMember/removeMember/changeManager` |
| Status workflow | `entity/ProjectStatus.allowedNext()` |
| Dashboard numbers | `GET /api/projects/stats` (GROUP BY queries in `ProjectRepository`) |
| For task-service | `GET /api/projects/{id}/membership`, `GET /api/projects/accessible` |
| Calls auth-service | `UserDirectoryClient` (from common) — manager/member checks, names |
| Calls task-service | `client/TaskClient` — task count before delete |

**Project workflow**
```
PLANNING ──► ACTIVE ──► COMPLETED
   │          │  ▲
   │          ▼  │
   │        ON_HOLD
   └──────────┴──────► CANCELLED        (COMPLETED and CANCELLED are final and read-only)
```

**Rules**
- Create: ADMIN or PROJECT_MANAGER. A PROJECT_MANAGER always manages their own project; only ADMIN may name another manager.
- The manager must be an enabled user with role PROJECT_MANAGER or ADMIN. The manager is always a member.
- View: members only (ADMIN sees all). Non-members get **404**.
- Edit details, change status, add/remove members, delete: the project's manager or ADMIN.
- Change manager: ADMIN only. The manager cannot be removed from the members.
- Completed/cancelled projects cannot be edited. Names are unique (case-insensitive).
- A project with tasks cannot be deleted (`409`) — delete the tasks first or cancel the project.

Entities: `Project` (members as `@ElementCollection` of `ProjectMember` → table `project_members`), `ProjectStatus`.
Tests: `ProjectStatusTest`, `ProjectServiceTest` (Mockito), `ProjectControllerIntegrationTest` (Testcontainers,
mocked clients, real signed JWTs).

---

## Task Service (`task-service`)

**Responsibility:** tasks inside projects, assignment, the task workflow, activity history and task numbers.
Owns `task_db`.

| Feature | Where |
|---|---|
| Create / get / update / delete | `TaskController`, `TaskService` |
| Assign / unassign | `PUT /api/tasks/{id}/assignee` |
| Status change | `PATCH /api/tasks/{id}/status` |
| Who may do what | `service/TaskPermissions` (plain Java, unit-tested) |
| Search: project, text, status, priority, assignee, open, overdue, sort, paging | `TaskSpecifications`, `Paging` (priority sorts by `priority_rank`) |
| Overdue detection | `Task.isOverdue(today)` and `TaskSpecifications.overdue` (UTC date from a `Clock` bean) |
| Activity history | `TaskHistory` entity, written in the same transaction as each change |
| Dashboard numbers | `GET /api/tasks/stats` (GROUP BY queries) |
| Task count for project delete | `GET /api/tasks/count?projectId=` |
| Calls project-service | `client/ProjectClient` — membership and accessible projects |
| Calls auth-service | `UserDirectoryClient` — assignee checks and names |

**Task workflow**
```
TODO ⇄ IN_PROGRESS ⇄ IN_REVIEW ──► COMPLETED ──► IN_PROGRESS (reopen)
  └────────┴────────────┴────────► CANCELLED ──► TODO        (restore)
```

**Rules** (a "lead" = ADMIN, the project's manager, or a TEAM_LEAD who is a member of the project)
- View: project members (non-members get 404).
- Create, edit, assign: leads. The assignee must be a member of the project and an enabled user.
- Status: leads may make any allowed move. The **assignee** may move their own task between TODO,
  IN_PROGRESS and IN_REVIEW, but not complete, cancel or reopen it.
- Delete: the project manager or ADMIN.
- Tasks change only while the project is PLANNING or ACTIVE. Completed/cancelled tasks cannot be reassigned.
- New due dates cannot be in the past. **Overdue** = due date before today (UTC) and status still open.
- Default priority: MEDIUM.

History actions: `CREATED`, `UPDATED` (title/description), `ASSIGNED`, `STATUS_CHANGED`,
`PRIORITY_CHANGED`, `DUE_DATE_CHANGED`. Values are stored as display text (e.g. the assignee's name at that time).

Tests: `TaskPermissionsTest`, `TaskControllerIntegrationTest` (Testcontainers, mocked clients that answer
depending on the calling user), `TaskServiceApplicationTests`.

---

## common (shared library)

See [architecture.md → Shared common module](architecture.md#shared-common-module).
Tests: `AuthenticatedUserTest`, `PageResponseTest`, `PagingTest`, `ServiceClientsTest`.

---

## Removed during development

- **notification-service** — created in Phase 1 as an empty service (ping endpoint only). Removed during
  the final review because the final architecture has no notifications. Its gateway route, `NOTIFICATION_*`
  env vars and database creation were removed too. See CHANGELOG.
