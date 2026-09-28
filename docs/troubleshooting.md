# Troubleshooting

## Service fails to start: "Port ... was already in use"
WorkFlowPro uses **9080–9083** (8080–8084 were already taken on the development machine).
If a port is still taken, check who holds it and override it with an env var:
```bash
ss -ltnp | grep 9081
AUTH_SERVICE_PORT=9181 mvn -pl auth-service spring-boot:run
```

## Warning during tests: "Mockito is currently self-attaching to enable the inline-mock-maker"
Harmless on Java 21. Tests still pass. It can be silenced later by configuring Mockito as a
Java agent in the Surefire plugin; not needed now.

## Gateway health shows `discoveryComposite: UNKNOWN`
Expected. No service registry (Eureka etc.) is used; routes will use fixed URLs from
environment variables. Overall gateway status is still `UP`.

## `pkill -f <pattern>` kills your own terminal command
If the pattern also matches the command line of the shell running `pkill`, that shell is
killed too (exit code 144). Stop services with `Ctrl+C`, or anchor the pattern:
`pkill -f '^java -jar .*-0\.1\.0-SNAPSHOT\.jar'`.

## Service fails: `password authentication failed for user "auth_user"`
**Cause:** the env vars from `.env` are not loaded in this terminal (the password has no default).
**Fix:** `set -a; source .env; set +a`, then start the service again.

## `docker compose up` fails: `required variable ... is missing a value`
**Cause:** no `.env` file, or a variable is empty. **Fix:** `cp .env.example .env` and fill it in.

## Changed a DB password in `.env` but the service still can't log in
**Cause:** the init script only runs when the data volume is empty; the old password is still in the DB.
**Fix (dev only — deletes all local data):**
```bash
docker compose down -v      # -v removes the workflowpro-pgdata volume
docker compose up -d        # init script runs again with the new values
```

## Tests fail with `Could not find a valid Docker environment`
**Cause:** Testcontainers needs Docker. **Fix:** start Docker and check `docker ps` works without `sudo`.

## Port 5440 already in use
Another Postgres is on 5440. Set `DB_PORT=5441` (any free port) in `.env`, then `docker compose up -d`.
Note: a different PostgreSQL already runs on 5435 on the dev machine — WorkFlowPro never uses it.

## auth-service fails: `Binding to target ...JwtProperties failed` / `Value: "${JWT_SECRET}"`
**Cause:** `JWT_SECRET` is not set in this terminal. **Fix:** `set -a; source .env; set +a`.

## auth-service fails: `JWT_SECRET must be at least 32 characters`
**Cause:** the secret is too short for HS256 (the `.env.example` placeholder is deliberately too short).
**Fix:** `openssl rand -base64 48`, put the result in `.env` as `JWT_SECRET`.

## `401` on a protected endpoint with a token that worked earlier
The access token expired (15 min by default) — call `/api/auth/refresh`. Also happens if `JWT_SECRET`
or `JWT_ISSUER` changed since the token was issued.

## `/api/auth/refresh` returns 401 for a token that was never used
If an **older** refresh token of the same user was replayed, reuse detection revoked all that user's
sessions. Log in again. The log shows `Revoked refresh token reused for user ...`.

## Gateway returns `503 Service Unavailable`
**Cause:** the target service is not running, or its `*_SERVICE_URL` points to the wrong place.
**Fix:** start the service, check `curl localhost:<port>/actuator/health`, check the URL env vars.
The gateway log shows `Backend service unreachable for /api/...`.

## Gateway returns `404` for an `/api/...` path
The path does not start with a routed prefix (`/api/auth`, `/api/users`, `/api/projects`, `/api/tasks`).
Check for typos such as `/api/project/` (singular). `/api/notifications` and `/api/auth/me` were removed —
use `/api/users/me`.

## Browser: "blocked by CORS policy" / preflight returns 403
**Cause:** the page's origin is not in `CORS_ALLOWED_ORIGINS`.
**Fix:** add it (exact scheme + host + port, e.g. `http://localhost:5173`), restart the gateway.
Call the gateway (`:9080`) from the browser, never a service port — services have no CORS config.

## Gateway routes ignored after upgrading Spring Cloud
Spring Cloud Gateway 5 reads `spring.cloud.gateway.server.webflux.routes`. The old
`spring.cloud.gateway.routes` key is silently ignored, so every path returns 404.

## `/actuator/health` shows only `{"status":"UP"}` without database details
Expected: details are shown only to authorized callers (`show-details: when-authorized`). `UP` already
includes the database check — a failing database makes it `DOWN`.

## A request returns `404` but the project/task exists
You are not a member of that project. Non-members get 404 on purpose (they must not learn it exists).
Ask the project manager or an ADMIN to add you.

## `422 Unprocessable Content` when changing a status
The move is not allowed by the workflow (e.g. PLANNING → COMPLETED), the project is ON_HOLD / closed,
or you are the assignee trying to complete/cancel. The message lists the allowed statuses.

## `409 Conflict` "This record was changed by someone else"
Two saves of the same project/task at once (optimistic locking). Reload the page and try again.

## `409` when deleting a project
It still has tasks. Delete them first, or set the project to CANCELLED.

## New role not visible after an admin changed it
Roles are inside the access token. They appear after the next refresh (≤ 15 min) or a new login.

## Frontend: "Cannot reach the server. Check that the API Gateway is running."
The gateway on 9080 is not running, or `VITE_API_BASE_URL` points elsewhere. Start it, or fix
`frontend/.env.local` and restart `npm run dev`.

## Frontend: logged out after a password change
Expected — a password change signs you out on every device. Sign in with the new password.

## Frontend: `npm run dev` says port 5173 is in use
Another Vite app is running. Stop it, or run `npx vite --port 5174` and add `http://localhost:5174`
to `CORS_ALLOWED_ORIGINS`, then restart the gateway.

## Old `notification_db` still exists in the database
It was created before notification-service was removed. It is unused; `docker compose down -v`
(deletes all local data) gives a clean database.

## `docker compose`: "no configuration file provided" although `docker-compose.yml` exists
Docker installed as a **snap** (Ubuntu) cannot read files under `/tmp` or other folders outside your home
directory. Clone the project into your home folder (e.g. `~/WorkFlowPro`).
