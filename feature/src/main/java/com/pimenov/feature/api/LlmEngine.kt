package com.pimenov.feature.api

import kotlinx.coroutines.flow.Flow

data class LlmMessage(val role: Role, val content: String) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

interface LlmEngine {
    val id: String
    suspend fun isReady(): Boolean
    /** Streams DM response tokens. System prompt is composed inside the engine. */
    fun generate(userPrompt: String, history: List<LlmMessage>): Flow<String>
}
