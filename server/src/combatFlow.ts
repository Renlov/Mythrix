import type { Env, PlayerState, Intent, CombatStats } from "./types.js";
import * as db from "./db.js";
import { resolveAttack, enemyAttack, playerAc, makeRng, type Rng } from "./combat.js";
import { useItem } from "./inventory.js";

export interface CombatOutcome {
  player: PlayerState;
  turnResult: string; // блок [TURN RESULT] для фазы 2 (с числами, для DM)
}

// ФАЗА 1: разрешает удар игрока по цели. Ответный удар врага НЕ делается —
// он отложен в combat_session.pendingEnemyTurn и будет разрешён отдельным
// запросом /enemy-turn (см. resolveEnemyTurn). Это даёт два отдельных
// сообщения в ленте: «удар игрока» и «удар врага».
export async function resolvePlayerAttack(
  env: Env,
  player: PlayerState,
  intent: Intent,
  rng: Rng = makeRng(),
): Promise<CombatOutcome | null> {
  const targetId = intent.target as string | undefined;
  if (!targetId) return null;

  const targetStats = await targetCombatStats(env, targetId);
  if (!targetStats) return null;

  const cfg = await db.getRulesetConfig(env);
  const cls = (await db.getClass(env, player.class_id)) ?? {};
  const pAttackBonus = (cls.attack_bonus as number) ?? 0;
  const weaponDie = (await equippedWeaponDie(env, player)) ?? (cfg.unarmed_die as string) ?? "d2";

  const session =
    player.combat_session && player.combat_session.enemyId === targetId
      ? player.combat_session
      : { enemyId: targetId, enemyHp: targetStats.hp, playerTurn: true };

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
  if (pr.killed) {
    lines.push("Цель повержена.");
    p = { ...p, combat_session: null };
  } else {
    // Враг жив — отложим его ответный удар до /enemy-turn.
    p = {
      ...p,
      combat_session: {
        enemyId: targetId,
        enemyHp: pr.targetHpAfter,
        playerTurn: false,
        pendingEnemyTurn: true,
      },
    };
  }

  return { player: p, turnResult: `[TURN RESULT]\n${lines.join("\n")}` };
}

// ФАЗА 2: ответный удар врага по игроку. Вызывается из /enemy-turn после
// фазы игрока. Использует противника из combat_session.
export async function resolveEnemyTurn(
  env: Env,
  player: PlayerState,
  rng: Rng = makeRng(),
): Promise<CombatOutcome | null> {
  const session = player.combat_session;
  if (!session || !session.pendingEnemyTurn) return null;
  const stats = await targetCombatStats(env, session.enemyId);
  if (!stats) return null;

  const cfg = await db.getRulesetConfig(env);
  const cls = (await db.getClass(env, player.class_id)) ?? {};
  const pAc = await playerArmorClass(env, player, cls, cfg);
  const er = enemyAttack(session.enemyId, stats, pAc, player.hp, rng);

  const lines = [
    er.hit
      ? `Противник попал (бросок ${er.roll}, урон ${er.damage}). Игрок HP ${er.targetHpAfter}/${player.max_hp}.`
      : `Противник промахнулся (бросок ${er.roll}).`,
  ];

  let p: PlayerState = {
    ...player,
    hp: er.targetHpAfter,
    combat_session: {
      enemyId: session.enemyId,
      enemyHp: session.enemyHp,
      playerTurn: true,
      pendingEnemyTurn: false,
    },
  };
  if (er.killed) {
    lines.push("Игрок погиб.");
    p = { ...p, combat_session: null };
  }
  return { player: p, turnResult: `[TURN RESULT]\n${lines.join("\n")}` };
}

// Применение расходника в бою — это ход игрока. Лечение происходит сейчас,
// ответный удар откладывается до /enemy-turn (как и обычная атака).
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

  const session = use.player.combat_session;
  const p: PlayerState = session
    ? {
        ...use.player,
        combat_session: { ...session, playerTurn: false, pendingEnemyTurn: true },
      }
    : use.player;

  return { player: p, turnResult: `[TURN RESULT]\n${use.resultLine}` };
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
