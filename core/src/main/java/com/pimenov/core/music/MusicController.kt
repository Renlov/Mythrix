package com.pimenov.core.music

import android.content.Context
import android.media.MediaPlayer

interface MusicController {
    fun start(track: Track)
    fun stop()
    fun setVolume(value: Float)
    fun setTrack(track: Track)
}

enum class Track { MENU, TAVERN, FOREST, DUNGEON, COMBAT }

class AndroidMusicController(private val context: Context) : MusicController {
    private var player: MediaPlayer? = null
    private var volume: Float = 0.5f
    private var current: Track? = null

    override fun start(track: Track) {
        if (current == track && player?.isPlaying == true) return
        stop()
        current = track
        player = MediaPlayer().apply {
            setVolume(volume, volume)
            isLooping = true
        }
    }

    override fun stop() {
        player?.runCatching { stop(); release() }
        player = null
    }

    override fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        player?.setVolume(volume, volume)
    }

    override fun setTrack(track: Track) {
        if (current != track) start(track)
    }
}
