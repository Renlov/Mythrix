package com.pimenov.core.common

import kotlin.random.Random

object Dice {
    fun roll(sides: Int, count: Int = 1, modifier: Int = 0): Int {
        require(sides > 0)
        require(count > 0)
        var sum = modifier
        repeat(count) { sum += Random.nextInt(1, sides + 1) }
        return sum
    }

    fun d20(modifier: Int = 0): Int = roll(20, 1, modifier)
}
