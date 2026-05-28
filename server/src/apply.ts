import type { Env, PlayerState, DmEvents, Intent } from "./types.js";
import * as db from "./db.js";
import { useItem, removeFromInventory } from "./inventory.js";

export interface ApplyResult {
  player: PlayerState;
  warnings: string[];
}

// Применяет не-боевые intents и location_change к состоянию игрока.
// Боевые intents (attack/skill_check) обрабатываются отдельно (combat-флоу).
export async function applyEvents(
  env: Env,
  player: PlayerState,
  events: DmEvents,
): Promise<ApplyResult> {
  const warnings: string[] = [];
  let p: PlayerState = { ...player };
  const PLAYER = "player_main";

  for (const intent of events.intents) {
    p = await applyIntent(env, p, intent, warnings, PLAYER);
  }

  // location_change — только по связям текущей локации.
  if (events.location_change) {
    const cur = await db.getLocation(env, p.location_id);
    if (cur?.connections?.includes(events.location_change)) {
      p = { ...p, location_id: events.location_change };
    } else {
      warnings.push(`location_change в несвязанную локацию: ${events.location_change}`);
    }
  }

  return { player: p, warnings };
}

async function applyIntent(
  env: Env,
  p: PlayerState,
  intent: Intent,
  warnings: string[],
  PLAYER: string,
): Promise<PlayerState> {
  const itemId = intent.item_id as string | undefined;

  switch (intent.type) {
    case "buy": {
      if (!itemId) return p;
      const [item] = await db.getItems(env, [itemId]);
      if (!item) return warn(p, warnings, `buy: нет предмета ${itemId}`);
      if (p.gold < item.price) return warn(p, warnings, `buy: не хватает золота на ${itemId}`);
      if (p.inventory.includes(itemId)) return warn(p, warnings, `buy: уже в инвентаре ${itemId}`);
      return { ...p, gold: p.gold - item.price, inventory: [...p.inventory, itemId] };
    }
    case "give_item": {
      // NPC → игрок (только в эту сторону для v1).
      if (!itemId) return p;
      if (intent.to !== PLAYER) return warn(p, warnings, `give_item: to != player (${intent.to})`);
      if (p.inventory.includes(itemId)) return p;
      return { ...p, inventory: [...p.inventory, itemId] };
    }
    case "take_item": {
      if (!itemId) return p;
      // Предмет берётся, если он в локации ИЛИ принадлежит мёртвому NPC здесь (обыск трупа).
      const locItems = await db.getItemsInLocation(env, p.location_id);
      let allowed = locItems.some((it) => it.id === itemId);
      if (!allowed) {
        const npcs = await db.getNpcsInLocation(env, p.location_id);
        if (npcs.length) {
          const states = await db.getNpcStates(env, p.telegram_user_id, npcs.map((n) => n.id));
          const deadIds = new Set(states.filter((s) => !s.alive).map((s) => s.npc_id));
          const corpseItemIds = new Set(
            npcs.filter((n) => deadIds.has(n.id)).flatMap((n) => n.inventory ?? []),
          );
          allowed = corpseItemIds.has(itemId);
        }
      }
      if (!allowed) return warn(p, warnings, `take_item: предмета нет в локации ${itemId}`);
      if (p.inventory.includes(itemId)) return p;
      return { ...p, inventory: [...p.inventory, itemId] };
    }
    case "drop_item": {
      if (!itemId) return p;
      const res = removeFromInventory(p, itemId);
      if (res.error) return warn(p, warnings, `drop_item: ${res.error}`);
      return res.player;
    }
    case "give_to_npc": {
      // Игрок отдаёт предмет NPC — предмет уходит из инвентаря.
      if (!itemId) return p;
      const res = removeFromInventory(p, itemId);
      if (res.error) return warn(p, warnings, `give_to_npc: ${res.error}`);
      return res.player;
    }
    case "use_item": {
      // Применение расходника вне боя (в бою обрабатывается combat-флоу до applyEvents).
      if (!itemId) return p;
      const res = await useItem(env, p, itemId);
      if (res.error) return warn(p, warnings, `use_item: ${res.error}`);
      return res.player;
    }
    case "meet_npc": {
      const npcId = intent.npc_id as string | undefined;
      if (!npcId || p.known_npcs.includes(npcId)) return p;
      return { ...p, known_npcs: [...p.known_npcs, npcId] };
    }
    case "quest_advance": {
      const questId = intent.quest_id as string | undefined;
      const newStage = intent.new_stage as number | undefined;
      if (!questId || newStage === undefined) return p;
      await db.setQuestProgress(env, p.telegram_user_id, questId, "active", newStage);
      return p;
    }
    case "attack":
    case "skill_check":
      // Обрабатывается в combat-флоу (см. index.ts). Здесь пропускаем.
      return p;
    default:
      return warn(p, warnings, `неизвестный intent: ${intent.type}`);
  }
}

function warn(p: PlayerState, warnings: string[], msg: string): PlayerState {
  warnings.push(msg);
  return p;
}
