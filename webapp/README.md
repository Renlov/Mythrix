# Mythrix Mini App (Telegram + Cloudflare Pages)

Тонкий web-UI: показывает нарратив, принимает ход, лист персонажа и
снаряжение. Логики и секретов не содержит — всё через Worker (см. `../server`).

Стек: Vite + TypeScript (без фреймворка, бандл ~3 КБ gzip).

## Локальная разработка

```bash
# 1. Поднять Worker (в каталоге server), с dev-обходом авторизации:
#    .dev.vars: DEV_BYPASS_AUTH="1"
cd ../server && npm run dev        # http://localhost:8787

# 2. Mini App
cd ../webapp
npm install
cp .env.example .env               # VITE_API_URL=http://localhost:8787
npm run dev                        # http://localhost:5173
```

Вне Telegram `initData` пустой — Worker с `DEV_BYPASS_AUTH="1"` пускает как
`dev-user`. Так можно играть в обычном браузере. **В проде флаг не ставить.**

## Запуск «у пользователей» (runbook)

1. **Бэкенд (server):** `wrangler d1 create mythrix` → вписать id; залить схему
   и сид (`npm run db:schema:remote`, `db:seed:remote`); задать секреты
   `DEEPSEEK_API_KEY`, `TELEGRAM_BOT_TOKEN`; `npm run deploy`. Запомнить URL
   Worker (например `https://mythrix.<acc>.workers.dev`).

2. **Фронтенд (webapp):** в `.env` указать `VITE_API_URL=<URL Worker>` →
   `npm run build` → задеплоить `dist/` на **Cloudflare Pages**
   (`npx wrangler pages deploy dist --project-name mythrix`). Запомнить URL
   страницы (например `https://mythrix.pages.dev`).

3. **Telegram-бот (@BotFather):** создать бота → `/newapp` (или Bot Settings →
   Menu Button / Web App) → указать URL страницы из шага 2. Токен бота — тот же,
   что в секрете `TELEGRAM_BOT_TOKEN`.

4. Открыть бота в Telegram → кнопка Mini App → игра запускается у пользователя.

> Ключ DeepSeek нигде в этом флоу не попадает на клиент — он только в секретах
> Worker. Mini App шлёт лишь `initData` + ход.

## Сборка

```bash
npm run build      # tsc + vite build → dist/
npm run preview    # локальный просмотр прод-сборки
```
