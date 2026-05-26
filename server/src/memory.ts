import type { Env } from "./types.js";
import * as db from "./db.js";
import { chat } from "./deepseek.js";

const SUMMARY_SYSTEM =
  "Ты ведёшь сжатый журнал памяти локации для гейммастера ролевой игры. " +
  "Обнови итог по предыдущему итогу и новым событиям. Сохраняй ТОЛЬКО факты, важные " +
  "для продолжения игры: что игрок сделал, что узнал, изменения в отношениях с NPC, " +
  "взятые/отданные предметы, незакрытые зацепки. Без украшательств и оценок. " +
  "3-5 коротких предложений на русском. Если нового мало — просто дополни прежний итог.";

// Сжимает события локации, которую игрок покинул, в накопительный summary (память локации).
// Возвращает токены, потраченные на сжатие (для учёта расхода контекста).
export async function summarizeOnExit(
  env: Env,
  uid: string,
  locationId: string,
  locationName: string,
): Promise<number> {
  const mem = await db.getLocationMemory(env, uid, locationId);
  const since = mem?.last_turn_index ?? -1;
  const fresh = await db.getLocationTurnsSince(env, uid, locationId, since);
  if (fresh.length === 0) return 0; // нечего сжимать

  const eventsText = fresh
    .map((t) => `Игрок: ${t.player_action}\nDM: ${t.narrative}`)
    .join("\n");
  const lastIndex = fresh[fresh.length - 1].turn_index;

  const userMsg =
    `Локация: ${locationName}.\n` +
    `Предыдущий итог: ${mem?.summary ?? "—"}.\n\n` +
    `Новые события:\n${eventsText}\n\nОбнови итог локации.`;

  const { content, usageTokens } = await chat(
    env,
    [
      { role: "system", content: SUMMARY_SYSTEM },
      { role: "user", content: userMsg },
    ],
    { temperature: 0.3 },
  );

  const summary = content.trim();
  if (summary) await db.saveLocationMemory(env, uid, locationId, summary, lastIndex);
  return usageTokens;
}
