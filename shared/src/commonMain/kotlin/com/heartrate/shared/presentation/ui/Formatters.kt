package com.heartrate.shared.presentation.ui

/**
 * Formatting utilities for heart rate monitoring data.
 * These functions provide consistent display formatting across all platforms.
 */

/**
 * Format heart rate value for display.
 *
 * @param heartRate Heart rate in BPM
 * @return Formatted string (e.g., "72 BPM")
 */
fun formatHeartRate(heartRate: Int): String {
    return "$heartRate BPM"
}

/**
 * Format heart rate with status indicator.
 *
 * @param heartRate Heart rate in BPM
 * @return Formatted string with status (e.g., "72 BPM (Normal)")
 */
fun formatHeartRateWithStatus(heartRate: Int): String {
    val status = when (heartRate) {
        in 0..39 -> "Very Low"
        in 40..59 -> "Low"
        in 60..100 -> "Normal"
        in 101..120 -> "Elevated"
        in 121..140 -> "High"
        else -> "Very High"
    }
    return "$heartRate BPM ($status)"
}

/**
 * Get heart rate status from BPM value.
 *
 * @param heartRate Heart rate in BPM
 * @return HeartRateStatus enum
 */
fun getHeartRateStatus(heartRate: Int): HeartRateStatus {
    return when (heartRate) {
        in 0..39 -> HeartRateStatus.VERY_LOW
        in 40..59 -> HeartRateStatus.LOW
        in 60..100 -> HeartRateStatus.NORMAL
        in 101..120 -> HeartRateStatus.ELEVATED
        in 121..140 -> HeartRateStatus.HIGH
        else -> HeartRateStatus.VERY_HIGH
    }
}

/**
 * Heart rate status enumeration.
 */
enum class HeartRateStatus {
    VERY_LOW,
    LOW,
    NORMAL,
    ELEVATED,
    HIGH,
    VERY_HIGH
}

/**
 * Format battery level for display.
 *
 * @param batteryLevel Battery level percentage (0-100), or null if unavailable
 * @return Formatted string (e.g., "85%", "N/A")
 */
fun formatBatteryLevel(batteryLevel: Int?): String {
    return if (batteryLevel != null) {
        "$batteryLevel%"
    } else {
        "N/A"
    }
}

/**
 * Format battery level with icon indicator.
 *
 * @param batteryLevel Battery level percentage (0-100), or null if unavailable
 * @return BatteryStatus enum
 */
fun getBatteryStatus(batteryLevel: Int?): BatteryStatus {
    return if (batteryLevel == null) {
        BatteryStatus.UNKNOWN
    } else when (batteryLevel) {
        in 0..15 -> BatteryStatus.CRITICAL
        in 16..30 -> BatteryStatus.LOW
        in 31..60 -> BatteryStatus.MEDIUM
        in 61..90 -> BatteryStatus.GOOD
        else -> BatteryStatus.EXCELLENT
    }
}

/**
 * Battery status enumeration.
 */
enum class BatteryStatus {
    UNKNOWN,
    CRITICAL,
    LOW,
    MEDIUM,
    GOOD,
    EXCELLENT
}

/**
 * Format timestamp to readable date/time string.
 *
 * @param timestamp Unix timestamp in milliseconds
 * @return Formatted date/time string
 */
fun formatTimestamp(timestamp: Long): String {
    // Simple formatting for now - platforms can provide better implementations
    val seconds = timestamp / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}

/**
 * Format duration in milliseconds to readable string.
 *
 * @param durationMs Duration in milliseconds
 * @return Formatted duration string (e.g., "1h 23m 45s")
 */
fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    val parts = mutableListOf<String>()

    if (hours > 0) {
        parts.add("${hours}h")
    }

    val remainingMinutes = minutes % 60
    if (remainingMinutes > 0 || hours > 0) {
        parts.add("${remainingMinutes}m")
    }

    val remainingSeconds = seconds % 60
    parts.add("${remainingSeconds}s")

    return parts.joinToString(" ")
}

/**
 * Format device ID for display (truncate if too long).
 *
 * @param deviceId Full device ID
 * @param maxLength Maximum length to display (default: 20)
 * @return Truncated device ID with ellipsis if needed
 */
fun formatDeviceId(deviceId: String?, maxLength: Int = 20): String {
    if (deviceId == null) return "Unknown Device"

    return if (deviceId.length <= maxLength) {
        deviceId
    } else {
        "${deviceId.take(maxLength - 3)}..."
    }
}

/**
 * Format signal quality for display.
 *
 * @param signalQuality Signal quality percentage (0-100)
 * @return Formatted string (e.g., "95%", "Poor")
 */
fun formatSignalQuality(signalQuality: Int?): String {
    return if (signalQuality != null) {
        "$signalQuality%"
    } else {
        "N/A"
    }
}

/**
 * Get signal quality status.
 *
 * @param signalQuality Signal quality percentage (0-100), or null if unavailable
 * @return SignalQualityStatus enum
 */
fun getSignalQualityStatus(signalQuality: Int?): SignalQualityStatus {
    return if (signalQuality == null) {
        SignalQualityStatus.UNKNOWN
    } else when (signalQuality) {
        in 0..20 -> SignalQualityStatus.POOR
        in 21..40 -> SignalQualityStatus.FAIR
        in 41..60 -> SignalQualityStatus.MODERATE
        in 61..80 -> SignalQualityStatus.GOOD
        else -> SignalQualityStatus.EXCELLENT
    }
}

/**
 * Signal quality status enumeration.
 */
enum class SignalQualityStatus {
    UNKNOWN,
    POOR,
    FAIR,
    MODERATE,
    GOOD,
    EXCELLENT
}

/**
 * Calculate heart rate zone based on age and max heart rate.
 *
 * @param heartRate Current heart rate in BPM
 * @param maxHeartRate Maximum heart rate (default: 220 - age, but can be customized)
 * @return HeartRateZone enum
 */
fun getHeartRateZone(heartRate: Int, maxHeartRate: Int = 200): HeartRateZone {
    val percentage = (heartRate.toFloat() / maxHeartRate * 100).toInt()

    return when {
        percentage < 50 -> HeartRateZone.REST
        percentage < 60 -> HeartRateZone.FAT_BURN
        percentage < 70 -> HeartRateZone.CARDIO
        percentage < 80 -> HeartRateZone.AEROBIC
        percentage < 90 -> HeartRateZone.ANAEROBIC
        else -> HeartRateZone.MAXIMUM
    }
}

/**
 * Heart rate zone enumeration based on percentage of max heart rate.
 */
enum class HeartRateZone {
    REST,
    FAT_BURN,
    CARDIO,
    AEROBIC,
    ANAEROBIC,
    MAXIMUM
}
