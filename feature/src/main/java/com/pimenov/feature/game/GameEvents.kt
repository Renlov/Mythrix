package com.pimenov.feature.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `[EVENTS]` block the DM appends to every turn. Mirrors the schema in
 * `prompts/dm_system_v1.txt`. Unknown fields are ignored on parse.
 */
@Serializable
data class EventsBlock(
    val intents: List<Intent> = emptyList(),
    @SerialName("location_change") val locationChange: String? = null,
    @SerialName("scene_ended") val sceneEnded: Boolean = false,
    @SerialName("scene_summary") val sceneSummary: String? = null,
    @SerialName("atmosphere_shift") val atmosphereShift: String? = null,
)

/**
 * A single intent inside [EventsBlock]. Flat by design: [type] discriminates
 * and the applier reads only the fields relevant to that type. Avoids
 * polymorphic serialization with a custom `type` key.
 */
@Serializable
data class Intent(
    val type: String,
    val actor: String? = null,
    val from: String? = null,
    val to: String? = null,
    @SerialName("item_id") val itemId: String? = null,
    val buyer: String? = null,
    val seller: String? = null,
    val target: String? = null,
    val weapon: String? = null,
    val skill: String? = null,
    val difficulty: String? = null,
    @SerialName("quest_id") val questId: String? = null,
    @SerialName("new_stage") val newStage: Int? = null,
)
