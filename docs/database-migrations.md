# Database Migrations (Flyway)

**Rule: never change the database by hand.** Every schema or reference-data change is a
Flyway migration file committed to Git.

- Location (per service): `src/main/resources/db/migration/`
- Naming: `V<number>__<description>.sql`, e.g. `V1__create_users_table.sql`
- A migration that has been applied is never edited — add a new one instead.

## Migration log

| Service | Version | File | Description | Added in |
|---|---|---|---|---|
| – | – | – | No migrations yet (Flyway is added in Step 2) | – |
