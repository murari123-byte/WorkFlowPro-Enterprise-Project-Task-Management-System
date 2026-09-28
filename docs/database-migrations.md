# Database Migrations (Flyway)

**Rule: never change the database by hand.** Every schema or reference-data change is a
Flyway migration file committed to Git.

- Location (per service): `src/main/resources/db/migration/`
- Naming: `V<number>__<description>.sql`, e.g. `V1__create_users_table.sql`
- A migration that has been applied is never edited — add a new one instead.

## How it runs

- Each service runs its own migrations against its own database when it starts.
- Flyway records what it applied in the `flyway_schema_history` table of that database.
- The context test `flywayMigrationsAreApplied` runs all migrations against a fresh Testcontainers
  database, so a broken migration fails `mvn install` before it reaches the dev database.
  **When you add a migration, update the expected version in that test.**

## Adding a migration

1. Create `<service>/src/main/resources/db/migration/V<next>__<what_it_does>.sql`.
2. Run `mvn -pl <service> test` (checks it on a fresh DB).
3. Restart the service — Flyway applies it to the dev DB.
4. Add a row to the log below.

## Migration log

| Service | Version | File | Description | Added in |
|---|---|---|---|---|
| auth-service | 1 | `V1__init.sql` | Baseline, no tables | Step 2 |
| project-service | 1 | `V1__init.sql` | Baseline, no tables | Step 2 |
| task-service | 1 | `V1__init.sql` | Baseline, no tables | Step 2 |
| notification-service | 1 | `V1__init.sql` | Baseline, no tables | Step 2 |
