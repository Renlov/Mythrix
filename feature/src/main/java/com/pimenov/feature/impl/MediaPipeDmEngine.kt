package com.pimenov.feature.impl

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.LlmMessage
import com.pimenov.feature.api.PromptBuilder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device LLM via Google MediaPipe Tasks GenAI.
 * Loads a `.task` model file (Gemma / Gemma 3 / Phi etc.) and streams tokens.
 *
 * If the native lib or model is missing/broken — falls back to [fallback] engine
 * so the app never crashes.
 */
class MediaPipeDmEngine(
    private val context: Context,
    private val modelFile: File,
    private val fallback: LlmEngine,
    /**
     * Returns the freshly-assembled system prompt for the next turn.
     * Re-invoked per generate() so that World Bible mutations (Phase 2)
     * propagate without engine restart. May be null to fall back to the
     * legacy [PromptBuilder.DM_SYSTEM_RU].
     */
    private val systemPromptProvider: () -> String? = { null }
) : LlmEngine {

    override val id: String = "mediapipe"

    private val cached = AtomicReference<LlmInference?>(null)

    override suspend fun isReady(): Boolean = modelFile.exists() && obtain() != null

    private fun obtain(): LlmInference? {
        cached.get()?.let { return it }
        if (!modelFile.exists() || modelFile.length() < 1_000_000) return null
        val created = runCatching {
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(MAX_TOP_K)
                .build()
            LlmInference.createFromOptions(context, opts)
        }.getOrNull() ?: return null
        return if (cached.compareAndSet(null, created)) {
            created
        } else {
            // Another thread won — discard ours.
            runCatching { created.close() }
            cached.get()
        }
    }

    override fun generate(prompt: String, history: List<LlmMessage>): Flow<String> {
        val engine = obtain() ?: return fallback.generate(prompt, history)
        val systemPrompt = systemPromptProvider() ?: PromptBuilder.DM_SYSTEM_RU
        val fullPrompt = PromptBuilder.build(systemPrompt, history, prompt)
        return callbackFlow {
            val session = runCatching {
                LlmInferenceSession.createFromOptions(
                    engine,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(MAX_TOP_K)
                        .setTemperature(TEMPERATURE)
                        .build()
                )
            }.getOrElse { err ->
                close(err)
                return@callbackFlow
            }

            // Buffer streamed tokens so we can cut the moment the model
            // tries to hallucinate a next turn ([PLAYER], "Игрок:", etc.).
            val buffer = StringBuilder()
            var emittedLen = 0
            var stopped = false

            fun safeEmitPrefix(): Int {
                // Find the earliest stop marker in the buffer.
                val firstStop = PromptBuilder.STOP_MARKERS
                    .map { buffer.indexOf(it) }
                    .filter { it >= 0 }
                    .minOrNull()
                if (firstStop != null) return firstStop
                // Otherwise, hold back tail that could be the start of a marker.
                val tailGuard = PromptBuilder.STOP_MARKERS.maxOf { it.length }
                return (buffer.length - tailGuard).coerceAtLeast(0)
            }

            val handle = runCatching {
                session.addQueryChunk(fullPrompt)
                session.generateResponseAsync { partial, done ->
                    if (stopped) return@generateResponseAsync
                    if (partial.isNotEmpty()) {
                        buffer.append(partial)
                        val safeEnd = safeEmitPrefix()
                        // Check if a stop marker is fully present.
                        val hardStop = PromptBuilder.STOP_MARKERS
                            .map { buffer.indexOf(it) }
                            .filter { it >= 0 }
                            .minOrNull()
                        if (hardStop != null) {
                            if (hardStop > emittedLen) {
                                trySend(buffer.substring(emittedLen, hardStop))
                            }
                            emittedLen = hardStop
                            stopped = true
                            close()
                            return@generateResponseAsync
                        }
                        if (safeEnd > emittedLen) {
                            trySend(buffer.substring(emittedLen, safeEnd))
                            emittedLen = safeEnd
                        }
                    }
                    if (done) {
                        if (buffer.length > emittedLen) {
                            trySend(buffer.substring(emittedLen, buffer.length))
                        }
                        close()
                    }
                }
            }
            handle.exceptionOrNull()?.let { close(it) }

            awaitClose {
                runCatching { session.close() }
            }
        }
    }

    fun release() {
        cached.getAndSet(null)?.let { runCatching { it.close() } }
    }

    private companion object {
        const val MAX_TOKENS = 1024
        const val MAX_TOP_K = 40
        const val TEMPERATURE = 0.8f
    }
}
