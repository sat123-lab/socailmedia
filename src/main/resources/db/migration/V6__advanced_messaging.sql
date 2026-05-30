-- =============================================================================
--  V6__advanced_messaging.sql
--
--  Advanced messaging ecosystem — adds the columns and tables the upgraded
--  chat system depends on. All additions are NULL-safe so older rows are
--  silently treated as "no reaction, no reply, not pinned, not view-once".
--
--  Hibernate ddl-auto=update will materialise these too; this migration is
--  the authoritative reference and the source of the indexes the new
--  hot-path queries depend on.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. CHAT MESSAGE — extra columns for reply, forward, view-once, voice.
--    All optional + indexed so they never slow the legacy hot-path.
-- -----------------------------------------------------------------------------
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS reply_to_id        BIGINT       NULL,
    ADD COLUMN IF NOT EXISTS forward_count      INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS forward_origin_id  BIGINT       NULL,
    ADD COLUMN IF NOT EXISTS view_once          BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS view_once_consumed BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS voice_waveform     VARCHAR(2048) NULL,
    ADD COLUMN IF NOT EXISTS voice_duration_ms  INT          NULL,
    ADD COLUMN IF NOT EXISTS media_kind         VARCHAR(16)  NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_reply   ON chat_messages(reply_to_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_forward ON chat_messages(forward_origin_id);

-- -----------------------------------------------------------------------------
-- 2. MESSAGE REACTIONS — append-only with a uniqueness guarantee.
--    Per (message, user, emoji) we keep at most one row; the toggle path
--    deletes that row to "unreact".
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_reactions (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    message_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    emoji       VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_message_reaction UNIQUE (message_id, user_id, emoji)
);
CREATE INDEX IF NOT EXISTS idx_reactions_message ON message_reactions(message_id);
CREATE INDEX IF NOT EXISTS idx_reactions_user    ON message_reactions(user_id);

-- -----------------------------------------------------------------------------
-- 3. CONVERSATION SETTINGS — per-user view of a conversation.
--    "Pinned for me", "archived for me", "muted for me", "in this folder
--    for me" — none of which leak to the other participant.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversation_settings (
    id              BIGINT     NOT NULL AUTO_INCREMENT,
    user_id         BIGINT     NOT NULL,
    conversation_id BIGINT     NOT NULL,
    pinned          BOOLEAN    NOT NULL DEFAULT FALSE,
    pin_order       INT        NOT NULL DEFAULT 0,
    archived        BOOLEAN    NOT NULL DEFAULT FALSE,
    muted_until     TIMESTAMP  NULL,
    folder_id       BIGINT     NULL,
    notifications   VARCHAR(16) NOT NULL DEFAULT 'ALL',  -- ALL | MENTIONS | NONE
    updated_at      TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_conv_setting UNIQUE (user_id, conversation_id)
);
CREATE INDEX IF NOT EXISTS idx_conv_settings_user_pinned   ON conversation_settings(user_id, pinned, pin_order);
CREATE INDEX IF NOT EXISTS idx_conv_settings_user_archived ON conversation_settings(user_id, archived);
CREATE INDEX IF NOT EXISTS idx_conv_settings_user_folder   ON conversation_settings(user_id, folder_id);

-- -----------------------------------------------------------------------------
-- 4. CHAT FOLDERS — Telegram-style user-defined organization buckets.
--    `kind` lets us ship a few "built-in" folders (unread, groups, creators)
--    that don't need rows but still appear in the rail.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_folders (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    name        VARCHAR(64) NOT NULL,
    emoji       VARCHAR(16) NULL,
    kind        VARCHAR(16) NOT NULL DEFAULT 'CUSTOM',  -- CUSTOM | FAVORITES | WORK | PERSONAL
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_chat_folders_user ON chat_folders(user_id, sort_order);

-- -----------------------------------------------------------------------------
-- 5. USER PRESENCE — current online state and the optional typing/recording
--    indicator. One row per user; updated by heartbeats and reaped by a
--    scheduled job that drops stale "online" markers after the timeout.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_presence (
    user_id            BIGINT      NOT NULL,
    online             BOOLEAN     NOT NULL DEFAULT FALSE,
    last_seen_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    typing_in_conv_id  BIGINT      NULL,
    typing_kind        VARCHAR(16) NULL,  -- TEXT | VOICE
    typing_until       TIMESTAMP   NULL,
    last_seen_privacy  VARCHAR(16) NOT NULL DEFAULT 'EVERYONE',  -- EVERYONE | CONTACTS | NOBODY
    PRIMARY KEY (user_id)
);
CREATE INDEX IF NOT EXISTS idx_presence_online ON user_presence(online, last_seen_at);
