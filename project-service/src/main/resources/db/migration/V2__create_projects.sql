-- V2: projects and their members.
-- manager_id, created_by and user_id are user ids owned by auth-service (a different database),
-- so there is no foreign key to a users table: the service checks them through auth-service's API.

CREATE TABLE projects (
    id          UUID          PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    description VARCHAR(2000),
    status      VARCHAR(20)   NOT NULL,
    start_date  DATE,
    end_date    DATE,
    manager_id  UUID          NOT NULL,
    created_by  UUID          NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,   -- optimistic locking (JPA @Version)
    CONSTRAINT ck_projects_status CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_projects_dates CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);

-- Project names are unique, ignoring upper/lower case
CREATE UNIQUE INDEX uk_projects_name_lower ON projects (LOWER(name));
CREATE INDEX idx_projects_manager_id ON projects (manager_id);
CREATE INDEX idx_projects_status ON projects (status);

CREATE TABLE project_members (
    project_id UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    added_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

-- "Which projects is this user in?" is the most common query (every list / access check)
CREATE INDEX idx_project_members_user_id ON project_members (user_id);
