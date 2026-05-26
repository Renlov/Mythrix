import { describe, it, expect } from "vitest";
import { makeRng, d20, rollDice, resolveAttack, playerAc, enemyAttack } from "./combat.js";

describe("rollDice", () => {
  it("парсит и кидает в диапазоне", () => {
    const rng = makeRng(1);
    for (let i = 0; i < 100; i++) {
      const v = rollDice("d6", rng);
      expect(v).toBeGreaterThanOrEqual(1);
      expect(v).toBeLessThanOrEqual(6);
    }
  });

  it("учитывает модификатор и количество", () => {
    const rng = makeRng(42);
    for (let i = 0; i < 100; i++) {
      const v = rollDice("2d6+1", rng);
      expect(v).toBeGreaterThanOrEqual(3); // 2*1 + 1
      expect(v).toBeLessThanOrEqual(13); // 2*6 + 1
    }
  });

  it("детерминирован по seed", () => {
    const a = makeRng(123);
    const b = makeRng(123);
    const seqA = Array.from({ length: 20 }, () => rollDice("d8", a));
    const seqB = Array.from({ length: 20 }, () => rollDice("d8", b));
    expect(seqA).toEqual(seqB);
  });

  it("бросает на некорректном выражении", () => {
    expect(() => rollDice("abc", makeRng(1))).toThrow();
  });
});

describe("d20", () => {
  it("в диапазоне 1..20", () => {
    const rng = makeRng(7);
    for (let i = 0; i < 200; i++) {
      const v = d20(rng);
      expect(v).toBeGreaterThanOrEqual(1);
      expect(v).toBeLessThanOrEqual(20);
    }
  });
});

describe("resolveAttack", () => {
  it("промах при недостаточном броске не наносит урона", () => {
    // targetAc заведомо недостижим (бросок + бонус < 100).
    const r = resolveAttack("a", "b", 0, "d6", 100, 10, makeRng(1));
    expect(r.hit).toBe(false);
    expect(r.damage).toBe(0);
    expect(r.targetHpAfter).toBe(10);
    expect(r.killed).toBe(false);
  });

  it("попадание при достижимом AC наносит урон, HP не ниже 0", () => {
    const r = resolveAttack("a", "b", 100, "d6", 1, 3, makeRng(2));
    expect(r.hit).toBe(true);
    expect(r.damage).toBeGreaterThanOrEqual(1);
    expect(r.targetHpAfter).toBe(Math.max(0, 3 - r.damage));
    expect(r.targetHpAfter).toBeGreaterThanOrEqual(0);
  });

  it("убивает, когда урон >= HP", () => {
    const r = resolveAttack("a", "b", 100, "d6", 1, 1, makeRng(3));
    expect(r.hit).toBe(true);
    expect(r.killed).toBe(true);
    expect(r.targetHpAfter).toBe(0);
  });
});

describe("playerAc", () => {
  it("складывает базовый AC и бонус брони", () => {
    expect(playerAc(10, 0)).toBe(10);
    expect(playerAc(10, 2)).toBe(12);
  });
});

describe("enemyAttack", () => {
  it("использует характеристики врага против игрока", () => {
    const r = enemyAttack(
      "enemy_x",
      { hp: 8, ac: 11, attack_die: "d4", attack_bonus: 100 },
      1,
      10,
      makeRng(5),
    );
    expect(r.attacker).toBe("enemy_x");
    expect(r.target).toBe("player_main");
    expect(r.hit).toBe(true);
  });
});
