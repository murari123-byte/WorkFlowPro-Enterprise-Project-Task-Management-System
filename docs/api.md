# REST API Reference

**Base URL: `http://localhost:9080`** (API Gateway). Service ports 9081–9083 are for debugging only.
All bodies are JSON. Protected endpoints need `Authorization: Bearer <accessToken>`.
Who may call what: [authentication.md → Permissions](authentication.md#permissions-rbac-matrix).

37 endpoints: auth 5, users 8, projects 13, tasks 11. Lists are always sorted in workflow order
(e.g. `allowedStatuses` for an ACTIVE project is `ON_HOLD, COMPLETED, CANCELLED`).

## Swagger / OpenAPI

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| auth-service | http://localhost:9081/swagger-ui.html | http://localhost:9081/v3/api-docs |
| project-service | http://localhost:9082/swagger-ui.html | http://localhost:9082/v3/api-docs |
| task-service | http://localhost:9083/swagger-ui.html | http://localhost:9083/v3/api-docs |

Log in (`POST /api/auth/login` in the auth page), copy `accessToken`, click **Authorize** on any page and
paste it (without `Bearer `). Every endpoint has a summary (`@Operation`). Turn Swagger off with
`SWAGGER_ENABLED=false`. Library: springdoc-openapi 3.0.3.

## Error format

Every error from every service has this shape (`common` `ErrorResponse`):

```json
{
  "timestamp": "2026-09-28T11:35:25.634Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/auth/register",
  "fieldErrors": { "email": "must be a well-formed email address", "password": "size must be between 8 and 72" }
}
```
`fieldErrors` only appears for validation errors.

| Status | When |
|---|---|
| 400 | validation failed, bad JSON, bad UUID/enum in the URL, unknown sort field, page/size out of range |
| 401 | no token, expired or invalid token; wrong login; invalid refresh token |
| 403 | logged in but not allowed (role or business rule); account disabled at login |
| 404 | not found — **also** when you are not a member of the project (so you cannot tell it exists) |
| 409 | duplicate (email, project name, member), project still has tasks, record changed by someone else (`@Version`), DB constraint |
| 422 | valid input that breaks a business rule (invalid status move, assignee not a member, project closed, ...) |
| 500 | unexpected error — generic message only, details in the server log |
| 503 | a needed service is down (from the gateway or from a service-to-service call) |

## Paging, sorting, search

Every list returns the same shape (`PageResponse`):
```json
{ "content": [ ... ], "page": 0, "size": 10, "totalElements": 42, "totalPages": 5 }
```

| Param | Meaning |
|---|---|
| `page` | 0-based page number (≥ 0) |
| `size` | 1–100 (default 10 for projects/tasks, 20 for users and history) |
| `sort` | `field` or `field,asc` / `field,desc`. **Only the fields listed per endpoint** — anything else → 400 |
| `search` | case-insensitive "contains" match (see each endpoint for which fields) |

---

## Auth — `/api/auth` (auth-service)

### `POST /api/auth/register` — public
Creates an **EMPLOYEE** account and logs it in. Any `roles` field in the body is ignored.
```json
{ "email": "jane@example.com", "password": "Secret123!", "firstName": "Jane", "lastName": "Doe" }
```
| Field | Rules |
|---|---|
| `email` | required, valid email, max 255, unique ignoring case |
| `password` | required, 8–72 characters |
| `firstName`, `lastName` | required, max 100 |

`201 Created` — `AuthResponse`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "q2Vb3...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "7e7b334d-2878-4197-b934-5b3bcc9102f1",
    "email": "jane@example.com",
    "firstName": "Jane",
    "lastName": "Doe",
    "roles": ["EMPLOYEE"],
    "enabled": true,
    "createdAt": "2026-09-28T11:35:25.382712Z"
  }
}
```
Errors: 400, 409 (email exists).

### `POST /api/auth/login` — public
`{ "email": "jane@example.com", "password": "Secret123!" }` → `200` `AuthResponse`.
Errors: 400, 401 "Invalid email or password", 403 account disabled.

### `POST /api/auth/refresh` — public
`{ "refreshToken": "q2Vb3..." }` → `200` `AuthResponse` with a **new** refresh token (the old one is dead).
Errors: 400, 401 (unknown, expired, already used — reuse also signs the user out everywhere).

### `POST /api/auth/logout` — public
`{ "refreshToken": "q2Vb3..." }` → `204 No Content` (also for unknown tokens).

### `GET /api/auth/ping` — public
`200` `{"service":"auth-service","status":"UP","timestamp":"..."}`

---

## Users — `/api/users` (auth-service)

`User` object (never contains the password hash):
```json
{ "id": "…", "email": "jane@example.com", "firstName": "Jane", "lastName": "Doe",
  "roles": ["EMPLOYEE"], "enabled": true, "createdAt": "2026-09-28T11:35:25Z" }
