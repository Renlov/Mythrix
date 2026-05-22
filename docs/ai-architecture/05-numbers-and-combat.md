# 05. Разделение «нарратив vs расчёты»

**Все цифры считает Android.** Модель никогда не пишет HP, урон,
броски, цены, расстояния в метрах, время в секундах.

## Статус реализации (на 2026-05-21)

Уже есть в движке (`feature/.../game/`):

- `PlayerState` — `hp`, `maxHp`, `gold`, `inventoryIds`, `equippedIds`,
  `questStages`, `locationId`. Сохраняется в `GameStateRepository`
  (файл `save_tavern.json`).
- `EventParser` вытаскивает блок `[EVENTS]` из ответа DM.
- `EventApplier` применяет: `take_item` / `give_item` / `buy` /
  `drop_item` / `quest_advance` / `location_change` (с валидацией:
  золото, владелец, связность локаций).
- Экипировка: один предмет на тип-слот; у предметов есть `damage_die`
  (оружие) и `armor_bonus` (броня) — пока только отображаются в UI.

**Чего НЕТ (это и есть боевой движок):** intents `attack` и
`skill_check` парсятся (`Intent.type`), но `EventApplier` их игнорирует.
Нет бросков, урона, HP врагов, двухфазного хода `[TURN RESULT]`, смерти.

## Зоны ответственности

| Кто | За что отвечает |
|---|---|
| **DM (модель)** | Нарратив, описания, реплики NPC, намерения |
| **Android** | HP, броски, урон, проверки, лут, цены, время |

## Запреты в системном промпте

> ЗАПРЕЩЕНО называть конкретные числа (HP, урон, броски, цены,
> расстояния, время). Все расчёты делает движок.
> Если действие требует броска или урона — опиши НАМЕРЕНИЕ через
> `[EVENTS]` и заверши ход. Движок вернёт результат — тогда опишешь
> исход следующим ходом.

## Двухфазный ход для действий с броском

```
ФАЗА 1 (намерение):
  Игрок: «Атакую Орма луком»
  → DM: [NARRATIVE] «Ты вскидываешь лук, целишься в грудь старого орка...»
        [EVENTS] {intents: [{type: attack, actor: player_main,
                              target: npc_gatekeeper_orm, weapon: item_short_bow}]}
  → Android кидает d20 + модификатор, считает урон, обновляет HP.

ФАЗА 2 (исход):
  Android → DM с дополнительным блоком [TURN RESULT]:
    «Попадание (бросок 17, урон 6). Орм: HP 18/24, не убит.»
  → DM: [NARRATIVE] «Стрела входит ему в плечо. Орм взвывает,
                     но удерживается на ногах и поднимает топор.»
        [EVENTS] {intents: []}
```

Простые действия без броска (взять предмет, заговорить) — в одну фазу.

## Что должен уметь Android-движок

### Минимум для пилота
- бросок `d20 + modifier` для атаки и skill checks
- бросок урона по типу оружия (d6/d8/d10)
- применение урона с учётом брони/резистов
- передача предметов с валидацией владельца
- проверка стоимости при покупке (`player.gold >= item.price`)
- продвижение квест-стадии с валидацией порядка

### Структуры
```kotlin
data class CombatResult(
    val hit: Boolean,
    val roll: Int,
    val damage: Int,
    val target_hp_before: Int,
    val target_hp_after: Int,
    val killed: Boolean,
)

data class SkillCheckResult(
    val success: Boolean,
    val roll: Int,
    val total: Int,
    val difficulty: Int,
)
```

Эти результаты сериализуются в `[TURN RESULT]` блок и подаются модели
во второй фазе.

## План боевого движка v1 (следующий шаг)

Привязка к реальным классам в `feature/src/main/java/com/pimenov/feature/game/`.

### 1. Модель данных врага

Боевые характеристики у NPC/врага. Добавить в Библию Мира (npcs.json
или отдельный enemies.json) и типизировать через `LocationDef`-стиль:

```kotlin
@Serializable
data class CombatStats(
    val hp: Int,
    val ac: Int,            // класс защиты, попадание если бросок >= ac
    @SerialName("attack_die") val attackDie: String,   // напр. "d10"
    @SerialName("attack_bonus") val attackBonus: Int = 0,
)
```

