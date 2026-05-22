package com.pimenov.feature.game

import kotlinx.serialization.Serializable

/**
 * Live, mutable-by-copy state of the player during a session.
 * Numbers (hp, gold) are owned by the engine — the DM never computes them.
 * [questStages] maps quest id → current stage; advanced via `quest_advance`.
 */
@Serializable
data class PlayerState(
    val name: String,
    val playerClass: String = "wanderer",
    val locationId: String = "loc_tavern_last_rest",
    val hp: Int,
    val maxHp: Int,
    val gold: Int,
    val inventoryIds: List<String>,
    val questStages: Map<String, Int>,
    /** Subset of [inventoryIds] the player has equipped (worn). */
    val equippedIds: Set<String> = emptySet(),
    /** Remaining charges of consumables, by item id. Absent = full per def. */
    val itemUses: Map<String, Int> = emptyMap(),
    /** Pilot ending once set: "victory" or "death". null = still playing. */
    val outcome: String? = null,
)
