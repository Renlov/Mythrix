package com.pimenov.feature.impl

import android.content.Context
import android.util.Log
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
 * Loads a `.task` model file (Qwen / Gemma) and streams tokens.
 *
 * If the native lib or model is missing/broken — falls back to [fallback] engine
 * so the app never crashes.
 */
class MediaPipeDmEngine(
    private val context: Context,
    private val modelFile: File,
    private val fallback: LlmEngine
) : LlmEngine {

    override val id: String = "mediapipe"

    private val cached = AtomicReference<LlmInference?>(null)

    override suspend fun isReady(): Boolean = modelFile.exists() && obtain() != null

    private fun obtain(): LlmInference? {
        cached.get()?.let { return it }
        if (!modelFile.exists() || modelFile.length() < 1_000_000) {
            Log.w(TAG, "model file missing or too small: ${modelFile.absolutePath}")
            return null
        }
        // Try GPU first — Qwen 1.5B on CPU is too slow for interactive UX.
        // Fall back to CPU if GPU init fails on this device.
        val backends = listOf(
            LlmInference.Backend.GPU to "GPU",
            LlmInference.Backend.CPU to "CPU"
        )
        val created = backends.firstNotNullOfOrNull { (backend, name) ->
            runCatching {
                val opts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setMaxTopK(MAX_TOP_K)
                    .setPreferredBackend(backend)
                    .build()
                LlmInference.createFromOptions(context, opts).also {
                    Log.i(TAG, "LlmInference created on $name backend, maxTokens=$MAX_TOKENS")
                }
            }.onFailure { Log.w(TAG, "backend $name init failed: ${it.message}") }
                .getOrNull()
        } ?: run {
            Log.e(TAG, "no backend could load the model")
            return null
        }
        return if (cached.compareAndSet(null, created)) {
            created
        } else {
            runCatching { created.close() }
            cached.get()
        }
    }

    override fun generate(prompt: String, history: List<LlmMessage>): Flow<String> {
        val engine = obtain() ?: run {
            Log.w(TAG, "engine unavailable, using scripted fallback")
            return fallback.generate(prompt, history)
        }
        val fullPrompt = PromptBuilder.build(PromptBuilder.DM_SYSTEM_RU, history, prompt)
        Log.d(TAG, "prompt chars=${fullPrompt.length}, history=${history.size}")

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
                Log.e(TAG, "session create failed", err)
                trySend("[ошибка движка: ${err.message ?: err.javaClass.simpleName}]")
                close()
                return@callbackFlow
            }

            // Buffer streamed tokens so we can cut the moment the model
            // tries to hallucinate a next turn ([PLAYER], "Игрок:", etc.).
            val buffer = StringBuilder()
            var emittedLen = 0
            var stopped = false

            fun safeEmitPrefix(): Int {
                val firstStop = PromptBuilder.STOP_MARKERS
                    .map { buffer.indexOf(it) }
                    .filter { it >= 0 }
                    .minOrNull()
                if (firstStop != null) return firstStop
                val tailGuard = PromptBuilder.STOP_MARKERS.maxOf { it.length }
                return (buffer.length - tailGuard).coerceAtLeast(0)
            }

            val handle = runCatching {
                session.addQueryChunk(fullPrompt)
                session.generateResponseAsync { partial, done ->
                    if (stopped) return@generateResponseAsync
                    if (partial.isNotEmpty()) {
                        buffer.append(PromptBuilder.decodeEscapes(partial))
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
                        val safeEnd = safeEmitPrefix()
                        if (safeEnd > emittedLen) {
                            trySend(buffer.substring(emittedLen, safeEnd))
                            emittedLen = safeEnd
                        }
                    }
                    if (done) {
                        if (buffer.length > emittedLen) {
                            trySend(buffer.substring(emittedLen, buffer.length))
                        }
                        Log.d(TAG, "generation done, totalChars=${buffer.length}")
                        if (buffer.isEmpty()) {
                            trySend("[модель вернула пустой ответ — попробуй переформулировать]")
                        }
                        close()
                    }
                }
            }
            handle.exceptionOrNull()?.let { err ->
                Log.e(TAG, "generate call failed", err)
                trySend("[ошибка генерации: ${err.message ?: err.javaClass.simpleName}]")
                close()
            }

            awaitClose {
                runCatching { session.close() }
            }
        }
    }

    fun release() {
        cached.getAndSet(null)?.let { runCatching { it.close() } }
    }

    private companion object {
        const val TAG = "MediaPipeDmEngine"
        const val MAX_TOKENS = 4096
        const val MAX_TOP_K = 40
        const val TEMPERATURE = 0.8f
    }
}
