# Architecture

## Services

| Service | Port | Base path | Owns data | Responsibility |
|---|---|---|---|---|
| api-gateway | 9080 | `/` | none | Single entry point for the React app; routes requests to services |
| auth-service | 9081 | `/api/auth` | `auth_db` | Users, roles, login, JWT access + refresh tokens |
| project-service | 9082 | `/api/projects` | `project_db` | Projects and project members |
| task-service | 9083 | `/api/tasks` | `task_db` | Tasks inside projects |
| notification-service | 9084 | `/api/notifications` | `notification_db` | User notifications |

## Rules

1. **Database per service.** A service reads and writes only its own database. It never queries another service's tables.
2. **REST between services.** If task-service needs project data, it calls project-service's REST API.
3. **Frontend only talks to the gateway.** The browser never calls a service port directly.
4. **Layering inside each service:** `controller` → `service` → `repository`, with DTOs at the controller boundary.

## Database layout

One PostgreSQL server (Docker container `workflowpro-postgres`, host port 5440) holds four separate databases.
Each database is owned by its own account, and `CONNECT` is revoked from everyone else, so a service
physically cannot open another service's database.

| Database | Owner account | Used by |
|---|---|---|
| `auth_db` | `auth_user` | auth-service |
| `project_db` | `project_user` | project-service |
| `task_db` | `task_user` | task-service |
| `notification_db` | `notification_user` | notification-service |

Flyway creates and changes all tables; Hibernate only validates (`ddl-auto: validate`).

## Package layout (per service)

```
com.workflowpro.<service>
├── <Service>Application.java
├── controller/     REST endpoints (DTOs in and out)
├── service/        business logic              (added with features)
├── repository/     Spring Data JPA interfaces  (Step 2+)
├── entity/         JPA entities                (Step 2+)
├── dto/            request/response objects    (added with features)
└── exception/      global exception handling   (Step 4)
```

## Request flow through the gateway

```
Browser (http://localhost:5173)
   │  GET http://localhost:9080/api/auth/me   Authorization: Bearer <jwt>
   ▼
API Gateway :9080  ── CORS check (CORS_ALLOWED_ORIGINS) ── route by path prefix
   │  GET http://localhost:9081/api/auth/me   (path + Authorization header unchanged)
   ▼
auth-service :9081 ── verifies JWT itself ── returns JSON
```

- The gateway does **not** check JWTs today. Each service verifies the token itself, so a service is
  never trusted just because a request came through the gateway.
- Unknown path → `404` from the gateway. Service not running → `503` (`ServiceUnavailableHandler`).

## Why the gateway is different

Spring Cloud Gateway runs on **WebFlux (Netty)**, not Spring MVC (Tomcat).
Never add `spring-boot-starter-webmvc` to `api-gateway` — the two web stacks conflict.
