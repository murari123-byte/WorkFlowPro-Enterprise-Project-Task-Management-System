# Troubleshooting

## Service fails to start: "Port 808x was already in use"
**Cause:** another application on the machine was already listening on 8080–8084.
**Fix (applied in Step 1):** WorkFlowPro defaults to ports **9080–9084**.
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
killed too (exit code 144). Stop services with `Ctrl+C`, or match a narrower pattern such as
`pkill -f 'auth-service-0.1.0-SNAPSHOT.jar'`.

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
