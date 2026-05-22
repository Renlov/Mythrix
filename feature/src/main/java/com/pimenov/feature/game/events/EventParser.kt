package com.pimenov.feature.game.events

import kotlinx.serialization.json.Json

/**
 * Extracts and parses the `[EVENTS]` JSON object from a raw DM reply.
 * Finds the first balanced `{…}` after the `[EVENTS]` marker. Returns null
 * when the marker is missing or the JSON is malformed — a bad block must
 * never crash a turn, it simply applies nothing.
 */
object EventParser {

    private val json = Json { ignoreUnknownKeys = true }

    private const val MARKER = "[EVENTS]"

    fun parse(raw: String): EventsBlock? {
        val markerAt = raw.indexOf(MARKER)
        if (markerAt < 0) return null
        val after = raw.substring(markerAt + MARKER.length)
        val start = after.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var end = -1
        for (k in start until after.length) {
            when (after[k]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = k
                        break
                    }
                }
            }
        }
        if (end < 0) return null

        val block = after.substring(start, end + 1)
        return runCatching { json.decodeFromString<EventsBlock>(block) }.getOrNull()
    }
}
