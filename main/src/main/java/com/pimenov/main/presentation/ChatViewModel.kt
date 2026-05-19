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
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(private val engine: LlmEngine) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var nextId = 0L
    private var streamJob: Job? = null

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

        val history = _state.value.messages
            .dropLast(2) // drop the just-added user + placeholder
            .map { LlmMessage(it.role, it.text) }

        streamJob = viewModelScope.launch {
            val buffer = StringBuilder()
            runCatching {
                engine.generate(trimmed, history).collect { delta ->
                    buffer.append(delta)
                    val snapshot = buffer.toString()
                    _state.update { st ->
                        st.copy(messages = st.messages.map { m ->
                            if (m.id == dmMsg.id) m.copy(text = snapshot) else m
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
                        if (m.id == dmMsg.id) m.copy(isStreaming = false) else m
                    },
                )
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
