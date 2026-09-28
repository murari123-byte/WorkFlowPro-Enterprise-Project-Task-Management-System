-- V2: users and roles for authentication.
-- A user can have several roles (many-to-many through user_roles).
-- Roles are fixed reference data: they are only ever added or changed by a new migration.

CREATE TABLE roles (
    id   SMALLINT    PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name)
);

INSERT INTO roles (id, name) VALUES
    (1, 'ADMIN'),
    (2, 'PROJECT_MANAGER'),
    (3, 'TEAM_LEAD'),
    (4, 'EMPLOYEE');

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt hash, never the plain password
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    -- the application stores emails in lower case; this makes the unique check case-insensitive
    CONSTRAINT ck_users_email_lowercase CHECK (email = LOWER(email))
);

CREATE TABLE user_roles (
    user_id UUID     NOT NULL,
    role_id SMALLINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);
