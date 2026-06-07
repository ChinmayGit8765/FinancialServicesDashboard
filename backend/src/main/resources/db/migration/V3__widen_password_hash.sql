-- V3__widen_password_hash.sql
-- Widen app_users.password_hash from VARCHAR(100) to VARCHAR(255) (WR-02).
-- BCrypt output is 60 chars, but Argon2 / SCrypt encoders can produce 95+ chars.
-- 255 characters future-proofs against algorithm changes per Spring Security guidance.
ALTER TABLE app_users
    ALTER COLUMN password_hash TYPE VARCHAR(255);
