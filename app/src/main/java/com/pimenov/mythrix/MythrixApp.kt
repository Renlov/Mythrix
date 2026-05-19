package com.pimenov.mythrix

import android.app.Application
import com.pimenov.core.di.coreModule
import com.pimenov.feature.di.featureModule
import com.pimenov.main.di.mainModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MythrixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@MythrixApp)
            modules(
                coreModule(),
                featureModule(),
                mainModule(),
            )
        }
    }
}
