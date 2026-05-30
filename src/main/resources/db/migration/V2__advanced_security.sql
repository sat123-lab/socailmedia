-- =============================================================================
--  V2__advanced_security.sql
--  Advanced privacy features — disappearing messages, sessions, vault,
--  temp bans, admin tier columns.
-- -----------------------------------------------------------------------------
--  As with V1, Hibernate `ddl-auto=update` will create these objects
--  automatically. This file is the authoritative reference for the same
--  schema and can be promoted to Flyway/Liquibase later.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Disappearing / deletion lifecycle on chat_messages
-- -----------------------------------------------------------------------------
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS expires_in_seconds     INT,
    ADD COLUMN IF NOT EXISTS expires_at             DATETIME,
    ADD COLUMN IF NOT EXISTS read_at                DATETIME,
    ADD COLUMN IF NOT EXISTS deleted_for_everyone   TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted_at             DATETIME,
    ADD COLUMN IF NOT EXISTS deletion_reason        VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_chat_expires_at ON chat_messages(expires_at);

-- -----------------------------------------------------------------------------
-- Device sessions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_sessions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    refresh_family_id   VARCHAR(64)  NOT NULL,
    device_fingerprint  VARCHAR(64),
    platform            VARCHAR(64),
    device_name         VARCHAR(128),
    user_agent          VARCHAR(256),
    ip_address          VARCHAR(48),
    location            VARCHAR(96),
    created_at          DATETIME     NOT NULL,
    last_seen_at        DATETIME     NOT NULL,
    revoked             TINYINT(1)   NOT NULL DEFAULT 0,
    revoked_at          DATETIME,
    UNIQUE KEY idx_session_family (refresh_family_id),
    KEY idx_session_user (user_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Private vault
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vault_items (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id             BIGINT        NOT NULL,
    blob_id              VARCHAR(64)   NOT NULL,
    encrypted_filename   VARCHAR(512),
    encrypted_mime_type  VARCHAR(128),
    metadata_nonce       VARCHAR(24),
    content_nonce        VARCHAR(24)   NOT NULL,
    size_bytes           BIGINT        NOT NULL,
    kind                 VARCHAR(16)   NOT NULL,
    created_at           DATETIME      NOT NULL,
    UNIQUE KEY idx_vault_blob (blob_id),
    KEY idx_vault_owner (owner_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Temp bans
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS temp_bans (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject     VARCHAR(96)  NOT NULL,
    reason      VARCHAR(128),
    issued_at   DATETIME     NOT NULL,
    expires_at  DATETIME     NOT NULL,
    KEY idx_ban_subject (subject),
    KEY idx_ban_expires (expires_at)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Admin tier columns on users
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS admin_role            VARCHAR(32),
    ADD COLUMN IF NOT EXISTS last_admin_login_at   DATETIME;
