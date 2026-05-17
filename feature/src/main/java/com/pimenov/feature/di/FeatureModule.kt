package com.pimenov.feature.di

import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.ModelDownloader
import com.pimenov.feature.impl.MediaPipeDmEngine
import com.pimenov.feature.impl.ScriptedDmEngine
import com.pimenov.feature.world.TavernWorldBibleLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun featureModule() = module {
    single { ModelDownloader(androidContext(), get()) }
    single<LlmEngine>(named("scripted")) { ScriptedDmEngine() }
    single<LlmEngine> {
        val downloader: ModelDownloader = get()
        val scripted: LlmEngine = get(named("scripted"))
        val ctx = androidContext()
        MediaPipeDmEngine(
            context = ctx,
            modelFile = downloader.modelFile,
            fallback = scripted,
            // Phase 1: hardcoded player name. Phase 2 will plumb
            // CharacterRepository through and rebuild per turn.
            systemPromptProvider = {
                TavernWorldBibleLoader.buildSystemPrompt(ctx, playerName = "Кейн")
            }
        )
    }
}
