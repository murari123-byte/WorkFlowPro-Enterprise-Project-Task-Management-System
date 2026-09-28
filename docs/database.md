# Database Design

## Setup

- **PostgreSQL 16** (`postgres:16-alpine`) in Docker, container `workflowpro-postgres`, host port **5440**
  (`docker-compose.yml`). Data is kept in the named volume `workflowpro-pgdata`.
- On the **first** start, `docker/postgres/init/01-create-service-databases.sh` creates three databases,
  each owned by its own login, and removes `CONNECT` from everyone else:

  | Database | Owner login | Used by |
  |---|---|---|
  | `auth_db` | `auth_user` | auth-service |
  | `project_db` | `project_user` | project-service |
  | `task_db` | `task_user` | task-service |

  So `auth_user` physically cannot open `task_db` (verified: "User does not have CONNECT privilege").
- The script only creates **empty** databases and logins. **All tables are created by Flyway**
  ([migrations.md](migrations.md)). Hibernate runs with `ddl-auto: validate`: it checks that the entities
  match the tables and never changes them.
- Names and passwords come from `.env` (`AUTH_DB_*`, `PROJECT_DB_*`, `TASK_DB_*`, `POSTGRES_ADMIN_*`).

## Rules used in every schema

- Primary keys are **UUID** (created by Hibernate), except `roles` (fixed small ids) and `task_history`
  (identity `BIGINT`, append-only).
- Every changeable row has `created_at` / `updated_at` (`TIMESTAMPTZ`, filled by Hibernate).
- `version BIGINT` on `projects` and `tasks` for optimistic locking (`@Version`) → `409` on a lost update.
- Enum values are stored as text and protected by `CHECK` constraints.
- **Ids from another service have no foreign key** (the row lives in another database). The owning
  service is asked through its API instead. They are marked "(→ other service)" below.

---

## auth_db (auth-service) — Flyway V1–V3

```
roles                    user_roles                     users                          refresh_tokens
─────                    ──────────                     ─────                          ──────────────
id    SMALLINT PK ◄──── role_id SMALLINT FK            id            UUID PK ◄──┬──── user_id    UUID FK (cascade)
name  VARCHAR(30) UQ     user_id UUID FK (cascade) ───► email         VARCHAR(255) UQ  id         UUID PK
                         PK (user_id, role_id)          password_hash VARCHAR(100)     token_hash VARCHAR(64) UQ
                                                        first_name    VARCHAR(100)     expires_at TIMESTAMPTZ
                                                        last_name     VARCHAR(100)     revoked_at TIMESTAMPTZ NULL
                                                        enabled       BOOLEAN          created_at TIMESTAMPTZ
                                                        created_at, updated_at
```

| Table | Purpose | Constraints / indexes |
|---|---|---|
| `roles` | The 4 roles, inserted by V2: 1 ADMIN, 2 PROJECT_MANAGER, 3 TEAM_LEAD, 4 EMPLOYEE | PK `id`, unique `name` |
| `users` | Accounts | unique `email`; `CHECK (email = LOWER(email))` (the app lower-cases emails, so uniqueness is case-insensitive); `password_hash` is BCrypt, never the password |
| `user_roles` | **Many-to-many** users ↔ roles | PK (`user_id`, `role_id`); FK to users `ON DELETE CASCADE`; FK to roles; index `idx_user_roles_role_id` |
| `refresh_tokens` | One row per issued refresh token | unique `token_hash` (SHA-256 hex of the token — the raw token is never stored); FK to users `ON DELETE CASCADE`; index `idx_refresh_tokens_user_id`; `revoked_at` NULL = still usable |

JPA mapping: `User.roles` is `@ManyToMany(fetch = EAGER)` with `@JoinTable(user_roles)` (roles are needed
on every login). `RefreshToken.user` is `@ManyToOne(fetch = LAZY)`.

---

## project_db (project-service) — Flyway V1–V2

```
projects                                          project_members
────────                                          ───────────────
id          UUID PK ◄─────────────────────────── project_id UUID FK (cascade)
name        VARCHAR(150)   unique on LOWER(name)  user_id    UUID  (→ auth-service user)
description VARCHAR(2000) NULL                    added_at   TIMESTAMPTZ
status      VARCHAR(20)    CHECK (5 values)       PK (project_id, user_id)
start_date  DATE NULL
end_date    DATE NULL      CHECK end >= start
manager_id  UUID           (→ auth-service user)
created_by  UUID           (→ auth-service user)
created_at, updated_at, version
```

