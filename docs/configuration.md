# Configuration

All settings come from `application.yml` in each service. Anything that differs
between environments (ports, DB credentials, secrets) is read from an environment
variable with a safe default: `${VARIABLE_NAME:default}`.

Secrets have **no** default and must be set in the environment (added in later steps).

## Environment variables

| Variable | Used by | Default | Added in |
|---|---|---|---|
| `GATEWAY_PORT` | api-gateway | `9080` | Step 1 |
| `AUTH_SERVICE_PORT` | auth-service | `9081` | Step 1 |
| `PROJECT_SERVICE_PORT` | project-service | `9082` | Step 1 |
| `TASK_SERVICE_PORT` | task-service | `9083` | Step 1 |
| `NOTIFICATION_SERVICE_PORT` | notification-service | `9084` | Step 1 |
| `DB_HOST` | all business services | `localhost` | Step 2 |
| `DB_PORT` | all business services, docker-compose | `5440` | Step 2 |
| `POSTGRES_ADMIN_USER` | docker-compose (superuser) | *none — required* | Step 2 |
| `POSTGRES_ADMIN_PASSWORD` | docker-compose (superuser) | *none — required, secret* | Step 2 |
| `AUTH_DB_NAME` / `AUTH_DB_USER` | auth-service, init script | `auth_db` / `auth_user` | Step 2 |
| `AUTH_DB_PASSWORD` | auth-service, init script | *none — required, secret* | Step 2 |
| `PROJECT_DB_NAME` / `PROJECT_DB_USER` | project-service, init script | `project_db` / `project_user` | Step 2 |
| `PROJECT_DB_PASSWORD` | project-service, init script | *none — required, secret* | Step 2 |
| `TASK_DB_NAME` / `TASK_DB_USER` | task-service, init script | `task_db` / `task_user` | Step 2 |
| `TASK_DB_PASSWORD` | task-service, init script | *none — required, secret* | Step 2 |
| `NOTIFICATION_DB_NAME` / `NOTIFICATION_DB_USER` | notification-service, init script | `notification_db` / `notification_user` | Step 2 |
| `NOTIFICATION_DB_PASSWORD` | notification-service, init script | *none — required, secret* | Step 2 |

Docker Compose reads `.env` automatically. Spring Boot does not: run `set -a; source .env; set +a`
in the terminal before starting a service.

Changing a DB name/user/password in `.env` **after** the first `docker compose up` has no effect on the
existing volume — the init script only runs on an empty volume. See troubleshooting.

## Database settings (business services)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5440}/${AUTH_DB_NAME:auth_db}
    username: ${AUTH_DB_USER:auth_user}
    password: ${AUTH_DB_PASSWORD}      # no default on purpose
  jpa:
    open-in-view: false                # no lazy-loading from controllers
    hibernate.ddl-auto: validate       # Flyway owns the schema
  flyway:
    enabled: true
    locations: classpath:db/migration
```

## Actuator

Every service exposes only `health` and `info` over HTTP:
```yaml
management:
  endpoints.web.exposure.include: health,info
  endpoint.health.show-details: always
```
`show-details: always` is fine for local development; it will be restricted before any real deployment.
