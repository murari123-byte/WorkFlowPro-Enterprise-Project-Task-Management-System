# Dependencies

Versions are managed by the parent POM (`spring-boot-starter-parent` 4.0.8 and the
`spring-cloud-dependencies` 2025.1.3 BOM), so modules do not list versions.

## Parent POM

| Item | Version | Why |
|---|---|---|
| `spring-boot-starter-parent` | 4.0.8 | Manages versions of all Spring Boot libraries and plugins |
| `spring-cloud-dependencies` (BOM) | 2025.1.3 | Spring Cloud release train compatible with Boot 4.0.x |
| Java | 21 | LTS version required by the project |

## auth-service, project-service, task-service, notification-service

| Dependency | Scope | Why |
|---|---|---|
| `spring-boot-starter-webmvc` | compile | REST controllers, JSON, embedded Tomcat |
| `spring-boot-starter-actuator` | compile | `/actuator/health` endpoint |
| `spring-boot-starter-validation` | compile | Bean Validation on request DTOs (`@NotBlank`, `@Email`, ...) |
| `spring-boot-starter-test` | test | JUnit 5, Mockito, AssertJ, Spring test support |
| `spring-boot-starter-data-jpa` | compile | Spring Data JPA repositories + Hibernate 7.2 (Step 2) |
| `spring-boot-starter-flyway` | compile | Runs Flyway 11 migrations on startup. In Boot 4 plain `flyway-core` is **not** enough (Step 2) |
| `flyway-database-postgresql` | compile | PostgreSQL support for Flyway (split into its own jar since Flyway 10) (Step 2) |
| `postgresql` | runtime | PostgreSQL JDBC driver 42.7 (Step 2) |
| `spring-boot-testcontainers` | test | `@ServiceConnection`: points tests at a container DB automatically (Step 2) |
| `testcontainers-postgresql` | test | Throwaway PostgreSQL container for tests; Testcontainers 2.x name (Step 2) |
| `testcontainers-junit-jupiter` | test | JUnit 5 integration for Testcontainers (Step 2) |

## Infrastructure

| Item | Version | Why |
|---|---|---|
| Docker image `postgres:16-alpine` | 16 | Dev database (docker-compose) and test database (Testcontainers) |

## api-gateway

| Dependency | Scope | Why |
|---|---|---|
| `spring-cloud-starter-gateway-server-webflux` | compile | Spring Cloud Gateway (reactive). Replaces the old `spring-cloud-starter-gateway` name |
| `spring-boot-starter-actuator` | compile | `/actuator/health` endpoint |
| `spring-boot-starter-test` | test | Context-load test |

## Build plugins

| Plugin | Why |
|---|---|
| `spring-boot-maven-plugin` | Builds runnable jars and provides `mvn spring-boot:run` |
