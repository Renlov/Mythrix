// Валидация Telegram Mini App initData (HMAC по bot token).
// https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app

const encoder = new TextEncoder();

async function hmacSha256(keyBytes: ArrayBuffer, msg: string): Promise<ArrayBuffer> {
  const key = await crypto.subtle.importKey(
    "raw",
    keyBytes,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return crypto.subtle.sign("HMAC", key, encoder.encode(msg));
}

function toHex(buf: ArrayBuffer): string {
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export interface TelegramUser {
  id: number;
  first_name?: string;
  username?: string;
}

export interface InitDataResult {
  ok: boolean;
  user?: TelegramUser;
  reason?: string;
}

// Проверяет подпись initData и возвращает доверенного пользователя.
export async function validateInitData(
  initData: string,
  botToken: string,
  maxAgeSeconds = 86400,
): Promise<InitDataResult> {
  if (!initData) return { ok: false, reason: "empty initData" };

  const params = new URLSearchParams(initData);
  const hash = params.get("hash");
  if (!hash) return { ok: false, reason: "no hash" };
  params.delete("hash");

  const dataCheckString = [...params.entries()]
    .map(([k, v]) => `${k}=${v}`)
    .sort()
    .join("\n");

  // secret_key = HMAC_SHA256("WebAppData", bot_token)
  const secretKey = await hmacSha256(encoder.encode("WebAppData").buffer as ArrayBuffer, botToken);
  const computed = toHex(await hmacSha256(secretKey, dataCheckString));
  if (computed !== hash) return { ok: false, reason: "bad signature" };

  const authDate = Number(params.get("auth_date") ?? 0);
  if (maxAgeSeconds > 0 && authDate > 0) {
    const ageSec = Math.floor(Date.now() / 1000) - authDate;
    if (ageSec > maxAgeSeconds) return { ok: false, reason: "expired" };
  }

  const userRaw = params.get("user");
  let user: TelegramUser | undefined;
  if (userRaw) {
    try {
      user = JSON.parse(userRaw) as TelegramUser;
    } catch {
      return { ok: false, reason: "bad user json" };
    }
  }
  if (!user?.id) return { ok: false, reason: "no user id" };

  return { ok: true, user };
}

// ============================================================
// Bot API: отправка сообщений, обработка webhook-команд.
// Документация: https://core.telegram.org/bots/api
// ============================================================

import type { Env } from "./types.js";

const TG_API = (token: string, method: string) => `https://api.telegram.org/bot${token}/${method}`;

interface TelegramUpdate {
  message?: {
    chat: { id: number };
    from?: { id: number };
    text?: string;
  };
}

async function tgPost(env: Env, method: string, payload: unknown): Promise<void> {
  await fetch(TG_API(env.TELEGRAM_BOT_TOKEN, method), {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload),
  });
}

// Кнопка-Mini App + текст. Используется и для /play, и для напоминаний.
function miniAppReplyMarkup(env: Env, label = "Открыть Mythrix") {
  return {
    inline_keyboard: [[{ text: label, web_app: { url: env.WEBAPP_URL } }]],
  };
}

// Webhook от Telegram: команды /start, /play, /resume — все шлют кнопку Mini App.
export async function handleTelegramUpdate(env: Env, update: TelegramUpdate): Promise<void> {
  const msg = update.message;
  if (!msg?.text) return;
  const text = msg.text.trim().toLowerCase();
  if (text.startsWith("/start") || text.startsWith("/play") || text.startsWith("/resume")) {
    await tgPost(env, "sendMessage", {
      chat_id: msg.chat.id,
      text:
        text.startsWith("/resume")
          ? "Продолжаем. Дверь открыта."
          : "Mythrix готов. Открой Mini App и шагни в таверну.",
      reply_markup: miniAppReplyMarkup(env),
    });
    // Постоянная кнопка Mini App в чате (рядом с полем ввода).
    await tgPost(env, "setChatMenuButton", {
      chat_id: msg.chat.id,
      menu_button: {
        type: "web_app",
        text: "Играть",
        web_app: { url: env.WEBAPP_URL },
      },
    });
  }
}

// Напоминание игроку: отправляет личное сообщение от бота с зацепкой и кнопкой возврата.
// Возвращает true, если Telegram принял сообщение.
export async function sendNudge(env: Env, telegramUserId: string, hook: string): Promise<boolean> {
  try {
    const res = await fetch(TG_API(env.TELEGRAM_BOT_TOKEN, "sendMessage"), {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        chat_id: Number(telegramUserId),
        text: hook,
        reply_markup: miniAppReplyMarkup(env, "Вернуться в Mythrix"),
      }),
    });
    return res.ok;
  } catch {
    return false;
  }
}
