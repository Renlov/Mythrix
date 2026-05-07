package com.pimenov.character.di

import com.pimenov.character.api.CharacterRepository
import com.pimenov.character.data.CharacterDao
import com.pimenov.character.data.CharacterRepositoryImpl
import com.pimenov.character.domain.CreateCharacterUseCase
import com.pimenov.character.presentation.CharacterCreationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun characterModule() = module {
    single<CharacterRepository> { CharacterRepositoryImpl(get<CharacterDao>()) }
    factory { CreateCharacterUseCase(get()) }
    viewModel { CharacterCreationViewModel(get()) }
}
