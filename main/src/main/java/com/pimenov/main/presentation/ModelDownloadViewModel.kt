package com.pimenov.main.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimenov.feature.api.ModelDownloader
import com.pimenov.feature.api.ModelVariant
import com.pimenov.feature.download.DownloadController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelDownloadState(
    val selected: ModelVariant = ModelVariant.LITE,
    val progress: Float = 0f,
    val inProgress: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null
)

class ModelDownloadViewModel(
    private val downloader: ModelDownloader,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(
        ModelDownloadState(completed = downloader.isModelPresent())
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            DownloadController.state.collect { ctrl ->
                // Skip the no-op initial controller state to keep VM's own completed flag.
                if (ctrl.variant == null && !ctrl.running && !ctrl.completed && ctrl.error == null) {
                    return@collect
                }
                _state.update {
                    it.copy(
                        selected = ctrl.variant ?: it.selected,
                        progress = ctrl.progress,
                        inProgress = ctrl.running,
                        completed = ctrl.completed || downloader.isModelPresent(),
                        error = ctrl.error
                    )
                }
            }
        }
    }

    fun selectVariant(variant: ModelVariant) {
        if (_state.value.inProgress) return
        _state.update { it.copy(selected = variant) }
    }

    fun start() {
        if (_state.value.inProgress) return
        DownloadController.clearError()
        DownloadController.start(appContext, _state.value.selected)
    }
}
