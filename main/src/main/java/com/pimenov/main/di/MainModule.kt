package com.pimenov.main.di

import com.pimenov.main.presentation.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun mainModule() = module {
    viewModel { ChatViewModel(get(), get(), get(), get()) }
}
