# Local Setup Guide

Follow these steps in order on a fresh machine. Every command is run from the project root
(`WorkFlowPro/`) unless it says otherwise.

> Current state: backend and React frontend both work end to end — see [PROJECT_STATUS.md](PROJECT_STATUS.md).

## 1. Install the tools

| Tool | Version | Check with |
|---|---|---|
| Java JDK | 21 | `java --version` |
| Maven | 3.9 or newer | `mvn --version` |
| Git | any recent | `git --version` |
| Docker + Docker Compose | Docker 24+, Compose v2 | `docker --version` and `docker compose version` |
| Node.js + npm | Node 20+, npm 10+ (only for the frontend) | `node --version` and `npm --version` |
| openssl | any (to create secrets) | `openssl version` |
| python3 | any (only used by the example commands in step 8) | `python3 --version` |

Ubuntu example:
```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven git openssl python3
# Docker:  https://docs.docker.com/engine/install/ubuntu/
# Then let your user run docker without sudo:  sudo usermod -aG docker $USER   (log out and in again)
# Node 20: https://nodejs.org/en/download  (or use nvm)
```

Docker must work **without sudo** — the tests start their own temporary PostgreSQL through Docker.
Check with `docker ps`.

## 2. Get the code

```bash
git clone https://github.com/murari123-byte/WorkFlowPro-Enterprise-Project-Task-Management-System.git WorkFlowPro
cd WorkFlowPro
```

## 3. Create your `.env` file

`.env` holds every password and secret. It is git-ignored and must never be committed.

```bash
cp .env.example .env
```

Now open `.env` and replace **every** value that starts with `change-me`:

| Variable | What to put | Example command to create a value |
|---|---|---|
| `POSTGRES_ADMIN_PASSWORD` | any strong password | `openssl rand -hex 16` |
| `AUTH_DB_PASSWORD` | any strong password | `openssl rand -hex 16` |
| `PROJECT_DB_PASSWORD` | any strong password | `openssl rand -hex 16` |
| `TASK_DB_PASSWORD` | any strong password | `openssl rand -hex 16` |
| `NOTIFICATION_DB_PASSWORD` | any strong password | `openssl rand -hex 16` |
| `JWT_SECRET` | **at least 32 characters**; the same value is used by every service | `openssl rand -base64 48` |
| `BOOTSTRAP_ADMIN_PASSWORD` | password for the first ADMIN login (8+ characters) | choose one you will remember |

Optional shortcut — fill all of them automatically (Linux):
```bash
for v in POSTGRES_ADMIN_PASSWORD AUTH_DB_PASSWORD PROJECT_DB_PASSWORD TASK_DB_PASSWORD NOTIFICATION_DB_PASSWORD; do
  sed -i "s|^$v=.*|$v=$(openssl rand -hex 16)|" .env
done
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n' | tr '/+' '_-')|" .env
sed -i "s|^BOOTSTRAP_ADMIN_PASSWORD=.*|BOOTSTRAP_ADMIN_PASSWORD=Admin-$(openssl rand -hex 4)|" .env
grep BOOTSTRAP_ADMIN .env     # note the admin email and password
```
(On macOS use `sed -i ''` instead of `sed -i`.)

All other values (ports, URLs, token lifetimes) can stay as they are. Every variable is
explained in [configuration.md](configuration.md).

## 4. Start PostgreSQL

```bash
docker compose up -d
docker compose ps                  # STATUS must show (healthy)
docker logs workflowpro-postgres | grep "Creating database"
```
The last command should list 4 databases: `auth_db`, `project_db`, `task_db`, `notification_db`.
They are created only on the **first** start. PostgreSQL listens on port **5440**.

If you change a database password in `.env` later, the old one stays in the database. Reset
(this deletes all local data): `docker compose down -v && docker compose up -d`.

## 5. Build and run the tests

```bash
mvn clean install
```
Expected: `BUILD SUCCESS` and **105 tests, 0 failures**. The first build downloads dependencies
(a few minutes). The tests use their own throwaway PostgreSQL containers, not your database and
not your `.env`.

To build without running tests: `mvn clean install -DskipTests`.

## 6. Start the services

Open **one terminal per service**. In **every** terminal, first load `.env` —
Spring Boot does not read the file by itself:

```bash
cd WorkFlowPro
set -a; source .env; set +a
```

Then start one service per terminal:

```bash
java -jar auth-service/target/auth-service-0.1.0-SNAPSHOT.jar          # port 9081
java -jar project-service/target/project-service-0.1.0-SNAPSHOT.jar    # port 9082
java -jar task-service/target/task-service-0.1.0-SNAPSHOT.jar          # port 9083
java -jar api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar            # port 9080
```
(`mvn -pl auth-service spring-boot:run` also works after step 5.)

notification-service (9084) is an empty placeholder and does not need to run.

On startup each service runs its **Flyway migrations** automatically. You will see lines like
`Successfully applied 2 migrations ... now at version v3`. On the next start it says
`Schema "public" is up to date`. You never create tables by hand.

