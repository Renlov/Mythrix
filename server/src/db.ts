import type {
  Env,
  Location,
  Npc,
  Item,
  Enemy,
  QuestDef,
  PlayerState,
  QuestProgress,
} from "./types.js";

const parse = <T>(s: string): T => JSON.parse(s) as T;

export async function getLocation(env: Env, id: string): Promise<Location | null> {
  const row = await env.DB.prepare("SELECT data FROM locations WHERE world_id=? AND id=?")
    .bind(env.WORLD_ID, id)
    .first<{ data: string }>();
  return row ? parse<Location>(row.data) : null;
}

export async function getNpcsInLocation(env: Env, locationId: string): Promise<Npc[]> {
  const res = await env.DB.prepare("SELECT data FROM npcs WHERE world_id=? AND location_id=?")
    .bind(env.WORLD_ID, locationId)
    .all<{ data: string }>();
  return res.results.map((r) => parse<Npc>(r.data));
}

export async function getItems(env: Env, ids: string[]): Promise<Item[]> {
  if (ids.length === 0) return [];
  const placeholders = ids.map(() => "?").join(",");
  const res = await env.DB.prepare(
    `SELECT data FROM items WHERE world_id=? AND id IN (${placeholders})`,
  )
    .bind(env.WORLD_ID, ...ids)
    .all<{ data: string }>();
  return res.results.map((r) => parse<Item>(r.data));
}

export async function getItemsInLocation(env: Env, locationId: string): Promise<Item[]> {
  const res = await env.DB.prepare("SELECT data FROM items WHERE world_id=? AND location_id=?")
    .bind(env.WORLD_ID, locationId)
    .all<{ data: string }>();
  return res.results.map((r) => parse<Item>(r.data));
}

export async function getNpc(env: Env, id: string): Promise<Npc | null> {
  const row = await env.DB.prepare("SELECT data FROM npcs WHERE world_id=? AND id=?")
    .bind(env.WORLD_ID, id)
    .first<{ data: string }>();
  return row ? parse<Npc>(row.data) : null;
}

export async function getClass(env: Env, classId: string): Promise<Record<string, unknown> | null> {
  const row = await env.DB.prepare("SELECT data FROM classes WHERE ruleset_id=? AND id=?")
    .bind(env.RULESET_ID, classId)
    .first<{ data: string }>();
  return row ? parse<Record<string, unknown>>(row.data) : null;
}

export async function getAllClasses(
  env: Env,
): Promise<{ id: string; name: string; description: string; avatar?: string }[]> {
  const res = await env.DB.prepare("SELECT data FROM classes WHERE ruleset_id=?")
    .bind(env.RULESET_ID)
    .all<{ data: string }>();
  return res.results.map((r) => {
    const c = parse<{ id: string; name: string; description: string; avatar?: string }>(r.data);
    return { id: c.id, name: c.name, description: c.description, avatar: c.avatar };
  });
}

// Стартовый прогресс квестов берётся из их определения (поля status/stage в data).
export async function getStartingQuests(
  env: Env,
): Promise<{ id: string; status: string; stage: number }[]> {
  const res = await env.DB.prepare("SELECT data FROM quests WHERE world_id=?")
    .bind(env.WORLD_ID)
    .all<{ data: string }>();
  return res.results.map((r) => {
    const q = parse<{ id: string; status?: string; stage?: number }>(r.data);
    return { id: q.id, status: q.status ?? "available", stage: q.stage ?? 0 };
  });
}

export async function getEnemy(env: Env, id: string): Promise<Enemy | null> {
  const row = await env.DB.prepare("SELECT data FROM enemies WHERE world_id=? AND id=?")
    .bind(env.WORLD_ID, id)
    .first<{ data: string }>();
  return row ? parse<Enemy>(row.data) : null;
}

export async function getQuestDefs(env: Env, ids: string[]): Promise<QuestDef[]> {
  if (ids.length === 0) return [];
  const placeholders = ids.map(() => "?").join(",");
  const res = await env.DB.prepare(
    `SELECT data FROM quests WHERE world_id=? AND id IN (${placeholders})`,
  )
    .bind(env.WORLD_ID, ...ids)
    .all<{ data: string }>();
  return res.results.map((r) => parse<QuestDef>(r.data));
}

