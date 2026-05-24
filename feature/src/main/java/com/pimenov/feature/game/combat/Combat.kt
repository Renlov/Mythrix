package com.pimenov.feature.game.combat

/** Transient state of an ongoing fight (not persisted in v1). */
data class CombatSession(val enemyId: String, val enemyHp: Int, val enemyMaxHp: Int)

/** Live enemy/player HP shown in the combat HUD while a fight is active. */
data class CombatHud(
    val enemyId: String,
    val enemyName: String,
    val enemyHp: Int,
    val enemyMaxHp: Int,
)

/**
 * A computed-but-not-yet-applied player strike. Rolls are done up front so the
 * DM can narrate the outcome; the HP change lands only when [GameStateRepository]
 * commits it — at the end of the narration message.
 */
data class PlayerBlow(
    val enemyId: String,
    val enemyName: String,
    val hit: Boolean,
    val roll: Int,
    val damage: Int,
    val allyDamage: Int,
    val enemyHpBefore: Int,
    val enemyHpAfter: Int,
    val enemyMaxHp: Int,
    val killed: Boolean,
    val victory: Boolean,
    val phase: String?,
)

/** A computed-but-not-yet-applied enemy retaliation. */
data class EnemyBlow(
    val enemyId: String,
    val enemyName: String,
    val hit: Boolean,
    val roll: Int,
    val damage: Int,
    val playerHpBefore: Int,
    val playerHpAfter: Int,
    val dead: Boolean,
)

/** A computed-but-not-yet-applied use of healing supplies during a fight. */
data class HealAction(
    val itemId: String,
    val healed: Int,
    val playerHpBefore: Int,
    val playerHpAfter: Int,
    val remainingAfter: Int,
    val hadCharges: Boolean,
)

/** Result of a `d20 + bonus` skill check against a difficulty class. */
data class SkillCheckResult(
    val success: Boolean,
    val roll: Int,
    val total: Int,
    val dc: Int,
)

