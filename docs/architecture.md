# Architecture

## Services

| Service | Port | Base path | Owns data | Responsibility |
|---|---|---|---|---|
| api-gateway | 9080 | `/` | none | Single entry point for the React app; routes requests to services |
| auth-service | 9081 | `/api/auth` | `auth_db` | Users, login, JWT issuing |
| project-service | 9082 | `/api/projects` | `project_db` | Projects and project members |
| task-service | 9083 | `/api/tasks` | `task_db` | Tasks inside projects |
| notification-service | 9084 | `/api/notifications` | `notification_db` | User notifications |

## Rules

1. **Database per service.** A service reads and writes only its own database. It never queries another service's tables.
2. **REST between services.** If task-service needs project data, it calls project-service's REST API.
3. **Frontend only talks to the gateway.** The browser never calls a service port directly.
4. **Layering inside each service:** `controller` → `service` → `repository`, with DTOs at the controller boundary.

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

## Why the gateway is different

Spring Cloud Gateway runs on **WebFlux (Netty)**, not Spring MVC (Tomcat).
Never add `spring-boot-starter-webmvc` to `api-gateway` — the two web stacks conflict.
