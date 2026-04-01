package com.heartrate.shared.domain.repository

import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for heart rate data operations.
 * This interface is platform-agnostic and implemented differently for each target.
 */
interface HeartRateRepository {
    /**
     * Observe heart rate data stream from the sensor.
     *
     * @return Flow of HeartRateData emitting at the sensor's sampling rate
     */
    fun observeHeartRate(): Flow<HeartRateData>

    /**
     * Start listening to heart rate sensor.
     * This may trigger permission requests on some platforms.
     */
    suspend fun startListening()

    /**
     * Stop listening to heart rate sensor.
     * Releases sensor resources.
     */
    suspend fun stopListening()

    /**
     * Get the current battery level of the device.
     *
     * @return Battery level as percentage (0-100), or null if unavailable
     */
    suspend fun getBatteryLevel(): Int?

    /**
     * Check if the sensor is currently active.
     *
     * @return true if sensor is listening, false otherwise
     */
    fun isListening(): Boolean
}
