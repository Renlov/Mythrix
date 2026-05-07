package com.pimenov.character.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimenov.character.api.CharClass
import com.pimenov.character.api.CharacterSheet
import com.pimenov.character.api.Race
import com.pimenov.character.domain.CreateCharacterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterCreationState(
    val name: String = "",
    val race: Race = Race.HUMAN,
    val charClass: CharClass = CharClass.FIGHTER,
    val created: CharacterSheet? = null,
    val isSaving: Boolean = false
)

class CharacterCreationViewModel(
    private val createCharacter: CreateCharacterUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(CharacterCreationState())
    val state = _state.asStateFlow()

    fun onName(value: String) = _state.update { it.copy(name = value) }
    fun onRace(value: Race) = _state.update { it.copy(race = value) }
    fun onClass(value: CharClass) = _state.update { it.copy(charClass = value) }

    fun create() {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val sheet = createCharacter(_state.value.name, _state.value.race, _state.value.charClass)
            _state.update { it.copy(created = sheet, isSaving = false) }
        }
    }
}
