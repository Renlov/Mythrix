package com.pimenov.feature.game.world

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A combat enemy from a story's `enemies.json`. */
@Serializable
data class EnemyDef(
    val id: String,
    val name: String,
    val hp: Int,
    val ac: Int,
    @SerialName("attack_die") val attackDie: String,
    @SerialName("attack_bonus") val attackBonus: Int = 0,
    val description: String = "",
)
