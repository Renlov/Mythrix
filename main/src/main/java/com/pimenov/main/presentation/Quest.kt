package com.pimenov.main.presentation

/** A single checkable objective shown in the quest banner. */
data class QuestStep(
    val label: String,
    val done: Boolean,
)

/**
 * Visible mission shown to the player at the top of the chat. For the pilot
 * it's an ordered checklist: learn where Aina went (advanced by the DM via
 * `quest_advance`) and arm yourself (resolved deterministically by the engine
 * the moment a real weapon enters the inventory).
 */
data class QuestObjective(
    val title: String,
    val steps: List<QuestStep>,
)

object TavernPilotQuest {
    const val MAIN_QUEST = "quest_find_aina"
    const val DRAGON_QUEST = "quest_slay_dragon"

    private val TOWN_LOCATIONS = setOf("loc_tavern_last_rest", "loc_village_northspur")
    private val LAIR_LOCATIONS = setOf("loc_lair_mouth", "loc_dragon_lair")

    /**
     * The visible objective for the banner, shown in phases (max two lines on a
     * small screen): in town — preparation; once the player resolves to go or
     * physically heads onto the northern road — the march to the lair.
     */
    fun objectiveFor(
        questStages: Map<String, Int>,
        hasWeapon: Boolean,
        locationId: String,
        outcome: String?,
    ): QuestObjective {
        if (outcome == "victory") {
            return QuestObjective("Путь окончен", listOf(QuestStep("Дракон повержен", done = true)))
        }
        if (outcome == "death") {
            return QuestObjective("Путь окончен", listOf(QuestStep("Дорога оборвалась", done = true)))
        }

        val foundDirection = (questStages[MAIN_QUEST] ?: 0) >= 1
        val dragonStage = questStages[DRAGON_QUEST] ?: 0
        val onRoad = locationId !in TOWN_LOCATIONS
        val reachedLair = locationId in LAIR_LOCATIONS

        // The march — once resolved to go OR already out on the northern road.
        if (onRoad || dragonStage >= 2) {
            return QuestObjective(
                title = "На север, к логову дракона",
                steps = listOf(
                    QuestStep("Добраться до логова на отроге", reachedLair),
                    QuestStep("Одолеть дракона", done = false),
                ),
            )
        }
        // Preparation in town: arm yourself, then resolve to set out.
        if (!foundDirection || !hasWeapon) {
            return QuestObjective(
                title = "Найти Айну, собраться в путь",
                steps = listOf(
                    QuestStep("Узнать, куда ушла Айна", foundDirection),
                    QuestStep("Купить оружие у трактирщика", hasWeapon),
                ),
            )
        }
        return QuestObjective(
            title = "Решиться на дорогу",
            steps = listOf(
                QuestStep("Узнать, что ждёт на севере", dragonStage >= 1),
                QuestStep("Решить, идти ли с воинами на рассвете", dragonStage >= 2),
            ),
        )
    }

    /**
     * Leads the player has uncovered, derived deterministically from quest
     * stages. Read-only notes shown in the action menu's «Зацепки» section.
     */
    fun leadsFor(questStages: Map<String, Int>): List<String> = buildList {
        if ((questStages[MAIN_QUEST] ?: 0) >= 1) {
            add("Айна полгода назад спрашивала, как короче выйти на северный тракт.")
        }
        if ((questStages[DRAGON_QUEST] ?: 0) >= 1) {
            add("Логово дракона — старая выработка на северном отроге. Воины уходят туда на рассвете.")
        }
    }

    val initial = objectiveFor(emptyMap(), hasWeapon = false, locationId = "loc_tavern_last_rest", outcome = null)
}
