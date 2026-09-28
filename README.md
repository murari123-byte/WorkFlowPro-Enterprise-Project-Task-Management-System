# WorkFlowPro — Enterprise Project & Task Management System

A Java 21 / Spring Boot microservices project with a React + TypeScript frontend.

> **Status:** Backend complete and tested (gateway, auth, project, task services, 105 tests).
> React frontend is in progress and does not build yet.
> **See [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for exactly what is done and what remains.**

## Architecture (target for Phase 1)

```
React (5173) ──► API Gateway (9080) ──┬──► Auth Service         (9081) ──► auth_db
                                      ├──► Project Service      (9082) ──► project_db
                                      ├──► Task Service         (9083) ──► task_db
                                      └──► Notification Service (9084) ──► notification_db
```

Each service owns its own database. Services talk to each other over REST only.
Details: [docs/architecture.md](docs/architecture.md)

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.8 |
| Gateway | Spring Cloud Gateway (Spring Cloud 2025.1.3) |
| Build | Maven 3.9+ (multi-module) |
| Database | PostgreSQL 16 (Docker) + Flyway 11 + Spring Data JPA / Hibernate 7 |
| Security | Spring Security 7 + JWT (HS256, OAuth2 Resource Server) + BCrypt |
| Frontend | React + TypeScript + Axios + React Router, plain CSS *(Step 5)* |
| Tests | JUnit 5, Mockito, AssertJ, Testcontainers |
| API docs | springdoc-openapi 3 (Swagger UI) |

## Repository layout

```
WorkFlowPro/
├── pom.xml                  # parent POM (versions + module list)
├── api-gateway/             # Spring Cloud Gateway (WebFlux / Netty)
├── auth-service/
├── project-service/
├── task-service/
├── notification-service/
├── frontend/                # React app (Step 5)
├── docker-compose.yml       # local PostgreSQL
├── docker/postgres/init/    # creates one DB + account per service (first start only)
├── docs/                    # setup, architecture, config, dependencies, troubleshooting
├── .env.example             # all environment variables, no real secrets
├── CHANGELOG.md
└── README.md
```

## Quick start

Full step-by-step guide with every command: **[docs/setup.md](docs/setup.md)**.

Prerequisites: Java 21, Maven 3.9+, Docker with Compose (without sudo), openssl.

```bash
# 1. Create .env and replace every change-me value (JWT_SECRET: openssl rand -base64 48)
cp .env.example .env

# 2. Start PostgreSQL (port 5440)
docker compose up -d

# 3. Build everything and run the 105 tests (tests use their own throwaway DB)
mvn clean install

# 4. In each terminal: load .env, then start one service
set -a; source .env; set +a
java -jar auth-service/target/auth-service-0.1.0-SNAPSHOT.jar         # 9081
java -jar project-service/target/project-service-0.1.0-SNAPSHOT.jar   # 9082
java -jar task-service/target/task-service-0.1.0-SNAPSHOT.jar         # 9083
java -jar api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar           # 9080

# 5. Check health, then log in as the bootstrap admin from .env (see docs/setup.md step 8)
curl http://localhost:9080/api/auth/ping
```

Swagger UI: http://localhost:9081/swagger-ui.html, :9082/swagger-ui.html, :9083/swagger-ui.html

## API Gateway routes (port 9080)

| Path | Forwarded to |
|---|---|
| `/api/auth/**` | auth-service (`AUTH_SERVICE_URL`) |
| `/api/projects/**` | project-service (`PROJECT_SERVICE_URL`) |
| `/api/tasks/**` | task-service (`TASK_SERVICE_URL`) |
| `/api/notifications/**` | notification-service (`NOTIFICATION_SERVICE_URL`) |

Paths are forwarded unchanged. CORS is handled only by the gateway (`CORS_ALLOWED_ORIGINS`).
A stopped service returns `503 Service Unavailable`.

## Auth API (auth-service)

| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/register` | public |
| POST | `/api/auth/login` | public |
| POST | `/api/auth/refresh` | public |
| POST | `/api/auth/logout` | public |
| GET | `/api/auth/me` | Bearer token |

Swagger UI: http://localhost:9081/swagger-ui.html — full reference in [docs/api.md](docs/api.md),
security design in [docs/authentication.md](docs/authentication.md).

## Endpoints available now

| Service | Port | Health | Ping |
|---|---|---|---|
| api-gateway | 9080 | `/actuator/health` | – |
| auth-service | 9081 | `/actuator/health` | `/api/auth/ping` |
| project-service | 9082 | `/actuator/health` | `/api/projects/ping` |
| task-service | 9083 | `/actuator/health` | `/api/tasks/ping` |
| notification-service | 9084 | `/actuator/health` | `/api/notifications/ping` |

## Documentation

- [docs/setup.md](docs/setup.md) — install and run, step by step
- [docs/authentication.md](docs/authentication.md) — JWT, refresh tokens, roles, security rules
- [docs/api.md](docs/api.md) — every endpoint with request/response examples
- [docs/architecture.md](docs/architecture.md) — services, ports, communication rules
- [docs/configuration.md](docs/configuration.md) — every environment variable
- [docs/dependencies.md](docs/dependencies.md) — every dependency and why it is there
- [docs/database-migrations.md](docs/database-migrations.md) — Flyway rules and migration log
- [docs/troubleshooting.md](docs/troubleshooting.md) — known problems and fixes
- [CHANGELOG.md](CHANGELOG.md)

## Project rules

- Never hardcode secrets — use environment variables (see `.env.example`).
- Never change the database by hand — always add a Flyway migration.
- Build one step at a time, and test each step before moving on.
