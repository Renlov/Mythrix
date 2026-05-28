// Чистая логика поведения NPC-стейта: разделение на живых/мёртвых,
// текущее отношение, союзники. Никаких обращений к БД — для юнит-тестов.

import type { Npc, NpcDisposition, NpcState } from "./types.js";

export interface PartitionResult {
  alive: Npc[];
  dead: Npc[];
}

// Разделяет NPC по их персональному состоянию. Если состояния нет —
// дефолт «жив».
export function partitionByAlive(npcs: Npc[], states: NpcState[]): PartitionResult {
  const stateById = new Map(states.map((s) => [s.npc_id, s]));
  return {
    alive: npcs.filter((n) => stateById.get(n.id)?.alive !== false),
    dead: npcs.filter((n) => stateById.get(n.id)?.alive === false),
  };
}

// Итоговое отношение NPC к игроку: state-override > карточка NPC > "neutral".
export function dispositionOf(npc: Npc, state: NpcState | undefined): NpcDisposition {
  return state?.disposition ?? npc.disposition ?? "neutral";
}

// Союзники атакованного NPC, которых надо перевести в hostile.
// Исключаем уже мёртвых.
export function alliesToFlagHostile(
  attackedNpc: Npc,
  alivenessByAlly: Map<string, boolean>,
): string[] {
  const allies = attackedNpc.allies ?? [];
  return allies.filter((id) => alivenessByAlly.get(id) !== false);
}

// id предметов «при теле» (инвентари мёртвых NPC текущей сцены).
export function corpseInventoryIds(deadNpcs: Npc[]): string[] {
  return deadNpcs.flatMap((n) => n.inventory ?? []);
}

// Можно ли подобрать предмет: либо лежит в текущей локации, либо принадлежит
// мёртвому NPC в этой же локации (обыск трупа).
export function canTakeItem(
  itemId: string,
  locationItemIds: Set<string>,
  corpseItemIds: Set<string>,
): boolean {
  return locationItemIds.has(itemId) || corpseItemIds.has(itemId);
}
