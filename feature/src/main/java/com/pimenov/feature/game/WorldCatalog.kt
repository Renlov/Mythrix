package com.pimenov.feature.game

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Immutable lookup of every item defined in the world bible. Used to validate
 * intents and to resolve ids → display names for the character sheet.
 */
class WorldCatalog private constructor(private val items: Map<String, ItemDef>) {

    fun byId(id: String): ItemDef? = items[id]

    fun exists(id: String): Boolean = items.containsKey(id)

    fun name(id: String): String = items[id]?.name ?: id

    /** Items a given NPC sells (its `owner_id`), cheapest first. */
    fun waresOf(ownerId: String): List<ItemDef> =
        items.values.filter { it.ownerId == ownerId }.sortedBy { it.price }

    companion object {
        private const val ITEMS_PATH = "world/tavern/items.json"

        fun load(context: Context): WorldCatalog {
            val json = Json { ignoreUnknownKeys = true }
            val text = context.assets.open(ITEMS_PATH)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val list = json.decodeFromString<List<ItemDef>>(text)
            return WorldCatalog(list.associateBy { it.id })
        }
    }
}
