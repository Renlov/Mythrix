package com.pimenov.feature.game

import android.content.Context
import com.pimenov.feature.game.combat.CombatEngine
import com.pimenov.feature.game.combat.CombatRound
import com.pimenov.feature.game.combat.CombatSession
import com.pimenov.feature.game.combat.DiceRoller
import com.pimenov.feature.game.combat.SkillCheckResult
import com.pimenov.feature.game.events.EventApplier
import com.pimenov.feature.game.events.EventsBlock
import com.pimenov.feature.game.events.Intent
import com.pimenov.feature.game.player.PlayerState
import com.pimenov.feature.game.world.Combatant
import com.pimenov.feature.game.world.ItemDef
import com.pimenov.feature.game.world.StoryContentSource
import com.pimenov.feature.game.world.StoryRules
import com.pimenov.feature.game.world.WorldCatalog
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
 * On first launch state is seeded from the story's `player.json` (via
 * [StoryContentSource]). After that it is restored from a per-story save file
 * (`save_<storyId>.json`) in the app's private storage. Each
 * applied turn persists hp, gold, inventory, quest stages and a running
 * [journal] — a short, AI-readable recap of what happened in the tavern that
 * is fed back into the system prompt so the DM keeps continuity across
 * sessions without re-sending the whole transcript.
 */
