import type { Env, PlayerState } from "./types.js";
import { validateInitData } from "./telegram.js";
import * as db from "./db.js";
import { chat, type ChatMessage } from "./deepseek.js";
import { buildSystemPrompt, buildSceneContext } from "./context.js";
import { parseDmResponse } from "./events.js";
import { applyEvents } from "./apply.js";
import { resolveAttackIntent, resolveUseItemIntent } from "./combatFlow.js";
import { equipItem, unequipSlot, type EquipSlot } from "./equip.js";
import { useItem, removeFromInventory } from "./inventory.js";
import { summarizeOnExit } from "./memory.js";

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-allow-headers": "content-type",
};

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json", ...CORS },
  });

const STARTING_LOCATION = "loc_tavern_last_rest";

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);

    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    // Публичный список классов (read-only контент, без авторизации).
    if (req.method === "GET" && url.pathname === "/classes") return await listClasses(env);
    if (req.method !== "POST") return json({ error: "POST only" }, 405);

    let body: Record<string, unknown>;
    try {
      body = (await req.json()) as Record<string, unknown>;
    } catch {
      return json({ error: "bad json" }, 400);
    }

    const uid = await authUid(env, body);
    if (!uid) return json({ error: "auth failed" }, 401);

    try {
      if (url.pathname === "/state") return await state(env, uid);
      if (url.pathname === "/new-game") return await newGame(env, uid, body);
      if (url.pathname === "/turn") return await turn(env, uid, body);
      if (url.pathname === "/equip") return await equip(env, uid, body);
      if (url.pathname === "/unequip") return await unequip(env, uid, body);
      if (url.pathname === "/use") return await use(env, uid, body);
      if (url.pathname === "/drop") return await drop(env, uid, body);
      return json({ error: "not found" }, 404);
    } catch (e) {
      return json({ error: (e as Error).message }, 500);
    }
  },
};

// Возвращает доверенный uid или null. Dev-обход — только при DEV_BYPASS_AUTH="1".
async function authUid(env: Env, body: Record<string, unknown>): Promise<string | null> {
  const initData = String(body.initData ?? "");
  const auth = await validateInitData(initData, env.TELEGRAM_BOT_TOKEN);
  if (auth.ok && auth.user) return String(auth.user.id);
  if (env.DEV_BYPASS_AUTH === "1") return "dev-user";
  return null;
}

async function listClasses(env: Env): Promise<Response> {
  const classes = await db.getAllClasses(env);
  return json({ classes });
}

// Текущее состояние игрока + последний нарратив (для возобновления игры).
async function state(env: Env, uid: string): Promise<Response> {
  const player = await db.getPlayer(env, uid);
  if (!player) return json({ player: null });
  const last = await env.DB.prepare(
    "SELECT narrative FROM turns WHERE telegram_user_id=? ORDER BY turn_index DESC LIMIT 1",
  )
    .bind(uid)
    .first<{ narrative: string }>();
  return json({
    player: await playerView(env, player),
    last_narrative: last?.narrative ?? null,
    game_over: player.game_over,
  });
}

async function newGame(env: Env, uid: string, body: Record<string, unknown>): Promise<Response> {
  const classId = String(body.class_id ?? "wanderer");
  const name = String(body.name ?? "Путник").slice(0, 40);

  const cls = await db.getClass(env, classId);
  if (!cls) return json({ error: `unknown class: ${classId}` }, 400);

  const hp = (cls.hp as number) ?? 10;
  const startItems = (cls.starting_items as string[]) ?? [];

  const player: PlayerState = {
    telegram_user_id: uid,
    world_id: env.WORLD_ID,
    ruleset_id: env.RULESET_ID,
    class_id: classId,
    name,
    level: 1,
    hp,
    max_hp: hp,
    location_id: STARTING_LOCATION,
    gold: 30,
    inventory: [...startItems],
    equipped: {},
    item_charges: {},
    known_npcs: [],
    status_effects: [],
    combat_session: null,
    game_over: false,
  };
  // Новая игра затирает прежний прогресс (ходы, память локаций, квесты).
  await db.clearPlayerProgress(env, uid);
  await db.savePlayer(env, player);

  // Стартовый прогресс квестов из их определений.
  for (const q of await db.getStartingQuests(env)) {
    await db.setQuestProgress(env, uid, q.id, q.status, q.stage);
  }

  return json({ ok: true, player: await playerView(env, player) });
}

