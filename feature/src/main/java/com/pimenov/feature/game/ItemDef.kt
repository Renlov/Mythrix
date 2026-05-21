package com.pimenov.feature.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Catalog entry from `world/tavern/items.json`. Only fields the engine needs
 * are typed; the rest (damage_die, weight, …) are ignored on parse.
 */
@Serializable
data class ItemDef(
    val id: String,
    val name: String,
    val type: String = "",
    val price: Int = 0,
    val description: String = "",
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("damage_die") val damageDie: String? = null,
    @SerialName("armor_bonus") val armorBonus: Int? = null,
)
