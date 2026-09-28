-- V2: tasks and their activity history.
-- project_id, assignee_id, created_by and actor_id are ids owned by other services
-- (project-service, auth-service), so they have no foreign keys here.

CREATE TABLE tasks (
    id            UUID          PRIMARY KEY,
    project_id    UUID          NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    description   VARCHAR(5000),
    status        VARCHAR(20)   NOT NULL,
    priority      VARCHAR(10)   NOT NULL,
    -- Numeric version of priority, computed by PostgreSQL, so sorting gives LOW < MEDIUM < HIGH < URGENT
    priority_rank SMALLINT GENERATED ALWAYS AS (
                      CASE priority WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'HIGH' THEN 3 WHEN 'URGENT' THEN 4 END
                  ) STORED,
    due_date      DATE,
    assignee_id   UUID,
    created_by    UUID          NOT NULL,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,   -- optimistic locking (JPA @Version)
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

-- Task list of one project, usually filtered by status
CREATE INDEX idx_tasks_project_status ON tasks (project_id, status);
-- "My tasks"
CREATE INDEX idx_tasks_assignee_id ON tasks (assignee_id);
-- Overdue checks only look at open tasks, so the index only contains open tasks (partial index)
CREATE INDEX idx_tasks_open_due_date ON tasks (due_date) WHERE status NOT IN ('COMPLETED', 'CANCELLED');

CREATE TABLE task_history (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id    UUID         NOT NULL,
    action     VARCHAR(30)  NOT NULL,
    field      VARCHAR(30),
    old_value  VARCHAR(500),
    new_value  VARCHAR(500),
    actor_id   UUID         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_task_history_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_task_history_action CHECK (action IN
        ('CREATED', 'UPDATED', 'ASSIGNED', 'STATUS_CHANGED', 'PRIORITY_CHANGED', 'DUE_DATE_CHANGED'))
);

-- History of one task, newest first
CREATE INDEX idx_task_history_task_created ON task_history (task_id, created_at DESC);
