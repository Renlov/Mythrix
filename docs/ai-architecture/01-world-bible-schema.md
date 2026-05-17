# 01. Схема Библии Мира

Все факты игрового мира — JSON-сущности, связанные через `id`.
Это единственный источник правды для ИИ-DM.

## Сущности

### `locations.json`
```json
{
  "id": "loc_dragon_tower_gate",
  "name": "Врата Драконьей Башни",
  "parent_id": "loc_dragon_tower",
  "description": "Массивные кованые ворота у подножия башни.",
  "atmosphere": {
    "mood": "tense",
    "tags": ["ruins", "watched"],
    "nuance": "Ветер несёт гарь со склона. За воротами слышен далёкий рёв."
  },
  "tags": ["entrance", "outdoor", "ruins"],
  "connections": ["loc_forest_path", "loc_tower_hall"],
  "npcs": ["npc_gatekeeper_orm"],
  "items": ["item_rune_key"]
}
```

**`atmosphere`** управляет тоном нарратива DM в этой локации.
- `mood` (enum, обязательно) — машинный селектор few-shot примеров:
  `cheerful` | `neutral` | `tense` | `somber` | `dangerous` | `mysterious` | `sacred`
- `tags` (array) — дополнительные оттенки.
- `nuance` (string) — свободный текст, подаётся в `[CURRENT SCENE]`
  как руководство к интонации.

Атмосфера может временно меняться через event `atmosphere_shift`
(см. `04-event-extraction.md`) — например, если игрок поджёг таверну,
mood становится `dangerous` до конца сцены.

### `npcs.json`
```json
{
  "id": "npc_gatekeeper_orm",
  "name": "Орм Привратник",
  "location_id": "loc_dragon_tower_gate",
  "role": "hostile",
  "race": "orc",
  "description": "Старый орк со шрамом, охраняет врата сто лет.",
  "inventory": ["item_rusty_axe"],
  "knows_about": ["quest_dragon_heart"],
  "dialogue_style": "грубый, короткими фразами"
}
```

### `items.json`
```json
{
  "id": "item_rune_key",
  "name": "Рунный ключ",
  "type": "key",
  "price": 0,
  "weight": 0.2,
  "location_id": "loc_dragon_tower_gate",
  "owner_id": null,
  "description": "Бронзовый ключ с руной огня.",
  "unlocks": ["loc_tower_hall"]
}
```
**Инвариант:** `location_id` ИЛИ `owner_id` — взаимоисключающие.
Предмет либо лежит в локации, либо у владельца.

### `quests.json`
```json
{
  "id": "quest_dragon_heart",
  "name": "Сердце дракона",
  "status": "active",
  "stage": 2,
  "stages": [
    {"id": 1, "goal": "Найти Орма", "done": true},
    {"id": 2, "goal": "Получить рунный ключ", "done": false}
  ],
  "related_npcs": ["npc_gatekeeper_orm"],
  "related_locations": ["loc_dragon_tower_gate"]
}
```

### `player.json` — корневая сущность
```json
{
  "id": "player_main",
  "name": "Кейн",
  "class": "ranger",
  "level": 3,
  "hp": 24,
  "max_hp": 30,
  "location_id": "loc_dragon_tower_gate",
  "inventory": ["item_short_bow", "item_health_potion"],
  "gold": 45,
  "active_quests": ["quest_dragon_heart"],
  "known_npcs": ["npc_gatekeeper_orm"],
  "status_effects": []
}
```

## Связи

| Поле | Указывает на |
|---|---|
| `player.location_id` | `locations.id` (где игрок сейчас) |
| `npcs.location_id` | `locations.id` (где встретить NPC) |
| `items.location_id` ⊕ `items.owner_id` | где предмет |
| `quests.related_*` | связанные сущности |
| `locations.connections` | соседние локации |
| `player.active_quests` | `quests.id` |
| `player.known_npcs` | `npcs.id` (с кем уже знаком) |
