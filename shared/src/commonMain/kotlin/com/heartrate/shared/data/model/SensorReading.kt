package com.heartrate.shared.data.model

import kotlinx.serialization.Serializable

/**
 * Raw sensor reading from the heart rate sensor.
 *
 * This represents a single reading from the PPG (photoplethysmogram) sensor
 * before processing and aggregation.
 *
 * @property timestamp Unix timestamp in milliseconds
 * @property value Raw sensor value
 * @property accuracy Accuracy level of the reading
 * @property deviceId Device that captured the reading
 */
@Serializable
data class SensorReading(
    val timestamp: Long,
    val value: Float,
    val accuracy: SensorAccuracy,
    val deviceId: String
) {
    init {
        require(value >= 0) { "Sensor value must be non-negative" }
    }
}

/**
 * Sensor accuracy levels as defined by Android's Sensor Manager.
 */
@Serializable
enum class SensorAccuracy {
    /**
     * Accuracy is unreliable or the sensor is not reporting accuracy.
     */
    UNRELIABLE,

    /**
     * Accuracy is low.
     */
    LOW,

    /**
     * Accuracy is medium.
     */
    MEDIUM,

    /**
     * Accuracy is high.
     */
    HIGH
}
