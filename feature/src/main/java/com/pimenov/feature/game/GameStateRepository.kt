package com.pimenov.feature.game

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Single source of truth for the live player state during the pilot.
 *
 * On first launch state is seeded from `world/tavern/player.json`. After that
 * it is restored from a compact save file in the app's private storage. Each
 * applied turn persists hp, gold, inventory, quest stages and a running
 * [journal] — a short, AI-readable recap of what happened in the tavern that
 * is fed back into the system prompt so the DM keeps continuity across
 * sessions without re-sending the whole transcript.
 */
class GameStateRepository(
    private val context: Context,
    private val catalog: WorldCatalog,
    private val defaultName: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val saveFile = File(context.filesDir, SAVE_FILE)

    private val initial: SaveData = restore() ?: seedFromAssets()

    private val _state = MutableStateFlow(initial.player)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var _journal: String = initial.journal

    fun journal(): String = _journal

    /** Applies a parsed DM events block: updates state, extends the journal, persists. */
    fun apply(events: EventsBlock) {
        _state.value = EventApplier.apply(_state.value, events, catalog)
        events.sceneSummary
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { _journal = appendJournal(_journal, it) }
        persist()
    }

    /**
     * Deterministic purchase, bypassing the LLM. Reuses the same validated
     * applier path; records the buy in the journal so the DM stays in the loop.
     * Returns true when the item was actually bought (enough gold, not owned).
     */
    /**
     * Deterministic quest advance — a safety net for when the DM forgets to
     * emit `quest_advance`. No-op if the quest is already at/past [stage].
     */
    fun advanceQuest(questId: String, stage: Int) {
        val before = _state.value
        val events = EventsBlock(
            intents = listOf(Intent(type = "quest_advance", questId = questId, newStage = stage)),
        )
        val after = EventApplier.apply(before, events, catalog)
        if (after != before) {
            _state.value = after
            persist()
        }
    }

    fun buy(itemId: String): Boolean {
        val before = _state.value
        val events = EventsBlock(
            intents = listOf(Intent(type = "buy", buyer = "player_main", itemId = itemId)),
        )
        val after = EventApplier.apply(before, events, catalog)
        if (after == before) return false
        _state.value = after
        catalog.byId(itemId)?.let { _journal = appendJournal(_journal, "Куплено у трактирщика: ${it.name}.") }
        persist()
        return true
    }

    private fun appendJournal(current: String, entry: String): String {
        val combined = if (current.isBlank()) entry else "$current $entry"
        // Cap so the prompt stays small; keep the most recent context.
        return if (combined.length <= JOURNAL_MAX) combined
        else combined.takeLast(JOURNAL_MAX).substringAfter(' ', combined.takeLast(JOURNAL_MAX))
    }

    private fun persist() {
        runCatching {
            saveFile.writeText(
                json.encodeToString(SaveData.serializer(), SaveData(_state.value, _journal)),
            )
        }
    }

    private fun restore(): SaveData? {
        if (!saveFile.exists()) return null
        return runCatching {
            json.decodeFromString(SaveData.serializer(), saveFile.readText())
        }.getOrNull()
    }

    private fun seedFromAssets(): SaveData {
        val obj = json.parseToJsonElement(readAsset(PLAYER_PATH)).jsonObject
        val name = obj["name"]?.jsonPrimitive?.contentOrNull() ?: defaultName
        val playerClass = obj["class"]?.jsonPrimitive?.contentOrNull() ?: "wanderer"
        val hp = obj["hp"]?.jsonPrimitive?.int ?: 0
        val maxHp = obj["max_hp"]?.jsonPrimitive?.int ?: hp
        val gold = obj["gold"]?.jsonPrimitive?.int ?: 0
        val inventory = obj["inventory"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val quests = obj["active_quests"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val player = PlayerState(
            name = name,
            playerClass = playerClass,
            hp = hp,
            maxHp = maxHp,
            gold = gold,
            inventoryIds = inventory,
            questStages = quests.associateWith { 0 },
        )
        return SaveData(player, journal = "")
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Serializable
    private data class SaveData(val player: PlayerState, val journal: String = "")

    companion object {
        private const val SAVE_FILE = "save_tavern.json"
        private const val PLAYER_PATH = "world/tavern/player.json"
        private const val JOURNAL_MAX = 1200
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (isString) content else content.takeIf { it != "null" }
