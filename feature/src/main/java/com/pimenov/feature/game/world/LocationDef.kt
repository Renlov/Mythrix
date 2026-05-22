package com.pimenov.feature.game.world

import kotlinx.serialization.Serializable

/**
 * A location from `world/tavern/locations.json`. Only the fields the engine
 * needs to drive movement and scene context are typed; the rest are ignored.
 */
@Serializable
data class LocationDef(
    val id: String,
    val name: String,
    val description: String = "",
    val connections: List<String> = emptyList(),
    val npcs: List<String> = emptyList(),
    val enemies: List<String> = emptyList(),
    val atmosphere: Atmosphere? = null,
)

@Serializable
data class Atmosphere(
    val mood: String = "neutral",
    val nuance: String = "",
)
