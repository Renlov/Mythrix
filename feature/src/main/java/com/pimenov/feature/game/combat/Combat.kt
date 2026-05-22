package com.pimenov.feature.game.combat

/** Transient state of an ongoing fight (not persisted in v1). */
data class CombatSession(val enemyId: String, val enemyHp: Int, val enemyMaxHp: Int)

/** Result of a `d20 + bonus` skill check against a difficulty class. */
data class SkillCheckResult(
    val success: Boolean,
    val roll: Int,
    val total: Int,
    val dc: Int,
)

/**
 * Outcome of one combat exchange: the player's action (attack or heal), the
 * allies' contribution, and — if the enemy survives — its reply. Numbers here
 * are for the hidden `[TURN RESULT]` only; the DM narrates without them.
 */
data class CombatRound(
    val enemyName: String,
    val action: String,                 // "attack" | "heal"
    val playerHit: Boolean = false,
    val playerRoll: Int = 0,
    val playerDamage: Int = 0,
    val healed: Int = 0,
    val allyDamage: Int = 0,            // warriors fighting alongside
    val enemyHpAfter: Int = 0,
    val enemyKilled: Boolean = false,
    val enemyPhase: String? = null,     // narrative phase hint for a boss
    val enemyHit: Boolean = false,
    val enemyDamage: Int = 0,
    val playerHpAfter: Int = 0,
    val playerDead: Boolean = false,
    val victory: Boolean = false,       // boss defeated → pilot won
)
