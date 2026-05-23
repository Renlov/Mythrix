package com.pimenov.feature.game.world

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An NPC from `world/tavern/npcs.json`. Only the fields the engine acts on are
 * typed (id, role, location, wares); narrative fields (description,
 * dialogue_style, knowledge, …) are ignored here and consumed by the DM via the
 * inlined world bible. See docs/ai-architecture/14-npc-structure.md.
 */
@Serializable
data class NpcDef(
    val id: String,
    val name: String? = null,
    @SerialName("name_reveal") val nameReveal: String = "on_introduction",
    @SerialName("role_label") val roleLabel: String? = null,
    val role: String = "",
    @SerialName("location_id") val locationId: String? = null,
    val inventory: List<String> = emptyList(),
    @SerialName("knows_about") val knowsAbout: List<String> = emptyList(),
    val combat: CombatStats? = null,
)

/** Combat stats for an attackable NPC — same shape as enemies. */
@Serializable
data class CombatStats(
    val hp: Int,
    val ac: Int,
    @SerialName("attack_die") val attackDie: String,
    @SerialName("attack_bonus") val attackBonus: Int = 0,
)
