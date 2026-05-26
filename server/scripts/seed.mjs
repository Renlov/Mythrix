// Генерирует db/seed.sql из JSON-контента (источник правды — git).
// Идемпотентно: upsert по (world_id, id); content_version инкрементится.
// Состояние игрока (players/player_quests/turns) сид НЕ трогает.
//
// Запуск: node scripts/seed.mjs   →   db/seed.sql
// Заливка: npm run db:seed (локально) / db:seed:remote (прод)

import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const WORLD_ID = "tavern";
const WORLD_NAME = "Северный отрог (пилот)";

const read = (p) => JSON.parse(readFileSync(join(ROOT, p), "utf8"));
const world = (f) => read(`world/tavern/${f}`);

// SQL-литерал строки: одинарные кавычки удваиваются.
const q = (v) => `'${String(v).replace(/'/g, "''")}'`;
const j = (obj) => q(JSON.stringify(obj));
const nul = (v) => (v === undefined || v === null ? "NULL" : q(v));

const out = [];
out.push("-- АВТОГЕНЕРАЦИЯ из scripts/seed.mjs. Не редактировать вручную.");

// --- world + content_version ---
out.push(
  `INSERT INTO worlds (id, name, content_version) VALUES (${q(WORLD_ID)}, ${q(WORLD_NAME)}, 1)\n` +
    `  ON CONFLICT(id) DO UPDATE SET name=excluded.name, content_version=worlds.content_version+1;`
);

// --- locations ---
for (const loc of world("locations.json")) {
  out.push(
    `INSERT INTO locations (world_id, id, parent_id, data) VALUES (${q(WORLD_ID)}, ${q(loc.id)}, ${nul(loc.parent_id)}, ${j(loc)})\n` +
      `  ON CONFLICT(world_id, id) DO UPDATE SET parent_id=excluded.parent_id, data=excluded.data;`
  );
}

// --- npcs ---
for (const npc of world("npcs.json")) {
  out.push(
    `INSERT INTO npcs (world_id, id, location_id, role, data) VALUES (${q(WORLD_ID)}, ${q(npc.id)}, ${nul(npc.location_id)}, ${nul(npc.role)}, ${j(npc)})\n` +
      `  ON CONFLICT(world_id, id) DO UPDATE SET location_id=excluded.location_id, role=excluded.role, data=excluded.data;`
  );
}

// --- items ---
for (const it of world("items.json")) {
  out.push(
    `INSERT INTO items (world_id, id, type, location_id, owner_id, data) VALUES (${q(WORLD_ID)}, ${q(it.id)}, ${nul(it.type)}, ${nul(it.location_id)}, ${nul(it.owner_id)}, ${j(it)})\n` +
      `  ON CONFLICT(world_id, id) DO UPDATE SET type=excluded.type, location_id=excluded.location_id, owner_id=excluded.owner_id, data=excluded.data;`
  );
}

// --- enemies ---
for (const en of world("enemies.json")) {
  out.push(
    `INSERT INTO enemies (world_id, id, data) VALUES (${q(WORLD_ID)}, ${q(en.id)}, ${j(en)})\n` +
      `  ON CONFLICT(world_id, id) DO UPDATE SET data=excluded.data;`
  );
}

// --- quests (только определение) ---
for (const qu of world("quests.json")) {
  out.push(
    `INSERT INTO quests (world_id, id, data) VALUES (${q(WORLD_ID)}, ${q(qu.id)}, ${j(qu)})\n` +
      `  ON CONFLICT(world_id, id) DO UPDATE SET data=excluded.data;`
  );
}

// --- scenario (rules.json) ---
const rules = world("rules.json");
out.push(
  `INSERT INTO scenario (world_id, id, data) VALUES (${q(WORLD_ID)}, 'main', ${j(rules)})\n` +
    `  ON CONFLICT(world_id, id) DO UPDATE SET data=excluded.data;`
);

// --- ruleset + классы (глобально) ---
const ruleset = read("world/ruleset.json");
out.push(
  `INSERT INTO rulesets (id, name, version, data) VALUES (${q(ruleset.id)}, ${q(ruleset.name)}, 1, ${j(ruleset.combat_config)})\n` +
    `  ON CONFLICT(id) DO UPDATE SET name=excluded.name, version=rulesets.version+1, data=excluded.data;`
);
for (const cls of world("classes.json")) {
  out.push(
    `INSERT INTO classes (ruleset_id, id, data) VALUES (${q(ruleset.id)}, ${q(cls.id)}, ${j(cls)})\n` +
      `  ON CONFLICT(ruleset_id, id) DO UPDATE SET data=excluded.data;`
  );
}

out.push("");

writeFileSync(join(ROOT, "db/seed.sql"), out.join("\n\n"), "utf8");
console.log("db/seed.sql written");
