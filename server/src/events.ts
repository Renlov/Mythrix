import type { DmEvents } from "./types.js";

export interface ParsedResponse {
  narrative: string;
  events: DmEvents | null;
  eventsRaw: string | null;
}

// Делит ответ DM на [NARRATIVE] и [EVENTS] и парсит JSON событий.
// events=null означает невалидный/отсутствующий блок → повод для retry.
export function parseDmResponse(raw: string): ParsedResponse {
  const narrative = extractBlock(raw, "NARRATIVE") ?? stripEventsBlock(raw).trim();
  const eventsRaw = extractBlock(raw, "EVENTS");

  let events: DmEvents | null = null;
  if (eventsRaw) {
    try {
      const obj = JSON.parse(eventsRaw) as Partial<DmEvents>;
      events = {
        intents: Array.isArray(obj.intents) ? (obj.intents as DmEvents["intents"]) : [],
        location_change: obj.location_change ?? null,
        scene_ended: Boolean(obj.scene_ended),
        scene_summary: obj.scene_summary ?? null,
        atmosphere_shift: obj.atmosphere_shift ?? null,
      };
    } catch {
      events = null;
    }
  }

  return { narrative: narrative.trim(), events, eventsRaw };
}

// Достаёт содержимое блока [TAG] ... до следующего [SOMETHING] или конца.
function extractBlock(raw: string, tag: string): string | null {
  const re = new RegExp(`\\[${tag}\\]\\s*([\\s\\S]*?)(?=\\n\\s*\\[[A-Z_]+\\]|$)`);
  const m = raw.match(re);
  return m ? m[1] : null;
}

function stripEventsBlock(raw: string): string {
  return raw.replace(/\[EVENTS\][\s\S]*$/, "");
}
