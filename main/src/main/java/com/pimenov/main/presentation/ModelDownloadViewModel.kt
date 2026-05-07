package com.pimenov.main.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimenov.feature.api.DownloadEvent
import com.pimenov.feature.api.ModelDownloader
import com.pimenov.feature.api.ModelVariant
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

class ModelDownloadViewModel(private val downloader: ModelDownloader) : ViewModel() {
    private val _state = MutableStateFlow(
        ModelDownloadState(completed = downloader.isModelPresent())
    )
    val state = _state.asStateFlow()

    fun selectVariant(variant: ModelVariant) {
        if (_state.value.inProgress) return
        _state.update { it.copy(selected = variant) }
    }

    fun start() {
        if (_state.value.inProgress) return
        val variant = _state.value.selected
        _state.update { it.copy(inProgress = true, error = null, progress = 0f) }
        viewModelScope.launch {
            downloader.download(variant).collect { event ->
                when (event) {
                    is DownloadEvent.Progress ->
                        _state.update { it.copy(progress = event.ratio) }
                    is DownloadEvent.Done ->
                        _state.update { it.copy(inProgress = false, completed = true, progress = 1f) }
                    is DownloadEvent.Failed ->
                        _state.update {
                            it.copy(
                                inProgress = false,
                                error = event.error.message ?: "unknown"
                            )
                        }
                }
            }
        }
    }
}
