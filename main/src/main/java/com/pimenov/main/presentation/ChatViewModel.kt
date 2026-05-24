package com.pimenov.main.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimenov.feature.api.ContextUsage
import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.LlmMessage
import com.pimenov.feature.game.GameStateRepository
import com.pimenov.feature.impl.ContextMeter
import com.pimenov.feature.game.combat.CombatHud
import com.pimenov.feature.game.combat.EnemyBlow
import com.pimenov.feature.game.combat.HealAction
import com.pimenov.feature.game.combat.PlayerBlow
import com.pimenov.feature.game.combat.SkillCheckResult
import com.pimenov.feature.game.events.EventParser
import com.pimenov.feature.game.world.WorldCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val role: LlmMessage.Role,
    val text: String,
    val isStreaming: Boolean = false,
    /** Hidden from UI but kept in conversation history sent to the model. */
    val hidden: Boolean = false,
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val quest: QuestObjective = TavernPilotQuest.initial,
    val player: PlayerSheet = PlayerSheet(),
    val wares: List<Ware> = emptyList(),
    val ownedItemIds: Set<String> = emptySet(),
    val showShop: Boolean = false,
    /** Latest DM-suggested actions, shown in the action picker beside the input. */
    val currentOptions: List<String> = emptyList(),
    /** Read-only leads the player has uncovered, shown in the same menu. */
    val leads: List<String> = emptyList(),
    /** Pilot ending: "victory" | "death" | null. Non-null shows the end overlay. */
    val outcome: String? = null,
    /** Latest context-window usage from the model, null until the first reply. */
    val context: ContextUsage? = null,
    /** Live enemy HP while a fight is active; null outside combat. */
    val combat: CombatHud? = null,
) {
    val visibleMessages: List<ChatMessage> get() = messages.filterNot { it.hidden }
}

