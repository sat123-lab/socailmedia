-- =============================================================================
--  V4__live_streaming.sql
--
--  Live streaming subsystem — Instagram/TikTok Live grade.
--
--    1. `live_streams`           — one row per session (live or ended).
--    2. `live_stream_messages`   — realtime chat messages.
--    3. `live_stream_viewers`    — active viewer presence (heartbeat-driven).
--    4. `live_stream_bans`       — per-stream banned/muted viewers.
--    5. `live_stream_reactions`  — aggregated reaction counters.
--    6. `live_stream_gifts`      — gift / monetization events (architecture).
--
--  Hibernate `ddl-auto=update` will materialise these from the JPA entities
--  too; this file is the authoritative schema reference.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. LIVE STREAMS
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS live_streams (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    creator_id      BIGINT       NOT NULL,
    title           VARCHAR(160),
    category        VARCHAR(64),
    thumbnail_url   VARCHAR(512),
    -- The opaque transport channel for the media stream (WebRTC room id /
    -- HLS endpoint / SFU session id, depending on which transport you wire
    -- in). The backend treats this as a server-issued secret.
    rtc_channel     VARCHAR(128),
    rtc_token       VARCHAR(512),
    privacy         VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC',
    state           VARCHAR(16)  NOT NULL DEFAULT 'LIVE',
    slow_mode_sec   INT          NOT NULL DEFAULT 0,
    peak_viewers    INT          NOT NULL DEFAULT 0,
    total_viewers   INT          NOT NULL DEFAULT 0,
    likes_count     INT          NOT NULL DEFAULT 0,
    messages_count  INT          NOT NULL DEFAULT 0,
    started_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at        TIMESTAMP    NULL,
    pinned_message  VARCHAR(280),
    PRIMARY KEY (id),
    CONSTRAINT fk_live_streams_creator FOREIGN KEY (creator_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_live_streams_state_started ON live_streams(state, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_live_streams_creator       ON live_streams(creator_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_live_streams_category      ON live_streams(category, state, started_at DESC);

-- -----------------------------------------------------------------------------
-- 2. CHAT MESSAGES
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS live_stream_messages (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    stream_id    BIGINT       NOT NULL,
    sender_id    BIGINT       NOT NULL,
    body         VARCHAR(280) NOT NULL,
    kind         VARCHAR(16)  NOT NULL DEFAULT 'CHAT',
    pinned       BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_lsm_stream FOREIGN KEY (stream_id) REFERENCES live_streams(id) ON DELETE CASCADE,
    CONSTRAINT fk_lsm_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_lsm_stream_created  ON live_stream_messages(stream_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_lsm_sender_created  ON live_stream_messages(sender_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 3. ACTIVE VIEWER PRESENCE
--    Rows expire whenever last_seen_at < NOW() - 30s — reconciled by a
--    server-side sweeper to keep viewer counts honest.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS live_stream_viewers (
    id            BIGINT    NOT NULL AUTO_INCREMENT,
    stream_id     BIGINT    NOT NULL,
    viewer_id     BIGINT    NOT NULL,
    joined_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    role          VARCHAR(16) NOT NULL DEFAULT 'VIEWER',
    PRIMARY KEY (id),
    CONSTRAINT uq_lsv_stream_viewer UNIQUE (stream_id, viewer_id),
    CONSTRAINT fk_lsv_stream FOREIGN KEY (stream_id) REFERENCES live_streams(id) ON DELETE CASCADE,
    CONSTRAINT fk_lsv_viewer FOREIGN KEY (viewer_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_lsv_last_seen ON live_stream_viewers(stream_id, last_seen_at DESC);

-- -----------------------------------------------------------------------------
-- 4. PER-STREAM BANS / MUTES
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS live_stream_bans (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    stream_id    BIGINT      NOT NULL,
    viewer_id    BIGINT      NOT NULL,
    kind         VARCHAR(8)  NOT NULL DEFAULT 'BAN',  -- BAN | MUTE
    reason       VARCHAR(256),
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_lsb_stream_viewer UNIQUE (stream_id, viewer_id),
    CONSTRAINT fk_lsb_stream FOREIGN KEY (stream_id) REFERENCES live_streams(id) ON DELETE CASCADE,
    CONSTRAINT fk_lsb_viewer FOREIGN KEY (viewer_id) REFERENCES users(id)
);

-- -----------------------------------------------------------------------------
-- 5. REACTION COUNTERS — aggregated, not per-event (per-event events go
--    through STOMP only; we keep a cheap rollup for analytics).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS live_stream_reactions (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    stream_id   BIGINT      NOT NULL,
    emoji       VARCHAR(16) NOT NULL,
    count       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_lsr_stream_emoji UNIQUE (stream_id, emoji),
    CONSTRAINT fk_lsr_stream FOREIGN KEY (stream_id) REFERENCES live_streams(id) ON DELETE CASCADE
);

-- -----------------------------------------------------------------------------
-- 6. GIFTS / MONETIZATION (architecture only — no payment processor yet)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS live_stream_gifts (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    stream_id     BIGINT      NOT NULL,
    sender_id     BIGINT      NOT NULL,
    creator_id    BIGINT      NOT NULL,
    gift_id       VARCHAR(64) NOT NULL,
    gift_value    INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_lsg_stream  FOREIGN KEY (stream_id)  REFERENCES live_streams(id) ON DELETE CASCADE,
    CONSTRAINT fk_lsg_sender  FOREIGN KEY (sender_id)  REFERENCES users(id),
    CONSTRAINT fk_lsg_creator FOREIGN KEY (creator_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_lsg_creator_created ON live_stream_gifts(creator_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_lsg_stream_created  ON live_stream_gifts(stream_id, created_at DESC);
