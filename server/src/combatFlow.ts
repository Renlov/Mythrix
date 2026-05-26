import type { Env, PlayerState, Intent, CombatStats } from "./types.js";
import * as db from "./db.js";
import { resolveAttack, enemyAttack, playerAc, makeRng, type Rng } from "./combat.js";
import { useItem } from "./inventory.js";

export interface CombatOutcome {
  player: PlayerState;
  turnResult: string; // блок [TURN RESULT] для фазы 2 (с числами, для DM)
}

// Разрешает атаку игрока по цели (враг или NPC с combat), затем ответный удар.
// Возвращает обновлённое состояние игрока и текст [TURN RESULT].
export async function resolveAttackIntent(
  env: Env,
  player: PlayerState,
  intent: Intent,
  rng: Rng = makeRng(),
): Promise<CombatOutcome | null> {
  const targetId = intent.target as string | undefined;
  if (!targetId) return null;

  const targetStats = await targetCombatStats(env, targetId);
  if (!targetStats) return null; // нет боевых характеристик → не механический бой

  const cfg = await db.getRulesetConfig(env);
  const cls = (await db.getClass(env, player.class_id)) ?? {};

  // Параметры игрока.
  const pAc = await playerArmorClass(env, player, cls, cfg);
  const pAttackBonus = (cls.attack_bonus as number) ?? 0;
  const weaponDie = (await equippedWeaponDie(env, player)) ?? (cfg.unarmed_die as string) ?? "d2";

  // HP цели берём из combat_session (если бой продолжается) или из статов.
  const session =
    player.combat_session && player.combat_session.enemyId === targetId
      ? player.combat_session
      : { enemyId: targetId, enemyHp: targetStats.hp, playerTurn: true };

  // Фаза игрока.
  const pr = resolveAttack(
    "player_main",
    targetId,
    pAttackBonus,
    weaponDie,
    targetStats.ac,
    session.enemyHp,
    rng,
  );

  const lines: string[] = [];
  lines.push(
    pr.hit
      ? `Игрок попал (бросок ${pr.roll}, урон ${pr.damage}). Цель HP ${pr.targetHpAfter}/${targetStats.hp}.`
      : `Игрок промахнулся (бросок ${pr.roll}).`,
  );

  let p: PlayerState = { ...player };
  const enemyHp = pr.targetHpAfter;

  if (pr.killed) {
    lines.push("Цель повержена.");
    p = { ...p, combat_session: null };
  } else {
    // Ответный удар.
    const er = enemyAttack(targetId, targetStats, pAc, p.hp, rng);
    lines.push(
      er.hit
        ? `Противник попал (бросок ${er.roll}, урон ${er.damage}). Игрок HP ${er.targetHpAfter}/${p.max_hp}.`
        : `Противник промахнулся (бросок ${er.roll}).`,
    );
    p = {
      ...p,
      hp: er.targetHpAfter,
      combat_session: { enemyId: targetId, enemyHp, playerTurn: true },
    };
    if (er.killed) {
      lines.push("Игрок погиб.");
      p = { ...p, combat_session: null };
    }
  }

  return { player: p, turnResult: `[TURN RESULT]\n${lines.join("\n")}` };
}

// Применение расходника в бою — это ход игрока: лечение + ответный удар врага.
// Возвращает null, если использовать нельзя (нет боя/предмета) — тогда intent
// обработается обычным потоком applyEvents.
export async function resolveUseItemIntent(
  env: Env,
  player: PlayerState,
  intent: Intent,
  rng: Rng = makeRng(),
): Promise<CombatOutcome | null> {
  if (!player.combat_session) return null;
  const itemId = intent.item_id as string | undefined;
  if (!itemId) return null;

  const use = await useItem(env, player, itemId, rng);
  if (use.error) return null;

  const lines: string[] = [use.resultLine];
  let p: PlayerState = use.player;

  const session = p.combat_session;
  if (session) {
    const stats = await targetCombatStats(env, session.enemyId);
    if (stats) {
      const cfg = await db.getRulesetConfig(env);
      const cls = (await db.getClass(env, p.class_id)) ?? {};
      const pAc = await playerArmorClass(env, p, cls, cfg);
      const er = enemyAttack(session.enemyId, stats, pAc, p.hp, rng);
      lines.push(
        er.hit
          ? `Противник попал (бросок ${er.roll}, урон ${er.damage}). Игрок HP ${er.targetHpAfter}/${p.max_hp}.`
          : `Противник промахнулся (бросок ${er.roll}).`,
      );
      p = { ...p, hp: er.targetHpAfter };
      if (er.killed) {
        lines.push("Игрок погиб.");
        p = { ...p, combat_session: null };
      }
    }
  }

  return { player: p, turnResult: `[TURN RESULT]\n${lines.join("\n")}` };
}

async function playerArmorClass(
  env: Env,
  player: PlayerState,
  cls: Record<string, unknown>,
  cfg: Record<string, unknown>,
): Promise<number> {
  const baseAc = (cls.base_ac as number) ?? (cfg.player_base_ac as number) ?? 10;
  const armorBonus = await equippedArmorBonus(env, player);
  return playerAc(baseAc, armorBonus);
}

async function targetCombatStats(env: Env, targetId: string): Promise<CombatStats | null> {
  const enemy = await db.getEnemy(env, targetId);
  if (enemy) return { hp: enemy.hp, ac: enemy.ac, attack_die: enemy.attack_die, attack_bonus: enemy.attack_bonus };
  const npc = await db.getNpc(env, targetId);
  return npc?.combat ?? null;
}

async function equippedWeaponDie(env: Env, player: PlayerState): Promise<string | null> {
  const id = player.equipped.weapon;
  if (!id) return null;
  const [item] = await db.getItems(env, [id]);
  return item?.damage_die ?? null;
}

async function equippedArmorBonus(env: Env, player: PlayerState): Promise<number> {
  const id = player.equipped.armor;
  if (!id) return 0;
  const [item] = await db.getItems(env, [id]);
  return item?.armor_bonus ?? 0;
}
