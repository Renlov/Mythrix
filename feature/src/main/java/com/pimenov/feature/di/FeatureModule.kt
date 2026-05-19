package com.pimenov.feature.di

import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.impl.DeepSeekLlmEngine
import com.pimenov.feature.world.TavernWorldBibleLoader
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun featureModule() = module {
    single<LlmEngine> {
        val ctx = androidContext()
        DeepSeekLlmEngine(
            systemPromptProvider = {
                TavernWorldBibleLoader.buildSystemPrompt(ctx, playerName = "Кейн")
            }
        )
    }
}
