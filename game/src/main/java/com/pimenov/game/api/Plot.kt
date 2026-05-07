package com.pimenov.game.api

/**
 * Hard-scripted backbone of the campaign — keeps the AI on track.
 * Each stage is a short brief that gets injected as system context into the LLM prompt
 * and used by the scripted fallback engine when the LLM is unavailable.
 *
 * Player advances stages explicitly via the "Дальше" quick action (or by recruiting/
 * defeating the stage NPC in case of an encounter).
 */
object Plot {

    enum class Stance { NEUTRAL, RECRUITABLE, HOSTILE }

    data class Encounter(
        val name: String,
        val description: String,
        val stance: Stance,
        val enemy: Enemy? = null
    )

    data class Stage(
        val index: Int,
        val sceneTag: String,
        val title: String,
        val situation: String,
        val encounter: Encounter? = null,
        /** Marks the final showdown — winning here saves the princess. */
        val isFinale: Boolean = false
    )

    /** Default campaign: tavern → forest → bandits → mountain pass → tower → dragon → home. */
    val DRAGON_TOWER: List<Stage> = listOf(
        Stage(
            index = 0,
            sceneTag = "tavern",
            title = "Таверна «Серебряный Грифон»",
            situation = "Король объявил награду за спасение принцессы Алинары, " +
                "похищенной красным драконом Малгротом. Дракон удерживает её в Чёрной башне " +
                "за горным перевалом. Герой только что принял задание у трактирщика."
        ),
        Stage(
            index = 1,
            sceneTag = "forest",
            title = "Лес у подножия гор",
            situation = "Тропа уходит в густой сосновый лес. У ручья на пне сидит молодой эльф-лучник " +
                "с подвязанной рукой. Он смотрит настороженно, но не враждебно.",
            encounter = Encounter(
                name = "Лириен Тенелов",
                description = "Эльфийский лучник, потерял брата в стычке с разбойниками. " +
                    "Ищет искупления и готов присоединиться к достойному.",
                stance = Stance.RECRUITABLE
            )
        ),
        Stage(
            index = 2,
            sceneTag = "forest",
            title = "Засада в чаще",
            situation = "За поворотом тропы из кустов выходят трое разбойников с дубинами. " +
                "Их главарь Грим со шрамом через глаз требует кошелёк или жизнь.",
            encounter = Encounter(
                name = "Грим Однодушный",
                description = "Главарь лесных разбойников. Не торгуется, не отступает.",
                stance = Stance.HOSTILE,
                enemy = Enemy(
                    name = "Грим Однодушный",
                    maxHp = 18,
                    currentHp = 18,
                    ac = 13,
                    attackBonus = 4,
                    damageDie = 8
                )
            )
        ),
        Stage(
            index = 3,
            sceneTag = "dungeon",
            title = "Горный перевал",
            situation = "Холодный ветер свистит между скал. У костра сидит закованный в чёрные латы " +
                "рыцарь без шлема, на щите — стёртый герб ордена Зари.",
            encounter = Encounter(
                name = "Сэр Корвин Бледный",
                description = "Бывший паладин, отлучённый от ордена за то, что не смог " +
                    "спасти своего сюзерена. Если убедить — пойдёт мстить вместе с героем.",
                stance = Stance.RECRUITABLE
            )
        ),
        Stage(
            index = 4,
            sceneTag = "dungeon",
            title = "У Чёрной башни",
            situation = "Башня поднимается из голого камня, как обугленный палец. " +
                "Дверь приоткрыта, изнутри тянет серой и старой кровью. " +
                "Где-то наверху раздаётся низкий гулкий рёв."
        ),
        Stage(
            index = 5,
            sceneTag = "dungeon",
            title = "Логово Малгрота",
            situation = "Зал на вершине башни залит багровым светом. На куче костей лежит " +
                "огромный красный дракон. В железной клетке у стены — принцесса Алинара. " +
                "Дракон открывает один глаз.",
            encounter = Encounter(
                name = "Малгрот, Багряный Тиран",
                description = "Древний красный дракон, владыка башни.",
                stance = Stance.HOSTILE,
                enemy = Enemy(
                    name = "Малгрот",
                    maxHp = 60,
                    currentHp = 60,
                    ac = 16,
                    attackBonus = 6,
                    damageDie = 12
                )
            ),
            isFinale = true
        ),
        Stage(
            index = 6,
            sceneTag = "tavern",
            title = "Триумфальное возвращение",
            situation = "Принцесса свободна, дракон повержен. Король встречает героя у ворот " +
                "столицы. Награда — мешок золота и место за королевским столом."
        )
    )

    fun stageAt(index: Int): Stage = DRAGON_TOWER.getOrElse(index) { DRAGON_TOWER.last() }

    /**
     * Render the stage and companion list as a SYSTEM context block for the LLM prompt.
     * Keeps the model on plot rails even when player drifts off-topic.
     */
    fun renderContext(stage: Stage, companions: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("[ТЕКУЩИЙ ЭТАП ${stage.index + 1}/${DRAGON_TOWER.size}: ${stage.title}]")
        sb.appendLine(stage.situation)
        stage.encounter?.let { enc ->
            sb.appendLine(
                "Встреча: ${enc.name}. ${enc.description} " +
                    when (enc.stance) {
                        Stance.RECRUITABLE -> "Может присоединиться к отряду."
                        Stance.HOSTILE -> "Враждебен, скорее всего нападёт."
                        Stance.NEUTRAL -> "Настроение нейтральное."
                    }
            )
        }
        if (companions.isNotEmpty()) {
            sb.appendLine("Спутники в отряде: ${companions.joinToString(", ")}.")
        }
        if (stage.isFinale) {
            sb.appendLine("Это финальная битва. Победа = принцесса спасена.")
        }
        sb.append(
            "Веди игрока через эту сцену. Не перепрыгивай дальше — переход на следующий этап " +
                "произойдёт по явному действию игрока."
        )
        return sb.toString()
    }
}
