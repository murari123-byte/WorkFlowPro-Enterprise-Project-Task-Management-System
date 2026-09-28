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
