-- Mythrix D1 schema (см. docs/ai-architecture/16-data-model.md)
-- Гибрид: индексируемые ключи-колонки + полный JSON сущности в `data`.
-- Идемпотентно: можно прогонять повторно.

----------------------------------------------------------------------
-- Слой 1: контент мира (Библия Мира). Разделён по world_id.
----------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS worlds (
  id              TEXT PRIMARY KEY,
  name            TEXT NOT NULL,
  content_version INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS locations (
  world_id  TEXT NOT NULL,
  id        TEXT NOT NULL,
  parent_id TEXT,
  data      TEXT NOT NULL,            -- полный JSON локации
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS npcs (
  world_id    TEXT NOT NULL,
  id          TEXT NOT NULL,
  location_id TEXT,
  role        TEXT,
  data        TEXT NOT NULL,
  PRIMARY KEY (world_id, id)
);
CREATE INDEX IF NOT EXISTS idx_npcs_loc ON npcs(world_id, location_id);

CREATE TABLE IF NOT EXISTS items (
  world_id    TEXT NOT NULL,
  id          TEXT NOT NULL,
  type        TEXT,
  location_id TEXT,                   -- базовое размещение (⊕ owner_id)
  owner_id    TEXT,
  data        TEXT NOT NULL,
  PRIMARY KEY (world_id, id)
);
CREATE INDEX IF NOT EXISTS idx_items_loc ON items(world_id, location_id);

CREATE TABLE IF NOT EXISTS enemies (
  world_id TEXT NOT NULL,
  id       TEXT NOT NULL,
  data     TEXT NOT NULL,             -- CombatStats + name/description
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS quests (
  world_id TEXT NOT NULL,
  id       TEXT NOT NULL,
  data     TEXT NOT NULL,             -- определение: stages, triggers, narrative_arc
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS scenario (
  world_id TEXT NOT NULL,
  id       TEXT NOT NULL,
  data     TEXT NOT NULL,
  PRIMARY KEY (world_id, id)
);

----------------------------------------------------------------------
-- Слой 2: правила гейм-мастера (ДНД-настройка). Глобально, не по миру.
----------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS rulesets (
  id      TEXT PRIMARY KEY,
  name    TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  data    TEXT NOT NULL               -- combat_config: базовый AC, кулаки, сложности, кубы
);

CREATE TABLE IF NOT EXISTS classes (
  ruleset_id TEXT NOT NULL,
  id         TEXT NOT NULL,
  data       TEXT NOT NULL,
  PRIMARY KEY (ruleset_id, id)
);

----------------------------------------------------------------------
-- Слой 3: состояние игрока (мутабельное). Сид НЕ трогает.
----------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS players (
  telegram_user_id TEXT PRIMARY KEY,
  world_id         TEXT NOT NULL,
  ruleset_id       TEXT NOT NULL,
  class_id         TEXT NOT NULL,
  name             TEXT NOT NULL,
  level            INTEGER NOT NULL,
  hp               INTEGER NOT NULL,
  max_hp           INTEGER NOT NULL,
  location_id      TEXT NOT NULL,
  gold             INTEGER NOT NULL,
  inventory        TEXT NOT NULL DEFAULT '[]',   -- JSON array of item ids
  equipped         TEXT NOT NULL DEFAULT '{}',   -- JSON {weapon?, armor?}
  item_charges     TEXT NOT NULL DEFAULT '{}',   -- JSON {item_id: оставшиеся заряды расходника}
  known_npcs       TEXT NOT NULL DEFAULT '[]',   -- JSON array
  status_effects   TEXT NOT NULL DEFAULT '[]',   -- JSON array
  combat_session   TEXT,                         -- JSON или NULL
  game_over        INTEGER NOT NULL DEFAULT 0    -- 1 = поражение, ходы заблокированы
);

CREATE TABLE IF NOT EXISTS player_quests (
  telegram_user_id TEXT NOT NULL,
  quest_id         TEXT NOT NULL,
  status           TEXT NOT NULL,                -- active|available|done
  stage            INTEGER NOT NULL,
  PRIMARY KEY (telegram_user_id, quest_id)
);

-- Персональное состояние NPC: жив/мёртв и текущее отношение к игроку.
CREATE TABLE IF NOT EXISTS npc_state (
  telegram_user_id TEXT NOT NULL,
  npc_id           TEXT NOT NULL,
  alive            INTEGER NOT NULL DEFAULT 1,   -- 0 = мёртв
  disposition     TEXT,                          -- hostile|neutral|friendly | NULL (берём из карточки)
  PRIMARY KEY (telegram_user_id, npc_id)
);

----------------------------------------------------------------------
-- Логи ходов (append-only).
----------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS turns (
  telegram_user_id TEXT NOT NULL,
  turn_index       INTEGER NOT NULL,
  ts               INTEGER NOT NULL,
  player_action    TEXT,
  narrative        TEXT,
  events           TEXT,
  context_usage    INTEGER,
  location_id      TEXT,                         -- где закончился ход (для журнала по локации)
  PRIMARY KEY (telegram_user_id, turn_index)
);
CREATE INDEX IF NOT EXISTS idx_turns_loc ON turns(telegram_user_id, location_id, turn_index);

----------------------------------------------------------------------
-- Память локаций: сжатый итог визитов (саммари по границе сцены, см. docs/03).
----------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS location_memory (
  telegram_user_id TEXT NOT NULL,
  location_id      TEXT NOT NULL,
  summary          TEXT NOT NULL,
  last_turn_index  INTEGER NOT NULL DEFAULT -1,  -- последний ход, уже вошедший в summary
  updated_at       INTEGER NOT NULL,
  PRIMARY KEY (telegram_user_id, location_id)
);
