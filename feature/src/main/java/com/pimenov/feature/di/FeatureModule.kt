package com.pimenov.feature.di

import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.game.GameStateRepository
import com.pimenov.feature.game.world.WorldCatalog
import com.pimenov.feature.impl.DeepSeekLlmEngine
import com.pimenov.feature.world.TavernWorldBibleLoader
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun featureModule() = module {
    single { WorldCatalog.load(androidContext()) }
    single { GameStateRepository(androidContext(), get(), defaultName = "Кейн") }
    single<LlmEngine> {
        val ctx = androidContext()
        val game = get<GameStateRepository>()
        val catalog = get<WorldCatalog>()
        DeepSeekLlmEngine(
            systemPromptProvider = {
                TavernWorldBibleLoader.buildSystemPrompt(ctx, game.state.value, game.journal(), catalog)
            }
        )
    }
}
