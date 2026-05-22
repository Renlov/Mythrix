package com.pimenov.feature.game

import kotlin.random.Random

/**
 * Rolls dice. RNG is injectable so tests can use a fixed seed for
 * reproducible outcomes.
 */
class DiceRoller(private val rng: Random = Random.Default) {

    fun d20(): Int = rng.nextInt(1, 21)

    /**
     * Rolls a dice expression: `d8`, `2d6`, `d4+2`, `2d6-1`. Returns 1 if the
     * expression can't be parsed, so a bad weapon stat never crashes a fight.
     */
    fun roll(expr: String): Int {
        val match = REGEX.matchEntire(expr.trim().lowercase()) ?: return 1
        val count = match.groupValues[1].ifEmpty { "1" }.toInt().coerceIn(1, 20)
        val sides = match.groupValues[2].toInt().coerceAtLeast(1)
        val modifier = match.groupValues[3].ifEmpty { "0" }.toInt()
        var total = 0
        repeat(count) { total += rng.nextInt(1, sides + 1) }
        return (total + modifier).coerceAtLeast(0)
    }

    companion object {
        private val REGEX = Regex("""(\d*)d(\d+)([+-]\d+)?""")
    }
}
