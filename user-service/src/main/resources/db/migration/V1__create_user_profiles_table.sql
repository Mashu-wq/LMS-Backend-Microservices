-- ============================================================
-- V1: Create user_profiles table
-- ============================================================

CREATE TABLE IF NOT EXISTS user_profiles (
    user_id     UUID         NOT NULL,
    email       VARCHAR(255) NOT NULL,
    first_name  VARCHAR(50)  NOT NULL,
    last_name   VARCHAR(50)  NOT NULL,
    bio         VARCHAR(500),
    avatar_url  VARCHAR(2048),
    role        VARCHAR(20)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id),
    CONSTRAINT uq_user_profiles_email UNIQUE (email),
    CONSTRAINT chk_user_profiles_role   CHECK (role IN ('ADMIN', 'INSTRUCTOR', 'STUDENT')),
    CONSTRAINT chk_user_profiles_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_email  ON user_profiles (email);
CREATE INDEX IF NOT EXISTS idx_user_profiles_role   ON user_profiles (role);
CREATE INDEX IF NOT EXISTS idx_user_profiles_status ON user_profiles (status);

COMMENT ON TABLE user_profiles IS 'User profile data — source of truth for identity display across the platform';
COMMENT ON COLUMN user_profiles.user_id IS 'UUID from auth-service — shared key across services';
