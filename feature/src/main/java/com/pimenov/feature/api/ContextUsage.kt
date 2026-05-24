package com.pimenov.feature.api

/**
 * Token accounting for the most recent turn, surfaced to the UI so the player
 * can see how full the model's context window is.
 *
 * [used] is the last turn's total tokens (prompt + completion); [remaining] is
 * [window] − [used]. [fraction] is a 0..1 fill ratio for a progress indicator.
 */
data class ContextUsage(
    val used: Int,
    val window: Int,
    val remaining: Int,
) {
    val fraction: Float get() = if (window <= 0) 0f else (used.toFloat() / window).coerceIn(0f, 1f)
}
