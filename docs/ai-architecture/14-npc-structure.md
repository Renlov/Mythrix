# 14. Структура NPC и взаимодействия

Чёткая, единая схема каждого NPC и того, как игрок с ним взаимодействует:
кто обрабатывает (движок vs DM), какие поля за что отвечают.

## Анализ текущего состояния (на 2026-05-22)

- **NPC не типизирован в коде.** Нет класса `NpcDef`. Движок берёт у NPC
  только `id` (фильтр «кто в локации», через `LocationDef.npcs`) и
  `inventory` (вырезать у торговца уже купленное). Всё остальное —
  свободный текст, который читает только DM.
- **Торговец — хардкод.** `MERCHANT_ID = "npc_innkeeper"` зашит в коде, а
  не выводится из `role: "merchant"`.
- **Бой с NPC не разрешается движком.** Боевые характеристики есть только
  у `enemies.json`. Атака по обычному NPC — чистый нарратив DM.
- **`meet_npc` не применяется.** Intent есть в схеме и в `Intent`, но
  движок не обновляет «знакомство»/имя; поле `player.known_npcs` нигде не
  используется (его нет в `PlayerState`).
- **Хак `_name_note`.** Анонимность трактирщика держится на комментарии в
  данных — формализуем полем `name_reveal`.

## Канонический NPC

```json
{
  "id": "npc_innkeeper",
  "name": null,
  "name_reveal": "never",
  "role_label": "Трактирщик",
  "location_id": "loc_tavern_last_rest",
  "role": "merchant",
  "race": "human",
  "age": 52,
  "description": "...",
  "dialogue_style": "...",
  "opening_line": "...",
  "inventory": ["item_iron_sword", "..."],
  "knows_about": ["quest_slay_dragon"],
  "knowledge": ["...", "..."],
  "combat": null
}
```

### Поля

| Поле | Тип | Кто читает | Назначение |
|---|---|---|---|
| `id` | string | движок | связи, фильтр по локации |
| `name` | string\|null | оба | имя; `null` = безымянный, DM зовёт по `role_label` |
| `name_reveal` | enum | DM | `on_introduction` (по умолч.) \| `never` — назовётся ли при прямом вопросе |
| `role_label` | string | DM | как звать, пока имя неизвестно («трактирщик») |
| `location_id` | string | движок | где встретить (дублирует `locations.npcs`) |
| `role` | enum | движок+DM | `merchant` \| `civilian` \| `warrior` \| `hostile` |
| `race`, `age` | — | DM | фактура для описания |
| `description` | string | DM | внешность/поведение |
| `dialogue_style` | string | DM | как говорит |
| `opening_line` | string? | DM | первая реплика при обращении |
| `inventory` | string[] | движок | для `merchant` — товар на продажу |
| `knows_about` | string[] | движок+DM | квесты, которые NPC может двигать/раскрывать |
| `knowledge` | string[] | DM | факты, которые DM может выдать (с условиями внутри текста) |
| `combat` | object?\| | движок | боевые статы, если NPC можно атаковать (см. ниже) |

`combat` (если NPC враждебен/атакуем): `{ "hp", "ac", "attack_die", "attack_bonus" }` — тот же формат, что `enemies.json`.

## Взаимодействия с NPC

| Взаимодействие | Триггер игрока | Обрабатывает | Поля NPC | Эффект / событие |
|---|---|---|---|---|
| **Разговор** | спросить, обратиться | DM (нарратив) | `dialogue_style`, `knowledge`, `opening_line`, `name`/`role_label` | реплика; возможно `quest_advance` |
| **Знакомство** | NPC представился / узнал имя | движок | `name`, `name_reveal` | `meet_npc` → имя в `known_npcs`, дальше DM зовёт по имени |
| **Торговля** | «купить», «что есть на продажу» | движок (магазин) | `role: merchant`, `inventory`, `items.price` | меню → `buy` (золото↔предмет) |
| **Дать / взять** | отдать или получить вещь | движок | `inventory` | `give_item` / `take_item` |
| **Раскрытие квеста** | прямой вопрос по теме | DM + движок | `knows_about`, `knowledge` | `quest_advance` (молча) |
| **Бой** | напасть на NPC | движок, если есть `combat` | `combat` | `attack` → `CombatEngine`; нет `combat` → DM нарратив без чисел |

### Правила
- **Числа — движку, нарратив — DM** (см. `05`). NPC не «считает» — он
  отвечает в характере; покупки/урон/проверки решает Android.
- **Безымянные NPC** остаются по `role_label`, пока `name_reveal` не
  позволит назваться. Трактирщик — `never`.
- **Враждебный NPC**: чтобы бой был механическим, NPC нужен блок `combat`
  (или его выносят в `enemies.json`). Иначе атака — чисто описательная.
- **Каждый NPC должен иметь:** `id`, `role`, `location_id`, `role_label`
  (или `name`), `description`, `dialogue_style`. Остальное — по нужде.

## Что доделать в движке (отдельные задачи)

1. Типизировать `NpcDef` и грузить в `WorldCatalog` (как `ItemDef`/`LocationDef`).
2. Торговца определять по `role == "merchant"`, убрать хардкод `MERCHANT_ID`.
3. Применять `meet_npc` → `known_npcs` в `PlayerState`; DM переходит на имя.
4. Поддержать блок `combat` у NPC — бой с враждебными NPC, не только `enemies.json`.
