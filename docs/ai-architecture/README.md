# Архитектура ИИ-гейммастера Mythrix

Этот раздел описывает архитектурные решения по ИИ-DM для Mythrix.
Решения приняты совместно с пользователем и должны соблюдаться при любых
изменениях, связанных с ИИ-частью приложения.

## Документы

1. [01-world-bible-schema.md](01-world-bible-schema.md) — JSON-схема Библии Мира
2. [02-context-assembler.md](02-context-assembler.md) — Сборщик контекста + RAG
3. [03-context-window-management.md](03-context-window-management.md) — Управление окном (Вариант D)
4. [04-event-extraction.md](04-event-extraction.md) — Извлечение событий из ответа DM
5. [05-numbers-and-combat.md](05-numbers-and-combat.md) — Разделение «нарратив vs расчёты»
6. [06-training-strategy.md](06-training-strategy.md) — Стратегия обучения (промптинг → fine-tune)
7. [07-dataset-collection.md](07-dataset-collection.md) — Сбор датасета
8. [08-project-decisions.md](08-project-decisions.md) — Решения по пилоту (язык, модель, сеттинг)
9. [09-mood-catalog.md](09-mood-catalog.md) — Каталог атмосфер (7 mood)
10. [10-dm-style-rules.md](10-dm-style-rules.md) — Стилевые правила DM (обращение, длина, тон)
11. [11-system-prompt.md](11-system-prompt.md) — Системный промпт DM v1 (структура, версионирование)
12. [12-local-testing.md](12-local-testing.md) — Локальное тестирование промпта через Ollama
13. [13-route-to-dragon.md](13-route-to-dragon.md) — Маршрут до дракона (узлы, механики, порядок)

## Ключевые принципы (TL;DR)

- **Никаких галлюцинаций:** DM использует ТОЛЬКО факты из Библии Мира,
  подаваемые через RAG-контекст. Не выдумывает NPC, предметы, локации.
- **Никаких чисел в нарративе:** все расчёты (HP, броски, урон, цены) —
  на стороне Android. DM описывает намерения и результаты, не числа.
- **Промптинг до fine-tune:** начинаем с системного промпта на чистом
  Qwen 2.5 1.5B. Fine-tune (LoRA) — только когда накоплен датасет
  ≥500 примеров и схема событий устоялась.
- **Структурированный вывод:** каждый ответ DM = `[NARRATIVE]` (текст для
  игрока) + `[EVENTS]` (JSON для движка). Невалидный JSON → один retry.
- **Игрок — корень контекста:** `player.json` грузится первым, остальное
  подтягивается через его поля (`location_id`, `inventory`, `active_quests`).
