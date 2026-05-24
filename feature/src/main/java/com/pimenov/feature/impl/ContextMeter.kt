package com.pimenov.feature.impl

import com.pimenov.feature.api.ContextUsage
import com.pimenov.feature.api.TurnLog
import com.pimenov.feature.api.TurnLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [TurnLogger] that also keeps the latest token usage observable, so the UI
 * can show how much of the context window is consumed. Updated on every turn
 * that reports usage; turns without usage leave the last value in place.
 */
class ContextMeter : TurnLogger {

    private val _usage = MutableStateFlow<ContextUsage?>(null)
    val usage: StateFlow<ContextUsage?> = _usage.asStateFlow()

    override fun log(entry: TurnLog) {
        val total = entry.totalTokens ?: return
        _usage.value = ContextUsage(
            used = total,
            window = entry.contextWindow,
            remaining = entry.remainingContext ?: (entry.contextWindow - total),
        )
    }
}
