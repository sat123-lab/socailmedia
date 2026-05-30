-- =============================================================================
--  V1__security_baseline.sql
--  Reference DDL for the privacy / security layer.
-- -----------------------------------------------------------------------------
--  The application currently relies on Hibernate `ddl-auto=update`, so Spring
--  generates these tables / columns automatically on next startup. This file
--  is kept under db/migration so it can be promoted to Flyway / Liquibase
--  later without changes (statements are idempotent where possible).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Refresh tokens
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti                 VARCHAR(64)  NOT NULL,
    family_id           VARCHAR(64)  NOT NULL,
    user_id             BIGINT       NOT NULL,
    device_fingerprint  VARCHAR(128),
    issued_at           DATETIME     NOT NULL,
    expires_at          DATETIME     NOT NULL,
    revoked             TINYINT(1)   NOT NULL DEFAULT 0,
    revoked_at          DATETIME,
    UNIQUE KEY idx_refresh_jti (jti),
    KEY idx_refresh_family (family_id),
    KEY idx_refresh_user  (user_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Audit log
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    action          VARCHAR(64)  NOT NULL,
    actor_user_id   BIGINT,
    target_id       BIGINT,
    target_type     VARCHAR(64),
    remote_ip       VARCHAR(48),
    user_agent      VARCHAR(256),
    metadata        VARCHAR(1024),
    created_at      DATETIME NOT NULL,
    KEY idx_audit_actor (actor_user_id),
    KEY idx_audit_action(action),
    KEY idx_audit_at    (created_at)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Login attempts (brute-force counter)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS login_attempts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    identifier        VARCHAR(191) NOT NULL,
    failure_count     INT          NOT NULL DEFAULT 0,
    first_failure_at  DATETIME     NOT NULL,
    locked_until      DATETIME,
    UNIQUE KEY idx_login_id (identifier)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- E2EE identity & one-time pre-keys
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_identity_keys (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT      NOT NULL,
    identity_public_key_base64  VARCHAR(88) NOT NULL,
    signing_public_key_base64   VARCHAR(88) NOT NULL,
    algorithm                   VARCHAR(16) NOT NULL,
    key_version                 INT         NOT NULL,
    created_at                  DATETIME    NOT NULL,
    updated_at                  DATETIME,
    UNIQUE KEY idx_identity_user (user_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS one_time_prekeys (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    key_id              VARCHAR(36)  NOT NULL,
    public_key_base64   VARCHAR(88)  NOT NULL,
    signature_base64    VARCHAR(128),
    used                TINYINT(1)   NOT NULL DEFAULT 0,
    created_at          DATETIME     NOT NULL,
    consumed_at         DATETIME,
    KEY idx_prekey_user_used (user_id, used),
    KEY idx_prekey_key_id    (key_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Envelope encryption columns added to existing message tables
-- (Hibernate's `update` mode will add these automatically; SQL is here only
--  for migrations / fresh DB bootstrapping.)
-- -----------------------------------------------------------------------------
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS encrypted              TINYINT(1)  NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS encryption_algo        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS sender_ephemeral_key   VARCHAR(88),
    ADD COLUMN IF NOT EXISTS recipient_pre_key_id   VARCHAR(36),
    ADD COLUMN IF NOT EXISTS nonce                  VARCHAR(24);

ALTER TABLE social_group_messages
    ADD COLUMN IF NOT EXISTS encrypted              TINYINT(1)  NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS encryption_algo        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS sender_ephemeral_key   VARCHAR(88),
    ADD COLUMN IF NOT EXISTS nonce                  VARCHAR(24);

ALTER TABLE chat_room_messages
    ADD COLUMN IF NOT EXISTS encrypted              TINYINT(1)  NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS encryption_algo        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS sender_ephemeral_key   VARCHAR(88),
    ADD COLUMN IF NOT EXISTS nonce                  VARCHAR(24);
