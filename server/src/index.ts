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
import { firstNudgeAt, pickHook, shouldSendNudge, nextNudgeAt } from "./nudge.js";
import { handleTelegramUpdate, sendNudge } from "./telegram.js";

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
    // Одноразовая регистрация webhook у Telegram. Авторизация — тот же секрет, что и сам webhook.
    // Идёт через worker, чтобы не светить TELEGRAM_BOT_TOKEN наружу.
    if (req.method === "POST" && url.pathname === "/admin/setup-webhook") {
      const secret = req.headers.get("X-Setup-Secret");
      if (secret !== env.TELEGRAM_WEBHOOK_SECRET) return json({ error: "forbidden" }, 403);
      const workerOrigin = `${url.protocol}//${url.host}`;
      const tgUrl = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/setWebhook`;
      const res = await fetch(tgUrl, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          url: `${workerOrigin}/telegram-webhook`,
          secret_token: env.TELEGRAM_WEBHOOK_SECRET,
          allowed_updates: ["message"],
        }),
      });
      const tgResult = (await res.json()) as Record<string, unknown>;
      return json({ worker_webhook: `${workerOrigin}/telegram-webhook`, telegram: tgResult });
    }
    // Telegram webhook: команды /start /play /resume. Авторизация — секретный заголовок.
    if (req.method === "POST" && url.pathname === "/telegram-webhook") {
      const secret = req.headers.get("X-Telegram-Bot-Api-Secret-Token");
      if (secret !== env.TELEGRAM_WEBHOOK_SECRET) return json({ error: "forbidden" }, 403);
      try {
        const update = (await req.json()) as Parameters<typeof handleTelegramUpdate>[1];
        await handleTelegramUpdate(env, update);
      } catch (e) {
        return json({ error: (e as Error).message }, 500);
      }
      return json({ ok: true });
    }
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

  // Cron Trigger: раз в час обходит игроков, которым пора напомнить.
  async scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(runNudgeScan(env));
  },
};

async function runNudgeScan(env: Env): Promise<void> {
  const now = Date.now();
  const due = await db.getDueNudges(env, now);
  for (const cand of due) {
    const decision = shouldSendNudge({
      game_over: cand.game_over,
      nudge_count: cand.nudge_count,
      nudge_next_at: now, // мы уже отфильтровали по времени в SQL
      now,
    });
    if (!decision) continue;
    const hook = cand.last_hook ?? pickHook(null, cand.nudge_count);
    const ok = await sendNudge(env, cand.telegram_user_id, hook);
    if (!ok) continue; // не сдвигаем расписание — попробуем в следующий cron-тик
    const newCount = cand.nudge_count + 1;
    const next = nextNudgeAt(cand.last_seen_at, newCount);
    await db.recordNudgeSent(env, cand.telegram_user_id, newCount, next);
  }
}

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
  const lastNarrative = await db.getLastNarrative(env, uid);
  // Игрок открыл Mini App → фиксируем активность и планируем первый пуш на +24ч.
  const now = Date.now();
  const hook = pickHook(lastNarrative, 0);
  await db.setPlayerActivity(env, uid, now, firstNudgeAt(now), hook);
  return json({
    player: await playerView(env, player),
    last_narrative: lastNarrative,
    game_over: player.game_over,
  });
}

async function newGame(env: Env, uid: string, body: Record<string, unknown>): Promise<Response> {
  const classId = String(body.class_id ?? "wanderer");
  const name = String(body.name ?? "Путник").slice(0, 40);
  // Точка старта зависит от выбранного пункта меню (сюжет/бой/таверна).
  // Должна быть валидной локацией мира, иначе откатываемся к таверне.
  const requestedStart = String(body.start_location ?? STARTING_LOCATION);
  const startLocation = (await db.getLocation(env, requestedStart))
    ? requestedStart
    : STARTING_LOCATION;

  const cls = await db.getClass(env, classId);
  if (!cls) return json({ error: `unknown class: ${classId}` }, 400);

  const hp = (cls.hp as number) ?? 10;
  const startItems = (cls.starting_items as string[]) ?? [];

  // Стартовое снаряжение сразу надето (первое оружие и первая броня).
  const startEquipped: { weapon?: string; armor?: string } = {};
  for (const it of await db.getItems(env, startItems)) {
    if (it.type === "weapon" && !startEquipped.weapon) startEquipped.weapon = it.id;
    if (it.type === "armor" && !startEquipped.armor) startEquipped.armor = it.id;
  }

  const player: PlayerState = {
    telegram_user_id: uid,
    world_id: env.WORLD_ID,
    ruleset_id: env.RULESET_ID,
    class_id: classId,
    name,
    level: 1,
    hp,
    max_hp: hp,
    location_id: startLocation,
    gold: 30,
    inventory: [...startItems],
    equipped: startEquipped,
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
      const targetId = String(combatIntent.target ?? "");
      // Если атакован живой NPC — союзники сразу становятся враждебны (даже если он ещё не убит).
      if (combatIntent.type === "attack" && targetId) {
        await markAlliesHostile(env, uid, targetId);
      }
      player = outcome.player;
      if (player.hp <= 0) player = { ...player, game_over: true };
      // Если бой по NPC завершён победой игрока — отметить NPC мёртвым.
      if (
        combatIntent.type === "attack" &&
        targetId &&
        player.combat_session === null &&
        player.hp > 0
      ) {
        const deadNpc = await db.getNpc(env, targetId);
        if (deadNpc) await db.setNpcAlive(env, uid, targetId, false);
      }
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
  // Игрок сделал ход → сбрасываем счётчик пушей, перепланируем первый на +24ч от сейчас.
  const now = Date.now();
  const nudgePlanned = player.game_over ? null : firstNudgeAt(now);
  await db.setPlayerActivity(env, uid, now, nudgePlanned, pickHook(narrative, 0));

  // Бой завершён, если на этом ходу был разрешён боевой intent и сессия закрылась
  // (враг повержен или игрок погиб). Клиент в режиме «Бой» по этому флагу выходит в меню.
  const combatOver = handledCombatIntent !== null && player.combat_session === null;

  return json({
    narrative,
    player: await playerView(env, player),
    game_over: player.game_over,
    combat_over: combatOver,
    warnings: applied.warnings,
    context_usage: usageTokens,
  });
}

// Союзники атакованного NPC переключаются на враждебное отношение.
// Если атакован враг (enemy), а не NPC — ничего не делаем.
async function markAlliesHostile(env: Env, uid: string, targetId: string): Promise<void> {
  const npc = await db.getNpc(env, targetId);
  if (!npc?.allies?.length) return;
  for (const allyId of npc.allies) {
    const ally = await db.getNpc(env, allyId);
    if (!ally) continue;
    // Если союзник уже мёртв — пропускаем.
    const [state] = await db.getNpcStates(env, uid, [allyId]);
    if (state && !state.alive) continue;
    await db.setNpcDisposition(env, uid, allyId, "hostile");
  }
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
