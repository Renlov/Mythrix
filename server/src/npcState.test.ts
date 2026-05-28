import { describe, it, expect } from "vitest";
import type { Npc, NpcState } from "./types.js";
import {
  partitionByAlive,
  dispositionOf,
  alliesToFlagHostile,
  corpseInventoryIds,
  canTakeItem,
} from "./npcState.js";
import { applyEvents } from "./apply.js";
import type { PlayerState, Env, Item, Location } from "./types.js";

// --- фабрики для лаконичности ---
function npc(id: string, extra: Partial<Npc> = {}): Npc {
  return {
    id,
    name: id,
    role_label: "Шахтёр",
    location_id: "loc_tavern",
    role: "civilian",
    description: "",
    inventory: [],
    ...extra,
  };
}

describe("partitionByAlive", () => {
  it("без состояний — все живы (дефолт)", () => {
    const npcs = [npc("a"), npc("b")];
    const { alive, dead } = partitionByAlive(npcs, []);
    expect(alive.map((n) => n.id)).toEqual(["a", "b"]);
    expect(dead).toEqual([]);
  });

  it("alive=false уводит NPC в dead", () => {
    const npcs = [npc("a"), npc("b"), npc("c")];
    const states: NpcState[] = [
      { npc_id: "b", alive: false, disposition: null },
      { npc_id: "c", alive: true, disposition: null },
    ];
    const { alive, dead } = partitionByAlive(npcs, states);
    expect(alive.map((n) => n.id).sort()).toEqual(["a", "c"]);
    expect(dead.map((n) => n.id)).toEqual(["b"]);
  });

  it("сохраняет порядок исходного списка", () => {
    const npcs = [npc("a"), npc("b"), npc("c"), npc("d")];
    const states: NpcState[] = [
      { npc_id: "a", alive: false, disposition: null },
      { npc_id: "c", alive: false, disposition: null },
    ];
    const { alive, dead } = partitionByAlive(npcs, states);
    expect(alive.map((n) => n.id)).toEqual(["b", "d"]);
    expect(dead.map((n) => n.id)).toEqual(["a", "c"]);
  });
});

describe("dispositionOf", () => {
  it("дефолт neutral, если ничего не указано", () => {
    expect(dispositionOf(npc("a"), undefined)).toBe("neutral");
  });

  it("берёт карточку NPC, если состояния нет", () => {
    expect(dispositionOf(npc("a", { disposition: "friendly" }), undefined)).toBe("friendly");
  });

  it("state перекрывает карточку (атака → союзник становится hostile)", () => {
    const n = npc("a", { disposition: "friendly" });
    const st: NpcState = { npc_id: "a", alive: true, disposition: "hostile" };
    expect(dispositionOf(n, st)).toBe("hostile");
  });
});

describe("alliesToFlagHostile", () => {
  it("возвращает всех живых союзников", () => {
    const attacked = npc("kettil", { allies: ["grim", "innkeeper"] });
    const aliveness = new Map([["grim", true], ["innkeeper", true]]);
    expect(alliesToFlagHostile(attacked, aliveness)).toEqual(["grim", "innkeeper"]);
  });

  it("исключает уже мёртвых союзников", () => {
    const attacked = npc("kettil", { allies: ["grim", "innkeeper"] });
    const aliveness = new Map([["grim", false], ["innkeeper", true]]);
    expect(alliesToFlagHostile(attacked, aliveness)).toEqual(["innkeeper"]);
  });

  it("нет союзников → пусто", () => {
    expect(alliesToFlagHostile(npc("a"), new Map())).toEqual([]);
  });
});

describe("corpseInventoryIds + canTakeItem (обыск трупа)", () => {
  it("из мёртвых NPC собирает их inventory", () => {
    const dead = [npc("d1", { inventory: ["sword", "shield"] }), npc("d2", { inventory: ["bow"] })];
    expect(corpseInventoryIds(dead).sort()).toEqual(["bow", "shield", "sword"]);
  });

  it("предмет с трупа можно взять", () => {
    expect(canTakeItem("sword", new Set(), new Set(["sword"]))).toBe(true);
  });

  it("предмет из локации можно взять", () => {
    expect(canTakeItem("torch", new Set(["torch"]), new Set())).toBe(true);
  });

  it("чужой предмет (живого NPC) брать нельзя", () => {
    expect(canTakeItem("ale", new Set(), new Set())).toBe(false);
  });
});

// --- интеграционный тест apply.ts с мини-моком Env.DB ---

// In-memory таблицы. Запросы матчатся по подстроке SQL — достаточно для нашего use-case.
interface InMemoryDb {
  locations: Map<string, Location>;
  npcs: Map<string, Npc>;
  items: Map<string, Item>;
  npc_state: Map<string, { alive: boolean }>; // key = `${uid}:${npc_id}`
}

