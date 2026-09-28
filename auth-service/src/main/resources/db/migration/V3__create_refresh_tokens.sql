-- V3: refresh tokens.
-- Only a SHA-256 hash of each token is stored, so a database leak does not leak usable tokens.
-- A token is single-use: refreshing sets revoked_at and issues a new one (rotation).

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL,   -- hex SHA-256 of the raw token
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,            -- NULL = still usable
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
