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
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
) {
    val visibleMessages: List<ChatMessage> get() = messages.filterNot { it.hidden }
}

class ChatViewModel(private val engine: LlmEngine) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var nextId = 0L
    private var streamJob: Job? = null

    init {
        // Auto-kickoff: send a hidden opening action so the DM describes the
        // entry scene without the player having to type "вхожу в таверну".
        startSession(OPENING_ACTION)
    }

    /**
     * Sends a hidden kickoff that the model treats as the player's first action
     * but the UI never shows. The first DM bubble is therefore the scene
     * description, not a reply to something the user typed.
     */
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
                messages = it.messages + userMsg + dmMsg,
                isSending = true,
                error = null,
            )
        }

        // History includes the hidden kickoff so the model keeps the scene
        // continuity; we drop the just-added user + placeholder.
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
                    val snapshot = buffer.toString()
                    _state.update { st ->
                        st.copy(messages = st.messages.map { m ->
                            if (m.id == targetId) m.copy(text = snapshot) else m
                        })
                    }
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Ошибка запроса") }
            }
            _state.update { st ->
                st.copy(
                    isSending = false,
                    messages = st.messages.map { m ->
                        if (m.id == targetId) m.copy(isStreaming = false) else m
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
            "Я открываю дверь и захожу в таверну. Опиши, что я вижу, слышу и чувствую."
    }
}