class GameStateRepository(
    context: Context,
    private val catalog: WorldCatalog,
    private val source: StoryContentSource,
    private val rules: StoryRules,
    private val defaultName: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // Per-story save: each story keeps its own progress, so switching stories
    // (incl. a downloaded one) never overwrites another's save. [FIREBASE]
    private val saveFile = File(context.filesDir, "save_${source.storyId}.json")

    private val initial: SaveData = restore() ?: seedFromAssets()

    private val _state = MutableStateFlow(initial.player)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var _journal: String = initial.journal

    private val combatEngine = CombatEngine()
    private val dice = DiceRoller()
    private var combat: CombatSession? = null

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

    /**
     * Toggles whether an owned item is equipped. One item per type slot —
     * equipping a weapon unequips any other weapon, and so on. No-op for
     * items not held.
     */
    fun toggleEquip(itemId: String) {
        val cur = _state.value
        if (itemId !in cur.inventoryIds) return
        val equipped = cur.equippedIds.toMutableSet()
        if (itemId in equipped) {
            equipped.remove(itemId)
        } else {
            val type = catalog.byId(itemId)?.type
            if (type != null) {
                equipped.removeAll { other -> catalog.byId(other)?.type == type }
            }
            equipped.add(itemId)
        }
        _state.value = cur.copy(equippedIds = equipped)
        persist()
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

    /**
     * Resolves one attack exchange against [enemyId]: the player swings with
     * the equipped weapon, then — if the enemy lives — it strikes back. HP of
     * both sides and the journal are updated. Returns null if the id is not a
     * known enemy (e.g. the DM aimed an attack at an NPC).
     */
    /** Player attacks an enemy with the equipped weapon; allies may chip in. */
    fun playerAttack(enemyId: String): CombatRound? {
        val target = catalog.combatant(enemyId) ?: return null
        val session = sessionFor(enemyId, target)
        val player = _state.value
        val atk = combatEngine.resolveAttack(
            attackBonus = PLAYER_ATTACK_BONUS,
            damageExpr = equippedWeaponDamage(player),
            targetAc = target.ac,
            targetHp = session.enemyHp,
        )
        val allies = allyDamage(enemyId)
        val enemyHp = (atk.targetHpAfter - allies).coerceAtLeast(0)
        return finishRound(
            target = target, session = session, player = player, action = "attack",
            playerHit = atk.hit, playerRoll = atk.attackRoll, playerDamage = atk.damage,
            healed = 0, allies = allies, enemyHp = enemyHp,
        )
    }

    /**
     * Player uses healing supplies. In a fight this spends the turn (allies
     * still strike, the enemy retaliates); out of combat it just heals.
     * Returns null if there are no supplies or charges left.
     */
    fun playerHeal(): CombatRound? {
        val player = _state.value
        val healItem = findHealItem(player) ?: return null
        val remaining = player.itemUses[healItem.id] ?: (healItem.uses ?: 1)
        if (remaining <= 0) return null
        val rolled = dice.roll(healItem.healDie ?: "d4")
        val newHp = (player.hp + rolled).coerceAtMost(player.maxHp)
        val healed = newHp - player.hp
        val healedPlayer = player.copy(
            hp = newHp,
            itemUses = player.itemUses + (healItem.id to (remaining - 1)),
        )
        _state.value = healedPlayer

        val session = combat
        if (session == null) {
            persist()
            return CombatRound(enemyName = "", action = "heal", healed = healed, playerHpAfter = newHp)
        }
        val target = catalog.combatant(session.enemyId) ?: return null
        val allies = allyDamage(session.enemyId)
        val enemyHp = (session.enemyHp - allies).coerceAtLeast(0)
        return finishRound(
            target = target, session = session, player = healedPlayer, action = "heal",
            playerHit = false, playerRoll = 0, playerDamage = 0,
            healed = healed, allies = allies, enemyHp = enemyHp,
        )
    }

    private fun sessionFor(enemyId: String, target: Combatant): CombatSession =
        combat?.takeIf { it.enemyId == enemyId } ?: CombatSession(enemyId, target.hp, target.hp)

    /** Allies fighting alongside add damage to the boss — only once joined. */
    private fun allyDamage(enemyId: String): Int {
        val ally = rules.allySupport ?: return 0
        if (enemyId != ally.enemyId) return 0
        val joined = (_state.value.questStages[ally.questId] ?: 0) >= ally.minStage
        return if (joined) dice.roll(ally.damageDie) else 0
    }

    private fun phaseOf(target: Combatant, maxHp: Int, hp: Int): String? {
        if (target.id != rules.winCondition?.enemyId || maxHp <= 0) return null
        val frac = hp.toDouble() / maxHp
        return when {
            hp == 0 -> "повержен"
            frac > 0.66 -> "невредим, разъярён"
            frac > 0.33 -> "ранен, рычит"
            else -> "при смерти, в ярости"
        }
    }

    private fun findHealItem(player: PlayerState): ItemDef? =
        player.inventoryIds.firstNotNullOfOrNull { id -> catalog.byId(id)?.takeIf { it.healDie != null } }

    /** Resolves enemy death (victory if it's the dragon) or its retaliation. */
    private fun finishRound(
        target: Combatant,
        session: CombatSession,
        player: PlayerState,
        action: String,
        playerHit: Boolean,
        playerRoll: Int,
        playerDamage: Int,
        healed: Int,
        allies: Int,
        enemyHp: Int,
    ): CombatRound {
        val phase = phaseOf(target, session.enemyMaxHp, enemyHp)
        if (enemyHp == 0) {
            combat = null
            val victory = target.id == rules.winCondition?.enemyId
            _journal = appendJournal(_journal, "Победа в бою: ${target.name} повержен.")
            if (victory) _state.value = _state.value.copy(outcome = "victory")
            persist()
            return CombatRound(
                enemyName = target.name, action = action, playerHit = playerHit, playerRoll = playerRoll,
                playerDamage = playerDamage, healed = healed, allyDamage = allies,
                enemyHpAfter = 0, enemyKilled = true, enemyPhase = phase,
                playerHpAfter = player.hp, victory = victory,
            )
        }
        val enemyAtk = combatEngine.resolveAttack(
            attackBonus = target.attackBonus,
            damageExpr = target.attackDie,
            targetAc = PLAYER_BASE_AC + equippedArmorBonus(player),
            targetHp = player.hp,
        )
        val dead = enemyAtk.killed
        _state.value = player.copy(hp = enemyAtk.targetHpAfter, outcome = if (dead) "death" else player.outcome)
        combat = if (dead) null else session.copy(enemyHp = enemyHp)
        if (dead) _journal = appendJournal(_journal, "Смерть в бою с ${target.name}.")
        persist()
        return CombatRound(
            enemyName = target.name, action = action, playerHit = playerHit, playerRoll = playerRoll,
            playerDamage = playerDamage, healed = healed, allyDamage = allies,
            enemyHpAfter = enemyHp, enemyKilled = false, enemyPhase = phase,
            enemyHit = enemyAtk.hit, enemyDamage = enemyAtk.damage,
            playerHpAfter = enemyAtk.targetHpAfter, playerDead = dead,
        )
    }

    /** Resolves a d20 skill check at the named difficulty (easy/medium/hard). */
    fun playerSkillCheck(difficulty: String?): SkillCheckResult {
        val dc = when (difficulty?.lowercase()) {
            "easy" -> DC_EASY
            "hard" -> DC_HARD
            else -> DC_MEDIUM
        }
        return combatEngine.resolveSkillCheck(PLAYER_SKILL_BONUS, dc)
    }

    private fun equippedWeaponDamage(player: PlayerState): String {
        val weapon = player.equippedIds.firstNotNullOfOrNull { id ->
            catalog.byId(id)?.takeIf { it.type == "weapon" }
        }
        return weapon?.damageDie ?: UNARMED_DAMAGE
    }

    private fun equippedArmorBonus(player: PlayerState): Int =
        player.equippedIds.sumOf { id ->
            catalog.byId(id)?.takeIf { it.type == "armor" }?.armorBonus ?: 0
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
        val obj = json.parseToJsonElement(source.read("player.json")).jsonObject
        val name = obj["name"]?.jsonPrimitive?.contentOrNull() ?: defaultName
        val playerClass = obj["class"]?.jsonPrimitive?.contentOrNull() ?: "wanderer"
        val locationId = obj["location_id"]?.jsonPrimitive?.contentOrNull() ?: "loc_tavern_last_rest"
        val hp = obj["hp"]?.jsonPrimitive?.int ?: 0
        val maxHp = obj["max_hp"]?.jsonPrimitive?.int ?: hp
        val gold = obj["gold"]?.jsonPrimitive?.int ?: 0
        val inventory = obj["inventory"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val quests = obj["active_quests"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val knownNpcs = obj["known_npcs"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet()
        val player = PlayerState(
            name = name,
            playerClass = playerClass,
            locationId = locationId,
            hp = hp,
            maxHp = maxHp,
            gold = gold,
            inventoryIds = inventory,
            questStages = quests.associateWith { 0 },
            knownNpcs = knownNpcs,
        )
        return SaveData(player, journal = "")
    }

    @Serializable
    private data class SaveData(val player: PlayerState, val journal: String = "")

    companion object {
        private const val JOURNAL_MAX = 1200
        private const val PLAYER_ATTACK_BONUS = 2
        private const val PLAYER_BASE_AC = 10
        private const val UNARMED_DAMAGE = "d2"
        private const val PLAYER_SKILL_BONUS = 2
        private const val DC_EASY = 8
        private const val DC_MEDIUM = 12
        private const val DC_HARD = 16
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (isString) content else content.takeIf { it != "null" }
