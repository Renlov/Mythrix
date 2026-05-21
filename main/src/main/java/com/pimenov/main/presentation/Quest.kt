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

    fun objectiveFor(questStages: Map<String, Int>, hasWeapon: Boolean): QuestObjective {
        val foundDirection = (questStages[MAIN_QUEST] ?: 0) >= 1
        val dragonStage = questStages[DRAGON_QUEST] ?: 0
        // Goals come in pairs; only the current pair is shown so the banner
        // stays at most two lines on a small screen. The next pair replaces
        // the previous one once both its goals are done.
        val groups = listOf(
            listOf(
                QuestStep("Узнать, куда ушла Айна", foundDirection),
                QuestStep("Купить оружие у трактирщика", hasWeapon),
            ),
            listOf(
                QuestStep("Узнать, что ждёт на севере", dragonStage >= 1),
                QuestStep("Решить, идти ли с воинами на рассвете", dragonStage >= 2),
            ),
        )
        val active = groups.firstOrNull { group -> group.any { !it.done } } ?: groups.last()
        return QuestObjective(title = "Найти Айну, собраться в путь", steps = active)
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

    val initial = objectiveFor(emptyMap(), hasWeapon = false)
}
