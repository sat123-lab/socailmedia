-- =============================================================================
--  V7__fix_identity_algorithm_column.sql
--
--  Widens user_identity_keys.algorithm from VARCHAR(16) → VARCHAR(64).
--
--  The original 16-char limit truncated the default tag
--  "X25519-Ed25519-AESGCM" (21 chars) emitted by the entity's @PrePersist
--  hook, surfacing as:
--      Data truncation: Data too long for column 'algorithm' at row 1
--  on every fresh E2EE bootstrap.
--
--  Idempotent — runs only if the column is still narrower than 64 chars.
-- =============================================================================
ALTER TABLE user_identity_keys
    MODIFY COLUMN algorithm VARCHAR(64) NOT NULL;
