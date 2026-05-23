package com.pimenov.feature.game.world

/**
 * Unified attackable target so the combat engine treats enemies and combat-
 * capable NPCs the same. Resolved via [WorldCatalog.combatant]; the source
 * (enemy vs NPC) does not matter once the fight starts.
 */
data class Combatant(
    val id: String,
    val name: String,
    val hp: Int,
    val ac: Int,
    val attackDie: String,
    val attackBonus: Int,
)
