package com.pimenov.feature.di

import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.ModelDownloader
import com.pimenov.feature.impl.LlamaCppDmEngine
import com.pimenov.feature.impl.ScriptedDmEngine
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun featureModule() = module {
    single { ModelDownloader(androidContext(), get()) }
    single<LlmEngine>(named("scripted")) { ScriptedDmEngine() }
    single<LlmEngine> {
        val downloader: ModelDownloader = get()
        val scripted: LlmEngine = get(named("scripted"))
        LlamaCppDmEngine(modelFile = downloader.modelFile, fallback = scripted)
    }
}