export async function getRulesetConfig(env: Env): Promise<Record<string, unknown>> {
  const row = await env.DB.prepare("SELECT data FROM rulesets WHERE id=?")
    .bind(env.RULESET_ID)
    .first<{ data: string }>();
  return row ? parse<Record<string, unknown>>(row.data) : {};
}

export async function getPlayer(env: Env, uid: string): Promise<PlayerState | null> {
  const row = await env.DB.prepare("SELECT * FROM players WHERE telegram_user_id=?")
    .bind(uid)
    .first<Record<string, unknown>>();
  if (!row) return null;
  return {
    telegram_user_id: row.telegram_user_id as string,
    world_id: row.world_id as string,
    ruleset_id: row.ruleset_id as string,
    class_id: row.class_id as string,
    name: row.name as string,
    level: row.level as number,
    hp: row.hp as number,
    max_hp: row.max_hp as number,
    location_id: row.location_id as string,
    gold: row.gold as number,
    inventory: parse(row.inventory as string),
    equipped: parse(row.equipped as string),
    item_charges: row.item_charges ? parse(row.item_charges as string) : {},
    known_npcs: parse(row.known_npcs as string),
    status_effects: parse(row.status_effects as string),
    combat_session: row.combat_session ? parse(row.combat_session as string) : null,
    game_over: Boolean(row.game_over),
  };
}

export async function savePlayer(env: Env, p: PlayerState): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO players (telegram_user_id, world_id, ruleset_id, class_id, name, level, hp, max_hp,
       location_id, gold, inventory, equipped, item_charges, known_npcs, status_effects, combat_session, game_over)
     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
     ON CONFLICT(telegram_user_id) DO UPDATE SET
       world_id=excluded.world_id, ruleset_id=excluded.ruleset_id, class_id=excluded.class_id,
       name=excluded.name, level=excluded.level, hp=excluded.hp, max_hp=excluded.max_hp,
       location_id=excluded.location_id, gold=excluded.gold, inventory=excluded.inventory,
       equipped=excluded.equipped, item_charges=excluded.item_charges, known_npcs=excluded.known_npcs,
       status_effects=excluded.status_effects, combat_session=excluded.combat_session,
       game_over=excluded.game_over`,
  )
    .bind(
      p.telegram_user_id,
      p.world_id,
      p.ruleset_id,
      p.class_id,
      p.name,
      p.level,
      p.hp,
      p.max_hp,
      p.location_id,
      p.gold,
      JSON.stringify(p.inventory),
      JSON.stringify(p.equipped),
      JSON.stringify(p.item_charges),
      JSON.stringify(p.known_npcs),
      JSON.stringify(p.status_effects),
      p.combat_session ? JSON.stringify(p.combat_session) : null,
      p.game_over ? 1 : 0,
    )
    .run();
}

export async function getQuestProgress(env: Env, uid: string): Promise<QuestProgress[]> {
  const res = await env.DB.prepare(
    "SELECT quest_id, status, stage FROM player_quests WHERE telegram_user_id=?",
  )
    .bind(uid)
    .all<QuestProgress>();
  return res.results;
}

export async function setQuestProgress(
  env: Env,
  uid: string,
  questId: string,
  status: string,
  stage: number,
): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO player_quests (telegram_user_id, quest_id, status, stage) VALUES (?,?,?,?)
     ON CONFLICT(telegram_user_id, quest_id) DO UPDATE SET status=excluded.status, stage=excluded.stage`,
  )
    .bind(uid, questId, status, stage)
    .run();
}

export async function nextTurnIndex(env: Env, uid: string): Promise<number> {
  const row = await env.DB.prepare(
    "SELECT COALESCE(MAX(turn_index), -1) + 1 AS n FROM turns WHERE telegram_user_id=?",
  )
    .bind(uid)
    .first<{ n: number }>();
  return row?.n ?? 0;
}

export async function appendTurn(
  env: Env,
  uid: string,
  turnIndex: number,
  playerAction: string,
  narrative: string,
  events: unknown,
  contextUsage: number,
): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO turns (telegram_user_id, turn_index, ts, player_action, narrative, events, context_usage)
     VALUES (?,?,?,?,?,?,?)`,
  )
    .bind(
      uid,
      turnIndex,
      Date.now(),
      playerAction,
      narrative,
      JSON.stringify(events),
      contextUsage,
    )
    .run();
}
