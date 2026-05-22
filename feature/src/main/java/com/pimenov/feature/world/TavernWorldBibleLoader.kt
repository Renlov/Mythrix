package com.pimenov.feature.world

import android.content.Context
import com.pimenov.feature.game.player.PlayerState
import com.pimenov.feature.game.world.WorldCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * Loads the pilot Tavern World Bible and the DM system prompt template, then
 * assembles a single system prompt string for the LLM.
 *
 * The world block reflects the player's current location: only that scene and
 * its exits/NPCs are shown, so moving (via the `location_change` event) swaps
 * the [LOCATION] and [NPCS] context. Mutable player fields and the journal are
 * injected from the live [PlayerState].
 */
object TavernWorldBibleLoader {
    private const val PROMPT_TEMPLATE = "prompts/dm_system_v1.txt"
    private const val WORLD_DIR = "world/tavern"
    private const val PLAYER_NAME_PLACEHOLDER = "{{player_name}}"

    private val json = Json { ignoreUnknownKeys = true }
    private val pretty = Json { prettyPrint = true }

    fun buildSystemPrompt(
        context: Context,
        player: PlayerState,
        journal: String,
        catalog: WorldCatalog,
    ): String {
        val template = readAsset(context, PROMPT_TEMPLATE)
        val worldBlock = buildWorldBlock(context, player, journal, catalog)
        return template.replace(PLAYER_NAME_PLACEHOLDER, player.name) + "\n\n" + worldBlock
    }

    private fun buildWorldBlock(
        context: Context,
        player: PlayerState,
        journal: String,
        catalog: WorldCatalog,
    ): String {
        val playerBlock = renderPlayerBlock(context, player)
        val locationBlock = renderLocationBlock(player, catalog)
        val npcs = renderNpcsForLocation(context, player, catalog)
        val enemies = renderEnemiesForLocation(player, catalog)
        val items = renderItemsBlock(context, player)
        val quests = readAsset(context, "$WORLD_DIR/quests.json")

        // Sub-headers deliberately avoid `[PLAYER]` — that token is a stop
        // marker for hallucinated next turns (see PromptBuilder.STOP_MARKERS).
        return buildString {
            appendLine("[МИР]")
            appendLine()
            appendLine("[PLAYER_STATE]")
            appendLine(playerBlock)
            appendLine()
            appendLine("[ЖУРНАЛ]")
            appendLine(journal.ifBlank { "Пока ничего не произошло — сцена только начинается." })
            appendLine()
            appendLine(locationBlock)
            appendLine()
            appendLine("[NPCS]")
            appendLine(npcs)
            appendLine()
            appendLine("[ВРАГИ]")
            appendLine(enemies)
            appendLine()
            appendLine("[ITEMS]")
            appendLine(items)
            appendLine()
            appendLine("[QUESTS]")
            append(quests)
        }
    }

    /** Current scene: name, description, atmosphere and the exits to move to. */
    private fun renderLocationBlock(player: PlayerState, catalog: WorldCatalog): String {
        val loc = catalog.location(player.locationId)
            ?: return "[LOCATION]\nИгрок в неизвестном месте (${player.locationId})."
        return buildString {
            appendLine("[LOCATION]")
            appendLine(loc.name)
            appendLine(loc.description)
            loc.atmosphere?.let { atm ->
                appendLine("Атмосфера: ${atm.mood} — ${atm.nuance}")
            }
            appendLine()
            appendLine("[ВЫХОДЫ]")
            val exits = loc.connections.map { id -> id to (catalog.location(id)?.name ?: id) }
            if (exits.isEmpty()) {
                append("Отсюда некуда идти, кроме как назад по своим следам.")
            } else {
                // Tell the DM which target id to emit in location_change.
                exits.forEach { (id, name) -> appendLine("- $name ($id)") }
                append("Перейти можно только сюда. Когда игрок идёт в один из выходов, верни location_change с его id.")
            }
        }
    }

