package com.pimenov.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CombatEngineTest {

    @Test
    fun `hit reduces hp and never below zero`() {
        val engine = CombatEngine(DiceRoller(Random(3)))
        val result = engine.resolveAttack(attackBonus = 20, damageExpr = "d6", targetAc = 5, targetHp = 3)
        assertTrue(result.hit)
        assertTrue(result.targetHpAfter >= 0)
        assertEquals(result.targetHpAfter == 0, result.killed)
    }

    @Test
    fun `impossible hit always misses and deals no damage`() {
        val engine = CombatEngine(DiceRoller(Random(9)))
        repeat(200) {
            val result = engine.resolveAttack(attackBonus = 0, damageExpr = "d8", targetAc = 99, targetHp = 10)
            assertFalse(result.hit)
            assertEquals(0, result.damage)
            assertEquals(10, result.targetHpAfter)
        }
    }

    @Test
    fun `lethal damage marks killed`() {
        val engine = CombatEngine(DiceRoller(Random(5)))
        val result = engine.resolveAttack(attackBonus = 20, damageExpr = "2d6+10", targetAc = 1, targetHp = 4)
        assertTrue(result.killed)
        assertEquals(0, result.targetHpAfter)
    }
}