function makeMockEnv(seed: Partial<InMemoryDb> = {}): { env: Env; store: InMemoryDb } {
  const store: InMemoryDb = {
    locations: seed.locations ?? new Map(),
    npcs: seed.npcs ?? new Map(),
    items: seed.items ?? new Map(),
    npc_state: seed.npc_state ?? new Map(),
  };

  const makeStmt = (sql: string, params: unknown[] = []): unknown => ({
    bind(...newParams: unknown[]) {
      return makeStmt(sql, newParams);
    },
    async first<T>(): Promise<T | null> {
      if (sql.includes("FROM locations")) {
        const loc = store.locations.get(String(params[1]));
        return loc ? ({ data: JSON.stringify(loc) } as unknown as T) : null;
      }
      return null;
    },
    async all<T>(): Promise<{ results: T[] }> {
      if (sql.includes("FROM npc_state")) {
        const uid = String(params[0]);
        const ids = params.slice(1).map(String);
        const results = ids
          .map((id) => {
            const st = store.npc_state.get(`${uid}:${id}`);
            return st ? { npc_id: id, alive: st.alive ? 1 : 0, disposition: null } : null;
          })
          .filter(Boolean);
        return { results: results as T[] };
      }
      if (sql.includes("FROM npcs") && sql.includes("location_id=?")) {
        const locId = String(params[1]);
        const results = [...store.npcs.values()]
          .filter((n) => n.location_id === locId)
          .map((n) => ({ data: JSON.stringify(n) }));
        return { results: results as T[] };
      }
      if (sql.includes("FROM items") && sql.includes("location_id=?")) {
        const locId = String(params[1]);
        const results = [...store.items.values()]
          .filter((it) => it.location_id === locId)
          .map((it) => ({ data: JSON.stringify(it) }));
        return { results: results as T[] };
      }
      if (sql.includes("FROM items") && sql.includes("id IN")) {
        const ids = params.slice(1).map(String);
        const results = ids
          .map((id) => store.items.get(id))
          .filter(Boolean)
          .map((it) => ({ data: JSON.stringify(it) }));
        return { results: results as T[] };
      }
      return { results: [] };
    },
    async run() {
      return { success: true };
    },
  });

  const env = {
    DB: {
      prepare(sql: string) {
        return makeStmt(sql);
      },
      batch() {
        return Promise.resolve([]);
      },
    },
    WORLD_ID: "tavern",
    RULESET_ID: "dnd",
  } as unknown as Env;

  return { env, store };
}

function basePlayer(): PlayerState {
  return {
    telegram_user_id: "u1",
    world_id: "tavern",
    ruleset_id: "dnd",
    class_id: "wanderer",
    name: "Кейн",
    level: 1,
    hp: 10,
    max_hp: 10,
    location_id: "loc_tavern",
    gold: 0,
    inventory: [],
    equipped: {},
    item_charges: {},
    known_npcs: [],
    status_effects: [],
    combat_session: null,
    game_over: false,
  };
}

describe("apply.ts take_item — поведение с трупом", () => {
  it("живого NPC обокрасть нельзя: предмет принадлежит ему, не лежит в локации", async () => {
    const { env } = makeMockEnv({
      locations: new Map([
        [
          "loc_tavern",
          {
            id: "loc_tavern",
            name: "Таверна",
            parent_id: null,
            description: "",
            atmosphere: { mood: "neutral", tags: [], nuance: "" },
            tags: [],
            connections: [],
            npcs: ["npc1"],
            items: [],
          },
        ],
      ]),
      npcs: new Map([
        [
          "npc1",
          npc("npc1", { location_id: "loc_tavern", inventory: ["item_sword"] }),
        ],
      ]),
      items: new Map([
        [
          "item_sword",
          {
            id: "item_sword",
            name: "Меч",
            type: "weapon",
            price: 5,
            location_id: null,
            owner_id: "npc1",
            description: "",
          },
        ],
      ]),
      npc_state: new Map(), // npc1 живой по дефолту
    });

    const res = await applyEvents(env, basePlayer(), {
      intents: [{ type: "take_item", actor: "player_main", item_id: "item_sword" }],
      location_change: null,
      scene_ended: false,
      scene_summary: null,
    });
    expect(res.player.inventory).toEqual([]);
    expect(res.warnings.join(" ")).toMatch(/take_item/);
  });

  it("мёртвого NPC можно обыскать: предмет переходит в инвентарь", async () => {
    const { env } = makeMockEnv({
      locations: new Map([
        [
          "loc_tavern",
          {
            id: "loc_tavern",
            name: "Таверна",
            parent_id: null,
            description: "",
            atmosphere: { mood: "neutral", tags: [], nuance: "" },
            tags: [],
            connections: [],
            npcs: ["npc1"],
            items: [],
          },
        ],
      ]),
      npcs: new Map([
        [
          "npc1",
          npc("npc1", { location_id: "loc_tavern", inventory: ["item_sword"] }),
        ],
      ]),
      items: new Map([
        [
          "item_sword",
          {
            id: "item_sword",
            name: "Меч",
            type: "weapon",
            price: 5,
            location_id: null,
            owner_id: "npc1",
            description: "",
          },
        ],
      ]),
      npc_state: new Map([["u1:npc1", { alive: false }]]), // труп
    });

    const res = await applyEvents(env, basePlayer(), {
      intents: [{ type: "take_item", actor: "player_main", item_id: "item_sword" }],
      location_change: null,
      scene_ended: false,
      scene_summary: null,
    });
    expect(res.warnings).toEqual([]);
    expect(res.player.inventory).toEqual(["item_sword"]);
  });
});
