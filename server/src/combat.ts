// Чистый боевой движок: броски и разрешение атаки. RNG инъектируется.
import type { CombatStats } from "./types.js";

export type Rng = () => number; // [0, 1)

export function makeRng(seed?: number): Rng {
  if (seed === undefined) return Math.random;
  // Детерминированный mulberry32 — для тестов.
  let s = seed >>> 0;
  return () => {
    s |= 0;
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function d20(rng: Rng): number {
  return Math.floor(rng() * 20) + 1;
}

// Парсит "d8", "d4+2", "2d6", "d2" и кидает.
export function rollDice(expr: string, rng: Rng): number {
  const m = expr.trim().match(/^(\d*)d(\d+)([+-]\d+)?$/);
  if (!m) throw new Error(`bad dice expr: ${expr}`);
  const count = m[1] ? parseInt(m[1], 10) : 1;
  const sides = parseInt(m[2], 10);
  const mod = m[3] ? parseInt(m[3], 10) : 0;
  let total = mod;
  for (let i = 0; i < count; i++) total += Math.floor(rng() * sides) + 1;
  return total;
}

export interface CombatResult {
  attacker: string;
  target: string;
  hit: boolean;
  roll: number;
  damage: number;
  targetHpBefore: number;
  targetHpAfter: number;
  killed: boolean;
}

// Разрешение одной атаки. Попадание: d20 + attackBonus >= targetAc.
export function resolveAttack(
  attacker: string,
  target: string,
  attackBonus: number,
  damageDie: string,
  targetAc: number,
  targetHp: number,
  rng: Rng,
): CombatResult {
  const roll = d20(rng);
  const hit = roll + attackBonus >= targetAc;
  const damage = hit ? rollDice(damageDie, rng) : 0;
  const targetHpAfter = Math.max(0, targetHp - damage);
  return {
    attacker,
    target,
    hit,
    roll,
    damage,
    targetHpBefore: targetHp,
    targetHpAfter,
    killed: targetHpAfter === 0,
  };
}

// AC игрока = base_ac правил + бонус надетой брони.
export function playerAc(baseAc: number, armorBonus: number): number {
  return baseAc + armorBonus;
}

export function enemyAttack(
  enemyId: string,
  stats: CombatStats,
  targetAc: number,
  targetHp: number,
  rng: Rng,
): CombatResult {
  return resolveAttack(
    enemyId,
    "player_main",
    stats.attack_bonus,
    stats.attack_die,
    targetAc,
    targetHp,
    rng,
  );
}
