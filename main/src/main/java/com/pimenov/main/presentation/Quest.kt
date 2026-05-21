package com.pimenov.main.presentation

/**
 * Visible mission shown to the player at the top of the chat.
 * Mirrors the active quest from the world bible; for pilot it's the
 * `quest_find_aina` main quest.
 */
data class QuestObjective(
    val title: String,
    val hint: String,
)

object TavernPilotQuest {
    private const val MAIN_QUEST = "quest_find_aina"

    /** Derives the banner objective from the live quest stage. */
    fun objectiveFor(questStages: Map<String, Int>): QuestObjective {
        val stage = questStages[MAIN_QUEST] ?: 0
        return when (stage) {
            0 -> QuestObjective(
                title = "Найти Айну",
                hint = "Расспроси людей в таверне. Кто-нибудь должен был её видеть.",
            )
            else -> QuestObjective(
                title = "Найти Айну",
                hint = "Кто-то здесь её помнит. Тяни за ниточку дальше.",
            )
        }
    }

    val initial = objectiveFor(emptyMap())
}
