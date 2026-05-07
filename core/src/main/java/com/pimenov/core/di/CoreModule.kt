package com.pimenov.core.di

import com.pimenov.core.dispatchers.AppDispatchers
import com.pimenov.core.dispatchers.DefaultAppDispatchers
import com.pimenov.core.music.AndroidMusicController
import com.pimenov.core.music.MusicController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun coreModule() = module {
    single<AppDispatchers> { DefaultAppDispatchers() }
    single<MusicController> { AndroidMusicController(androidContext()) }
}
