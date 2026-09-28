# Dependencies

Backend versions are managed by the root `pom.xml`: `spring-boot-starter-parent` **4.0.8** and the
`spring-cloud-dependencies` **2025.1.3** BOM, plus `springdoc.version` **3.0.3**. Modules list no versions.

## Why each major technology

| Technology | Version | Why it is used |
|---|---|---|
| Java | 21 (LTS) | long-term support; records, switch expressions, text blocks |
| Spring Boot | 4.0.8 | auto-configuration, embedded server, one way to configure everything |
| Spring Framework / Spring MVC | 7.0.9 | REST controllers in auth, project and task services |
| Spring Cloud Gateway (WebFlux) | 5.0.3 | one entry point: routing + CORS |
| Spring Security + OAuth2 Resource Server | 7.0.7 | stateless JWT verification, `@PreAuthorize`, BCrypt |
| Spring Data JPA + Hibernate | Data 2025.1.7, Hibernate 7.2.24 | repositories, Specifications for search, paging |
| PostgreSQL + JDBC driver | 16 / 42.7.13 | reliable relational database, one per service |
| Flyway | 11.14.1 | versioned schema changes |
| Jackson | 3.1 | JSON (Boot 4 uses Jackson 3, package `tools.jackson`) |
| springdoc-openapi | 3.0.3 | Swagger UI + OpenAPI JSON from the controllers |
| JUnit Jupiter | 6.0.3 | tests |
| Mockito / AssertJ | 5.20 / 3.27 | mocks and readable assertions |
| Testcontainers | 2.0.5 | real PostgreSQL in Docker for integration tests |
| React | 19.3 | UI |
| TypeScript | 6.0 | types for API data, errors at build time |
| Vite | 8.3 | dev server and build |
| React Router | 7.18 | pages and protected routes |
| Axios | 1.20 | HTTP with interceptors (token + refresh) |

Not used on purpose (kept simple): Redis, Kafka, WebSockets, service discovery (Eureka), Kubernetes,
Lombok, MapStruct, UI/CSS frameworks, chart libraries.

## common (shared library)

| Dependency | Why |
|---|---|
| `spring-boot-starter-webmvc`, `spring-boot-starter-validation` | `@RestControllerAdvice`, `RestClient`, validation types |
| `spring-boot-starter-security-oauth2-resource-server` | `JwtDecoder`, `JwtAuthenticationConverter` |
| `spring-data-commons` | `Page` / `Pageable` for `PageResponse` and `Paging` |
| `spring-orm` | `ObjectOptimisticLockingFailureException` handling |
| `spring-boot-starter-test` (test) | JUnit, AssertJ |

## auth-service, project-service, task-service

| Dependency | Scope | Why |
|---|---|---|
| `com.workflowpro:common` | compile | shared errors, JWT verification, paging, service clients |
| `spring-boot-starter-webmvc` | compile | REST controllers, embedded Tomcat |
| `spring-boot-starter-actuator` | compile | `/actuator/health`, `/actuator/info` |
| `spring-boot-starter-validation` | compile | `@NotBlank`, `@Email`, `@Size`, ... on request DTOs |
| `spring-boot-starter-security` | compile | security filter chain, `@PreAuthorize`, BCrypt (auth) |
| `spring-boot-starter-security-oauth2-resource-server` | compile | Bearer JWT verification; Nimbus `JwtEncoder` in auth-service |
| `springdoc-openapi-starter-webmvc-ui` | compile | Swagger UI |
| `spring-boot-starter-data-jpa` | compile | JPA/Hibernate, repositories |
| `spring-boot-starter-flyway` | compile | runs migrations at startup (Boot 4 needs the starter, not just `flyway-core`) |
| `flyway-database-postgresql` | compile | PostgreSQL support for Flyway (separate jar since Flyway 10) |
| `postgresql` | runtime | JDBC driver |
| `spring-boot-starter-test` | test | JUnit Jupiter, Mockito, AssertJ, Spring test |
| `spring-boot-starter-webmvc-test` | test | MockMvc / `@AutoConfigureMockMvc` (own module in Boot 4) |
| `spring-boot-starter-security-test` | test | security test support |
| `spring-boot-testcontainers` | test | `@ServiceConnection` |
| `testcontainers-postgresql`, `testcontainers-junit-jupiter` | test | PostgreSQL container (Testcontainers 2.x artifact names) |

## api-gateway

| Dependency | Scope | Why |
|---|---|---|
| `spring-cloud-starter-gateway-server-webflux` | compile | Spring Cloud Gateway (reactive; replaces the old `spring-cloud-starter-gateway` name) |
| `spring-boot-starter-actuator` | compile | health endpoint |
| `spring-boot-starter-test` | test | tests (WebTestClient) |

The gateway must **not** get `spring-boot-starter-webmvc` or `common` (MVC and WebFlux conflict).

## Build plugins

| Plugin | Why |
|---|---|
| `spring-boot-maven-plugin` | runnable jars, `mvn spring-boot:run` |

## Frontend (`frontend/package.json`)

| Package | Version | Kind | Why |
|---|---|---|---|
| `react`, `react-dom` | 19.3.0 | runtime | UI |
| `react-router-dom` | 7.18.4 | runtime | routing, protected routes |
| `axios` | 1.20.0 | runtime | HTTP client, interceptors |
| `vite` | 8.3.1 | dev | dev server + build |
| `@vitejs/plugin-react` | 6.1.1 | dev | React support in Vite |
| `typescript` | 6.0.3 | dev | type checking (`tsc -b` in `npm run build`) |
| `oxlint` | 1.86.0 | dev | linter (`npm run lint`), from the Vite template |
| `@types/react`, `@types/react-dom`, `@types/node` | 19.3 / 19.3 / 24.19 | dev | type definitions |

## Infrastructure

| Item | Why |
|---|---|
| Docker image `postgres:16-alpine` | dev database (Compose) and test database (Testcontainers) |
| Docker Compose v2 | starts the database with one command |
