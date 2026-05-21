package com.pimenov.feature.world

import android.content.Context
import com.pimenov.feature.game.PlayerState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * Loads the pilot Tavern World Bible and the DM system prompt template, then
 * assembles a single system prompt string for the LLM.
 *
 * The static narrative grounding (backstory, motivation hooks) comes from
 * `player.json`, but the mutable fields — hp, gold, inventory, quest stages —
 * are overridden with the live [PlayerState]. A `[ЖУРНАЛ]` block carries the
 * compact recap so the DM keeps continuity across sessions.
 */
object TavernWorldBibleLoader {
    private const val PROMPT_TEMPLATE = "prompts/dm_system_v1.txt"
    private const val WORLD_DIR = "world/tavern"
    private const val PLAYER_NAME_PLACEHOLDER = "{{player_name}}"

    private val json = Json { ignoreUnknownKeys = true }
    private val pretty = Json { prettyPrint = true }

    fun buildSystemPrompt(context: Context, player: PlayerState, journal: String): String {
        val template = readAsset(context, PROMPT_TEMPLATE)
        val worldBlock = buildWorldBlock(context, player, journal)
        return template.replace(PLAYER_NAME_PLACEHOLDER, player.name) + "\n\n" + worldBlock
    }

    private fun buildWorldBlock(context: Context, player: PlayerState, journal: String): String {
        val playerBlock = renderPlayerBlock(context, player)
        val location = readAsset(context, "$WORLD_DIR/locations.json")
        val npcs = readAsset(context, "$WORLD_DIR/npcs.json")
        val items = readAsset(context, "$WORLD_DIR/items.json")
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
            appendLine("[LOCATION]")
            appendLine(location)
            appendLine()
            appendLine("[NPCS]")
            appendLine(npcs)
            appendLine()
            appendLine("[ITEMS]")
            appendLine(items)
            appendLine()
            appendLine("[QUESTS]")
            append(quests)
        }
    }

    /** Static player.json with the mutable fields overwritten from [player]. */
    private fun renderPlayerBlock(context: Context, player: PlayerState): String {
        val base = json.parseToJsonElement(readAsset(context, "$WORLD_DIR/player.json")).jsonObject
        val merged = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            put("name", player.name)
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
