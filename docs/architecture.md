# Architecture

## The big picture

```
                    Browser — React app (Vite dev server :5173)
                                  │  HTTPS/HTTP + JSON
                                  │  Authorization: Bearer <access token>
                                  ▼
                    ┌───────────────────────────┐
                    │   API Gateway   :9080     │  Spring Cloud Gateway (WebFlux)
                    │   routing + CORS + 503    │  no database, no business logic
                    └──────┬──────────┬─────────┘
          /api/auth/**     │          │ /api/projects/**      /api/tasks/**
          /api/users/**    │          │                             │
                           ▼          ▼                             ▼
                ┌──────────────┐ ┌─────────────────┐   ┌─────────────────┐
                │ Auth Service │ │ Project Service │   │  Task Service   │
                │    :9081     │ │      :9082      │   │      :9083      │
                └──────┬───────┘ └───┬─────────▲───┘   └──┬──────────┬───┘
                       │   REST      │  REST   │  REST    │  REST    │
                       │◄────────────┘         └──────────┘          │
                       │◄────────────────────────────────────────────┘
                       ▼                  ▼                        ▼
                  ┌─────────┐       ┌────────────┐          ┌─────────┐
                  │ auth_db │       │ project_db │          │ task_db │
                  └─────────┘       └────────────┘          └─────────┘
                        one PostgreSQL 16 server (Docker, host port 5440)
```

