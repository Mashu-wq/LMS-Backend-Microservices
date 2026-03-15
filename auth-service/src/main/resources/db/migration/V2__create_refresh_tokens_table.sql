-- ============================================================
-- V2: Create refresh_tokens table
-- ============================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token_hash  TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMPTZ,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at)
    WHERE revoked = FALSE;  -- Partial index — only active tokens need expiry lookups

COMMENT ON TABLE refresh_tokens IS 'Stored refresh tokens (SHA-256 hashed). Enables rotation and revocation.';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of the raw token. Raw token is never stored.';
