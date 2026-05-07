package com.pimenov.settings.di

import com.pimenov.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun settingsModule() = module {
    viewModel { SettingsViewModel(get(), get()) }
}
