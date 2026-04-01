package com.heartrate.wear.di

import com.heartrate.shared.domain.repository.HeartRateRepository
import com.heartrate.wear.data.HeartRateSensorManager
import com.heartrate.wear.data.OffBodySensorMonitor
import com.heartrate.wear.data.WearDataLayerSender
import com.heartrate.wear.data.WearHeartRateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val wearAppModule = module {
    single { HeartRateSensorManager(androidContext()) }
    single { OffBodySensorMonitor(androidContext()) }
    single { WearDataLayerSender(androidContext()) }
    single<HeartRateRepository> {
        WearHeartRateRepository(
            appContext = androidContext(),
            sensorManager = get(),
            offBodySensorMonitor = get(),
            dataLayerSender = get()
        )
    }
}
