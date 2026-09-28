# Flyway Migrations

**Rule: the database is never changed by hand.** Every table, column, index, constraint and seed row
comes from a versioned SQL file that is committed to Git.

## How it works

- Each service has its own migrations in `<service>/src/main/resources/db/migration/` and applies them
  to **its own** database when it starts (Spring Boot runs Flyway before the app is ready).
- Flyway records every applied file in the table `flyway_schema_history` of that database, with a checksum.
- Order = version number: `V1` → `V2` → `V3`. A file that was already applied is never edited
  (its checksum would change and the service would refuse to start) — add a new version instead.
- Hibernate only **validates** the schema (`spring.jpa.hibernate.ddl-auto: validate`): if an entity does not
  match the tables, the service fails at startup instead of silently changing the database.
- Dependencies: `spring-boot-starter-flyway` (in Boot 4, `flyway-core` alone is not enough) and
  `flyway-database-postgresql` (PostgreSQL support, a separate jar since Flyway 10). Flyway version 11.14.
- Config (each service):
  ```yaml
  spring:
    flyway:
      enabled: true
      locations: classpath:db/migration
  ```

## All migrations (in order)

| Service / database | Version | File | What it does |
|---|---|---|---|
| auth-service / `auth_db` | 1 | `V1__init.sql` | Baseline. No tables; proves Flyway is connected and creates `flyway_schema_history` |
| auth-service / `auth_db` | 2 | `V2__create_users_and_roles.sql` | Tables `roles`, `users`, `user_roles`; inserts the 4 roles (1 ADMIN, 2 PROJECT_MANAGER, 3 TEAM_LEAD, 4 EMPLOYEE); lower-case email check; index on `user_roles.role_id` |
| auth-service / `auth_db` | 3 | `V3__create_refresh_tokens.sql` | Table `refresh_tokens` (hashed token, expiry, revoked flag), FK to users, index on `user_id` |
| project-service / `project_db` | 1 | `V1__init.sql` | Baseline, no tables |
| project-service / `project_db` | 2 | `V2__create_projects.sql` | Tables `projects` (status + date checks, `version`) and `project_members` (composite PK, FK cascade); unique index on `LOWER(name)`; indexes on `manager_id`, `status`, `project_members.user_id` |
| task-service / `task_db` | 1 | `V1__init.sql` | Baseline, no tables |
| task-service / `task_db` | 2 | `V2__create_tasks_and_history.sql` | Tables `tasks` (status/priority checks, generated `priority_rank`, `version`) and `task_history` (identity PK, action check, FK cascade); indexes on (project_id, status), assignee_id, partial index on open tasks' due_date, (task_id, created_at DESC) |

Current versions: **auth_db V3, project_db V2, task_db V2.** Full table descriptions: [database.md](database.md).

## Run the migrations

Nothing to run by hand — start the service:
```bash
set -a; source .env; set +a
java -jar auth-service/target/auth-service-0.1.0-SNAPSHOT.jar
```
Log on the first start: `Successfully applied 3 migrations to schema "public", now at version v3`.
On later starts: `Schema "public" is up to date. No migration necessary.`

## Verify the migrations

```bash
set -a; source .env; set +a
for db in AUTH PROJECT TASK; do
  u=${db}_DB_USER; p=${db}_DB_PASSWORD; n=${db}_DB_NAME
  echo "== ${!n}"
  docker exec -e PGPASSWORD=${!p} workflowpro-postgres psql -U ${!u} -d ${!n} \
    -c 'select version, description, success from flyway_schema_history order by installed_rank'
done
```
Every row must show `success = t`.

The tests also check migrations: each service's `*ApplicationTests.flywayMigrationsAreApplied` runs all
migrations on a fresh Testcontainers PostgreSQL and asserts the current version (auth 3, project 2, task 2)
and that nothing is pending. A broken migration fails `mvn install` before it reaches your database.

## Add a new migration

1. Create `<service>/src/main/resources/db/migration/V<next>__<what_it_does>.sql`
   (two underscores, e.g. `V3__add_task_comments.sql`).
2. Change the JPA entity to match.
3. Update the expected version in that service's `*ApplicationTests.flywayMigrationsAreApplied`.
4. `mvn -pl <service> -am install` — runs the migration on a fresh test database.
5. Restart the service — Flyway applies it to your local database.
6. Add a row to the table above and describe the change in [database.md](database.md) and CHANGELOG.md.

## Problems

- **`Validate failed: Migration checksum mismatch`** — an applied file was edited. Undo the edit and
  create a new version. (Local-only data: `docker compose down -v` resets everything.)
- **`Schema-validation: missing column`** — the entity has a field without a migration. Add the migration.
- More in [troubleshooting.md](troubleshooting.md).