async function turn(env: Env, uid: string, body: Record<string, unknown>): Promise<Response> {
  const action = String(body.action ?? "").trim();
  if (!action) return json({ error: "empty action" }, 400);

  let player = await db.getPlayer(env, uid);
  if (!player) return json({ error: "no game — call /new-game first" }, 409);
  if (player.game_over) return json({ error: "game over — call /new-game", game_over: true }, 409);
  const startLocation = player.location_id;

  const system = buildSystemPrompt(player.name);
  const sceneContext = await buildSceneContext(env, player);
  const messages: ChatMessage[] = [
    { role: "system", content: system },
    { role: "user", content: `${sceneContext}\n\n[PLAYER ACTION]\n${action}` },
  ];

  // Фаза 1 + валидация формата с одним retry.
  let { content, usageTokens } = await chat(env, messages);
  let parsed = parseDmResponse(content);
  if (!parsed.events) {
    const retry = await chat(env, [
      ...messages,
      { role: "assistant", content },
      {
        role: "user",
        content:
          "Формат нарушен. Верни ответ строго двумя блоками: [NARRATIVE] и [EVENTS] с валидным JSON по схеме.",
      },
    ]);
    content = retry.content;
    usageTokens = retry.usageTokens;
    parsed = parseDmResponse(content);
  }

  let narrative = parsed.narrative;
  const events = parsed.events ?? {
    intents: [],
    location_change: null,
    scene_ended: false,
    scene_summary: null,
  };

  // Боевой ход → расчёт на сервере + фаза 2 (нарратив исхода).
  // Боевым считается атака по присутствующей цели или применение расходника во время боя.
  const combatIntent = events.intents.find(
    (i) =>
      i.type === "attack" ||
      (i.type === "use_item" && player!.combat_session !== null),
  );
  let handledCombatIntent: typeof combatIntent | null = null;
  if (combatIntent) {
    let outcome = null;
    if (
      combatIntent.type === "attack" &&
      (await targetPresent(env, player.location_id, String(combatIntent.target ?? "")))
    ) {
      outcome = await resolveAttackIntent(env, player, combatIntent);
    } else if (combatIntent.type === "use_item") {
      outcome = await resolveUseItemIntent(env, player, combatIntent);
    }
    if (outcome) {
      handledCombatIntent = combatIntent;
      player = outcome.player;
      if (player.hp <= 0) player = { ...player, game_over: true };
      const phase2 = await chat(env, [
        { role: "system", content: system },
        { role: "user", content: `${sceneContext}\n\n[PLAYER ACTION]\n${action}` },
        { role: "assistant", content: parsed.narrative },
        {
          role: "user",
          content: `${outcome.turnResult}\n\nОпиши исход боя по этим фактам. Без чисел. Только [NARRATIVE].`,
        },
      ]);
      const p2 = parseDmResponse(phase2.content);
      narrative = `${parsed.narrative}\n\n${p2.narrative || phase2.content}`.trim();
      usageTokens += phase2.usageTokens;
    }
  }

  // Применяем не-боевые события (боевой intent, уже посчитанный выше, исключаем).
  const restEvents = handledCombatIntent
    ? { ...events, intents: events.intents.filter((i) => i !== handledCombatIntent) }
    : events;
  const applied = await applyEvents(env, player, restEvents);
  player = applied.player;

  // Граница сцены: игрок сменил локацию → сжимаем покинутую в память локации.
  if (player.location_id !== startLocation) {
    const leftLoc = await db.getLocation(env, startLocation);
    usageTokens += await summarizeOnExit(env, uid, startLocation, leftLoc?.name ?? startLocation);
  }

  await db.savePlayer(env, player);
  const turnIndex = await db.nextTurnIndex(env, uid);
  await db.appendTurn(env, uid, turnIndex, action, narrative, events, usageTokens, player.location_id);

  return json({
    narrative,
    player: await playerView(env, player),
    game_over: player.game_over,
    warnings: applied.warnings,
    context_usage: usageTokens,
  });
}

