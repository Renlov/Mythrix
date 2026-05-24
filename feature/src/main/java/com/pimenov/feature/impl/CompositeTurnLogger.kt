package com.pimenov.feature.impl

import com.pimenov.feature.api.TurnLog
import com.pimenov.feature.api.TurnLogger

/** Fans a [TurnLog] out to several loggers; one failing never blocks the rest. */
class CompositeTurnLogger(private val delegates: List<TurnLogger>) : TurnLogger {
    override fun log(entry: TurnLog) {
        delegates.forEach { runCatching { it.log(entry) } }
    }
}
