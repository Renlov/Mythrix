package com.pimenov.main.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.LlmMessage
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
    /** Suggested actions parsed from the DM's [OPTIONS] block. Empty for user. */
    val options: List<String> = emptyList(),
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val quest: QuestObjective = TavernPilotQuest.initial,
) {
    val visibleMessages: List<ChatMessage> get() = messages.filterNot { it.hidden }
}

class ChatViewModel(private val engine: LlmEngine) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var nextId = 0L
    private var streamJob: Job? = null

    init {
        startSession(OPENING_ACTION)
    }

    private fun startSession(opening: String) {
        val kickoff = ChatMessage(nextId++, LlmMessage.Role.USER, opening, hidden = true)
        val dmMsg = ChatMessage(nextId++, LlmMessage.Role.ASSISTANT, "", isStreaming = true)
        _state.update {
            it.copy(messages = it.messages + kickoff + dmMsg, isSending = true)
        }
        streamAssistant(prompt = opening, history = emptyList(), targetId = dmMsg.id)
    }

    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return

        val userMsg = ChatMessage(nextId++, LlmMessage.Role.USER, trimmed)
        val dmMsg = ChatMessage(nextId++, LlmMessage.Role.ASSISTANT, "", isStreaming = true)

        _state.update {
            it.copy(
                // Strip prior options once the player has chosen — keeps the
                // log clean and prevents stale chips from cluttering.
                messages = it.messages.map { m -> m.copy(options = emptyList()) } + userMsg + dmMsg,
                isSending = true,
                error = null,
            )
        }

        val history = _state.value.messages
            .dropLast(2)
            .map { LlmMessage(it.role, it.text) }

        streamAssistant(prompt = trimmed, history = history, targetId = dmMsg.id)
    }

    private fun streamAssistant(prompt: String, history: List<LlmMessage>, targetId: Long) {
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
            // After streaming, parse [OPTIONS] and attach to the message.
            val raw = buffer.toString()
            val options = parseOptions(raw)
            _state.update { st ->
                st.copy(
                    isSending = false,
                    messages = st.messages.map { m ->
                        if (m.id == targetId) m.copy(isStreaming = false, options = options) else m
                    },
                )
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
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
