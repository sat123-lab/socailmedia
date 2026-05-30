-- =============================================================================
--  V8__reels_editor_overlays.sql
--
--  Reels Editor — adds the opaque `overlays_json` column on `reels` so the
--  client-side editor can persist its text/sticker/music/transition manifest
--  alongside the published video. The server treats this as a blob; it is
--  rendered (and validated) only by the player.
--
--  The column is nullable so legacy reels (and any client build that doesn't
--  ship the editor) keep working with no migration of existing rows.
-- =============================================================================

ALTER TABLE reels
    ADD COLUMN IF NOT EXISTS overlays_json TEXT NULL;
