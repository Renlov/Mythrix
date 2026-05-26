import type { Env, PlayerState } from "./types.js";
import * as db from "./db.js";

export type EquipSlot = "weapon" | "armor";

export interface EquipResult {
  player: PlayerState;
  error?: string;
}

const TYPE_TO_SLOT: Record<string, EquipSlot> = { weapon: "weapon", armor: "armor" };

// Надеть предмет из инвентаря. Один предмет на слот (перетирает прежний).
export async function equipItem(
  env: Env,
  player: PlayerState,
  itemId: string,
): Promise<EquipResult> {
  if (!player.inventory.includes(itemId)) {
    return { player, error: "предмета нет в инвентаре" };
  }
  const [item] = await db.getItems(env, [itemId]);
  if (!item) return { player, error: "предмет не найден" };

  const slot = TYPE_TO_SLOT[item.type];
  if (!slot) return { player, error: `тип '${item.type}' нельзя экипировать` };

  return { player: { ...player, equipped: { ...player.equipped, [slot]: itemId } } };
}

// Снять предмет со слота.
export function unequipSlot(player: PlayerState, slot: EquipSlot): EquipResult {
  const equipped = { ...player.equipped };
  delete equipped[slot];
  return { player: { ...player, equipped } };
}
