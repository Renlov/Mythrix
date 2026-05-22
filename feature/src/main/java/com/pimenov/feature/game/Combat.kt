package com.pimenov.feature.game

/** Transient state of an ongoing fight (not persisted in v1). */
data class CombatSession(val enemyId: String, val enemyHp: Int)

/**
 * Outcome of one combat exchange: the player's blow and — if the enemy
 * survives — its reply. Numbers here are for the hidden `[TURN RESULT]` only;
 * the DM narrates the outcome without them.
 */
data class CombatRound(
    val enemyName: String,
    val playerHit: Boolean,
    val playerRoll: Int,
    val playerDamage: Int,
    val enemyHpAfter: Int,
    val enemyKilled: Boolean,
    val enemyHit: Boolean = false,
    val enemyDamage: Int = 0,
    val playerHpAfter: Int = 0,
    val playerDead: Boolean = false,
)
