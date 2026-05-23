package com.pimenov.feature.world

import com.pimenov.feature.game.player.PlayerState
import com.pimenov.feature.game.world.StoryContentSource
import com.pimenov.feature.game.world.StoryRules
import com.pimenov.feature.game.world.WorldCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Loads a story's World Bible and assembles the LLM system prompt. Story files
 * come from a [StoryContentSource] (bundled or downloaded), so this is not tied
 * to any single story.
 *
 * Relational RAG (см. docs/ai-architecture/02-context-assembler.md): вместо
 * всей Библии Мира в промпт подаётся только релевантное текущей сцене —
 * текущая локация и её выходы, присутствующие NPC и враги, предметы в этой
 * локации / у этих NPC / у игрока, и активные/доступные квесты. Связи берутся
 * по `id`. Mutable-поля игрока и журнал инжектятся из живого [PlayerState].
 *
 * [promptTemplate] — текст шаблона DM-промпта. [FIREBASE] сейчас он общий
 * (bundled prompts/dm_system_v1.txt), но в дальнейшем может приезжать вместе со
 * скачанным сюжетом, если история захочет свой стиль ведущего.
 */
class WorldBibleLoader(
    private val source: StoryContentSource,
    private val rules: StoryRules,
    private val promptTemplate: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val pretty = Json { prettyPrint = true }

    fun buildSystemPrompt(
        player: PlayerState,
        journal: String,
        catalog: WorldCatalog,
    ): String {
        val worldBlock = buildWorldBlock(player, journal, catalog)
        return promptTemplate.replace(PLAYER_NAME_PLACEHOLDER, player.name) + "\n\n" + worldBlock
    }

    private fun buildWorldBlock(
        player: PlayerState,
        journal: String,
        catalog: WorldCatalog,
    ): String {
        val playerBlock = renderPlayerBlock(player)
        val locationBlock = renderLocationBlock(player, catalog)
        val npcs = renderNpcsForLocation(player, catalog)
        val companions = renderCompanions(player, catalog)
        val enemies = renderEnemiesForLocation(player, catalog)
        val items = renderItemsBlock(player, catalog)
        val quests = renderQuestsBlock(player, catalog)

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
            companions?.let {
                appendLine("[СПУТНИКИ]")
                appendLine(it)
                appendLine()
            }
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
            player.sceneAtmosphere?.let { shift ->
                appendLine("Сдвиг атмосферы в этой сцене: $shift. Поддерживай его.")
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
     * Ids of NPCs the DM should treat as present this scene: those physically in
     * the location plus any active companions travelling with the player.
     */
    private fun presentNpcIds(player: PlayerState, catalog: WorldCatalog): Set<String> =
        catalog.location(player.locationId)?.npcs.orEmpty().toSet() + activeCompanionIds(player)

    /** Companion ids whose join condition is met, else empty. */
    private fun activeCompanionIds(player: PlayerState): Set<String> {
        val c = rules.companions ?: return emptySet()
        val joined = (player.questStages[c.questId] ?: 0) >= c.minStage
        return if (joined) c.npcIds.toSet() else emptySet()
    }

    /**
     * NPCs present in the current scene (location + active companions). Items the
     * player already owns are stripped from each NPC's inventory so the DM never
     * treats a sold item as still belonging to the merchant. Names of NPCs the
     * player has not been introduced to are hidden (см. meet_npc).
     */
    private fun renderNpcsForLocation(player: PlayerState, catalog: WorldCatalog): String {
        val present = presentNpcIds(player, catalog)
        if (present.isEmpty()) return "[]"
        val owned = player.inventoryIds.toSet()
        val all = json.parseToJsonElement(source.read("npcs.json")).jsonArray
        val filtered = JsonArray(
            all.filter { it.jsonObject["id"]?.jsonPrimitive?.content in present }
                .map { gateName(it.jsonObject, player.knownNpcs) }
                .map { stripOwnedFromInventory(it, owned) },
        )
        return pretty.encodeToString(JsonArray.serializer(), filtered)
    }

    /**
     * Hides an NPC's name until the player has met them. `name_reveal: "never"`
     * → always hidden; default `"on_introduction"` → hidden until the id is in
     * [known]; `"always"` → never hidden. When hidden, adds a `_name_hint` so the
     * DM knows to emit `meet_npc` once the NPC introduces itself.
     */
    private fun gateName(npc: JsonObject, known: Set<String>): JsonObject {
        val id = npc["id"]?.jsonPrimitive?.content ?: return npc
        val reveal = npc.stringOrNull("name_reveal") ?: "on_introduction"
        val hidden = when (reveal) {
            "always" -> false
            "never" -> true
            else -> id !in known
        }
        if (!hidden) return npc
        val hint = if (reveal == "never") {
            "Этот NPC своё имя не называет — обращайся по роли, имя не раскрывай."
        } else {
            "Имя ещё не известно игроку — называй по роли. Когда NPC представится, " +
                "верни intent {\"type\":\"meet_npc\",\"npc_id\":\"$id\"}."
        }
        return buildJsonObject {
            npc.forEach { (key, value) ->
                if (key == "name") put("name", JsonNull) else put(key, value)
            }
            put("_name_hint", JsonPrimitive(hint))
        }
    }

    /** Companions travelling with the player; null when none are active. */
    private fun renderCompanions(player: PlayerState, catalog: WorldCatalog): String? {
        val ids = activeCompanionIds(player)
        if (ids.isEmpty()) return null
        val names = ids.mapNotNull { id ->
            catalog.npc(id)?.let { it.name ?: it.roleLabel ?: id }
        }
        if (names.isEmpty()) return null
        return "Идут рядом с игроком как союзники: ${names.joinToString(", ")}. " +
            "Они присутствуют в каждой сцене пути и в бою — держи их в повествовании, " +
            "а не только в логове."
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
     * Relational selection of items relevant to the scene: held by the player,
     * lying in the current location, or owned by an NPC present here. Items the
     * player holds are re-owned to `player_main` so the merchant can't claim a
     * sold item. Items tied to other places/NPCs are left out of the prompt.
     */
    private fun renderItemsBlock(player: PlayerState, catalog: WorldCatalog): String {
        val owned = player.inventoryIds.toSet()
        val present = presentNpcIds(player, catalog)
        val all = json.parseToJsonElement(source.read("items.json")).jsonArray
        val relevant = all.filter { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content
            val owner = obj.stringOrNull("owner_id")
            val itemLocation = obj.stringOrNull("location_id")
            (id != null && id in owned) ||
                owner == "player_main" ||
                (owner != null && owner in present) ||
                itemLocation == player.locationId
        }.map { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content
            if (id != null && id in owned) reown(obj) else obj
        }
        return pretty.encodeToString(JsonArray.serializer(), JsonArray(relevant))
    }

    private fun reown(obj: JsonObject): JsonObject = buildJsonObject {
        obj.forEach { (key, value) ->
            if (key == "owner_id") put("owner_id", JsonPrimitive("player_main")) else put(key, value)
        }
        if (!obj.containsKey("owner_id")) put("owner_id", JsonPrimitive("player_main"))
    }

    /**
     * Relational selection of quests: those still `active`/`available`, or tied
     * to the current location or a present NPC. Resolved/irrelevant quests are
     * left out of the prompt.
     */
    private fun renderQuestsBlock(player: PlayerState, catalog: WorldCatalog): String {
        val present = presentNpcIds(player, catalog)
        val all = json.parseToJsonElement(source.read("quests.json")).jsonArray
        val relevant = all.filter { element ->
            val obj = element.jsonObject
            val status = obj["status"]?.jsonPrimitive?.content
            val relLocations = obj["related_locations"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val relNpcs = obj["related_npcs"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            status == "active" || status == "available" ||
                player.locationId in relLocations ||
                relNpcs.any { it in present }
        }
        return pretty.encodeToString(JsonArray.serializer(), JsonArray(relevant))
    }

    /** Reads a string field, treating JSON `null` and non-string values as null. */
    private fun JsonObject.stringOrNull(key: String): String? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
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
    private fun renderPlayerBlock(player: PlayerState): String {
        val base = json.parseToJsonElement(source.read("player.json")).jsonObject
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

    companion object {
        private const val PLAYER_NAME_PLACEHOLDER = "{{player_name}}"
    }
}
