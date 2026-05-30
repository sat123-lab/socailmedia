-- =====================================================================
-- V10 — Comments Ecosystem upgrade
-- ---------------------------------------------------------------------
-- Threaded replies, reactions, mentions, pin, edit, soft-delete, reports.
--
-- JPA `ddl-auto=update` evolves the live schema today, but this file
-- documents the structure so a clean install can recover it
-- deterministically and so the V1..Vn sequence stays contiguous.
-- =====================================================================

-- ---- 1. New columns on `comments` ------------------------------------

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS depth INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reply_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pinned TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pinned_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS pinned_by_user_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS edited_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS deleted TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS hot_score DOUBLE NOT NULL DEFAULT 0;

-- Hot indexes for the most common queries (top-level page, replies page,
-- "pinned first", admin/report queue).
CREATE INDEX IF NOT EXISTS idx_comments_post_parent_created
    ON comments (post_id, parent_id, created_at);
CREATE INDEX IF NOT EXISTS idx_comments_post_pinned_hot
    ON comments (post_id, pinned, hot_score);
CREATE INDEX IF NOT EXISTS idx_comments_parent_created
    ON comments (parent_id, created_at);

-- Only one pinned comment per post — enforced as a partial unique index.
-- MySQL 8 can't do partial indexes, so we model it via a regular composite
-- index + a service-layer check. (The check is the source of truth; the
-- index just keeps lookups cheap.)
CREATE INDEX IF NOT EXISTS idx_comments_post_pinned
    ON comments (post_id, pinned);

-- ---- 2. comment_reactions --------------------------------------------
-- One row per (comment, user) — replacing the previous comment_likes
-- model. The old comment_likes table is *kept* for backward compatibility
-- and stays in sync with reactions where emoji='LIKE' via the service.

CREATE TABLE IF NOT EXISTS comment_reactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    -- The 7 supported reactions: LIKE / LAUGH / FIRE / LOVE / WOW / SAD / CLAP.
    -- Stored as VARCHAR so adding new reactions in v2 doesn't require a migration.
    emoji VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_comment_reactions_user UNIQUE (comment_id, user_id),
    CONSTRAINT fk_comment_reactions_comment FOREIGN KEY (comment_id)
        REFERENCES comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_reactions_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_comment_reactions_emoji
    ON comment_reactions (comment_id, emoji);

-- ---- 3. comment_mentions ---------------------------------------------
-- One row per (comment, mentioned_user). MentionService dedups on write.

CREATE TABLE IF NOT EXISTS comment_mentions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    mentioned_user_id BIGINT NOT NULL,
    -- Display token as the author typed it (e.g. "@Jane Smith") — used for
    -- the inline highlight when the mentioned user later renames themselves.
    display VARCHAR(64) NOT NULL,
    -- Character offset within Comment.text — lets the client render the
    -- highlight without re-running the regex.
    start_index INT NOT NULL DEFAULT 0,
    end_index INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_comment_mentions UNIQUE (comment_id, mentioned_user_id),
    CONSTRAINT fk_comment_mentions_comment FOREIGN KEY (comment_id)
        REFERENCES comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_mentions_user FOREIGN KEY (mentioned_user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_comment_mentions_user
    ON comment_mentions (mentioned_user_id, created_at);

-- ---- 4. comment_reports ----------------------------------------------
-- Plain abuse-report table — feeds the admin moderation queue.

CREATE TABLE IF NOT EXISTS comment_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    reporter_user_id BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    note VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    -- A user can only report a given comment once (idempotent), but multiple
    -- users can independently report the same comment.
    CONSTRAINT uq_comment_reports UNIQUE (comment_id, reporter_user_id)
);

CREATE INDEX IF NOT EXISTS idx_comment_reports_status_created
    ON comment_reports (status, created_at);
