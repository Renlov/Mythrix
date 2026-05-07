package com.pimenov.character.domain

import com.pimenov.character.api.CharClass
import com.pimenov.character.api.CharacterRepository
import com.pimenov.character.api.CharacterSheet
import com.pimenov.character.api.Race

class CreateCharacterUseCase(private val repository: CharacterRepository) {
    suspend operator fun invoke(name: String, race: Race, charClass: CharClass): CharacterSheet {
        val sheet = CharacterSheet.new(name.trim().ifEmpty { "Безымянный" }, race, charClass)
        val id = repository.save(sheet)
        return sheet.copy(id = id)
    }
}
