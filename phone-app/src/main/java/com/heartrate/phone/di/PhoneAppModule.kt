package com.heartrate.phone.di

import androidx.room.Room
import com.heartrate.phone.data.persistence.HeartRateDao
import com.heartrate.phone.data.persistence.HeartRateDatabase
import com.heartrate.phone.data.persistence.HeartRateExportManager
import com.heartrate.phone.BuildConfig
import com.heartrate.phone.data.PhoneBleRelayController
import com.heartrate.phone.data.PhoneRelayHeartRateRepository
import com.heartrate.phone.data.PhoneWebSocketRelayController
import com.heartrate.phone.network.PhoneBleGattServer
import com.heartrate.phone.network.PhoneWebSocketRelayServer
import com.heartrate.shared.domain.repository.HeartRateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val phoneAppModule = module {
    single { PhoneWebSocketRelayServer(port = BuildConfig.WS_SERVER_PORT) }
    single { PhoneBleGattServer(appContext = androidContext()) }
    single {
        Room.databaseBuilder(
            androidContext(),
            HeartRateDatabase::class.java,
            "heart-rate.db"
        ).fallbackToDestructiveMigration().build()
    }
    single<HeartRateDao> { get<HeartRateDatabase>().heartRateDao() }
    single { HeartRateExportManager(appContext = androidContext(), heartRateDao = get()) }
    single {
        PhoneRelayHeartRateRepository(
            appContext = androidContext(),
            relayServer = get(),
            bleGattServer = get(),
            heartRateDao = get()
        )
    }
    single<PhoneBleRelayController> { get<PhoneRelayHeartRateRepository>() }
    single<PhoneWebSocketRelayController> { get<PhoneRelayHeartRateRepository>() }
    single<HeartRateRepository> {
        get<PhoneRelayHeartRateRepository>()
    }
}
