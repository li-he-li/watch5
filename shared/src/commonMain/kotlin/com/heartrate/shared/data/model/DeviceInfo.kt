package com.heartrate.shared.data.model

import kotlinx.serialization.Serializable

/**
 * Device information model for identifying and managing connected devices.
 *
 * @property deviceId Unique identifier for the device
 * @property deviceType Type of device (WATCH, PHONE, DESKTOP)
 * @property deviceName Human-readable device name
 * @property osVersion Operating system version
 * @property appVersion Application version
 * @property batteryLevel Current battery level (0-100)
 * @property isCharging Whether the device is currently charging
 */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceType: DeviceType,
    val deviceName: String,
    val osVersion: String,
    val appVersion: String,
    val batteryLevel: Int,
    val isCharging: Boolean = false
) {
    init {
        require(batteryLevel in 0..100) { "Battery level must be between 0 and 100" }
    }
}

/**
 * Enum representing the type of device in the three-tier system.
 */
@Serializable
enum class DeviceType {
    WATCH,
    PHONE,
    DESKTOP
}
