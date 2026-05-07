package com.pimenov.feature.download

import android.content.Context
import android.content.Intent
import android.os.Build
import com.pimenov.feature.api.DownloadEvent
import com.pimenov.feature.api.ModelVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton bridging the foreground download service and ViewModels.
 *
 * The service writes events here. ViewModels collect them as a StateFlow.
 * Survives Activity destruction so download keeps running when the app is closed.
 */
object DownloadController {

    data class State(
        val variant: ModelVariant? = null,
        val progress: Float = 0f,
        val running: Boolean = false,
        val completed: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(context: Context, variant: ModelVariant) {
        if (_state.value.running) return
        _state.value = State(variant = variant, running = true)
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            putExtra(ModelDownloadService.EXTRA_VARIANT, variant.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /** Called by the service to push events. */
    internal fun onEvent(variant: ModelVariant, event: DownloadEvent) {
        _state.value = when (event) {
            is DownloadEvent.Progress -> _state.value.copy(
                variant = variant,
                progress = event.ratio,
                running = true,
                completed = false,
                error = null
            )
            is DownloadEvent.Done -> _state.value.copy(
                variant = variant,
                progress = 1f,
                running = false,
                completed = true,
                error = null
            )
            is DownloadEvent.Failed -> _state.value.copy(
                variant = variant,
                running = false,
                completed = false,
                error = event.error.message ?: "unknown"
            )
        }
    }

    /** Reset error so user can retry. */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
