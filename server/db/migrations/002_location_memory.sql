-- Миграция для уже существующих БД: журнал по локации + память локаций.
-- Локально:  wrangler d1 execute mythrix --local  --file=db/migrations/002_location_memory.sql
-- Удалённо:  wrangler d1 execute mythrix --remote --file=db/migrations/002_location_memory.sql
ALTER TABLE turns ADD COLUMN location_id TEXT;
CREATE INDEX IF NOT EXISTS idx_turns_loc ON turns(telegram_user_id, location_id, turn_index);

CREATE TABLE IF NOT EXISTS location_memory (
  telegram_user_id TEXT NOT NULL,
  location_id      TEXT NOT NULL,
  summary          TEXT NOT NULL,
  last_turn_index  INTEGER NOT NULL DEFAULT -1,
  updated_at       INTEGER NOT NULL,
  PRIMARY KEY (telegram_user_id, location_id)
);
