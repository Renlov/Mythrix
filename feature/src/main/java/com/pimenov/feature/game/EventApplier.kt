package com.pimenov.feature.game

/**
 * Applies DM intents to [PlayerState], returning a new immutable state.
 * Every change is validated against the [WorldCatalog]: unknown items, items
 * the player already holds, or purchases beyond the player's gold are silently
 * skipped. Combat-resolving intents (attack, skill_check) carry no numbers and
 * mutate nothing here yet — that belongs to a future combat engine.
 */
object EventApplier {

    private const val PLAYER = "player_main"

    fun apply(state: PlayerState, events: EventsBlock, catalog: WorldCatalog): PlayerState {
        var s = state
        for (intent in events.intents) {
            s = when (intent.type) {
                "take_item", "give_item" -> addItem(s, intent.itemId, catalog)
                "buy" -> buy(s, intent, catalog)
                "drop_item" -> dropItem(s, intent.itemId)
                "quest_advance" -> advanceQuest(s, intent)
                else -> s
            }
        }
        return changeLocation(s, events.locationChange, catalog)
    }

    /**
     * Moves the player only to a location connected to the current one. An
     * unknown or non-adjacent target is ignored, so the DM can't teleport the
     * player off the map.
     */
    private fun changeLocation(state: PlayerState, target: String?, catalog: WorldCatalog): PlayerState {
        if (target == null || target == state.locationId) return state
        if (!catalog.locationExists(target)) return state
        val current = catalog.location(state.locationId)
        if (current != null && target !in current.connections) return state
        return state.copy(locationId = target)
    }

    private fun addItem(state: PlayerState, itemId: String?, catalog: WorldCatalog): PlayerState {
        val id = itemId ?: return state
        if (!catalog.exists(id) || id in state.inventoryIds) return state
        return state.copy(inventoryIds = state.inventoryIds + id)
    }

    private fun buy(state: PlayerState, intent: Intent, catalog: WorldCatalog): PlayerState {
        if (intent.buyer != PLAYER) return state
        val id = intent.itemId ?: return state
        val item = catalog.byId(id) ?: return state
        if (state.gold < item.price || id in state.inventoryIds) return state
        return state.copy(gold = state.gold - item.price, inventoryIds = state.inventoryIds + id)
    }

    private fun dropItem(state: PlayerState, itemId: String?): PlayerState {
        val id = itemId ?: return state
        if (id !in state.inventoryIds) return state
        return state.copy(inventoryIds = state.inventoryIds - id)
    }

    private fun advanceQuest(state: PlayerState, intent: Intent): PlayerState {
        val quest = intent.questId ?: return state
        val newStage = intent.newStage ?: return state
        val current = state.questStages[quest] ?: 0
        if (newStage <= current) return state
        return state.copy(questStages = state.questStages + (quest to newStage))
    }
}
