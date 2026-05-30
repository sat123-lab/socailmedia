-- =====================================================================
-- V9 — Referral & Invite system
-- ---------------------------------------------------------------------
-- Schema documentation. JPA `ddl-auto=update` is what actually evolves
-- the live MySQL schema today; this file is here so a clean install can
-- recover the structure deterministically (and to keep V1..Vn coverage
-- consistent for when Flyway is wired in).
-- =====================================================================

-- ---- 1. user.referral_code, user.referred_by_user_id -----------------

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS referral_code VARCHAR(16) NULL,
    ADD COLUMN IF NOT EXISTS referred_by_user_id BIGINT NULL;

-- Case-insensitive uniqueness is enforced in code; we still want a hard
-- DB-level guard for double-mints from concurrent transactions.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_referral_code
    ON users (referral_code);

-- Faster reverse lookups for "who did this user refer".
CREATE INDEX IF NOT EXISTS idx_users_referred_by
    ON users (referred_by_user_id);

-- ---- 2. referrals table ----------------------------------------------

CREATE TABLE IF NOT EXISTS referrals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    referrer_user_id BIGINT NOT NULL,
    referee_user_id BIGINT NULL,
    code VARCHAR(16) NOT NULL,
    source VARCHAR(24) NOT NULL DEFAULT 'LINK',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING_CLICK',
    creator_referral TINYINT(1) NOT NULL DEFAULT 0,

    -- Hashed fingerprints (sha256 hex). Never raw IPs / device ids.
    ip_hash CHAR(64) NULL,
    device_hash CHAR(64) NULL,
    ua_bucket VARCHAR(16) NULL,

    -- Free-form CSV of fraud tags appended by ReferralFraudService.
    fraud_flags VARCHAR(255) NOT NULL DEFAULT '',

    created_at DATETIME NOT NULL,
    signed_up_at DATETIME NULL,
    activated_at DATETIME NULL,

    PRIMARY KEY (id),
    -- One signup is credited to at most one inviter — even on retries.
    CONSTRAINT uq_referrals_referee UNIQUE (referee_user_id)
);

CREATE INDEX IF NOT EXISTS idx_referrals_referrer
    ON referrals (referrer_user_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_referrals_code
    ON referrals (code);

-- Used by the velocity / device-reuse fraud probes.
CREATE INDEX IF NOT EXISTS idx_referrals_ip_hash
    ON referrals (ip_hash, created_at);
