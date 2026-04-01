package com.heartrate.wear.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow

/**
 * Selects the best watch-side heart-rate path available on the current device.
 */
class HeartRateSensorManager(
    context: Context
) {
    private val samsungSource = SamsungHealthSensorSdkHeartRateSource(context)
    private val platformSensorSource = PlatformSensorHeartRateSource(context)
    private val healthServicesSource = HealthServicesHeartRateSource(context)
    private val selectedSource: WearHeartRateDataSource by lazy {
        if (samsungSource.isSupportedOnDevice()) {
            Log.i(TAG, "Samsung heart-rate source detected, preferring SDK path")
            samsungSource
        } else if (platformSensorSource.isSupportedOnDevice()) {
            Log.i(TAG, "Samsung SDK unavailable, using direct platform heart-rate sensor")
            platformSensorSource
        } else {
            Log.i(TAG, "No direct heart-rate sensor path, using Health Services fallback")
            healthServicesSource
        }
    }

    val heartRateReadings: Flow<Int> = selectedSource.heartRateReadings

    fun start(): Result<Unit> = selectedSource.start()

    fun stop() = selectedSource.stop()

    fun isListening(): Boolean = selectedSource.isListening()

    fun updateSamplingRate(targetHz: Int): Result<Unit> = selectedSource.updateSamplingRate(targetHz)

    fun currentSamplingRateHz(): Int = selectedSource.currentSamplingRateHz()

    fun sourceName(): String = selectedSource.name

    companion object {
        private const val TAG = "P2A-WearSensor"
    }
}
