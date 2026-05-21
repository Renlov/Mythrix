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
    private const val MAIN_QUEST = "quest_find_aina"

    fun objectiveFor(questStages: Map<String, Int>, hasWeapon: Boolean): QuestObjective {
        val foundDirection = (questStages[MAIN_QUEST] ?: 0) >= 1
        return QuestObjective(
            title = "Найти Айну, собраться в путь",
            steps = listOf(
                QuestStep("Узнать, куда ушла Айна", foundDirection),
                QuestStep("Купить оружие у трактирщика", hasWeapon),
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
    }

    val initial = objectiveFor(emptyMap(), hasWeapon = false)
}
