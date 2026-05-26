import systemPromptTemplate from "../world/prompts/dm_system_v1.txt";
import type { Env, PlayerState, Npc, Item } from "./types.js";
import * as db from "./db.js";

export function buildSystemPrompt(playerName: string): string {
  return systemPromptTemplate.replaceAll("{{player_name}}", playerName);
}

const npcName = (n: Npc) => n.name ?? n.role_label;

// Собирает динамический контекст сцены (реляционная выборка по id, см. docs/02).
export async function buildSceneContext(env: Env, player: PlayerState): Promise<string> {
  const loc = await db.getLocation(env, player.location_id);
  const blocks: string[] = [];

  // [PLAYER_STATE]
  blocks.push(
    `[PLAYER_STATE]\n` +
      `Имя: ${player.name}; класс: ${player.class_id}; уровень: ${player.level}.\n` +
      `Состояние: ${player.hp <= player.max_hp / 3 ? "тяжело ранен" : player.hp < player.max_hp ? "ранен" : "цел"}.\n` +
      `Золота: ${player.gold > 20 ? "достаточно" : player.gold > 0 ? "немного" : "нет"}.`,
  );

  if (loc) {
    // [LOCATION] + [ВЫХОДЫ]
    blocks.push(
      `[LOCATION] ${loc.name}\n${loc.description}\n` +
        `Атмосфера (${loc.atmosphere.mood}): ${loc.atmosphere.nuance}`,
    );
    if (loc.connections?.length) {
      const exits = await Promise.all(
        loc.connections.map(async (id) => (await db.getLocation(env, id))?.name ?? id),
      );
      blocks.push(`[ВЫХОДЫ] ${exits.join("; ")}`);
    }

    // [NPCS] — только присутствующие
    const npcs = await db.getNpcsInLocation(env, loc.id);
    if (npcs.length) {
      blocks.push(
        `[NPCS]\n` +
          npcs
            .map(
              (n) =>
                `- ${npcName(n)} (${n.role}): ${n.description}` +
                (n.dialogue_style ? ` Стиль речи: ${n.dialogue_style}` : ""),
            )
            .join("\n"),
      );
    }

    // [ВРАГИ]
    if (loc.enemies?.length) {
      const enemies = (await Promise.all(loc.enemies.map((id) => db.getEnemy(env, id)))).filter(
        Boolean,
      );
      if (enemies.length) {
        blocks.push(`[ВРАГИ]\n` + enemies.map((e) => `- ${e!.name}: ${e!.description}`).join("\n"));
      }
    }

    // [ITEMS] — у игрока, в локации, у присутствующих NPC
    const npcItemIds = npcs.flatMap((n) => n.inventory ?? []);
    const playerItems = await db.getItems(env, player.inventory);
    const locItems = await db.getItemsInLocation(env, loc.id);
    const npcItems = await db.getItems(env, npcItemIds);
    const itemLine = (it: Item, where: string) =>
      `- ${it.name} [${where}]: ${it.description}` + (it.price ? ` (цена: ${it.price})` : "");
    const itemBlock = [
      ...playerItems.map((it) => itemLine(it, "у игрока")),
      ...locItems.map((it) => itemLine(it, "в локации")),
      ...npcItems.map((it) => itemLine(it, "на продажу")),
    ];
    if (itemBlock.length) blocks.push(`[ITEMS]\n${itemBlock.join("\n")}`);
  }

  // [БОЙ] — статус текущего боя (качественно, без чисел), чтобы DM был консистентен
  if (player.combat_session) {
    const s = player.combat_session;
    const enemy = await db.getEnemy(env, s.enemyId);
    const npc = enemy ? null : await db.getNpc(env, s.enemyId);
    const maxHp = enemy?.hp ?? npc?.combat?.hp ?? s.enemyHp;
    const enemyName = enemy?.name ?? (npc ? (npc.name ?? npc.role_label) : s.enemyId);
    const ratio = maxHp > 0 ? s.enemyHp / maxHp : 0;
    const cond = ratio > 0.66 ? "почти невредим" : ratio > 0.33 ? "ранен" : "тяжело ранен";
    blocks.push(`[БОЙ] Идёт бой. ${enemyName}: ${cond}. Бой не окончен.`);
  }

  // [QUESTS] — активные
  const progress = await db.getQuestProgress(env, player.telegram_user_id);
  const activeIds = progress.filter((p) => p.status === "active").map((p) => p.quest_id);
  const defs = await db.getQuestDefs(env, activeIds);
  if (defs.length) {
    blocks.push(
      `[QUESTS]\n` +
        defs
          .map((d) => {
            const cur = progress.find((p) => p.quest_id === d.id);
            const stage = d.stages.find((s) => s.id === cur?.stage);
            return `- ${d.name}: ${stage?.goal ?? ""}`;
          })
          .join("\n"),
    );
  }

  // [ЖУРНАЛ] — последние ходы
  const recent = await env.DB.prepare(
    "SELECT player_action, narrative FROM turns WHERE telegram_user_id=? ORDER BY turn_index DESC LIMIT 6",
  )
    .bind(player.telegram_user_id)
    .all<{ player_action: string; narrative: string }>();
  if (recent.results.length) {
    const lines = recent.results
      .reverse()
      .map((t) => `Игрок: ${t.player_action}\nDM: ${t.narrative}`)
      .join("\n");
    blocks.push(`[ЖУРНАЛ]\n${lines}`);
  }

  return blocks.join("\n\n");
}
