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
val engine = obtain() ?: return fallback.generate(prompt, history)
        val systemPrompt = systemPromptProvider() ?: PromptBuilder.DM_SYSTEM_RU
        val fullPrompt = PromptBuilder.build(systemPrompt, history, prompt)
        return callbackFlow {
            val session = runCatching {
                val builder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(SAMPLING_TOP_K)
                    .setTemperature(TEMPERATURE)
                // setTopP is available in tasks-genai 0.10.20+, but we guard via
                // reflection in case the runtime jar is older.
                runCatching {
                    val m = builder.javaClass.getMethod("setTopP", Float::class.javaPrimitiveType)
                    m.invoke(builder, SAMPLING_TOP_P)
                }
                LlmInferenceSession.createFromOptions(engine, builder.build())
            }.getOrElse { err ->
                Log.e(TAG, "session create failed", err)
                trySend("[ошибка движка: ${err.message ?: err.javaClass.simpleName}]")
                close()
                return@callbackFlow
            }

            // Buffer streamed tokens so we can cut the moment the model
            // tries to hallucinate a next turn ([PLAYER], "Игрок:", etc.)
            // or falls into a phrase-loop.
            val buffer = StringBuilder()
            var emittedLen = 0
            var stopped = false

            /**
             * Detect a phrase-loop in the generated buffer. Small models often
             * latch onto an N-gram and repeat it verbatim. We look for a tail
             * substring that already appears earlier in the buffer.
             * Returns the cut position, or -1 if no loop detected.
             */
            fun loopCutPosition(): Int {
                val len = buffer.length
                if (len < LOOP_MIN_TAIL * 2) return -1
                val tail = buffer.substring(len - LOOP_MIN_TAIL, len)
                val earlier = buffer.substring(0, len - LOOP_MIN_TAIL).lastIndexOf(tail)
                if (earlier < 0) return -1
                // Loop confirmed — cut after the first occurrence.
                return earlier + LOOP_MIN_TAIL
            }

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
                        val loopAt = loopCutPosition()
                        if (loopAt >= 0) {
                            if (loopAt > emittedLen) {
                                trySend(buffer.substring(emittedLen, loopAt))
                            }
                            emittedLen = loopAt
                            stopped = true
                            Log.w(TAG, "phrase-loop detected, cutting at $loopAt")
                            close()
                            return@generateResponseAsync
                        }
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
        // Sampling tuned to suppress phrase-loops on small Qwen models.
        const val SAMPLING_TOP_K = 40
        const val SAMPLING_TOP_P = 0.9f
        const val TEMPERATURE = 0.7f
        // Anti-loop: if the last 32 chars already appear earlier in the output,
        // we treat it as a repetition lock-in and stop generating.
        const val LOOP_MIN_TAIL = 32
    }
}
