package com.pimenov.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimenov.core.music.MusicController
import com.pimenov.feature.api.ModelDownloader
import com.pimenov.feature.api.ModelVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val volume: Float = 0.5f,
    val modelPresent: Boolean = false,
    val installedVariant: ModelVariant? = null,
    val language: String = "ru"
)

class SettingsViewModel(
    private val music: MusicController,
    private val downloader: ModelDownloader
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsState(
            modelPresent = downloader.isModelPresent(),
            installedVariant = downloader.installedVariant()
        )
    )
    val state = _state.asStateFlow()

    fun setVolume(value: Float) {
        music.setVolume(value)
        _state.update { it.copy(volume = value) }
    }

    fun deleteModel() {
        viewModelScope.launch {
            downloader.deleteAll()
            _state.update { it.copy(modelPresent = false, installedVariant = null) }
        }
    }

    fun setLanguage(code: String) = _state.update { it.copy(language = code) }
}
