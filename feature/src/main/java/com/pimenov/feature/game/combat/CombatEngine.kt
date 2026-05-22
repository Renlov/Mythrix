package com.pimenov.feature.game.combat

/** Result of a single attack resolution. Pure data, no narrative. */
data class CombatResult(
    val hit: Boolean,
    val attackRoll: Int,
    val damage: Int,
    val targetHpBefore: Int,
    val targetHpAfter: Int,
    val killed: Boolean,
)

/**
 * Deterministic combat math. Hit if `d20 + attackBonus >= targetAc`; on a hit
 * the [damageExpr] die is rolled and subtracted from the target's HP.
 */
class CombatEngine(private val dice: DiceRoller = DiceRoller()) {

    fun resolveAttack(
        attackBonus: Int,
        damageExpr: String,
        targetAc: Int,
        targetHp: Int,
    ): CombatResult {
        val roll = dice.d20()
        val hit = roll + attackBonus >= targetAc
        val damage = if (hit) dice.roll(damageExpr) else 0
        val after = (targetHp - damage).coerceAtLeast(0)
        return CombatResult(
            hit = hit,
            attackRoll = roll,
            damage = damage,
            targetHpBefore = targetHp,
            targetHpAfter = after,
            killed = after == 0,
        )
    }

    /** Success if `d20 + bonus >= dc`. */
    fun resolveSkillCheck(bonus: Int, dc: Int): SkillCheckResult {
        val roll = dice.d20()
        val total = roll + bonus
        return SkillCheckResult(success = total >= dc, roll = roll, total = total, dc = dc)
    }
}
