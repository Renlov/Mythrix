package com.pimenov.feature.game.world

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-story rules that used to be hardcoded in the engine (the dragon win
 * condition, the allies' bonus damage). Loaded from a story's `rules.json` so a
 * new story can declare its own ending and special combat without touching
 * engine code. Absent file → empty rules (a story with no boss/allies).
 *
 * [winCondition.enemyId] also marks the "boss": only that enemy gets phased
 * health descriptions in combat.
 */
@Serializable
data class StoryRules(
    @SerialName("win_condition") val winCondition: WinCondition? = null,
    @SerialName("ally_support") val allySupport: AllySupport? = null,
    @SerialName("companions") val companions: Companions? = null,
) {
    companion object {
        private const val FILE = "rules.json"
        private val json = Json { ignoreUnknownKeys = true }

        // [FIREBASE] rules.json — часть скачиваемого бандла сюжета. Победа,
        // поражение и спец-боёвка задаются данными, не кодом, поэтому новый
        // сюжет из Firebase приходит со своими правилами и не требует апдейта APK.
        fun load(source: StoryContentSource): StoryRules =
            if (source.exists(FILE)) {
                json.decodeFromString(serializer(), source.read(FILE))
            } else {
                StoryRules()
            }
    }
}

/** How the player wins this story. Currently only "defeat a specific enemy". */
@Serializable
data class WinCondition(
    val type: String = "enemy_defeated",
    @SerialName("enemy_id") val enemyId: String? = null,
)

/** Allies that add damage to a target enemy once the player joined them. */
@Serializable
data class AllySupport(
    @SerialName("quest_id") val questId: String,
    @SerialName("min_stage") val minStage: Int = 1,
    @SerialName("enemy_id") val enemyId: String,
    @SerialName("damage_die") val damageDie: String,
)

/**
 * NPCs that travel with the player once a quest reaches [minStage]. They are
 * injected into the prompt as present in every scene from then on, so the DM
 * keeps them in the narrative along the path — not only in the boss fight.
 */
@Serializable
data class Companions(
    @SerialName("quest_id") val questId: String,
    @SerialName("min_stage") val minStage: Int = 1,
    @SerialName("npc_ids") val npcIds: List<String> = emptyList(),
)
