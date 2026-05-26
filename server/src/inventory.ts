import type { Env, PlayerState } from "./types.js";
import * as db from "./db.js";
import { rollDice, makeRng, type Rng } from "./combat.js";

export interface UseResult {
  player: PlayerState;
  resultLine: string; // факт с числами для [TURN RESULT] / лога (игроку числа не показываем)
  healed?: number;
  error?: string;
}

// Применить расходник из инвентаря. Эффект (лечение и т.п.) считает сервер.
// Заряды: при первом использовании берём item.uses; на нуле — предмет уходит из инвентаря.
export async function useItem(
  env: Env,
  player: PlayerState,
  itemId: string,
  rng: Rng = makeRng(),
): Promise<UseResult> {
  if (!player.inventory.includes(itemId))
    return { player, resultLine: "", error: "предмета нет в инвентаре" };

  const [item] = await db.getItems(env, [itemId]);
  if (!item) return { player, resultLine: "", error: "предмет не найден" };
  if (item.type !== "consumable")
    return { player, resultLine: "", error: `предмет '${item.type}' нельзя использовать` };

  const maxUses = item.uses ?? 1;
  const current = player.item_charges[itemId] ?? maxUses;
  if (current <= 0) return { player, resultLine: "", error: "предмет израсходован" };

  let p: PlayerState = { ...player };
  let resultLine: string;
  let healed: number | undefined;

  if (item.subtype === "healing" && item.heal_die) {
    const roll = rollDice(item.heal_die, rng);
    const after = Math.min(p.max_hp, p.hp + roll);
    healed = after - p.hp;
    p = { ...p, hp: after };
    resultLine = `Игрок применил «${item.name}»: восстановлено ${healed} HP (бросок ${roll}). HP ${after}/${p.max_hp}.`;
  } else {
    resultLine = `Игрок применил «${item.name}».`;
  }

  return { player: consumeCharge(p, itemId, current, maxUses), resultLine, healed };
}

// Списывает один заряд; при исчерпании убирает предмет из инвентаря.
function consumeCharge(
  p: PlayerState,
  itemId: string,
  current: number,
  _maxUses: number,
): PlayerState {
  const remaining = current - 1;
  const item_charges = { ...p.item_charges };
  if (remaining <= 0) {
    delete item_charges[itemId];
    return { ...p, item_charges, inventory: p.inventory.filter((id) => id !== itemId) };
  }
  item_charges[itemId] = remaining;
  return { ...p, item_charges };
}

export interface RemoveResult {
  player: PlayerState;
  error?: string;
}

// Убрать предмет из инвентаря (выбросить или передать NPC). Снимает экипировку и чистит заряды.
export function removeFromInventory(player: PlayerState, itemId: string): RemoveResult {
  if (!player.inventory.includes(itemId))
    return { player, error: "предмета нет в инвентаре" };

  const equipped = { ...player.equipped };
  if (equipped.weapon === itemId) delete equipped.weapon;
  if (equipped.armor === itemId) delete equipped.armor;

  const item_charges = { ...player.item_charges };
  delete item_charges[itemId];

  return {
    player: {
      ...player,
      inventory: player.inventory.filter((id) => id !== itemId),
      equipped,
      item_charges,
    },
  };
}
