package com.heartrate.shared.domain.usecase

import com.heartrate.shared.domain.repository.HeartRateRepository

/**
 * Use case for retrieving the current battery level.
 * Provides a clean abstraction for battery level access.
 */
class GetBatteryLevel(
    private val repository: HeartRateRepository
) {
    /**
     * Execute the use case to get the current battery level.
     *
     * @return Battery level as percentage (0-100), or null if unavailable
     */
    suspend operator fun invoke(): Int? {
        return repository.getBatteryLevel()
    }
}