Service-to-service calls (all REST, all with the caller's token):

| From | To | Why |
|---|---|---|
| project-service | auth-service `GET /api/users/{id}`, `GET /api/users/batch` | check that a manager/member exists and is enabled; show names |
| project-service | task-service `GET /api/tasks/count` | refuse to delete a project that still has tasks |
| task-service | project-service `GET /api/projects/{id}/membership` | is the caller a member / manager? is the project open? is the assignee a member? |
| task-service | project-service `GET /api/projects/accessible` | which projects' tasks may the caller list / count |
| task-service | auth-service `GET /api/users/{id}`, `GET /api/users/batch` | assignee checks and names |

Every service is a separate Spring Boot application with its own port, its own database and its own
Flyway migrations. Details per service: [microservices.md](microservices.md).

## Main design decisions (and why)

| Decision | Why |
|---|---|
| **One database per service** (`auth_db`, `project_db`, `task_db`), each with its own login that cannot connect to the others | Services stay independent: a service can change its tables without breaking another. It is the core rule of microservices. |
| **No foreign keys between services** — e.g. `tasks.project_id` is just a UUID | The data lives in different databases. The owning service checks the id through its REST API instead. |
| **REST between services, no message broker** | Simple, synchronous, easy to follow and test. Enough for this size. |
| **API Gateway as the only entry point** | The browser needs one URL; CORS is handled in one place; service ports stay internal. |
| **JWT access tokens (HS256) verified by every service** | Stateless: a service does not need to call auth-service for every request. The gateway does not check tokens, so no service trusts a request just because it came through the gateway. |
| **Token relay** — service-to-service calls forward the user's own token | The called service applies its own rules to the same user. No service gets more rights than the person who started the request. |
| **Refresh tokens stored hashed, single use** | A stolen refresh token can only be used once, and reuse is detected. A leaked database does not leak usable tokens. |
| **Shared `common` library** (not a service) | Same error format, same JWT checks, same paging rules everywhere, without copy-paste. It has no business logic. |
| **Flyway for every schema change**, Hibernate only validates (`ddl-auto: validate`) | The schema is versioned in Git and identical on every machine; nobody changes the database by hand. |
| **UUID primary keys** | Ids are created by the service, are unique across services, and do not reveal how many rows exist. |
| **Optimistic locking (`@Version`)** on projects and tasks | Two people saving the same record: the second gets 409 instead of silently overwriting. |
| **404 instead of 403 for non-members** | A user who is not in a project cannot even find out that it exists. |

## Request flow — "employee moves a task to IN_REVIEW"

1. React calls `PATCH http://localhost:9080/api/tasks/{id}/status` with `{"status":"IN_REVIEW"}` and the
   header `Authorization: Bearer <access token>`.
2. **API Gateway** checks CORS (the page's origin must be in `CORS_ALLOWED_ORIGINS`) and forwards the
   request unchanged to `TASK_SERVICE_URL` (task-service). If task-service is down → `503`.
3. **task-service — security filter**: verifies the JWT signature (HS256, `JWT_SECRET`), expiry and issuer.
   Invalid → `401` JSON. It builds `AuthenticatedUser` (id, email, roles) **only from the token**.
4. **TaskController** validates the body (`@Valid`, status must be one of the enum values → else `400`).
5. **TaskService** loads the task from `task_db` (`404` if missing), then asks project-service
   `GET /api/projects/{projectId}/membership` **with the same token** (token relay).
   project-service answers `404` if the caller is not a member → task-service answers "Task not found".
6. **Business rules**: the project must be PLANNING or ACTIVE (`422`); the move must follow the workflow
   (`422`); the caller must be allowed to make it — here the assignee may move IN_PROGRESS → IN_REVIEW (`403` otherwise).
7. In **one database transaction**: the task's status is updated and a `task_history` row
   (`STATUS_CHANGED`, old → new, who, when) is inserted. `@Version` protects against a parallel update (`409`).
8. The response (`TaskResponse`) includes the names of assignee and creator (one batch call to
   auth-service) and `allowedStatuses` / `permissions` for the caller, so the UI can show the right buttons.
9. Any error anywhere becomes the same JSON `ErrorResponse` (see [api.md](api.md#error-format)).

## Layers inside each service

```
controller/   HTTP only: URL, @Valid request DTOs, @PreAuthorize role checks, returns response DTOs
service/      business rules and permissions, @Transactional
repository/   Spring Data JPA interfaces (+ Specifications for search)
entity/       JPA entities and enums (the database shape)
dto/          request / response records (never expose entities)
client/       REST clients to other services
config/       security, OpenAPI, properties
```

Rules followed everywhere:
- Controllers never touch repositories; entities never leave the service layer (DTOs only).
- Validation of input shape: Bean Validation annotations on request records → `400` with `fieldErrors`.
- Business rules: service layer → `403` / `404` / `409` / `422` via `ApiException` subclasses.
- Read methods are `@Transactional(readOnly = true)`.

## Shared `common` module

A small Maven library used by auth-, project- and task-service. It has **no business logic and no database**.

| Package | Contents |
|---|---|
| `common.exception` | `ApiException` and subclasses: `BadRequestException` 400, `UnauthorizedException` 401, `ForbiddenException` 403, `ResourceNotFoundException` 404, `ConflictException` 409, `BusinessRuleException` 422, `ServiceUnavailableException` 503; `ErrorResponse`; `GlobalExceptionHandler` |
| `common.security` | `JwtProperties` (secret, issuer), `JwtVerificationConfig` (JwtDecoder + `roles` → `ROLE_*`), `SecurityErrorHandler` (JSON 401/403), `AuthenticatedUser` |
| `common.web` | `PageResponse<T>` (JSON shape of every paged list), `Paging` (page/size limits, sort whitelist) |
| `common.client` | `ServiceClients` (RestClient with timeouts + token relay, error mapping), `BearerTokenRelayInterceptor`, `UserDirectoryClient`, `RemoteUser`, `UserSummary` |

Each service scans it: `@SpringBootApplication(scanBasePackages = {"com.workflowpro.<service>", "com.workflowpro.common"})`.

## Why the gateway is different

Spring Cloud Gateway runs on **WebFlux (Netty)**, not Spring MVC (Tomcat). It must not get
`spring-boot-starter-webmvc` — the two web stacks conflict. It does not depend on `common` (which is MVC-based).

## Ports

| Component | Port | Env var |
|---|---|---|
| React dev server | 5173 | — (Vite) |
| API Gateway | 9080 | `GATEWAY_PORT` |
| auth-service | 9081 | `AUTH_SERVICE_PORT` |
| project-service | 9082 | `PROJECT_SERVICE_PORT` |
| task-service | 9083 | `TASK_SERVICE_PORT` |
| PostgreSQL | 5440 (host) → 5432 (container) | `DB_PORT` |

Ports 8080–8084 are avoided on purpose: they were already used by other applications on the development machine.

## Known limitations

- **Deleting a project is not atomic across services.** project-service checks "no tasks" in task-service,
  then deletes. A task created in the same moment would be left without a project.
- **Remote calls run inside database transactions**, so a slow auth- or project-service keeps a database
  connection busy for up to the 5-second read timeout.
- **Logout does not cancel the access token** — it stays valid until it expires (15 minutes by default).
- **HS256 with one shared secret**: every service that can verify tokens could also create them.
- No service discovery, load balancing, circuit breaker or central logging — fixed URLs from env vars.

## Future improvements

- RS256: auth-service signs with a private key, other services only get the public key (JWKS endpoint).
- Refresh token in an `HttpOnly` cookie instead of `localStorage`.
- A scheduled job that deletes expired/revoked refresh tokens.
- Docker images for each service and one `docker compose up` for the whole system.
- Resilience4j retries/circuit breaker on service-to-service calls.
- Kanban board, task comments, organizations/teams, notifications (requested earlier, not built).
