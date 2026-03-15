-- ============================================================
-- V3: Seed default admin user (development only)
-- Password: Admin@123! (BCrypt strength=12)
-- IMPORTANT: Change or remove this in production!
-- ============================================================

INSERT INTO users (id, email, password_hash, first_name, last_name, role, enabled, email_verified)
VALUES (
    gen_random_uuid(),
    'admin@lms-platform.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj6HHSGXDJui',
    'System',
    'Admin',
    'ADMIN',
    TRUE,
    TRUE
)
ON CONFLICT (email) DO NOTHING;