Дракон — особый враг (`enemy_dragon`) с высоким hp/ac. Хранить в
`world/tavern/enemies.json`, грузить в `WorldCatalog` рядом с предметами
и локациями.

### 2. Броски кубов — `DiceRoller`

Чистая функция, RNG инъектируется (для тестов — фиксированный seed):

```kotlin
class DiceRoller(private val rng: Random = Random.Default) {
    fun d20(): Int = rng.nextInt(1, 21)
    /** Парсит "d8", "d4+2", "2d6" и кидает. */
    fun roll(expr: String): Int
}
```

### 3. Разрешение атаки (формула v1, D&D-подобная)

- **Попадание:** `d20 + attackBonus >= targetAC`.
- **AC цели:** игрок — `10 + armorBonus(экипированной брони)`; враг — из
  `CombatStats.ac`.
- **Урон:** бросок `damage_die` экипированного оружия (или базовый d2
  «кулаки», если оружие не надето). Враг — `attack_die`.
- **Применение:** `hp = max(0, hp - damage)`; `killed = hp == 0`.

Вынести в `CombatEngine` (чистый Kotlin, без Android), результат:

```kotlin
data class CombatResult(
    val attacker: String, val target: String,
    val hit: Boolean, val roll: Int, val damage: Int,
    val targetHpBefore: Int, val targetHpAfter: Int, val killed: Boolean,
)
```

### 4. Состояние боя — `CombatSession`

Транзиентное состояние (в `GameStateRepository`, не обязательно
персистить v1):

```kotlin
data class CombatSession(val enemyId: String, val enemyHp: Int, val playerTurn: Boolean)
```

- `attack` от игрока → `CombatEngine` считает по игроку, обновляет
  `enemyHp`; затем ход врага → удар по игроку, обновляет `PlayerState.hp`.
- Победа: `enemyHp == 0`. Поражение: `player.hp == 0` → исход «смерть».
- Атмосфера локации на время боя → `dangerous`.

### 5. Двухфазный ход `[TURN RESULT]` (где в коде)

Сейчас `ChatViewModel.streamAssistant` делает один проход. Для боя:

1. Фаза 1: DM вернул `attack` в `[EVENTS]` → `ChatViewModel` отдаёт его
   `CombatEngine`, получает `CombatResult`(ы).
2. Движок строит блок `[TURN RESULT]` (текст с фактами: попал/промах,
   урон, hp, убит) и **повторно** вызывает `engine.generate` со вторым
   сообщением, где DM описывает исход (без цифр).
3. `DeepSeekLlmEngine`/`LlmEngine` нужно уметь принять системную вставку
   `[TURN RESULT]` для фазы 2 (доп. user-сообщение).

### 6. Порядок реализации (этапы)

1. `DiceRoller` + юнит-тесты (детерминизм по seed).
2. `CombatStats` + `enemies.json` + загрузка в `WorldCatalog`.
3. `CombatEngine.resolveAttack(...)` + тесты инвариантов.
4. `CombatSession` в репозитории; применение `attack` (игрок→враг→игрок).
5. Двухфазный `[TURN RESULT]` в `ChatViewModel` + `LlmEngine`.
6. Исходы: победа/смерть, сброс боя, атмосфера `dangerous`.
7. Локация `loc_dragon_lair` (отрог) + связь `loc_north_forest → отрог`.
8. `skill_check` (d20 vs сложность easy/medium/hard) — после атак.

## Тестирование

Расчёты — **детерминированный Kotlin-код**, покрывается юнит-тестами:
- одинаковый seed RNG → одинаковый исход (для воспроизводимости)
- инварианты: HP не уходит ниже 0, золото не ниже 0
- валидация: нельзя купить, если не хватает золота; нельзя отдать
  то, чего нет в инвентаре

## Почему так

- **Никаких галлюцинаций по цифрам** — модель в принципе их не пишет.
- **Балансировка в руках разработчика**, а не нейросети.
- **Маленькие модели тянут** — даже Qwen 1.5B справляется с нарративом
  без арифметики.
