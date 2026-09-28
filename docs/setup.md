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
cp .env.example .env          # optional in Step 1 — defaults work
set -a; source .env; set +a   # load variables into the current shell
```

## 3. Build and test

```bash
mvn clean install
```
Expected: `BUILD SUCCESS`, 9 tests, 0 failures.
The first build downloads dependencies and can take a few minutes.

## 4. Run the services

Open one terminal per service (from the project root):
```bash
mvn -pl api-gateway spring-boot:run
mvn -pl auth-service spring-boot:run
mvn -pl project-service spring-boot:run
mvn -pl task-service spring-boot:run
mvn -pl notification-service spring-boot:run
```
Or run the built jar: `java -jar auth-service/target/auth-service-0.1.0-SNAPSHOT.jar`

Stop a service with `Ctrl+C` in its terminal.

## 5. Verify

```bash
for p in 9080 9081 9082 9083 9084; do curl -s localhost:$p/actuator/health | grep -o '"status":"UP"' | tail -1; done
curl localhost:9081/api/auth/ping
curl localhost:9082/api/projects/ping
curl localhost:9083/api/tasks/ping
curl localhost:9084/api/notifications/ping
```
Each health check should print `"status":"UP"`. Each ping returns JSON like
`{"service":"auth-service","status":"UP","timestamp":"..."}`.
