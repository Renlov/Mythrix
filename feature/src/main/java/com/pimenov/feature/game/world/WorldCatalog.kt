package com.pimenov.feature.game.world

import kotlinx.serialization.json.Json

/**
 * Immutable lookup of every item defined in the world bible. Used to validate
 * intents and to resolve ids → display names for the character sheet.
 */
class WorldCatalog private constructor(
    private val items: Map<String, ItemDef>,
    private val locations: Map<String, LocationDef>,
    private val enemies: Map<String, EnemyDef>,
    private val npcs: Map<String, NpcDef>,
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

    fun npc(id: String): NpcDef? = npcs[id]

    /**
     * Resolves any attackable target — an enemy from `enemies.json` or an NPC
     * with a `combat` block — into a uniform [Combatant]. null if [id] is neither
     * or the NPC has no combat stats.
     */
    fun combatant(id: String): Combatant? {
        enemies[id]?.let { e ->
            return Combatant(e.id, e.name, e.hp, e.ac, e.attackDie, e.attackBonus)
        }
        val npc = npcs[id] ?: return null
        val stats = npc.combat ?: return null
        val name = npc.name ?: npc.roleLabel ?: id
        return Combatant(npc.id, name, stats.hp, stats.ac, stats.attackDie, stats.attackBonus)
    }

    /** First merchant present in the given location, if any. */
    fun merchantAt(locationId: String): NpcDef? =
        npcs.values.firstOrNull { it.role == "merchant" && it.locationId == locationId }

    companion object {
        fun load(source: StoryContentSource): WorldCatalog {
            val json = Json { ignoreUnknownKeys = true }
            val items = json.decodeFromString<List<ItemDef>>(source.read("items.json"))
            val locations = json.decodeFromString<List<LocationDef>>(source.read("locations.json"))
            val enemies = runCatching {
                json.decodeFromString<List<EnemyDef>>(source.read("enemies.json"))
            }.getOrDefault(emptyList())
            val npcs = runCatching {
                json.decodeFromString<List<NpcDef>>(source.read("npcs.json"))
            }.getOrDefault(emptyList())
            return WorldCatalog(
                items = items.associateBy { it.id },
                locations = locations.associateBy { it.id },
                enemies = enemies.associateBy { it.id },
                npcs = npcs.associateBy { it.id },
            )
        }
    }
}
