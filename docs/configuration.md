# Configuration

## Where settings live

| File | What |
|---|---|
| `.env` (from `.env.example`, git-ignored) | every value that differs per machine and every secret |
| `docker-compose.yml` | the PostgreSQL container (reads `.env` automatically) |
| `docker/postgres/init/01-create-service-databases.sh` | creates the 3 databases and logins on the first start |
| `<service>/src/main/resources/application.yml` | Spring Boot settings; values come from env vars as `${VAR:default}` |
| `<service>/src/test/resources/application-test.yml` | test-only values (test JWT secret, test admin) |
| `frontend/.env.example` → `frontend/.env.local` | `VITE_API_BASE_URL` for the React app |
| `pom.xml` (root) | Java, Spring Boot, Spring Cloud and springdoc versions; module list |

**Spring Boot does not read `.env` by itself.** Load it in each terminal before starting a service:
`set -a; source .env; set +a`. Docker Compose reads it automatically.

**Secrets have no default.** A service refuses to start without its DB password and `JWT_SECRET`
(and `JWT_SECRET` must be at least 32 characters).

## All environment variables

| Variable | Used by | Default | Secret |
|---|---|---|---|
| `GATEWAY_PORT` | api-gateway | `9080` | |
| `AUTH_SERVICE_PORT` | auth-service | `9081` | |
| `PROJECT_SERVICE_PORT` | project-service | `9082` | |
| `TASK_SERVICE_PORT` | task-service | `9083` | |
| `AUTH_SERVICE_URL` | api-gateway, project-service, task-service | `http://localhost:9081` | |
| `PROJECT_SERVICE_URL` | api-gateway, task-service | `http://localhost:9082` | |
| `TASK_SERVICE_URL` | api-gateway, project-service | `http://localhost:9083` | |
| `CORS_ALLOWED_ORIGINS` | api-gateway (comma-separated) | `http://localhost:5173` | |
| `DB_HOST` | auth, project, task services | `localhost` | |
| `DB_PORT` | auth, project, task services; docker-compose (host port) | `5440` | |
| `POSTGRES_ADMIN_USER` | docker-compose (PostgreSQL superuser, only for the init script and troubleshooting) | — required | |
| `POSTGRES_ADMIN_PASSWORD` | docker-compose | — required | ✅ |
| `AUTH_DB_NAME`, `AUTH_DB_USER` | auth-service, init script | `auth_db`, `auth_user` | |
| `AUTH_DB_PASSWORD` | auth-service, init script | — required | ✅ |
| `PROJECT_DB_NAME`, `PROJECT_DB_USER` | project-service, init script | `project_db`, `project_user` | |
| `PROJECT_DB_PASSWORD` | project-service, init script | — required | ✅ |
| `TASK_DB_NAME`, `TASK_DB_USER` | task-service, init script | `task_db`, `task_user` | |
| `TASK_DB_PASSWORD` | task-service, init script | — required | ✅ |
| `JWT_SECRET` | auth-service (signs), project- and task-service (verify) — **same value everywhere** | — required, min 32 chars | ✅ |
| `JWT_ISSUER` | auth, project, task services | `workflowpro-auth` | |
| `JWT_ACCESS_TOKEN_TTL` | auth-service | `15m` | |
| `JWT_REFRESH_TOKEN_TTL` | auth-service | `7d` | |
| `BOOTSTRAP_ADMIN_EMAIL` | auth-service (first ADMIN; empty = skip) | empty | |
| `BOOTSTRAP_ADMIN_PASSWORD` | auth-service | empty | ✅ |
| `BOOTSTRAP_ADMIN_FIRST_NAME`, `BOOTSTRAP_ADMIN_LAST_NAME` | auth-service | `System`, `Admin` | |
| `SWAGGER_ENABLED` | auth, project, task services | `true` (set `false` in production) | |
| `VITE_API_BASE_URL` | frontend (`frontend/.env.local`) | `http://localhost:9080` | never put secrets here |

Durations use Spring Boot format: `30s`, `15m`, `1h`, `7d`.

Changing a DB name/user/password in `.env` **after** the first `docker compose up` does not change the
existing database volume — the init script only runs on an empty volume (reset: `docker compose down -v`).

## application.yml — explained (task-service; auth and project are the same shape)

```yaml
spring:
  datasource:                                  # this service's own database
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5440}/${TASK_DB_NAME:task_db}
    username: ${TASK_DB_USER:task_user}
    password: ${TASK_DB_PASSWORD}              # no default on purpose
  jpa:
    open-in-view: false                        # no lazy loading after the service layer
    properties:
      hibernate.default_batch_fetch_size: 50   # lazy collections for up to 50 rows in one query (no N+1)
    hibernate:
      ddl-auto: validate                       # Flyway owns the schema; Hibernate only checks it
  flyway:
    enabled: true
    locations: classpath:db/migration

app:
  jwt:
    secret: ${JWT_SECRET}                      # bound to common JwtProperties (validated, min 32 chars)
    issuer: ${JWT_ISSUER:workflowpro-auth}
  services:                                    # direct service-to-service URLs (not through the gateway)
    auth-url: ${AUTH_SERVICE_URL:http://localhost:9081}
    project-url: ${PROJECT_SERVICE_URL:http://localhost:9082}

springdoc:
  api-docs.enabled: ${SWAGGER_ENABLED:true}
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:true}
    path: /swagger-ui.html

server:
  port: ${TASK_SERVICE_PORT:9083}

management:
  endpoints.web.exposure.include: health,info  # only these actuator endpoints over HTTP
  endpoint.health.show-details: when-authorized  # anonymous callers see only UP/DOWN
```

Differences per service:
- **auth-service** adds `app.jwt.access-token-ttl`, `app.jwt.refresh-token-ttl` (bound to `TokenProperties`)
  and `app.bootstrap-admin.*` (bound to `BootstrapAdminProperties`). It has no `app.services`.
- **project-service** has `app.services.auth-url` and `app.services.task-url` (`ServiceUrlsProperties`).
- **task-service** has `app.services.auth-url` and `app.services.project-url`.
- **api-gateway** has routes and CORS under `spring.cloud.gateway.server.webflux.*` (the property prefix of
  Spring Cloud Gateway 5; the old `spring.cloud.gateway.routes` is ignored). See [microservices.md](microservices.md).

## Fixed values in code (not configurable)

| Value | Where |
|---|---|
| Service-to-service timeouts: 2 s connect, 5 s read | `common` `ServiceClients` |
| Max page size 100 | `common` `Paging.MAX_PAGE_SIZE` |
| Max ids per user batch lookup 100 | auth-service `UserService.MAX_BATCH_SIZE` |
| BCrypt strength 10 | auth-service `SecurityConfig.passwordEncoder` |
| "Today" for overdue = UTC date | task-service `ClientConfig.clock` |
| Refresh token size 32 random bytes | auth-service `RefreshTokenService` |
