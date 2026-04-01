package com.heartrate.wear.data

import kotlinx.coroutines.flow.Flow

/**
 * Watch-side heart-rate source abstraction.
 * Implementations can use Health Services or Samsung's sensor SDK.
 */
interface WearHeartRateDataSource {
    val name: String
    val heartRateReadings: Flow<Int>

    fun start(): Result<Unit>

    fun stop()

    fun isListening(): Boolean

    fun updateSamplingRate(targetHz: Int): Result<Unit>

    fun currentSamplingRateHz(): Int
}
