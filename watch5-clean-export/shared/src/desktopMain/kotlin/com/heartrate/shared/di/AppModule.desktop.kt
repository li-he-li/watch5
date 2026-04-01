package com.heartrate.shared.di

import com.heartrate.shared.data.communication.BleClient
import com.heartrate.shared.data.communication.DataLayerClient
import com.heartrate.shared.data.communication.WebSocketClient
import com.heartrate.shared.domain.repository.TransportModeController
import com.heartrate.shared.domain.usecase.GetBatteryLevel
import com.heartrate.shared.domain.usecase.ObserveHeartRate
import com.heartrate.shared.presentation.viewmodel.HeartRateViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Desktop-specific Koin DI module with actual repository implementation
 */
val desktopSharedModule = module {
    single { DataLayerClient() }
    single { WebSocketClient() }
    single { BleClient() }

    // Use Cases
    singleOf(::ObserveHeartRate)
    singleOf(::GetBatteryLevel)

    // ViewModels (created as factory to allow multiple instances)
    factory {
        HeartRateViewModel(
            observeHeartRate = get(),
            getBatteryLevel = get(),
            webSocketClient = get(),
            bleClient = get(),
            transportModeController = getOrNull<TransportModeController>()
        )
    }
}

/**
 * Get all Koin modules for Desktop app
 */
actual fun getAppModules(): List<Module> = listOf(desktopSharedModule)
