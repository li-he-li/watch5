package com.heartrate.shared.domain.usecase

import com.heartrate.shared.data.model.HeartRateData
import com.heartrate.shared.domain.repository.HeartRateRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing heart rate data stream.
 * Encapsulates business logic for heart rate monitoring.
 */
class ObserveHeartRate(
    private val repository: HeartRateRepository
) {
    /**
     * Execute the use case to observe heart rate data.
     *
     * @return Flow of HeartRateData
     */
    operator fun invoke(): Flow<HeartRateData> {
        return repository.observeHeartRate()
    }

    /**
     * Start the heart rate sensor.
     * Must be called before invoke() will emit values.
     */
    suspend fun start() {
        repository.startListening()
    }

    /**
     * Stop the heart rate sensor.
     */
    suspend fun stop() {
        repository.stopListening()
    }

    /**
     * Check if the sensor is currently active.
     *
     * @return true if listening
     */
    fun isActive(): Boolean {
        return repository.isListening()
    }
}
