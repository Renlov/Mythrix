package com.pimenov.feature.api

/**
 * One DM turn captured for diagnostics: the full request sent to the model, the
 * full reply, and the token accounting so we can see how close a session is to
 * the model's context window (the player can otherwise silently "hit the limit").
 *
 * [remainingContext] = [contextWindow] − [totalTokens]: a rough budget of how
 * much of the window is still free after this turn. Null when the model didn't
 * report usage.
 */
data class TurnLog(
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val response: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val contextWindow: Int,
    val remainingContext: Int?,
)

/** Sink for [TurnLog]s. Implementations must never throw — logging is best-effort. */
interface TurnLogger {
    fun log(entry: TurnLog)

    /** Drops everything. Used when no logging backend is configured. */
    object NoOp : TurnLogger {
        override fun log(entry: TurnLog) = Unit
    }
}
