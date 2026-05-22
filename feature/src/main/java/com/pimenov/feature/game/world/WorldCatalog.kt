package com.pimenov.feature.game.world

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Immutable lookup of every item defined in the world bible. Used to validate
 * intents and to resolve ids → display names for the character sheet.
 */
class WorldCatalog private constructor(
    private val items: Map<String, ItemDef>,
    private val locations: Map<String, LocationDef>,
    private val enemies: Map<String, EnemyDef>,
) {

    fun byId(id: String): ItemDef? = items[id]

    fun exists(id: String): Boolean = items.containsKey(id)

    fun name(id: String): String = items[id]?.name ?: id

    /** Items a given NPC sells (its `owner_id`), cheapest first. */
    fun waresOf(ownerId: String): List<ItemDef> =
        items.values.filter { it.ownerId == ownerId }.sortedBy { it.price }

    fun location(id: String): LocationDef? = locations[id]

    fun locationExists(id: String): Boolean = locations.containsKey(id)

    fun enemy(id: String): EnemyDef? = enemies[id]

    companion object {
        private const val ITEMS_PATH = "world/tavern/items.json"
        private const val LOCATIONS_PATH = "world/tavern/locations.json"
        private const val ENEMIES_PATH = "world/tavern/enemies.json"

        fun load(context: Context): WorldCatalog {
            val json = Json { ignoreUnknownKeys = true }
            val items = json.decodeFromString<List<ItemDef>>(readAsset(context, ITEMS_PATH))
            val locations = json.decodeFromString<List<LocationDef>>(readAsset(context, LOCATIONS_PATH))
            val enemies = runCatching {
                json.decodeFromString<List<EnemyDef>>(readAsset(context, ENEMIES_PATH))
            }.getOrDefault(emptyList())
            return WorldCatalog(
                items = items.associateBy { it.id },
                locations = locations.associateBy { it.id },
                enemies = enemies.associateBy { it.id },
            )
        }

        private fun readAsset(context: Context, path: String): String =
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
