-- ============================================================
-- V1: Create users table
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    email                 VARCHAR(255) NOT NULL,
    password_hash         TEXT         NOT NULL,
    first_name            VARCHAR(50)  NOT NULL,
    last_name             VARCHAR(50)  NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'INSTRUCTOR', 'STUDENT'))
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);

COMMENT ON TABLE users IS 'Auth service user credentials and account state';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash, strength=12';
COMMENT ON COLUMN users.locked_until IS 'NULL means not locked. Set after max failed attempts.';
