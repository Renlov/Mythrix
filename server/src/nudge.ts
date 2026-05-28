// Логика напоминаний: расписание и подбор текста.
// Чистые функции — без обращения к БД и сети. Удобно покрыть юнит-тестами.

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

// Мягкое расписание: 24ч, 3 дня, 7 дней от ПОСЛЕДНЕГО касания игрока.
// Возвращает абсолютное время следующего пуша или null, если квота исчерпана.
export function nextNudgeAt(lastSeenAt: number, alreadySent: number): number | null {
  const offsets = [1 * DAY_MS, 3 * DAY_MS, 7 * DAY_MS];
  if (alreadySent >= offsets.length) return null;
  return lastSeenAt + offsets[alreadySent];
}

// Когда планировать первый пуш для свежего касания — то же, что nextNudgeAt(now, 0).
export function firstNudgeAt(lastSeenAt: number): number {
  return nextNudgeAt(lastSeenAt, 0)!;
}

// Атмосферные фолбэки, если у игрока нет персональной зацепки.
const FALLBACK_HOOKS = [
  "В таверне догорают свечи. Кто-то поднимает голову на дверь.",
  "Северный тракт пуст. Дракон не ждёт никого.",
  "Над отрогом тянет гарью. Лес ещё дышит.",
  "Дорога не пройдена. Кто-то всё ещё там — ждёт или не ждёт.",
  "Свет в таверне ещё горит. Дверь открывается на каждый сквозняк.",
];

// Подбирает короткую зацепку из последнего нарратива DM:
// последнее предложение длиной до 160 символов. Если не получилось —
// детерминированный фолбэк по индексу попытки.
export function pickHook(lastNarrative: string | null, attempt: number): string {
  if (lastNarrative) {
    const sentences = lastNarrative
      .replace(/\s+/g, " ")
      .split(/(?<=[.!?…])\s+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0 && s.length <= 160);
    if (sentences.length > 0) {
      const last = sentences[sentences.length - 1];
      if (last.length >= 12) return last;
    }
  }
  return FALLBACK_HOOKS[attempt % FALLBACK_HOOKS.length];
}

// Достоин ли возврат пуша: игра не окончена, есть смысл звать обратно.
export function shouldSendNudge(opts: {
  game_over: boolean;
  nudge_count: number;
  nudge_next_at: number | null;
  now: number;
}): boolean {
  if (opts.game_over) return false;
  if (opts.nudge_next_at === null) return false;
  if (opts.nudge_count >= 3) return false;
  return opts.now >= opts.nudge_next_at;
}
