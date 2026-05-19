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
    val initial = QuestObjective(
        title = "Найти Айну",
        hint = "Расспроси людей в таверне. Кто-нибудь должен был её видеть.",
    )
}
