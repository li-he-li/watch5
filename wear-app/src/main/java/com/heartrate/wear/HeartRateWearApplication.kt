package com.heartrate.wear

import android.app.Application
import com.heartrate.shared.di.getAppModules
import com.heartrate.wear.di.wearAppModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Wear OS Application class
 * Initializes Koin dependency injection
 */
class HeartRateWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin if not already started
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@HeartRateWearApplication)
                modules(getAppModules() + wearAppModule)
            }
        }
    }
}
