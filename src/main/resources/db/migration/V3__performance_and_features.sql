-- =============================================================================
--  V3__performance_and_features.sql
--
--  Production performance + new feature tables.
--    1. Hot-path indexes (feed, reels, search, notifications).
--    2. Reels engine tables.
--    3. Stories 2.0 (stickers, polls, reactions, highlights, close friends).
--    4. FCM token registry.
--    5. Verified / creator account columns on `users`.
--    6. Hashtag store.
-- -----------------------------------------------------------------------------
--  Hibernate `ddl-auto=update` will create the tables; this file is the
--  authoritative schema reference and can be promoted to Flyway later.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. HOT-PATH INDEXES
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_posts_user_created   ON posts(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_created        ON posts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_type           ON posts(type);

CREATE INDEX IF NOT EXISTS idx_likes_post           ON likes(post_id);
CREATE INDEX IF NOT EXISTS idx_likes_user           ON likes(user_id);

CREATE INDEX IF NOT EXISTS idx_comments_post        ON comments(post_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_comments_user        ON comments(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_follows_follower     ON follows(follower_id);
CREATE INDEX IF NOT EXISTS idx_follows_following    ON follows(following_id);

CREATE INDEX IF NOT EXISTS idx_chat_conv_created    ON chat_messages(conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_sender          ON chat_messages(sender_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notif_receiver       ON notifications(receiver_id, read, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_stories_user         ON stories(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stories_active       ON stories(active, expires_at);

-- -----------------------------------------------------------------------------
-- 2. REELS ENGINE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reels (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    caption           VARCHAR(2200),
    video_url         VARCHAR(512) NOT NULL,
    thumbnail_url     VARCHAR(512),
    audio_url         VARCHAR(512),
    audio_title       VARCHAR(200),
    duration_seconds  INT          NOT NULL DEFAULT 0,
    width             INT,
    height            INT,
    likes_count       INT          NOT NULL DEFAULT 0,
    comments_count    INT          NOT NULL DEFAULT 0,
    shares_count      INT          NOT NULL DEFAULT 0,
    views_count       BIGINT       NOT NULL DEFAULT 0,
    watch_time_seconds BIGINT      NOT NULL DEFAULT 0,
    trending_score    DOUBLE       NOT NULL DEFAULT 0,
    hashtags          VARCHAR(512),
    visibility        VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC',
    deleted           TINYINT(1)   NOT NULL DEFAULT 0,
    created_at        DATETIME     NOT NULL,
    KEY idx_reels_user (user_id, created_at DESC),
    KEY idx_reels_trending (trending_score DESC, created_at DESC),
    KEY idx_reels_visibility (visibility, deleted, created_at DESC)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reel_views (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    reel_id      BIGINT NOT NULL,
    user_id      BIGINT,
    watch_ms     INT    NOT NULL DEFAULT 0,
    completed    TINYINT(1) NOT NULL DEFAULT 0,
    created_at   DATETIME NOT NULL,
    KEY idx_reel_views_reel (reel_id),
    KEY idx_reel_views_user (user_id, created_at DESC)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reel_likes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reel_id     BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    created_at  DATETIME NOT NULL,
    UNIQUE KEY uq_reel_user (reel_id, user_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reel_comments (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reel_id     BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    text        VARCHAR(500) NOT NULL,
    created_at  DATETIME     NOT NULL,
    KEY idx_reel_comments_reel (reel_id, created_at DESC)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 3. STORIES 2.0
-- -----------------------------------------------------------------------------
ALTER TABLE stories
    ADD COLUMN IF NOT EXISTS music_url            VARCHAR(512),
    ADD COLUMN IF NOT EXISTS music_title          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS close_friends_only   TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sticker_json         TEXT,
    ADD COLUMN IF NOT EXISTS reaction_count       INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS story_polls (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id      BIGINT NOT NULL,
    question      VARCHAR(280) NOT NULL,
    option_a      VARCHAR(100) NOT NULL,
    option_b      VARCHAR(100) NOT NULL,
    votes_a       INT NOT NULL DEFAULT 0,
    votes_b       INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL,
    KEY idx_story_polls_story (story_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_poll_votes (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id    BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    choice     CHAR(1) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uq_poll_user (poll_id, user_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_reactions (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id   BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    emoji      VARCHAR(8) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uq_story_react (story_id, user_id, emoji),
    KEY idx_story_react_story (story_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_highlights (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    title         VARCHAR(60) NOT NULL,
    cover_url     VARCHAR(512),
    created_at    DATETIME NOT NULL,
    KEY idx_highlights_user (user_id, created_at DESC)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_highlight_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    highlight_id  BIGINT NOT NULL,
    story_id      BIGINT NOT NULL,
    sort_order    INT NOT NULL DEFAULT 0,
    KEY idx_highlight_items_hl (highlight_id, sort_order)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS close_friends (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    friend_id   BIGINT NOT NULL,
    created_at  DATETIME NOT NULL,
    UNIQUE KEY uq_close (user_id, friend_id)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 4. FCM TOKEN REGISTRY
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fcm_tokens (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    token         VARCHAR(512) NOT NULL,
    platform      VARCHAR(16),
    device_name   VARCHAR(128),
    locale        VARCHAR(16),
    app_version   VARCHAR(32),
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    last_seen_at  DATETIME,
    invalid       TINYINT(1)   NOT NULL DEFAULT 0,
    UNIQUE KEY uq_fcm_token (token(191)),
    KEY idx_fcm_user (user_id, invalid)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 5. CREATOR / VERIFIED COLUMNS
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS verified         TINYINT(1)  NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS account_type     VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
    ADD COLUMN IF NOT EXISTS bio              VARCHAR(280),
    ADD COLUMN IF NOT EXISTS website          VARCHAR(256),
    ADD COLUMN IF NOT EXISTS category         VARCHAR(64);

-- -----------------------------------------------------------------------------
-- 6. HASHTAG STORE (powers search + trending tags)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hashtags (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag           VARCHAR(64) NOT NULL,
    usage_count   BIGINT NOT NULL DEFAULT 0,
    last_used_at  DATETIME NOT NULL,
    UNIQUE KEY uq_hashtag (tag),
    KEY idx_hashtag_trending (usage_count DESC, last_used_at DESC)
) ENGINE=InnoDB CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hashtag_usage (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag         VARCHAR(64) NOT NULL,
    entity_type VARCHAR(16) NOT NULL,
    entity_id   BIGINT      NOT NULL,
    created_at  DATETIME    NOT NULL,
    KEY idx_hashtag_usage_tag (tag, created_at DESC),
    KEY idx_hashtag_usage_entity (entity_type, entity_id)
) ENGINE=InnoDB CHARSET=utf8mb4;
