package com.pimenov.game.di

import com.pimenov.character.data.CharacterDao
import com.pimenov.game.api.GameRepository
import com.pimenov.game.data.AppDatabase
import com.pimenov.game.data.ChatMessageDao
import com.pimenov.game.data.GameRepositoryImpl
import com.pimenov.game.data.GameSaveDao
import com.pimenov.game.domain.LoadSaveUseCase
import com.pimenov.game.domain.ResolveAttackUseCase
import com.pimenov.game.domain.RollDiceUseCase
import com.pimenov.game.domain.SaveGameUseCase
import com.pimenov.game.domain.SendPlayerMessageUseCase
import com.pimenov.game.domain.StartCombatUseCase
import com.pimenov.game.presentation.GameViewModel
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun gameModule() = module {
    single { Json { ignoreUnknownKeys = true; prettyPrint = false } }
    single { AppDatabase.build(androidContext()) }
    single<CharacterDao> { get<AppDatabase>().characterDao() }
    single<GameSaveDao> { get<AppDatabase>().gameSaveDao() }
    single<ChatMessageDao> { get<AppDatabase>().chatMessageDao() }
    single<GameRepository> { GameRepositoryImpl(get(), get(), get()) }

    factory { SendPlayerMessageUseCase(get(), get()) }
    factory { RollDiceUseCase() }
    factory { StartCombatUseCase(get()) }
    factory { ResolveAttackUseCase(get()) }
    factory { LoadSaveUseCase(get()) }
    factory { SaveGameUseCase(get()) }

    viewModel { GameViewModel(get(), get(), get(), get(), get(), get()) }
}
