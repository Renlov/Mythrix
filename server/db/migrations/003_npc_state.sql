-- Миграция: персональное состояние NPC (жив/мёртв и текущее отношение к игроку).
-- Локально:  wrangler d1 execute mythrix --local  --file=db/migrations/003_npc_state.sql
-- Удалённо:  wrangler d1 execute mythrix --remote --file=db/migrations/003_npc_state.sql

CREATE TABLE IF NOT EXISTS npc_state (
  telegram_user_id TEXT NOT NULL,
  npc_id           TEXT NOT NULL,
  alive            INTEGER NOT NULL DEFAULT 1,   -- 0 = мёртв, не реагирует
  disposition     TEXT,                          -- 'hostile' | 'neutral' | 'friendly' | NULL (берём из карточки)
  PRIMARY KEY (telegram_user_id, npc_id)
);