auth-service also creates the **first ADMIN** from `BOOTSTRAP_ADMIN_EMAIL` /
`BOOTSTRAP_ADMIN_PASSWORD` (log line `Created bootstrap admin ...`).

Stop a service with `Ctrl+C`.

## 7. Check that everything is up

```bash
for p in 9080 9081 9082 9083; do echo "$p $(curl -s localhost:$p/actuator/health | grep -o '"status":"UP"}$')"; done
```
Each line must end with `"status":"UP"}`.

Check the migrations in the database (example: auth):
```bash
set -a; source .env; set +a
docker exec -e PGPASSWORD=$AUTH_DB_PASSWORD workflowpro-postgres \
  psql -U $AUTH_DB_USER -d $AUTH_DB_NAME -c 'select version, description, success from flyway_schema_history order by installed_rank'
```
Expected versions: auth_db 1–3, project_db 1–2, task_db 1–2. See [database-migrations.md](database-migrations.md).

## 8. Try it (through the gateway, like the frontend will)

```bash
set -a; source .env; set +a
G=http://localhost:9080

# log in as the bootstrap admin and keep the access token
TOKEN=$(curl -s -X POST $G/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$BOOTSTRAP_ADMIN_EMAIL\",\"password\":\"$BOOTSTRAP_ADMIN_PASSWORD\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')

curl -s $G/api/users/me -H "Authorization: Bearer $TOKEN"; echo

# create a project (you become its manager)
PROJECT=$(curl -s -X POST $G/api/projects -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"My first project","startDate":"2026-10-01","endDate":"2026-12-31"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')

# create a task in it
curl -s -X POST $G/api/tasks -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"projectId\":\"$PROJECT\",\"title\":\"First task\",\"priority\":\"HIGH\"}"; echo

# dashboard numbers
curl -s $G/api/projects/stats -H "Authorization: Bearer $TOKEN"; echo
curl -s $G/api/tasks/stats -H "Authorization: Bearer $TOKEN"; echo
```
Project names must be unique — change `"My first project"` if you run this twice.

New users register with `POST /api/auth/register` and always start as `EMPLOYEE`.
The admin gives them other roles with `PUT /api/users/{id}/roles`. The new role appears in the
user's next access token (after refresh or a new login).

## 9. Swagger UI (try every API in the browser)

| Service | URL |
|---|---|
| auth-service | http://localhost:9081/swagger-ui.html |
| project-service | http://localhost:9082/swagger-ui.html |
| task-service | http://localhost:9083/swagger-ui.html |

1. Call `POST /api/auth/login` in the auth Swagger page and copy `accessToken`.
2. On any Swagger page click **Authorize**, paste the token (without `Bearer `), click Authorize.
3. Protected endpoints now work. Tokens expire after 15 minutes — log in again if you get 401.

Turn Swagger off with `SWAGGER_ENABLED=false` in `.env`.

## 10. Start the frontend

In a new terminal (the gateway from step 6 must be running):
```bash
cd WorkFlowPro/frontend
npm install                    # first time only
npm run dev                    # http://localhost:5173
```
Open http://localhost:5173 and sign in with `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`
from your `.env`, or click "Create one" to register a new (EMPLOYEE) account.

Optional: `cp .env.example .env.local` in `frontend/` to change `VITE_API_BASE_URL`
(default `http://localhost:9080`). The gateway already allows the origin `http://localhost:5173`
(`CORS_ALLOWED_ORIGINS`).

Production build: `npm run build` (output in `frontend/dist`). Lint: `npm run lint`.
Details: [frontend.md](frontend.md).

### A quick tour
1. Sign in as the admin → **Projects → New project** → create it → click **Active**.
2. In another browser (or private window) register a second user.
3. Back as admin: on the project page click **Add member**, search the new user, **Add**.
4. **New task** → give it a title, priority and the new user as assignee.
5. Sign in as the new user: the task is on the dashboard under "My tasks". Open it → **In progress** → **In review**.
6. As admin open the task → **Completed**. The **Activity** list shows every step.
7. **Users** (admin only) → **Edit roles** to make someone a TEAM_LEAD or PROJECT_MANAGER.

## 11. Stopping everything

```bash
# Ctrl+C in each service terminal and in the frontend terminal, then:
docker compose stop            # keeps the data
# docker compose down -v       # deletes the database volume too (full reset)
```

## Problems?

See [troubleshooting.md](troubleshooting.md). The most common ones:
- `password authentication failed` or `Value: "${JWT_SECRET}"` → you forgot `set -a; source .env; set +a` in that terminal.
- `JWT_SECRET must be at least 32 characters` → create one with `openssl rand -base64 48`.
- `Port ... already in use` → change the port variable in `.env` (e.g. `AUTH_SERVICE_PORT`).
- Tests fail with `Could not find a valid Docker environment` → Docker is not running or needs sudo.