class ChatViewModel(
    private val engine: LlmEngine,
    private val game: GameStateRepository,
    private val catalog: WorldCatalog,
    private val contextMeter: ContextMeter,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var nextId = 0L
    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            game.state.collect { ps ->
                val hasWeapon = ps.inventoryIds.any { id ->
                    id != STARTER_WEAPON && catalog.byId(id)?.type == "weapon"
                }
                // Wares track the merchant of the CURRENT location, so the shop
                // reflects where the player is now (empty once they leave the tavern).
                val wares = catalog.merchantAt(ps.locationId)?.let { merchant ->
                    catalog.waresOf(merchant.id)
                        .map { Ware(id = it.id, name = it.name, price = it.price, type = it.type) }
                }.orEmpty()
                _state.update {
                    it.copy(
                        player = ps.toSheet(catalog),
                        quest = TavernPilotQuest.objectiveFor(ps.questStages, hasWeapon, ps.locationId, ps.outcome),
                        leads = TavernPilotQuest.leadsFor(ps.questStages),
                        ownedItemIds = ps.inventoryIds.toSet(),
                        outcome = ps.outcome,
                        wares = wares,
                        // A shop with no wares can't stay open.
                        showShop = it.showShop && wares.isNotEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch {
            contextMeter.usage.collect { usage ->
                _state.update { it.copy(context = usage) }
            }
        }
        viewModelScope.launch {
            game.combatHud.collect { hud ->
                _state.update { it.copy(combat = hud) }
            }
        }
        startSession(OPENING_ACTION)
    }

    /** Deterministic purchase from the shop menu — no LLM round-trip. */
    fun buy(itemId: String) {
        game.buy(itemId)
    }

    fun toggleEquip(itemId: String) {
        game.toggleEquip(itemId)
    }

    fun openShop() = _state.update { it.copy(showShop = true) }

    fun dismissShop() = _state.update { it.copy(showShop = false) }

    private fun startSession(opening: String) {
        val kickoff = ChatMessage(nextId++, LlmMessage.Role.USER, opening, hidden = true)
        val dmMsg = ChatMessage(nextId++, LlmMessage.Role.ASSISTANT, "", isStreaming = true)
        _state.update {
            it.copy(messages = it.messages + kickoff + dmMsg, isSending = true)
        }
        streamAssistant(prompt = opening, history = emptyList(), targetId = dmMsg.id, playerTurn = false)
    }

    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return

        // In an active fight the player's line IS the combat action — skip the
        // generic DM turn and resolve the two-message round directly.
        if (game.isInCombat()) {
            val enemyId = game.currentEnemyId()
            if (enemyId != null) {
                val userMsg = ChatMessage(nextId++, LlmMessage.Role.USER, trimmed)
                _state.update {
                    it.copy(messages = it.messages + userMsg, isSending = true, error = null, currentOptions = emptyList())
                }
                streamJob = viewModelScope.launch {
                    runCombatRound(enemyId, heal = wantsHeal(trimmed))
                }
                return
            }
        }

        val userMsg = ChatMessage(nextId++, LlmMessage.Role.USER, trimmed)
        val dmMsg = ChatMessage(nextId++, LlmMessage.Role.ASSISTANT, "", isStreaming = true)

        _state.update {
            it.copy(
                messages = it.messages + userMsg + dmMsg,
                isSending = true,
                error = null,
                // Clear stale actions until the next DM reply provides new ones.
                currentOptions = emptyList(),
            )
        }

        val history = _state.value.messages
            .dropLast(2)
            .map { LlmMessage(it.role, it.text) }

        streamAssistant(prompt = trimmed, history = history, targetId = dmMsg.id, playerTurn = true)
    }

    private fun streamAssistant(
        prompt: String,
        history: List<LlmMessage>,
        targetId: Long,
        playerTurn: Boolean,
    ) {
        streamJob = viewModelScope.launch {
            var raw = streamOnce(prompt, history, targetId, live = true)
            // Validation + one retry: if the [EVENTS] block didn't parse,
            // re-ask once with a format reminder. The retry streams silently —
            // the first reply stays on screen and the bubble is swapped once at
            // the end, so the player never sees the answer erased and retyped.
            if (EventParser.parse(raw) == null) {
                raw = streamOnce(prompt + RETRY_HINT, history, targetId, live = false)
            }
            // After streaming, apply [EVENTS] to game state, then parse
            // [OPTIONS] and attach to the message.
            val events = EventParser.parse(raw)
            // A skill_check that comes with a location_change gates the move:
            // resolve the check first and drop the move on failure, so a failed
            // climb/jump/persuasion keeps the player where they are.
            val skillIntent = if (playerTurn) events?.intents?.firstOrNull { it.type == "skill_check" } else null
            val skillResult = skillIntent?.let { game.playerSkillCheck(it.difficulty) }
            val gatedOutMove = skillResult?.success == false && events?.locationChange != null
            val toApply = if (gatedOutMove) events?.copy(locationChange = null) else events
            toApply?.let { game.apply(it) }
            if (playerTurn) {
                val narrative = cleanForDisplay(raw)
                maybeAdvanceFindAina(prompt, narrative)
                maybeAdvanceDragon(prompt, narrative)
            }
            // The phase-1 bubble is done streaming.
            _state.update { st ->
                st.copy(messages = st.messages.map { m -> if (m.id == targetId) m.copy(isStreaming = false) else m })
            }

            // Combat: a DM attack intent on the player's turn opens the deferred
            // two-message round (player blow, then enemy reply). Dice resolve only
            // on the player's own turn, never on a phase-2 outcome turn.
            val attackTarget = if (playerTurn) {
                events?.intents?.firstOrNull { it.type == "attack" && it.target != null }?.target
            } else null
            if (attackTarget != null && game.isCombatant(attackTarget)) {
                game.startEncounter(attackTarget)
                _state.update { it.copy(isSending = true, currentOptions = emptyList()) }
                runCombatRound(attackTarget, heal = false)
                return@launch
            }

            // Skill check outcome (phase 2), if any.
            val skillTurnResult = if (playerTurn && skillIntent != null && skillResult != null) {
                buildSkillResult(skillIntent.skill, skillResult, gatedOutMove)
            } else null
            val options = parseOptions(raw)
            _state.update { st ->
                st.copy(
                    isSending = skillTurnResult != null,
                    currentOptions = if (skillTurnResult != null) emptyList() else options,
                )
            }
            if (skillTurnResult != null) {
                runOutcomePhase2(skillTurnResult)
            } else {
                // Just walked into the forest? Spring the tutorial wolf.
                maybeTriggerWolfTutorial(playerTurn)
            }
        }
    }

    /**
     * One combat round in two visible messages: the player's blow (enemy HP
     * lands at the end of that message) and — if the enemy survives — its reply
     * (player HP lands at the end of its message). HP is committed only after
     * each narration finishes streaming, never before.
     */
    private suspend fun runCombatRound(enemyId: String, heal: Boolean) {
        if (heal) {
            val action = game.computePlayerHeal()
            streamCombatTurn(buildHealResult(action))
            action?.takeIf { it.hadCharges }?.let { game.commitPlayerHeal(it) }
        } else {
            val blow = game.computePlayerBlow(enemyId)
            if (blow == null) {
                _state.update { it.copy(isSending = false) }
                return
            }
            val raw1 = streamCombatTurn(buildPlayerBlowResult(blow))
            game.commitPlayerBlow(blow) // enemy HP lands now, at the end of the message
            if (blow.killed) {
                finishCombat(raw1)
                return
            }
        }
        // Enemy retaliation — its own message; player HP lands at the end of it.
        val counter = game.computeEnemyBlow(enemyId)
        if (counter == null) {
            _state.update { it.copy(isSending = false) }
            return
        }
        val raw2 = streamCombatTurn(buildEnemyBlowResult(counter))
        game.commitEnemyBlow(counter)
        finishCombat(raw2)
    }

    /** Streams one combat narration message from a hidden [TURN RESULT]; returns raw text. */
    private suspend fun streamCombatTurn(resultText: String): String {
        val hidden = ChatMessage(nextId++, LlmMessage.Role.USER, resultText, hidden = true)
        val dmMsg = ChatMessage(nextId++, LlmMessage.Role.ASSISTANT, "", isStreaming = true)
        _state.update { it.copy(messages = it.messages + hidden + dmMsg) }
        val history = _state.value.messages.dropLast(2).map { LlmMessage(it.role, it.text) }
        val raw = streamOnce(resultText, history, dmMsg.id, live = true)
        _state.update { st ->
            st.copy(messages = st.messages.map { m -> if (m.id == dmMsg.id) m.copy(isStreaming = false) else m })
        }
        return raw
    }

    /** Closes a round: stops sending and offers the next combat actions. */
    private fun finishCombat(rawLast: String) {
        val options = parseOptions(rawLast)
        _state.update {
            it.copy(
                isSending = false,
                currentOptions = if (game.isInCombat()) options.ifEmpty { COMBAT_FALLBACK_OPTIONS } else options,
            )
        }
    }

    /**
     * Fires the one-shot tutorial fight the first time the player reaches the
     * forest: the wolf lunges (narration only, no damage), then the player's
     * strikes drive the two-message rounds.
     */
    private suspend fun maybeTriggerWolfTutorial(playerTurn: Boolean) {
        if (!playerTurn) return
        val ps = game.state.value
        if (ps.locationId != FOREST_LOC || ps.tutorialWolfTriggered || ps.outcome != null || game.isInCombat()) return
        game.markWolfTutorialTriggered()
        game.startEncounter(WOLF_ID)
        _state.update { it.copy(isSending = true, currentOptions = emptyList()) }
        val raw = streamCombatTurn(WOLF_TUTORIAL_KICKOFF)
        val options = parseOptions(raw)
        _state.update { it.copy(isSending = false, currentOptions = options.ifEmpty { COMBAT_FALLBACK_OPTIONS }) }
    }

    /** Heuristic: does the player's combat line mean "use healing supplies"? */
    private fun wantsHeal(text: String): Boolean =
        text.lowercase().let { t -> HEAL_MARKERS.any { t.contains(it) } }

    /**
     * Streams one DM reply into [targetId], returning the raw text (with tags).
     * When [live] is true the bubble updates token-by-token; when false the
     * stream is collected silently and the bubble is set once at the end (used
     * for the format retry so the visible answer isn't wiped mid-stream).
     */
    private suspend fun streamOnce(
        prompt: String,
        history: List<LlmMessage>,
        targetId: Long,
        live: Boolean,
    ): String {
        val buffer = StringBuilder()
        runCatching {
            engine.generate(prompt, history).collect { delta ->
                buffer.append(delta)
                if (live) {
                    val display = cleanForDisplay(buffer.toString())
                    _state.update { st ->
                        st.copy(messages = st.messages.map { m ->
                            if (m.id == targetId) m.copy(text = display) else m
                        })
                    }
                }
            }
        }.onFailure { e ->
            _state.update { it.copy(error = e.message ?: "Ошибка запроса") }
        }
        if (!live) {
            val display = cleanForDisplay(buffer.toString())
            _state.update { st ->
                st.copy(messages = st.messages.map { m ->
                    if (m.id == targetId) m.copy(text = display) else m
                })
            }
        }
        return buffer.toString()
    }

    /** Feeds a hidden [TURN RESULT] to the DM and streams the outcome narration. */
    private fun runOutcomePhase2(resultText: String) {
        val hidden = ChatMessage(nextId++, LlmMessage.Role.USER, resultText, hidden = true)
        val dmMsg = ChatMessage(nextId++, LlmMessage.Role.ASSISTANT, "", isStreaming = true)
        _state.update { it.copy(messages = it.messages + hidden + dmMsg) }
        val history = _state.value.messages.dropLast(2).map { LlmMessage(it.role, it.text) }
        streamAssistant(prompt = resultText, history = history, targetId = dmMsg.id, playerTurn = false)
    }

    /**
     * Safety net for the "узнать, куда ушла Айна" objective: if the player
     * asked about the sister and the DM's narrative reveals a direction, mark
     * the quest stage advanced even when the DM forgot the `quest_advance`
     * intent. Vague answers (no direction) don't trigger it.
     */
    private fun maybeAdvanceFindAina(playerText: String, narrative: String) {
        val asked = playerText.lowercase().let { it.contains("айн") || it.contains("сестр") }
        if (!asked) return
        val revealed = narrative.lowercase().let { n ->
            DIRECTION_MARKERS.any { n.contains(it) }
        }
        if (revealed) game.advanceQuest(TavernPilotQuest.MAIN_QUEST, 1)
    }

    /**
     * Drives the dragon-line goals. Stage 1 (узнать опасность): the player
     * asks about the north/dragon and the DM confirms the threat. Stage 2
     * (решить идти): the player explicitly offers to go with the warriors —
     * a deliberate statement, so detected on the player's own line.
     */
    private fun maybeAdvanceDragon(playerText: String, narrative: String) {
        val low = playerText.lowercase()
        if (JOIN_MARKERS.any { low.contains(it) }) {
            game.advanceQuest(TavernPilotQuest.DRAGON_QUEST, 2)
            return
        }
        val askedDanger = DANGER_MARKERS.any { low.contains(it) }
        val confirmedDanger = narrative.lowercase().let { n -> DANGER_MARKERS.any { n.contains(it) } }
        if (askedDanger && confirmedDanger) game.advanceQuest(TavernPilotQuest.DRAGON_QUEST, 1)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        private const val STARTER_WEAPON = "item_traveler_dagger"

        /** Tutorial wolf encounter: the location it fires in and the enemy id. */
        private const val FOREST_LOC = "loc_north_forest"
        private const val WOLF_ID = "enemy_forest_wolf"

        /** Words in a combat line that mean "use healing supplies". */
        private val HEAL_MARKERS = listOf("леч", "перевяз", "припас", "бинт", "зелье", "мазь")

        /** Shown when a fight continues but the DM offered no [OPTIONS]. */
        private val COMBAT_FALLBACK_OPTIONS = listOf("ударить врага", "отступить на шаг", "перевязать раны")

        /** Hidden kickoff that opens the tutorial wolf fight (narration only, no damage). */
        private const val WOLF_TUTORIAL_KICKOFF =
            "[TUTORIAL] Игрок впервые вышел на лесную тропу. Из подлеска ему наперерез " +
                "бросается тощий волк — он голоден и не отступит. Это первый бой и обучение. " +
                "Опиши нападение волка в 2-3 предложениях (волк только бросается навстречу, ещё не ударил). " +
                "Затем одной фразой подскажи: чтобы атаковать, напиши, как и чем ты бьёшь. " +
                "В [OPTIONS] дай 2-3 конкретных варианта удара. НЕ списывай урон и не решай исход боя."

        /** Words in the DM narrative that signal Aina's direction was revealed. */
        private val DIRECTION_MARKERS = listOf(
            "север", "тракт", "на север", "ушла", "дорог", "отрог",
        )

        /** Topic words for the northern threat (dragon, mines, spur). */
        private val DANGER_MARKERS = listOf(
            "дракон", "шахт", "отрог", "логов", "выработк", "налёт", "сожг",
        )

        /** Player phrases that mean "I'll go with the warriors". */
        private val JOIN_MARKERS = listOf(
            "пойду с вами", "иду с вами", "пойду с воин", "иду с воин",
            "присоедин", "возьмите меня", "пойдём вместе", "идти с вами",
            "идём вместе", "с вами на рассвете",
        )

        /** Player's strike facts for the DM to narrate (enemy HP not yet applied). */
        private fun buildPlayerBlowResult(blow: PlayerBlow): String = buildString {
            appendLine("[TURN RESULT]")
            val enemy = blow.enemyName.ifBlank { "враг" }
            if (blow.hit) {
                appendLine("Игрок попал по «$enemy» (бросок ${blow.roll}), урон ${blow.damage}.")
            } else {
                appendLine("Игрок промахнулся по «$enemy» (бросок ${blow.roll}).")
            }
            if (blow.allyDamage > 0) {
                appendLine("Воины бьются рядом и наносят «$enemy» урон ${blow.allyDamage}.")
            }
            blow.phase?.let { appendLine("Состояние «$enemy»: $it.") }
            when {
                blow.victory -> {
                    appendLine("«$enemy» повержен. ПОБЕДА — это финал.")
                    append("Опиши гибель «$enemy» и победу коротко, без цифр, с весом. Это конец пилота.")
                }
                blow.killed -> {
                    appendLine("«$enemy» убит.")
                    append("Опиши, как «$enemy» падает, коротко, без цифр. Заверши живой деталью.")
                }
                else ->
                    append(
                        "Опиши, как удар игрока ${if (blow.hit) "достаёт" else "проходит мимо"} «$enemy», " +
                            "коротко и без цифр. НЕ описывай ответный удар врага — он будет следующим ходом.",
                    )
            }
        }

        /** Enemy's retaliation facts for the DM to narrate (player HP not yet applied). */
        private fun buildEnemyBlowResult(blow: EnemyBlow): String = buildString {
            appendLine("[TURN RESULT]")
            val enemy = blow.enemyName.ifBlank { "враг" }
            if (blow.hit) {
                appendLine("«$enemy» бьёт в ответ и попадает (бросок ${blow.roll}), урон ${blow.damage}.")
            } else {
                appendLine("«$enemy» бьёт в ответ и промахивается (бросок ${blow.roll}).")
            }
            if (blow.dead) {
                appendLine("Игрок погибает. КОНЕЦ.")
                append("Опиши смерть игрока коротко и без цифр. Это конец.")
            } else {
                append(
                    "Опиши ответный удар «$enemy» коротко, без цифр. Бой продолжается — " +
                        "в [OPTIONS] предложи конкретные варианты следующего удара.",
                )
            }
        }

        /** Heal facts for the DM to narrate (HP not yet applied). [action] null = no supplies at all. */
        private fun buildHealResult(action: HealAction?): String = buildString {
            appendLine("[TURN RESULT]")
            if (action == null || !action.hadCharges) {
                appendLine("Игрок тянется к лечебным припасам, но их нет.")
            } else {
                appendLine("Игрок пускает в ход лечебные припасы и восстанавливает силы (+${action.healed}).")
            }
            append("Опиши это коротко, без цифр. Следом враг бьёт в ответ.")
        }

        /** Hidden facts of a skill check, fed to the DM for phase-2 narration. */
        private fun buildSkillResult(
            skill: String?,
            result: SkillCheckResult,
            gatedOutMove: Boolean,
        ): String = buildString {
            appendLine("[TURN RESULT]")
            val label = skill?.takeIf { it.isNotBlank() }?.let { "Проверка ($it)" } ?: "Проверка"
            if (result.success) {
                appendLine("$label — успех (бросок ${result.roll}).")
                append("Опиши коротко, как игроку удалось, без цифр. Заверши живой деталью.")
            } else {
                appendLine("$label — провал (бросок ${result.roll}).")
                if (gatedOutMove) {
                    appendLine("Игрок НЕ перешёл дальше — остаётся на месте.")
                }
                append("Опиши неудачу и её осязаемое последствие коротко, без цифр. Дай игроку выбор, что делать дальше, в [OPTIONS].")
            }
        }

        /** Appended on a format-retry to nudge the model back to the schema. */
        private const val RETRY_HINT =
            "\n\n(Система: твой прошлый ответ был без валидного блока [EVENTS]. " +
                "Ответь строго по формату: [NARRATIVE], затем [OPTIONS], затем [EVENTS] с валидным JSON.)"

        private const val OPENING_ACTION =
            "Я ищу свою младшую сестру Айну — полгода назад она ушла по этой дороге. " +
                "Захожу в таверну на исходе сил, надеюсь расспросить и переночевать. " +
                "Опиши коротко, что я вижу."

        /**
         * Strip DM service tags so only narrative prose reaches the UI.
         * Cuts at the first of `[OPTIONS]` or `[EVENTS]` markers — whichever
         * appears earlier. Removes the leading `[NARRATIVE]` header.
         */
        private fun cleanForDisplay(raw: String): String {
            val cutAt = listOf("[OPTIONS]", "[EVENTS]")
                .map { raw.indexOf(it) }
                .filter { it >= 0 }
                .minOrNull() ?: raw.length
            return raw.substring(0, cutAt)
                .removePrefix("[NARRATIVE]")
                .trim()
        }

        /**
         * Parses the `[OPTIONS]` block — lines starting with `-` until the
         * next `[` marker. Returns up to 3 trimmed options or an empty list.
         */
        private fun parseOptions(raw: String): List<String> {
            val start = raw.indexOf("[OPTIONS]")
            if (start < 0) return emptyList()
            val after = raw.substring(start + "[OPTIONS]".length)
            val end = after.indexOf("[").let { if (it >= 0) it else after.length }
            val block = after.substring(0, end)
            return block.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("-") }
                .map { it.removePrefix("-").trim() }
                .filter { it.isNotEmpty() }
                .take(3)
                .toList()
        }
    }
}
