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
