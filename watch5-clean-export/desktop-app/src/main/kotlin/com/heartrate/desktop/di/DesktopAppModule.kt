package com.heartrate.desktop.di

import com.heartrate.desktop.data.DesktopWebSocketHeartRateRepository
import com.heartrate.shared.domain.repository.HeartRateRepository
import com.heartrate.shared.domain.repository.TransportModeController
import org.koin.dsl.module

val desktopAppModule = module {
    single<HeartRateRepository> {
        DesktopWebSocketHeartRateRepository(
            webSocketClient = get(),
            bleClient = get()
        )
    }
    single<TransportModeController> {
        get<HeartRateRepository>() as DesktopWebSocketHeartRateRepository
    }
}
