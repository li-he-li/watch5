package com.heartrate.shared.data.model

import kotlinx.serialization.Serializable

/**
 * Standard heart rate data model for transmission across all platform tiers.
 *
 * @property timestamp Unix timestamp in milliseconds
 * @property heartRate Heart rate in beats per minute (BPM)
 * @property deviceId Unique identifier for the source device
 * @property batteryLevel Optional battery level percentage (0-100)
 * @property signalQuality Optional signal quality indicator (0-100, higher is better)
 */
@Serializable
data class HeartRateData(
    val timestamp: Long,
    val heartRate: Int,
    val deviceId: String,
    val batteryLevel: Int? = null,
    val signalQuality: Int? = null
) {
    init {
        require(heartRate > 0) { "Heart rate must be positive" }
        require(heartRate <= 250) { "Heart rate exceeds realistic maximum" }
        require(batteryLevel == null || batteryLevel in 0..100) { "Battery level must be between 0 and 100" }
        require(signalQuality == null || signalQuality in 0..100) { "Signal quality must be between 0 and 100" }
    }
}