```

| Method | Path | Who | Body / params | Success |
|---|---|---|---|---|
| GET | `/api/users/me` | logged in | — | `200` User |
| PUT | `/api/users/me` | logged in | `{ "firstName": "Janet", "lastName": "Smith" }` (required, max 100) | `200` User |
| PUT | `/api/users/me/password` | logged in | `{ "currentPassword": "...", "newPassword": "..." }` (new: 8–72, must differ) | `204`; all refresh tokens revoked |
| GET | `/api/users` | ADMIN, PROJECT_MANAGER, TEAM_LEAD | `search` (email, first, last name), `role`, `page`, `size`, `sort` = `email`, `firstName`, `lastName`, `createdAt` (default first+last name) | `200` Page of User (disabled users only for ADMIN) |
| GET | `/api/users/batch?ids=a,b,c` | logged in | up to 100 ids | `200` array of User (unknown ids skipped) |
| GET | `/api/users/{id}` | logged in | — | `200` User |
| PUT | `/api/users/{id}/roles` | ADMIN | `{ "roles": ["PROJECT_MANAGER", "EMPLOYEE"] }` (not empty) | `200` User |
| PUT | `/api/users/{id}/status` | ADMIN | `{ "enabled": false }` | `200` User; disabling revokes their refresh tokens |

Errors: 400 (validation, wrong current password, bad sort, >100 ids), 403 (role), 404 (user),
422 (admin removing own ADMIN role / disabling self).

---

## Projects — `/api/projects` (project-service)

`ProjectResponse`:
```json
{
  "id": "c29609c1-1a0e-43f7-849d-c103a514e54b",
  "name": "Website Revamp",
  "description": "Refresh the company website.",
  "status": "ACTIVE",
  "allowedStatuses": ["ON_HOLD", "COMPLETED", "CANCELLED"],
  "startDate": "2026-10-01",
  "endDate": "2026-12-31",
  "manager": { "id": "…", "firstName": "Pam", "lastName": "Manager", "email": "pm@x.com" },
  "members": [ { "id": "…", "firstName": "Eve", "lastName": "Worker", "email": "eve@x.com" } ],
  "canManage": true,
  "createdAt": "2026-09-28T12:26:01Z",
  "updatedAt": "2026-09-28T12:26:05Z"
}
```
`allowedStatuses` = moves the **caller** may make now (empty if they cannot manage).
`canManage` = caller is the manager or ADMIN.

`ProjectSummaryResponse` (list rows, no members): `id, name, status, startDate, endDate, manager, updatedAt`.

| Method | Path | Who | Body / params | Success |
|---|---|---|---|---|
| POST | `/api/projects` | ADMIN, PROJECT_MANAGER | `ProjectRequest` (below) | `201` ProjectResponse (status PLANNING) |
| GET | `/api/projects` | logged in (own projects; ADMIN all) | `search` (name, description), `status`, `managerId`, `page`, `size`, `sort` = `name`, `status`, `startDate`, `endDate`, `createdAt`, `updatedAt` (default `updatedAt,desc`) | `200` Page of ProjectSummaryResponse |
| GET | `/api/projects/{id}` | members | — | `200` ProjectResponse |
| PUT | `/api/projects/{id}` | manager, ADMIN | `ProjectRequest` (`managerId` ignored) | `200` ProjectResponse |
| PATCH | `/api/projects/{id}/status` | manager, ADMIN | `{ "status": "ACTIVE" }` | `200` ProjectResponse |
| PUT | `/api/projects/{id}/manager` | ADMIN | `{ "userId": "…" }` | `200` ProjectResponse |
| POST | `/api/projects/{id}/members` | manager, ADMIN | `{ "userId": "…" }` | `201` ProjectResponse |
| DELETE | `/api/projects/{id}/members/{userId}` | manager, ADMIN | — | `204` |
| DELETE | `/api/projects/{id}` | manager, ADMIN | — | `204` |
| GET | `/api/projects/stats` | logged in | — | `200` ProjectStatsResponse |
| GET | `/api/projects/{id}/membership` | members *(used by task-service)* | — | `200` `{ projectId, name, status, managerId, memberIds[] }` |
| GET | `/api/projects/accessible` | logged in *(used by task-service)* | — | `200` `{ "allProjects": false, "projectIds": ["…"] }` (`allProjects: true` for ADMIN) |
| GET | `/api/projects/ping` | public | — | `200` |

`ProjectRequest`:
```json
{ "name": "Website Revamp", "description": "optional, max 2000", "startDate": "2026-10-01",
  "endDate": "2026-12-31", "managerId": null }
