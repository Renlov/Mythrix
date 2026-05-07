package com.pimenov.main.di

import com.pimenov.main.presentation.ModelDownloadViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun mainModule() = module {
    viewModel { ModelDownloadViewModel(get(), androidContext()) }
}