| Table | Purpose | Constraints / indexes |
|---|---|---|
| `projects` | Projects | `ck_projects_status`, `ck_projects_dates`; unique index `uk_projects_name_lower` on `LOWER(name)`; `idx_projects_manager_id`; `idx_projects_status` |
| `project_members` | **One-to-many** project → members | PK (`project_id`, `user_id`) — a user is a member once; FK to projects `ON DELETE CASCADE`; `idx_project_members_user_id` ("which projects is this user in?" — used by every list and access check) |

JPA mapping: members are an `@ElementCollection` of the `@Embeddable` `ProjectMember` (they have no
life of their own outside a project). The collection is LAZY: project lists never read it.
The project manager is always also a row in `project_members`.

---

## task_db (task-service) — Flyway V1–V2

```
tasks                                                          task_history
─────                                                          ────────────
id            UUID PK ◄──────────────────────────────────────── task_id    UUID FK (cascade)
project_id    UUID           (→ project-service project)        id         BIGINT identity PK
title         VARCHAR(200)                                      action     VARCHAR(30) CHECK (6 values)
description   VARCHAR(5000) NULL                                field      VARCHAR(30) NULL
status        VARCHAR(20)    CHECK (5 values)                   old_value  VARCHAR(500) NULL
priority      VARCHAR(10)    CHECK (4 values)                   new_value  VARCHAR(500) NULL
priority_rank SMALLINT       GENERATED from priority (1–4)      actor_id   UUID (→ auth-service user)
due_date      DATE NULL                                         created_at TIMESTAMPTZ
assignee_id   UUID NULL      (→ auth-service user)
created_by    UUID           (→ auth-service user)
completed_at  TIMESTAMPTZ NULL
created_at, updated_at, version
```

| Table | Purpose | Constraints / indexes |
|---|---|---|
| `tasks` | Tasks | `ck_tasks_status`, `ck_tasks_priority`; `priority_rank` is a **generated column** (`LOW`=1 … `URGENT`=4) so sorting by priority is correct instead of alphabetical; `idx_tasks_project_status` (project, status); `idx_tasks_assignee_id`; **partial index** `idx_tasks_open_due_date` on `due_date` only for open tasks (overdue queries) |
| `task_history` | **One-to-many** task → activity entries, append-only | FK to tasks `ON DELETE CASCADE`; `ck_task_history_action`; `idx_task_history_task_created` (task_id, created_at DESC) for "newest first" |

JPA mapping: `Task.priorityRank` is read-only (`insertable = false, updatable = false`).
`TaskHistory` stores the task id as a plain column (it is only ever read by task id).

---

## Relationships summary

| Relationship | Kind | How it is enforced |
|---|---|---|
| user ↔ role | many-to-many | `user_roles` join table with 2 FKs |
| user → refresh tokens | one-to-many | FK `refresh_tokens.user_id` (cascade) |
| project → members | one-to-many | FK `project_members.project_id` (cascade), composite PK |
| task → history | one-to-many | FK `task_history.task_id` (cascade) |
| project → tasks | one-to-many **across services** | no FK; task-service asks project-service; project-service refuses to delete a project that has tasks |
| user → projects / tasks | across services | no FK; ids checked through auth-service's API |

## Useful queries (read-only)

```bash
set -a; source .env; set +a
# list tables of a database
docker exec -e PGPASSWORD=$TASK_DB_PASSWORD workflowpro-postgres psql -U $TASK_DB_USER -d $TASK_DB_NAME -c '\dt'
# describe a table (columns, indexes, constraints)
docker exec -e PGPASSWORD=$TASK_DB_PASSWORD workflowpro-postgres psql -U $TASK_DB_USER -d $TASK_DB_NAME -c '\d tasks'
```

Never change tables with SQL by hand — add a Flyway migration.

## Reset the local database

```bash
docker compose down -v     # deletes the volume (ALL local data)
docker compose up -d       # init script runs again; Flyway recreates the tables when services start
```
A volume created before the final review also contains an unused `notification_db`; the reset removes it.
