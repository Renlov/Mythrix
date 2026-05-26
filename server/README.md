# Mythrix server (Cloudflare Worker + D1)

Бэкенд ИИ-DM: держит ключ DeepSeek, собирает контекст, считает числа/бой,
хранит состояние и логи. См. `docs/ai-architecture/15-platform-architecture.md`
и `16-data-model.md`.

## Структура

```
src/            код Worker (TypeScript)
  index.ts        маршруты: POST /new-game, POST /turn
  telegram.ts     валидация Telegram initData (HMAC)
  deepseek.ts     клиент DeepSeek
  context.ts      сборка системного промпта + контекста сцены из D1
  events.ts       парсер [NARRATIVE]/[EVENTS]
  apply.ts        применение не-боевых событий к состоянию
  combat.ts       чистый боевой движок (броски, атака)
  combatFlow.ts   двухфазный бой (расчёт → [TURN RESULT])
  db.ts           доступ к D1
world/          контент мира (источник правды, git)
  tavern/*.json   локации, NPC, предметы, враги, квесты, классы, правила
  ruleset.json    combat_config (ДНД-настройка)
  prompts/        системный промпт DM
db/             schema.sql + сгенерированный seed.sql
scripts/seed.mjs  генератор seed.sql из JSON
```

## Первый запуск

```bash
npm install

# 1. Создать D1 и вписать database_id в wrangler.toml
npx wrangler d1 create mythrix

# 2. Применить схему (локально)
npm run db:schema

# 3. Сгенерировать и залить контент
npm run seed:build      # world/*.json -> db/seed.sql
npm run db:seed         # залить в локальную D1

# 4. Секреты для локальной разработки
cp .dev.vars.example .dev.vars   # вписать ключи

# 5. Запуск
npm run dev
```

## Прод

```bash
npx wrangler secret put DEEPSEEK_API_KEY
npx wrangler secret put TELEGRAM_BOT_TOKEN
npm run db:schema:remote
npm run db:seed:remote
npm run deploy
```

## Обновление контента

Правим JSON в `world/`, затем `npm run seed:build && npm run db:seed[:remote]`.
Сид идемпотентен (upsert по id), `content_version` инкрементится. Состояние
игроков сид не трогает.

## API

- `POST /new-game` — `{ initData, class_id, name }` → создаёт персонажа.
- `POST /turn` — `{ initData, action }` → ход: контекст → DeepSeek → события →
  расчёты → сохранение → `{ narrative, player, warnings, context_usage }`.

Оба требуют валидный Telegram `initData`. Ключ DeepSeek — только на сервере.