```
`name` required, max 150, unique ignoring case. Dates optional; `endDate` must not be before `startDate`
(→ 400 `fieldErrors.dateRangeValid`). `managerId`: only ADMIN may set someone else; must be PROJECT_MANAGER or ADMIN.

`ProjectStatsResponse`:
```json
{ "total": 4, "active": 3, "completed": 0,
  "byStatus": { "PLANNING": 1, "ACTIVE": 3, "ON_HOLD": 0, "COMPLETED": 0, "CANCELLED": 0 } }
```

Errors: 400, 403 (not manager/ADMIN, PM creating for someone else), 404 (not found or not a member),
409 (name exists, already a member, project still has tasks, changed by someone else),
422 (invalid status move, project closed, manager lacks role, user not found/disabled, removing the manager),
503 (auth- or task-service down).

---

## Tasks — `/api/tasks` (task-service)

`TaskResponse`:
```json
{
  "id": "b58c8d3a-971d-476e-ab4c-6cc0713172c7",
  "projectId": "048e7c7d-4658-49b5-bdfc-26eed3404be5",
  "projectName": "Website Revamp",
  "title": "Design login page",
  "description": "Make it responsive.",
  "status": "IN_REVIEW",
  "priority": "URGENT",
  "dueDate": "2026-10-15",
  "overdue": false,
  "assignee": { "id": "…", "firstName": "Eva", "lastName": "Tester", "email": "eva@example.com" },
  "createdBy": { "id": "…", "firstName": "System", "lastName": "Admin", "email": "admin@workflowpro.local" },
  "completedAt": null,
  "createdAt": "2026-09-28T12:28:01Z",
  "updatedAt": "2026-09-28T12:28:09Z",
  "allowedStatuses": ["IN_PROGRESS", "COMPLETED", "CANCELLED"],
  "permissions": { "canEdit": true, "canAssign": true, "canDelete": true }
}
```
`assignee` is `null` when unassigned. `allowedStatuses` / `permissions` are for the **caller**.

`TaskSummaryResponse` (list rows): `id, projectId, title, status, priority, dueDate, overdue, assignee, updatedAt`.

| Method | Path | Who | Body / params | Success |
|---|---|---|---|---|
| POST | `/api/tasks` | leads of the project | `CreateTaskRequest` (below) | `201` TaskResponse (status TODO) |
| GET | `/api/tasks` | logged in (tasks of own projects; ADMIN all) | see search params below | `200` Page of TaskSummaryResponse |
| GET | `/api/tasks/{id}` | project members | — | `200` TaskResponse |
| PUT | `/api/tasks/{id}` | leads | `{ "title", "description", "priority", "dueDate" }` (title required max 200, description max 5000, priority required) | `200` TaskResponse |
| PATCH | `/api/tasks/{id}/status` | leads; assignee for TODO/IN_PROGRESS/IN_REVIEW | `{ "status": "IN_PROGRESS" }` | `200` TaskResponse |
| PUT | `/api/tasks/{id}/assignee` | leads | `{ "assigneeId": "…" }` or `{ "assigneeId": null }` to unassign | `200` TaskResponse |
| DELETE | `/api/tasks/{id}` | manager, ADMIN | — | `204` (history deleted too) |
| GET | `/api/tasks/{id}/history` | project members | `page`, `size` (default 20) | `200` Page of TaskHistoryResponse, newest first |
| GET | `/api/tasks/stats` | logged in | optional `projectId` | `200` TaskStatsResponse |
| GET | `/api/tasks/count?projectId=` | project members *(used by project-service)* | — | `200` `{ "count": 3 }` |
| GET | `/api/tasks/ping` | public | — | `200` |

`CreateTaskRequest`:
```json
{ "projectId": "…", "title": "Design login page", "description": "optional",
  "priority": "HIGH", "dueDate": "2026-10-15", "assigneeId": null }
