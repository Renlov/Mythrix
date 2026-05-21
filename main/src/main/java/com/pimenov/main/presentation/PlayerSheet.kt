package com.pimenov.main.presentation

import com.pimenov.feature.game.PlayerState
import com.pimenov.feature.game.WorldCatalog

/** UI-facing snapshot of the player: numbers ready to show, items as names. */
data class PlayerSheet(
    val name: String = "—",
    val hp: Int = 0,
    val maxHp: Int = 0,
    val gold: Int = 0,
    val inventory: List<String> = emptyList(),
)

fun PlayerState.toSheet(catalog: WorldCatalog): PlayerSheet = PlayerSheet(
    name = name,
    hp = hp,
    maxHp = maxHp,
    gold = gold,
    inventory = inventoryIds.map { catalog.name(it) },
)
