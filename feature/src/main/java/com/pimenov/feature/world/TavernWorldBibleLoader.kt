package com.pimenov.feature.world

import android.content.Context
import java.io.IOException

/**
 * Loads pilot Tavern World Bible (raw JSON text) and the DM system prompt
 * template, then assembles a single system prompt string ready for the LLM.
 *
 * Phase 1: no parsing — JSONs are inlined as-is into the [МИР] block.
 * The model reads JSON natively; we save ourselves a kotlinx-serialization
 * dependency until we need typed updates (Phase 2: event applier).
 */
object TavernWorldBibleLoader {
    private const val PROMPT_TEMPLATE = "prompts/dm_system_v1.txt"
    private const val WORLD_DIR = "world/tavern"
    private const val PLAYER_NAME_PLACEHOLDER = "{{player_name}}"

    fun buildSystemPrompt(context: Context, playerName: String): String {
        val template = readAsset(context, PROMPT_TEMPLATE)
        val worldBlock = buildWorldBlock(context, playerName)
        return template.replace(PLAYER_NAME_PLACEHOLDER, playerName) + "\n\n" + worldBlock
    }

    private fun buildWorldBlock(context: Context, playerName: String): String {
        val player = readAsset(context, "$WORLD_DIR/player.json")
            .replace("\"name\": null", "\"name\": \"$playerName\"")
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
            appendLine(player)
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

    private fun readAsset(context: Context, path: String): String {
        return try {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw IllegalStateException("Asset not found: $path", e)
        }
    }
}
