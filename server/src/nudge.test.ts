import { describe, it, expect } from "vitest";
import { nextNudgeAt, firstNudgeAt, pickHook, shouldSendNudge } from "./nudge.js";

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

describe("nextNudgeAt — мягкое расписание", () => {
  const base = 1_700_000_000_000;

  it("первый пуш через 24 часа", () => {
    expect(nextNudgeAt(base, 0)).toBe(base + DAY);
  });

  it("второй — через 3 дня от последнего касания", () => {
    expect(nextNudgeAt(base, 1)).toBe(base + 3 * DAY);
  });

  it("третий — через 7 дней", () => {
    expect(nextNudgeAt(base, 2)).toBe(base + 7 * DAY);
  });

  it("после третьего — тишина", () => {
    expect(nextNudgeAt(base, 3)).toBeNull();
    expect(nextNudgeAt(base, 99)).toBeNull();
  });

  it("firstNudgeAt = первый пуш через сутки", () => {
    expect(firstNudgeAt(base)).toBe(base + DAY);
  });
});

describe("pickHook — зацепка из последнего нарратива", () => {
  it("берёт последнее предложение, если оно короткое", () => {
    const narr = "Трактирщик молчит. В таверне догорают свечи.";
    expect(pickHook(narr, 0)).toBe("В таверне догорают свечи.");
  });

  it("игнорирует слишком длинные предложения", () => {
    const tooLong = "А".repeat(200) + ".";
    expect(pickHook(tooLong, 0)).toMatch(/В таверне догорают свечи|Северный тракт пуст|Над отрогом|Дорога не пройдена|Свет в таверне/);
  });

  it("фолбэк, если нарратив отсутствует", () => {
    expect(pickHook(null, 0)).toBeTruthy();
    expect(pickHook(null, 0).length).toBeGreaterThan(10);
  });

  it("разные попытки — разные фолбэки (детерминированно)", () => {
    expect(pickHook(null, 0)).not.toBe(pickHook(null, 1));
  });

  it("игнорирует слишком короткие предложения (мусор)", () => {
    expect(pickHook("Да.", 0)).not.toBe("Да.");
  });
});

describe("shouldSendNudge", () => {
  const now = 1_700_000_000_000;

  it("шлёт, если время пришло и квота не исчерпана", () => {
    expect(
      shouldSendNudge({ game_over: false, nudge_count: 0, nudge_next_at: now - 1, now }),
    ).toBe(true);
  });

  it("не шлёт, если время ещё не пришло", () => {
    expect(
      shouldSendNudge({ game_over: false, nudge_count: 0, nudge_next_at: now + DAY, now }),
    ).toBe(false);
  });

  it("не шлёт, если игра окончена", () => {
    expect(
      shouldSendNudge({ game_over: true, nudge_count: 0, nudge_next_at: now - 1, now }),
    ).toBe(false);
  });

  it("не шлёт, если уже отправлено 3", () => {
    expect(
      shouldSendNudge({ game_over: false, nudge_count: 3, nudge_next_at: now - 1, now }),
    ).toBe(false);
  });

  it("не шлёт, если расписание не выставлено", () => {
    expect(
      shouldSendNudge({ game_over: false, nudge_count: 0, nudge_next_at: null, now }),
    ).toBe(false);
  });
});
