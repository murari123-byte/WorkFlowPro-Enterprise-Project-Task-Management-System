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

## Actuator

Every service exposes only `health` and `info` over HTTP:
```yaml
management:
  endpoints.web.exposure.include: health,info
  endpoint.health.show-details: always
```
`show-details: always` is fine for local development; it will be restricted before any real deployment.
