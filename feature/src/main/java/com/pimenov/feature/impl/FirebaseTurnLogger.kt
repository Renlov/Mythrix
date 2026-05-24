package com.pimenov.feature.impl

import com.google.firebase.firestore.FirebaseFirestore
import com.pimenov.feature.api.TurnLog
import com.pimenov.feature.api.TurnLogger

/**
 * Writes each [TurnLog] to Firestore (`dm_turns` collection). Resilient by
 * design: if Firebase isn't configured (no `google-services.json` yet), the
 * Firestore handle is null and logging is a no-op — the game never crashes or
 * blocks on logging.
 *
 * [sessionId] groups turns from one app run so a play-through reads as a thread.
 */
class FirebaseTurnLogger(private val sessionId: String) : TurnLogger {

    private val firestore: FirebaseFirestore? =
        runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    override fun log(entry: TurnLog) {
        val db = firestore ?: return
        val doc = hashMapOf(
            "ts" to System.currentTimeMillis(),
            "session" to sessionId,
            "model" to entry.model,
            "system_prompt" to entry.systemPrompt,
            "user_prompt" to entry.userPrompt,
            "response" to entry.response,
            "prompt_tokens" to entry.promptTokens,
            "completion_tokens" to entry.completionTokens,
            "total_tokens" to entry.totalTokens,
            "context_window" to entry.contextWindow,
            "remaining_context" to entry.remainingContext,
        )
        runCatching { db.collection(COLLECTION).add(doc) }
    }

    companion object {
        private const val COLLECTION = "dm_turns"
    }
}
