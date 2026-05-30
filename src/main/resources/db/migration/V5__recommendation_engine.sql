-- =============================================================================
--  V5__recommendation_engine.sql
--
--  AI-ready recommendation engine + smart feed algorithm.
--
--    1. `feed_signals`       — append-only event log of every interaction.
--    2. `user_interests`     — rolled-up per-user → topic affinity scores.
--    3. `creator_affinity`   — rolled-up per-user → creator affinity scores.
--    4. `trending_items`     — precomputed decayed trending scores per item.
--    5. `creator_stats_rt`   — realtime aggregate creator quality scores.
--
--  Hibernate `ddl-auto=update` materialises these too; this file is the
--  authoritative schema reference and the source of truth for the indexes
--  ranking SQL depends on.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. FEED SIGNALS — every "I did something" event the client wants to teach
--    the recommender about. Append-only; roll-ups read this then archive.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS feed_signals (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    kind          VARCHAR(32)  NOT NULL,    -- VIEW, LIKE, COMMENT, SHARE, SAVE, FOLLOW,
                                            -- COMPLETE_REEL, REWATCH, SKIP, REPORT, PROFILE_VISIT
    target_type   VARCHAR(16)  NOT NULL,    -- POST | REEL | STORY | LIVE | USER | HASHTAG
    target_id     BIGINT,
    target_label  VARCHAR(128),             -- e.g. hashtag text (when target_id is null)
    weight        DOUBLE       NOT NULL DEFAULT 1,
    creator_id    BIGINT,                   -- denormalized author of the target
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_feed_signals_user_time  ON feed_signals(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feed_signals_target     ON feed_signals(target_type, target_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feed_signals_creator    ON feed_signals(creator_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 2. USER INTERESTS — per-user → topic (hashtag, category) affinity score.
--    Updated by the roll-up; expressed as an exponentially-decayed sum.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_interests (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    topic         VARCHAR(64)  NOT NULL,    -- normalized hashtag or category id
    score         DOUBLE       NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_interests UNIQUE (user_id, topic)
);
CREATE INDEX IF NOT EXISTS idx_ui_user_score ON user_interests(user_id, score DESC);

-- -----------------------------------------------------------------------------
-- 3. CREATOR AFFINITY — per-user → creator score (positive interactions / decay).
--    Drives "people you may know", reel boost-from-affinity scoring, etc.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS creator_affinity (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    creator_id    BIGINT       NOT NULL,
    score         DOUBLE       NOT NULL DEFAULT 0,
    last_signal   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_creator_affinity UNIQUE (user_id, creator_id)
);
CREATE INDEX IF NOT EXISTS idx_ca_user_score ON creator_affinity(user_id, score DESC);

-- -----------------------------------------------------------------------------
-- 4. TRENDING ITEMS — precomputed trending scores per (type, target).
--    Items decay; a scheduler tops up active items and ages the rest out.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trending_items (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    target_type   VARCHAR(16)  NOT NULL,    -- HASHTAG | CREATOR | REEL | STORY | LIVE
    target_id     BIGINT,
    target_label  VARCHAR(128),
    category      VARCHAR(64),
    score         DOUBLE       NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_trending UNIQUE (target_type, target_id, target_label)
);
CREATE INDEX IF NOT EXISTS idx_trending_type_score ON trending_items(target_type, score DESC);
CREATE INDEX IF NOT EXISTS idx_trending_cat_score  ON trending_items(category, score DESC);

-- -----------------------------------------------------------------------------
-- 5. CREATOR STATS — realtime aggregate quality / engagement / consistency.
--    Maintained by the same roll-up job; used by creator-discovery ranking.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS creator_stats_rt (
    creator_id        BIGINT       NOT NULL,
    engagement_score  DOUBLE       NOT NULL DEFAULT 0,
    consistency_score DOUBLE       NOT NULL DEFAULT 0,
    quality_score     DOUBLE       NOT NULL DEFAULT 0,
    posts_30d         INT          NOT NULL DEFAULT 0,
    reels_30d         INT          NOT NULL DEFAULT 0,
    lives_30d         INT          NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (creator_id)
);
CREATE INDEX IF NOT EXISTS idx_creator_stats_quality ON creator_stats_rt(quality_score DESC);
