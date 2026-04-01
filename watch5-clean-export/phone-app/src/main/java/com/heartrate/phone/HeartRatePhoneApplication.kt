package com.heartrate.phone

import android.app.Application
import com.heartrate.phone.di.phoneAppModule
import com.heartrate.shared.di.getAppModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Phone Application class
 * Initializes Koin dependency injection
 */
class HeartRatePhoneApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin if not already started
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@HeartRatePhoneApplication)
                modules(getAppModules() + phoneAppModule)
            }
        }
    }
}
