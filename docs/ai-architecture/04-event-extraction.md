# 04. Извлечение игровых событий из ответа DM

DM генерирует **два блока в одном ответе**: художественный текст для
игрока и структурированный JSON для движка.

## Формат ответа

```
[NARRATIVE]
Орм хмурится и швыряет тебе рунный ключ.
— Бери и проваливай.

[EVENTS]
{
  "intents": [
    {
      "type": "give_item",
      "from": "npc_gatekeeper_orm",
      "to": "player_main",
      "item_id": "item_rune_key"
    }
  ],
  "location_change": null,
  "scene_ended": false,
  "scene_summary": null
}
```

Игрок видит только `[NARRATIVE]`. Android парсит `[EVENTS]` и применяет
изменения к JSON-сущностям.

## Типы intent'ов

DM описывает **намерение**, движок (Android) считает **исход**.
См. `05-numbers-and-combat.md`.

| type | Поля | Что делает движок |
|---|---|---|
| `attack` | actor, target, weapon | кидает d20, считает урон, обновляет HP |
| `skill_check` | actor, skill, difficulty | кидает d20 + модификатор, возвращает успех/провал |
| `give_item` | from, to, item_id | переносит предмет (с валидацией) |
| `take_item` | actor, item_id | предмет из локации → инвентарь |
| `drop_item` | actor, item_id | инвентарь → текущая локация |
| `buy` | buyer, seller, item_id | проверяет золото, переносит item + золото |
| `quest_advance` | quest_id, new_stage | продвигает стадию (с валидацией) |
| `meet_npc` | npc_id | добавляет в `player.known_npcs` |

## Поля верхнего уровня

| Поле | Тип | Назначение |
|---|---|---|
| `intents` | array | список действий выше |
| `location_change` | string\|null | новый `location_id`, если игрок переместился |
| `scene_ended` | bool | сигнал триггера границы сцены (см. док 03) |
| `scene_summary` | string\|null | если `scene_ended=true` — 1-2 предложения для архива |

## Pipeline применения

```
1. Получили ответ модели.
2. Парсим [EVENTS]. Невалидный JSON → один retry с фидбеком
   («верни валидный JSON по схеме»). Если снова провал — events
   игнорируются, narrative показывается как есть.
3. Применяем intents к JSON-сущностям в SQLite-транзакции.
   Каждый intent валидируется (предмет реально у того, кто отдаёт;
   квест-стадия существует; и т.д.). При нарушении инварианта —
   intent игнорируется, в лог пишется warning.
4. Если scene_ended=true — пушим scene_summary в RAG как чанк типа
   event, привязка к location_id. Чистим скользящее окно (см. док 03).
5. Показываем игроку [NARRATIVE].
```

## Почему не function calling

Qwen 2.5 1.5B нестабильно делает function calling (на бенчмарках
~50% точности). Structured output через текстовые маркеры
`[NARRATIVE] / [EVENTS]` + JSON работает надёжнее на маленьких моделях
и требует только парсера регуляркой + `JSON.parse`.

## Расширение схемы

Новые типы intent'ов добавляются в общую схему, документируются здесь
и в системном промпте. **Старые intent'ы не удаляются** — иначе сломаются
сохранённые логи в датасете.
