-- ============================================================
-- V5: Add device tracking to refresh tokens
-- ============================================================

ALTER TABLE refresh_tokens
    ADD COLUMN device_name VARCHAR(255),
ADD COLUMN ip_address VARCHAR(50),
ADD COLUMN user_agent TEXT,
ADD COLUMN last_used_at TIMESTAMPTZ;

CREATE INDEX idx_refresh_tokens_last_used
    ON refresh_tokens(last_used_at);

COMMENT ON COLUMN refresh_tokens.device_name IS 'Device identifier (browser or mobile)';
COMMENT ON COLUMN refresh_tokens.ip_address IS 'Client IP address';
COMMENT ON COLUMN refresh_tokens.user_agent IS 'Full user agent string';
COMMENT ON COLUMN refresh_tokens.last_used_at IS 'Last time this token was used';