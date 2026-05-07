package com.pimenov.feature.impl

import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.LlmMessage
import com.pimenov.feature.api.PromptBuilder
import kotlinx.coroutines.flow.Flow
import java.io.File

class LlamaCppDmEngine(
    private val modelFile: File,
    private val fallback: LlmEngine
) : LlmEngine {

    override val id: String = "llama.cpp"

    private val nativeLoaded: Boolean = runCatching {
        System.loadLibrary("llama")
        true
    }.getOrDefault(false)

    override suspend fun isReady(): Boolean = nativeLoaded && modelFile.exists()

    override fun generate(prompt: String, history: List<LlmMessage>): Flow<String> {
        if (!nativeLoaded || !modelFile.exists()) {
            return fallback.generate(prompt, history)
        }
        // TODO: JNI bridge to llama.cpp inference. For now delegate to fallback.
        @Suppress("UNUSED_VARIABLE") val full = PromptBuilder.build(PromptBuilder.DM_SYSTEM_RU, history, prompt)
        return fallback.generate(prompt, history)
    }
}
