import { describe, it, expect, vi, beforeEach } from "vitest";
import type { Env, PlayerState, Item, Enemy, Intent } from "./types.js";
import { makeRng } from "./combat.js";

// Мокаем db: combatFlow тянет конфиг класса, ruleset, врагов, NPC и предметы.
vi.mock("./db.js", () => {
  return {
    getRulesetConfig: vi.fn(),
    getClass: vi.fn(),
    getEnemy: vi.fn(),
    getNpc: vi.fn(),
    getItems: vi.fn(),
  };
});

import * as db from "./db.js";
import { resolvePlayerAttack, resolveEnemyTurn, resolveUseItemIntent } from "./combatFlow.js";

const mocked = db as unknown as {
  getRulesetConfig: ReturnType<typeof vi.fn>;
  getClass: ReturnType<typeof vi.fn>;
  getEnemy: ReturnType<typeof vi.fn>;
  getNpc: ReturnType<typeof vi.fn>;
  getItems: ReturnType<typeof vi.fn>;
};

const env = {} as Env;

function basePlayer(over: Partial<PlayerState> = {}): PlayerState {
  return {
    telegram_user_id: "u1",
    world_id: "tavern",
    ruleset_id: "dnd",
    class_id: "wanderer",
    name: "Кейн",
    level: 1,
    hp: 10,
    max_hp: 10,
    location_id: "loc_x",
    gold: 0,
    inventory: [],
    equipped: {},
    item_charges: {},
    known_npcs: [],
    status_effects: [],
    combat_session: null,
    game_over: false,
    ...over,
  };
}

function enemy(over: Partial<Enemy> = {}): Enemy {
  return {
    id: "enemy_wolf",
    name: "волк",
    hp: 6,
    ac: 10,
    attack_die: "d4",
    attack_bonus: 0,
    description: "",
    ...over,
  } as Enemy;
}

// Высокий бонус игрока + низкий AC врага → всегда попадание; обратное — промах.
const HIT_BONUS = 100;
const MISS_BONUS = -100;

beforeEach(() => {
  vi.resetAllMocks();
  mocked.getRulesetConfig.mockResolvedValue({ unarmed_die: "d2", player_base_ac: 10 });
  mocked.getClass.mockResolvedValue({ hp: 10, attack_bonus: 0, base_ac: 10 });
  mocked.getNpc.mockResolvedValue(null);
  mocked.getItems.mockResolvedValue([] as Item[]);
});

describe("resolvePlayerAttack", () => {
  const attackIntent: Intent = { type: "attack", target: "enemy_wolf" };

  it("без target → null", async () => {
    const r = await resolvePlayerAttack(env, basePlayer(), { type: "attack" });
    expect(r).toBeNull();
  });

  it("у цели нет боевых характеристик → null", async () => {
    mocked.getEnemy.mockResolvedValue(null);
    mocked.getNpc.mockResolvedValue({ id: "npc_x", combat: null });
    const r = await resolvePlayerAttack(env, basePlayer(), attackIntent);
    expect(r).toBeNull();
  });

  it("попадание оставляет врага живым и отмечает pendingEnemyTurn", async () => {
    mocked.getEnemy.mockResolvedValue(enemy({ hp: 10, ac: 1 }));
    mocked.getClass.mockResolvedValue({ attack_bonus: HIT_BONUS, base_ac: 10 });
    const r = await resolvePlayerAttack(env, basePlayer(), attackIntent, makeRng(1));
    expect(r).not.toBeNull();
    const session = r!.player.combat_session!;
    expect(session.enemyId).toBe("enemy_wolf");
    expect(session.pendingEnemyTurn).toBe(true);
    expect(session.playerTurn).toBe(false);
    expect(session.enemyHp).toBeLessThan(10);
    expect(r!.turnResult).toContain("Игрок попал");
    expect(r!.turnResult).not.toContain("Противник");
  });

  it("промах не меняет HP врага, но всё равно отмечает pendingEnemyTurn", async () => {
    mocked.getEnemy.mockResolvedValue(enemy({ hp: 8, ac: 99 }));
    mocked.getClass.mockResolvedValue({ attack_bonus: MISS_BONUS, base_ac: 10 });
    const r = await resolvePlayerAttack(env, basePlayer(), attackIntent, makeRng(2));
    const session = r!.player.combat_session!;
    expect(session.enemyHp).toBe(8);
    expect(session.pendingEnemyTurn).toBe(true);
    expect(r!.turnResult).toContain("Игрок промахнулся");
  });

  it("убийство закрывает combat_session и не оставляет pendingEnemyTurn", async () => {
    mocked.getEnemy.mockResolvedValue(enemy({ hp: 1, ac: 1 }));
    mocked.getClass.mockResolvedValue({ attack_bonus: HIT_BONUS, base_ac: 10 });
    const r = await resolvePlayerAttack(env, basePlayer(), attackIntent, makeRng(3));
    expect(r!.player.combat_session).toBeNull();
    expect(r!.turnResult).toContain("Цель повержена");
  });

  it("использует HP врага из текущей combat_session, а не из его базовых статов", async () => {
    mocked.getEnemy.mockResolvedValue(enemy({ hp: 20, ac: 1 }));
    mocked.getClass.mockResolvedValue({ attack_bonus: HIT_BONUS, base_ac: 10 });
    const p = basePlayer({
      combat_session: { enemyId: "enemy_wolf", enemyHp: 3, playerTurn: true },
    });
    const r = await resolvePlayerAttack(env, p, attackIntent, makeRng(4));
    // 3 - урон >= 0; ожидаем либо убийство, либо очень низкий HP.
    expect(r!.player.combat_session?.enemyHp ?? 0).toBeLessThan(3);
  });
});

