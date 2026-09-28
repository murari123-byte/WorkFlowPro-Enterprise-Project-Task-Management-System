# Setup Guide

## 1. Install prerequisites

| Tool | Version | Needed from | Check with |
|---|---|---|---|
| Java (JDK) | 21 | Step 1 | `java --version` |
| Maven | 3.9+ | Step 1 | `mvn --version` |
| Git | any recent | Step 1 | `git --version` |
| Docker + Docker Compose | 24+ / v2+ | Step 2 (PostgreSQL) | `docker --version`, `docker compose version` |
| Node.js + npm | 20+ / 10+ | Step 5 (React) | `node --version`, `npm --version` |

Ubuntu install examples:
```bash
sudo apt install openjdk-21-jdk maven git
# Docker: https://docs.docker.com/engine/install/ubuntu/
# Node 20: https://nodejs.org/en/download (or nvm)
```

## 2. Clone and configure

```bash
git clone https://github.com/murari123-byte/WorkFlowPro-Enterprise-Project-Task-Management-System.git WorkFlowPro
cd WorkFlowPro
cp .env.example .env          # then replace every change-me value, e.g. with: openssl rand -hex 16
```

## 3. Start PostgreSQL

```bash
docker compose up -d
docker compose ps          # STATUS should show (healthy)
```
On the very first start the init script creates the 4 service databases and accounts
(`docker logs workflowpro-postgres | grep "Creating database"`).

Stop it with `docker compose stop` (data kept). See troubleshooting for a full reset.

## 4. Build and test

```bash
mvn clean install
```
Expected: `BUILD SUCCESS`, 13 tests, 0 failures.
Docker must be running: context tests start a temporary PostgreSQL container (Testcontainers).
They do **not** use the dev database or your `.env`.
The first build downloads dependencies and can take a few minutes.

## 5. Run the services

Open one terminal per service (from the project root). In **each** terminal load the env file first —
the DB passwords have no defaults:
```bash
set -a; source .env; set +a
mvn -pl api-gateway spring-boot:run
mvn -pl auth-service spring-boot:run
mvn -pl project-service spring-boot:run
mvn -pl task-service spring-boot:run
mvn -pl notification-service spring-boot:run
```
Or run the built jar: `java -jar auth-service/target/auth-service-0.1.0-SNAPSHOT.jar`

Stop a service with `Ctrl+C` in its terminal.

## 6. Verify

```bash
for p in 9080 9081 9082 9083 9084; do curl -s localhost:$p/actuator/health | grep -o '"status":"UP"' | tail -1; done
curl localhost:9081/api/auth/ping
curl localhost:9082/api/projects/ping
curl localhost:9083/api/tasks/ping
curl localhost:9084/api/notifications/ping
```
Each health check should print `"status":"UP"`, and business services show a `db` component with `"status":"UP"`.

Check Flyway ran (example for auth):
```bash
set -a; source .env; set +a
docker exec -e PGPASSWORD=$AUTH_DB_PASSWORD workflowpro-postgres \
  psql -U $AUTH_DB_USER -d $AUTH_DB_NAME -c 'select version, description, success from flyway_schema_history'
```
 Each ping returns JSON like
`{"service":"auth-service","status":"UP","timestamp":"..."}`.
