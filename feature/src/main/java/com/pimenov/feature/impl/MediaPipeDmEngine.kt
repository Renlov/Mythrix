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
    private val fallback: LlmEngine
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
        val fullPrompt = PromptBuilder.build(PromptBuilder.DM_SYSTEM_RU, history, prompt)
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

            val handle = runCatching {
                session.addQueryChunk(fullPrompt)
                session.generateResponseAsync { partial, done ->
                    if (partial.isNotEmpty()) trySend(partial)
                    if (done) close()
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
