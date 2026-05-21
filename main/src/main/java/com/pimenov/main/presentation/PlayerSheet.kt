package com.pimenov.main.presentation

import com.pimenov.feature.game.PlayerState
import com.pimenov.feature.game.WorldCatalog

/** An inventory entry ready for the sheet: display name + type for the icon. */
data class InventoryItem(
    val name: String,
    val type: String,
)

/** UI-facing snapshot of the player: numbers ready to show, items with icons. */
data class PlayerSheet(
    val name: String = "—",
    val className: String = "",
    val hp: Int = 0,
    val maxHp: Int = 0,
    val gold: Int = 0,
    val inventory: List<InventoryItem> = emptyList(),
)

private val CLASS_NAMES = mapOf(
    "wanderer" to "Странник",
    "warrior" to "Воин",
    "mage" to "Маг",
    "rogue" to "Плут",
    "ranger" to "Следопыт",
)

fun PlayerState.toSheet(catalog: WorldCatalog): PlayerSheet = PlayerSheet(
    name = name,
    className = CLASS_NAMES[playerClass] ?: playerClass.replaceFirstChar { it.uppercase() },
    hp = hp,
    maxHp = maxHp,
    gold = gold,
    inventory = inventoryIds.map { id ->
        val item = catalog.byId(id)
        InventoryItem(name = item?.name ?: id, type = item?.type.orEmpty())
    },
)
