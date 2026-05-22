package com.pimenov.feature.game.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceRollerTest {

    @Test
    fun `d20 stays in 1 to 20`() {
        val dice = DiceRoller(Random(42))
        repeat(1000) {
            val r = dice.d20()
            assertTrue("roll $r out of range", r in 1..20)
        }
    }

    @Test
    fun `fixed seed is reproducible`() {
        assertEquals(DiceRoller(Random(7)).d20(), DiceRoller(Random(7)).d20())
        assertEquals(DiceRoller(Random(7)).roll("2d6+1"), DiceRoller(Random(7)).roll("2d6+1"))
    }

    @Test
    fun `roll respects bounds of expression`() {
        val dice = DiceRoller(Random(1))
        repeat(500) { assertTrue(dice.roll("d8") in 1..8) }
        repeat(500) { assertTrue(dice.roll("2d6+1") in 3..13) }
        repeat(500) { assertTrue(dice.roll("d4-1") in 0..3) }
    }

    @Test
    fun `bad expression returns 1`() {
        assertEquals(1, DiceRoller(Random(1)).roll("банан"))
    }
}