    /**
     * Only NPCs physically present in the current location. Items the player
     * already owns are stripped from each NPC's inventory so the DM never
     * treats a sold item as still belonging to the merchant.
     */
    private fun renderNpcsForLocation(context: Context, player: PlayerState, catalog: WorldCatalog): String {
        val present = catalog.location(player.locationId)?.npcs.orEmpty().toSet()
        if (present.isEmpty()) return "[]"
        val owned = player.inventoryIds.toSet()
        val all = json.parseToJsonElement(readAsset(context, "$WORLD_DIR/npcs.json")).jsonArray
        val filtered = JsonArray(
            all.filter { it.jsonObject["id"]?.jsonPrimitive?.content in present }
                .map { stripOwnedFromInventory(it.jsonObject, owned) },
        )
        return pretty.encodeToString(JsonArray.serializer(), filtered)
    }

    private fun stripOwnedFromInventory(npc: JsonObject, owned: Set<String>): JsonObject {
        val inventory = npc["inventory"]?.jsonArray ?: return npc
        val kept = inventory.filter { it.jsonPrimitive.content !in owned }
        if (kept.size == inventory.size) return npc
        return buildJsonObject {
            npc.forEach { (key, value) ->
                if (key == "inventory") put("inventory", JsonArray(kept)) else put(key, value)
            }
        }
    }

    /**
     * Items as the DM should see them: anything the player holds is re-owned
     * to `player_main`, so the merchant can't claim or "put away" a sold item.
     */
    private fun renderItemsBlock(context: Context, player: PlayerState): String {
        val owned = player.inventoryIds.toSet()
        val all = json.parseToJsonElement(readAsset(context, "$WORLD_DIR/items.json")).jsonArray
        val updated = JsonArray(
            all.map { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content
                if (id != null && id in owned) {
                    buildJsonObject {
                        obj.forEach { (key, value) ->
                            if (key == "owner_id") put("owner_id", JsonPrimitive("player_main")) else put(key, value)
                        }
                        if (!obj.containsKey("owner_id")) put("owner_id", JsonPrimitive("player_main"))
                    }
                } else {
                    obj
                }
            },
        )
        return pretty.encodeToString(JsonArray.serializer(), updated)
    }

    /** Enemies present in the current location and how to fight them. */
    private fun renderEnemiesForLocation(player: PlayerState, catalog: WorldCatalog): String {
        val ids = catalog.location(player.locationId)?.enemies.orEmpty()
        val enemies = ids.mapNotNull { catalog.enemy(it) }
        if (enemies.isEmpty()) return "Врагов рядом нет."
        return buildString {
            enemies.forEach { e -> appendLine("- ${e.name} (${e.id}): ${e.description}") }
            append(
                "Если игрок нападает на врага — опиши замах (намерение) и верни в [EVENTS] " +
                    "{\"type\":\"attack\",\"actor\":\"player_main\",\"target\":\"<id_врага>\",\"weapon\":\"<id_оружия|null>\"}. " +
                    "НЕ пиши попадание, урон или HP — это посчитает движок и пришлёт [TURN RESULT].",
            )
        }
    }

    /** Static player.json with the mutable fields overwritten from [player]. */
    private fun renderPlayerBlock(context: Context, player: PlayerState): String {
        val base = json.parseToJsonElement(readAsset(context, "$WORLD_DIR/player.json")).jsonObject
        val merged = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            put("name", player.name)
            put("location_id", player.locationId)
            put("hp", player.hp)
            put("max_hp", player.maxHp)
            put("gold", player.gold)
            put("inventory", JsonArray(player.inventoryIds.map { JsonPrimitive(it) }))
            put("active_quests", JsonArray(player.questStages.keys.map { JsonPrimitive(it) }))
            put("_quest_stages", JsonObject(player.questStages.mapValues { JsonPrimitive(it.value) }))
        }
        return pretty.encodeToString(JsonObject.serializer(), merged)
    }

    private fun readAsset(context: Context, path: String): String {
        return try {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw IllegalStateException("Asset not found: $path", e)
        }
    }
}
