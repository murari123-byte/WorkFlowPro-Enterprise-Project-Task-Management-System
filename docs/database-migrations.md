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

## auth_db schema (current: V3)

```
roles            users                         refresh_tokens
─────            ─────                         ──────────────
id   SMALLINT PK id            UUID PK ◄──┐    id          UUID PK
name VARCHAR UQ  email         VARCHAR UQ │    user_id     UUID FK → users (cascade)
      ▲          password_hash VARCHAR    ├──  token_hash  VARCHAR(64) UQ
      │          first_name    VARCHAR    │    expires_at  TIMESTAMPTZ
      │          last_name     VARCHAR    │    revoked_at  TIMESTAMPTZ NULL
      │          enabled       BOOLEAN    │    created_at  TIMESTAMPTZ
      │          created_at / updated_at  │
      │                                   │
      └── user_roles (user_id FK, role_id FK, PK both) ──┘
```

## Migration log

| Service | Version | File | Description | Added in |
|---|---|---|---|---|
| auth-service | 1 | `V1__init.sql` | Baseline, no tables | Step 2 |
| auth-service | 2 | `V2__create_users_and_roles.sql` | `roles` (seeded: ADMIN, PROJECT_MANAGER, TEAM_LEAD, EMPLOYEE), `users`, `user_roles` | Phase 2 / Step 1 |
| auth-service | 3 | `V3__create_refresh_tokens.sql` | `refresh_tokens` (hashed, FK to users) | Phase 2 / Step 1 |
| project-service | 1 | `V1__init.sql` | Baseline, no tables | Step 2 |
| task-service | 1 | `V1__init.sql` | Baseline, no tables | Step 2 |
| notification-service | 1 | `V1__init.sql` | Baseline, no tables | Step 2 |
