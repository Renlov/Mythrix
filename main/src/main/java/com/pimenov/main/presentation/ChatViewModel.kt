package com.pimenov.main.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.LlmMessage
import com.pimenov.feature.game.EventParser
import com.pimenov.feature.game.GameStateRepository
import com.pimenov.feature.game.WorldCatalog
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
) {
    val visibleMessages: List<ChatMessage> get() = messages.filterNot { it.hidden }
}

class ChatViewModel(
    private val engine: LlmEngine,
    private val game: GameStateRepository,
    private val catalog: WorldCatalog,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var nextId = 0L
    private var streamJob: Job? = null

    init {
        val wares = catalog.waresOf(MERCHANT_ID).map {
            Ware(id = it.id, name = it.name, price = it.price, type = it.type)
        }
        _state.update { it.copy(wares = wares) }

        viewModelScope.launch {
            game.state.collect { ps ->
                val hasWeapon = ps.inventoryIds.any { id ->
                    id != STARTER_WEAPON && catalog.byId(id)?.type == "weapon"
                }
                _state.update {
                    it.copy(
                        player = ps.toSheet(catalog),
                        quest = TavernPilotQuest.objectiveFor(ps.questStages, hasWeapon),
                        leads = TavernPilotQuest.leadsFor(ps.questStages),
                        ownedItemIds = ps.inventoryIds.toSet(),
                    )
                }
            }
        }
        startSession(OPENING_ACTION)
    }

    /** Deterministic purchase from the shop menu — no LLM round-trip. */
    fun buy(itemId: String) {
        game.buy(itemId)
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
            val buffer = StringBuilder()
            runCatching {
                engine.generate(prompt, history).collect { delta ->
                    buffer.append(delta)
                    val display = cleanForDisplay(buffer.toString())
                    _state.update { st ->
                        st.copy(messages = st.messages.map { m ->
                            if (m.id == targetId) m.copy(text = display) else m
                        })
                    }
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Ошибка запроса") }
            }
            // After streaming, apply [EVENTS] to game state, then parse
            // [OPTIONS] and attach to the message.
            val raw = buffer.toString()
            EventParser.parse(raw)?.let { game.apply(it) }
            if (playerTurn) {
                val narrative = cleanForDisplay(raw)
                maybeAdvanceFindAina(prompt, narrative)
                maybeAdvanceDragon(prompt, narrative)
            }
            val options = parseOptions(raw)
            _state.update { st ->
                st.copy(
                    isSending = false,
                    currentOptions = options,
                    messages = st.messages.map { m ->
                        if (m.id == targetId) m.copy(isStreaming = false) else m
                    },
                )
            }
        }
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
        private const val MERCHANT_ID = "npc_innkeeper"
        private const val STARTER_WEAPON = "item_traveler_dagger"

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
