-- Активность игрока + расписание напоминаний.
-- Локально:  wrangler d1 execute mythrix --local  --file=db/migrations/004_player_activity.sql
-- Удалённо:  wrangler d1 execute mythrix --remote --file=db/migrations/004_player_activity.sql

ALTER TABLE players ADD COLUMN last_seen_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN nudge_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN nudge_next_at INTEGER;  -- ms timestamp; NULL = не планируем
ALTER TABLE players ADD COLUMN last_hook TEXT;          -- короткая зацепка из последнего хода для пуша

CREATE INDEX IF NOT EXISTS idx_players_nudge ON players(nudge_next_at) WHERE nudge_next_at IS NOT NULL;