describe("resolveEnemyTurn", () => {
  it("без combat_session → null", async () => {
    const r = await resolveEnemyTurn(env, basePlayer());
    expect(r).toBeNull();
  });

  it("без pendingEnemyTurn → null", async () => {
    const p = basePlayer({
      combat_session: { enemyId: "enemy_wolf", enemyHp: 4, playerTurn: true },
    });
    const r = await resolveEnemyTurn(env, p);
    expect(r).toBeNull();
  });

  it("попадание врага снижает HP игрока и снимает pendingEnemyTurn", async () => {
    mocked.getEnemy.mockResolvedValue(enemy({ attack_bonus: HIT_BONUS, attack_die: "d4" }));
    const p = basePlayer({
      hp: 10,
      combat_session: {
        enemyId: "enemy_wolf",
        enemyHp: 4,
        playerTurn: false,
        pendingEnemyTurn: true,
      },
    });
    const r = await resolveEnemyTurn(env, p, makeRng(5));
    expect(r!.player.hp).toBeLessThan(10);
    expect(r!.player.combat_session?.pendingEnemyTurn).toBe(false);
    expect(r!.player.combat_session?.playerTurn).toBe(true);
    expect(r!.player.combat_session?.enemyHp).toBe(4);
    expect(r!.turnResult).toContain("Противник попал");
  });

  it("промах врага не меняет HP", async () => {
    mocked.getEnemy.mockResolvedValue(enemy({ attack_bonus: MISS_BONUS, attack_die: "d4" }));
    const p = basePlayer({
      hp: 10,
      combat_session: {
        enemyId: "enemy_wolf",
        enemyHp: 4,
        playerTurn: false,
        pendingEnemyTurn: true,
      },
    });
    const r = await resolveEnemyTurn(env, p, makeRng(6));
    expect(r!.player.hp).toBe(10);
    expect(r!.turnResult).toContain("Противник промахнулся");
  });

  it("смертельный удар врага закрывает combat_session", async () => {
    mocked.getEnemy.mockResolvedValue(enemy({ attack_bonus: HIT_BONUS, attack_die: "d4+10" }));
    const p = basePlayer({
      hp: 1,
      combat_session: {
        enemyId: "enemy_wolf",
        enemyHp: 4,
        playerTurn: false,
        pendingEnemyTurn: true,
      },
    });
    const r = await resolveEnemyTurn(env, p, makeRng(7));
    expect(r!.player.hp).toBe(0);
    expect(r!.player.combat_session).toBeNull();
    expect(r!.turnResult).toContain("Игрок погиб");
  });
});

describe("resolveUseItemIntent", () => {
  const healIntent: Intent = { type: "use_item", item_id: "item_potion" };
  const potion: Partial<Item> = {
    id: "item_potion",
    name: "зелье",
    type: "consumable",
    subtype: "healing",
    heal_die: "d4+2",
    uses: 1,
  };

  it("вне боя → null (обрабатывается обычным потоком)", async () => {
    const r = await resolveUseItemIntent(env, basePlayer(), healIntent);
    expect(r).toBeNull();
  });

  it("в бою лечит и помечает pendingEnemyTurn у врага", async () => {
    mocked.getItems.mockResolvedValue([potion as Item]);
    const p = basePlayer({
      hp: 4,
      max_hp: 10,
      inventory: ["item_potion"],
      combat_session: { enemyId: "enemy_wolf", enemyHp: 5, playerTurn: true },
    });
    const r = await resolveUseItemIntent(env, p, healIntent, makeRng(8));
    expect(r).not.toBeNull();
    expect(r!.player.hp).toBeGreaterThan(4);
    expect(r!.player.combat_session?.pendingEnemyTurn).toBe(true);
    expect(r!.player.combat_session?.playerTurn).toBe(false);
    expect(r!.turnResult).toContain("зелье");
  });

  it("без item_id → null", async () => {
    const p = basePlayer({
      combat_session: { enemyId: "enemy_wolf", enemyHp: 5, playerTurn: true },
    });
    const r = await resolveUseItemIntent(env, p, { type: "use_item" });
    expect(r).toBeNull();
  });
});