```
`projectId` and `title` required; `priority` optional (default MEDIUM); `dueDate` cannot be in the past;
`assigneeId` optional, must be a project member.

**Search params for `GET /api/tasks`**

| Param | Meaning |
|---|---|
| `projectId` | only this project (caller must be a member, else 404) |
| `search` | title or description contains (case-insensitive) |
| `status` | `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `COMPLETED`, `CANCELLED` |
| `priority` | `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| `assigneeId` | only tasks assigned to this user (the UI's "Assigned to me") |
| `open=true` | only TODO / IN_PROGRESS / IN_REVIEW |
| `overdue=true` | due date before today (UTC) and still open |
| `sort` | `title`, `status`, `priority` (by rank LOW < … < URGENT), `dueDate`, `createdAt`, `updatedAt` (default `updatedAt,desc`) |
| `page`, `size` | paging (size 1–100, default 10) |

Example: `GET /api/tasks?projectId=…&priority=URGENT&open=true&sort=dueDate,asc&page=0&size=10`

`TaskHistoryResponse`:
```json
{ "id": 42, "action": "STATUS_CHANGED", "field": "status", "oldValue": "IN_PROGRESS",
  "newValue": "IN_REVIEW", "actor": { "id": "…", "firstName": "Eva", "lastName": "Tester", "email": "…" },
  "createdAt": "2026-09-28T12:28:09Z" }
```
Actions: `CREATED`, `UPDATED` (field `title` or `description`), `ASSIGNED` (values = names), `STATUS_CHANGED`,
`PRIORITY_CHANGED`, `DUE_DATE_CHANGED`.

`TaskStatsResponse`:
```json
{ "total": 3, "pending": 2, "completed": 1, "overdue": 0, "myOpenTasks": 0,
  "byStatus": { "TODO": 2, "IN_PROGRESS": 0, "IN_REVIEW": 0, "COMPLETED": 1, "CANCELLED": 0 },
  "byPriority": { "LOW": 0, "MEDIUM": 0, "HIGH": 1, "URGENT": 2 } }
```
`pending` = TODO + IN_PROGRESS + IN_REVIEW. `myOpenTasks` = open tasks assigned to the caller.

Errors: 400, 403 (not a lead / not allowed to make that move), 404 (task not found or not a member),
409 (changed by someone else), 422 (invalid move, project not PLANNING/ACTIVE, assignee not a member or
disabled, closed task reassigned, due date moved into the past), 503 (project- or auth-service down).

---

## Health

`GET /actuator/health` on every service and the gateway → `{"status":"UP", ...}`. Component details
(database, disk) are shown only to authorized callers. `GET /actuator/info` is also public.

## curl walk-through

```bash
set -a; source .env; set +a
G=http://localhost:9080
TOKEN=$(curl -s -X POST $G/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$BOOTSTRAP_ADMIN_EMAIL\",\"password\":\"$BOOTSTRAP_ADMIN_PASSWORD\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
H="Authorization: Bearer $TOKEN"

PROJECT=$(curl -s -X POST $G/api/projects -H "$H" -H 'Content-Type: application/json' \
  -d '{"name":"Demo project"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
# (project names are unique: change "Demo project" before running this a second time)
curl -s -X PATCH $G/api/projects/$PROJECT/status -H "$H" -H 'Content-Type: application/json' -d '{"status":"ACTIVE"}'
TASK=$(curl -s -X POST $G/api/tasks -H "$H" -H 'Content-Type: application/json' \
  -d "{\"projectId\":\"$PROJECT\",\"title\":\"First task\",\"priority\":\"HIGH\"}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
curl -s -X PATCH $G/api/tasks/$TASK/status -H "$H" -H 'Content-Type: application/json' -d '{"status":"IN_PROGRESS"}'
curl -s "$G/api/tasks?projectId=$PROJECT&open=true" -H "$H"
curl -s $G/api/tasks/$TASK/history -H "$H"
curl -s $G/api/tasks/stats -H "$H"
```