// Цель атаки должна присутствовать в текущей локации (NPC или враг).
async function targetPresent(env: Env, locationId: string, targetId: string): Promise<boolean> {
  if (!targetId) return false;
  const loc = await db.getLocation(env, locationId);
  if (!loc) return false;
  return (loc.npcs?.includes(targetId) ?? false) || (loc.enemies?.includes(targetId) ?? false);
}

async function equip(env: Env, uid: string, body: Record<string, unknown>): Promise<Response> {
  const itemId = String(body.item_id ?? "");
  const player = await db.getPlayer(env, uid);
  if (!player) return json({ error: "no game" }, 409);
  const res = await equipItem(env, player, itemId);
  if (res.error) return json({ error: res.error }, 400);
  await db.savePlayer(env, res.player);
  return json({ ok: true, player: await playerView(env, res.player) });
}

async function unequip(env: Env, uid: string, body: Record<string, unknown>): Promise<Response> {
  const slot = String(body.slot ?? "") as EquipSlot;
  if (slot !== "weapon" && slot !== "armor") return json({ error: "bad slot" }, 400);
  const player = await db.getPlayer(env, uid);
  if (!player) return json({ error: "no game" }, 409);
  const res = unequipSlot(player, slot);
  await db.savePlayer(env, res.player);
  return json({ ok: true, player: await playerView(env, res.player) });
}

// Прямое использование расходника игроком (кнопка в UI, без хода DM/боя).
async function use(env: Env, uid: string, body: Record<string, unknown>): Promise<Response> {
  const itemId = String(body.item_id ?? "");
  const player = await db.getPlayer(env, uid);
  if (!player) return json({ error: "no game" }, 409);
  const res = await useItem(env, player, itemId);
  if (res.error) return json({ error: res.error }, 400);
  await db.savePlayer(env, res.player);
  return json({ ok: true, player: await playerView(env, res.player), healed: res.healed ?? 0 });
}

// Прямой выброс предмета игроком (кнопка в UI).
async function drop(env: Env, uid: string, body: Record<string, unknown>): Promise<Response> {
  const itemId = String(body.item_id ?? "");
  const player = await db.getPlayer(env, uid);
  if (!player) return json({ error: "no game" }, 409);
  const res = removeFromInventory(player, itemId);
  if (res.error) return json({ error: res.error }, 400);
  await db.savePlayer(env, res.player);
  return json({ ok: true, player: await playerView(env, res.player) });
}

// Что отдаём клиенту (без серверных деталей).
function publicPlayer(p: PlayerState) {
  return {
    name: p.name,
    class_id: p.class_id,
    level: p.level,
    hp: p.hp,
    max_hp: p.max_hp,
    location_id: p.location_id,
    gold: p.gold,
    inventory: p.inventory,
    equipped: p.equipped,
    game_over: p.game_over,
  };
}

// Обогащённое представление: + детали предметов инвентаря (для UI).
async function playerView(env: Env, p: PlayerState) {
  const items = await db.getItems(env, p.inventory);
  const inventory_items = items.map((it) => ({
    id: it.id,
    name: it.name,
    type: it.type,
    subtype: it.subtype,
    description: it.description,
    price: it.price,
    damage_die: it.damage_die,
    armor_bonus: it.armor_bonus,
    two_handed: it.two_handed,
    uses: it.uses,
    charges: it.type === "consumable" ? (p.item_charges[it.id] ?? it.uses ?? 1) : undefined,
  }));
  return { ...publicPlayer(p), inventory_items };
}
