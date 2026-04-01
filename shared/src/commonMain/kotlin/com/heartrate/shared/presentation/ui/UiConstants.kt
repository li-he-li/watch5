package com.heartrate.shared.presentation.ui

/**
 * UI constants for the heart rate monitoring app.
 * These values provide consistency across all platforms.
 */

/**
 * Animation duration constants (in milliseconds).
 */
object AnimationDuration {
    const val FAST = 150L
    const val MEDIUM = 300L
    const val SLOW = 500L
    const val EXTRA_SLOW = 1000L
}

/**
 * Heart rate monitoring constants.
 */
object HeartRateLimits {
    const val MIN_VALID_HEART_RATE = 30
    const val MAX_VALID_HEART_RATE = 250
    const val NORMAL_RESTING = 70
    const val NORMAL_MIN = 60
    const val NORMAL_MAX = 100

    // Heart rate zones (as percentage of max heart rate)
    const val ZONE_REST_MAX = 50
    const val ZONE_FAT_BURN_MIN = 50
    const val ZONE_FAT_BURN_MAX = 60
    const val ZONE_CARDIO_MIN = 60
    const val ZONE_CARDIO_MAX = 70
    const val ZONE_AEROBIC_MIN = 70
    const val ZONE_AEROBIC_MAX = 80
    const val ZONE_ANAEROBIC_MIN = 80
    const val ZONE_ANAEROBIC_MAX = 90
    const val ZONE_MAXIMUM_MIN = 90
}

/**
 * Sensor sampling rate constants.
 */
object SamplingRate {
    const val SLEEPING = 1  // 1 Hz - for power saving
    const val SITTING = 1   // 1 Hz - minimal activity
    const val WALKING = 3   // 3 Hz - moderate activity
    const val RUNNING = 5   // 5 Hz - high activity (max recommended)
}

/**
 * Battery level thresholds.
 */
object BatteryThresholds {
    const val CRITICAL = 15
    const val LOW = 30
    const val MEDIUM = 60
    const val GOOD = 90
}

/**
 * Connection timeout constants (in milliseconds).
 */
object ConnectionTimeout {
    const val WEAROS_DATA_LAYER = 5000L
    const val WEBSOCKET_CONNECTION = 10000L
    const val BLE_CONNECTION = 15000L
    const val RETRY_DELAY = 2000L
    const val MAX_RETRY_ATTEMPTS = 3
}

/**
 * Data refresh intervals (in milliseconds).
 */
object RefreshInterval {
    const val BATTERY_LEVEL = 5000L     // 5 seconds
    const val CONNECTION_STATUS = 2000L  // 2 seconds
    const val HEART_RATE_DISPLAY = 1000L // 1 second
}

/**
 * UI display constants.
 */
object DisplayConstants {
    const val MAX_HEART_RATE_HISTORY_SIZE = 100
    const val MAX_LOG_MESSAGES = 50
    const val DEFAULT_CHART_POINTS = 30
}

/**
 * Validation constants.
 */
object Validation {
    const val MIN_DEVICE_ID_LENGTH = 3
    const val MAX_DEVICE_ID_LENGTH = 50
    const val MIN_SERVER_NAME_LENGTH = 1
    const val MAX_SERVER_NAME_LENGTH = 100

    // WebSocket URL validation
    const val WEBSOCKET_MIN_LENGTH = 10
    const val WEBSOCKET_MAX_LENGTH = 500

    // Port ranges
    const val MIN_PORT = 1
    const val MAX_PORT = 65535
    const val DEFAULT_WEBSOCKET_PORT = 8080
}

/**
 * Error messages for common scenarios.
 */
object ErrorMessages {
    const val SENSOR_UNAVAILABLE = "Heart rate sensor not available"
    const val SENSOR_PERMISSION_DENIED = "Permission to access sensor denied"
    const val BLUETOOTH_PERMISSION_DENIED = "Bluetooth permission required"
    const val LOCATION_PERMISSION_DENIED = "Location permission required for BLE"
    const val CONNECTION_FAILED = "Failed to establish connection"
    const val CONNECTION_LOST = "Connection lost"
    const val DATA_LAYER_ERROR = "Wear OS Data Layer error"
    const val WEBSOCKET_ERROR = "WebSocket connection error"
    const val BLE_ERROR = "Bluetooth error"
    const val BATTERY_OPTIMIZATION_ENABLED = "Battery optimization may affect monitoring"

    fun formatConnectionError(error: String?): String {
        return when {
            error == null -> CONNECTION_FAILED
            error.contains("timeout", ignoreCase = true) -> "Connection timeout"
            error.contains("refused", ignoreCase = true) -> "Connection refused"
            error.contains("network", ignoreCase = true) -> "Network error"
            else -> "Connection error: $error"
        }
    }
}

/**
 * Notification constants for Android platforms.
 */
object NotificationConstants {
    const val CHANNEL_ID_HEART_RATE = "heart_rate_monitoring"
    const val CHANNEL_NAME_HEART_RATE = "Heart Rate Monitoring"
    const val CHANNEL_ID_CONNECTION = "connection_status"
    const val CHANNEL_NAME_CONNECTION = "Connection Status"

    const val NOTIFICATION_ID_HEART_RATE = 1001
    const val NOTIFICATION_ID_CONNECTION = 1002

    const val STOP_ACTION = "stop_monitoring"
    const val DISCONNECT_ACTION = "disconnect"
}

/**
 * Intent action constants for Android platforms.
 */
object IntentActions {
    const val ACTION_START_MONITORING = "com.heartrate.START_MONITORING"
    const val ACTION_STOP_MONITORING = "com.heartrate.STOP_MONITORING"
    const val ACTION_CONNECT_WEBSOCKET = "com.heartrate.CONNECT_WEBSOCKET"
    const val ACTION_DISCONNECT_WEBSOCKET = "com.heartrate.DISCONNECT_WEBSOCKET"
    const val ACTION_START_BLE = "com.heartrate.START_BLE"
    const val ACTION_STOP_BLE = "com.heartrate.STOP_BLE"
}

/**
 * Extra keys for intents (Android platforms).
 */
object IntentExtras {
    const val EXTRA_SERVER_URL = "server_url"
    const val EXTRA_SERVICE_NAME = "service_name"
    const val EXTRA_DEVICE_ID = "device_id"
    const val EXTRA_HEART_RATE = "heart_rate"
    const val EXTRA_BATTERY_LEVEL = "battery_level"
}

/**
 * SharedPreferences keys (Android platforms).
 */
object PreferencesKeys {
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_SERVICE_NAME = "service_name"
    const val KEY_SAMPLING_RATE = "sampling_rate"
    const val KEY_ENABLE_SOUND = "enable_sound"
    const val KEY_ENABLE_VIBRATION = "enable_vibration"
    const val KEY_NOTIFICATION_THRESHOLD = "notification_threshold"
    const val KEY_LAST_CONNECTED_DEVICE = "last_connected_device"
}

/**
 * Logging constants.
 */
object LogTags {
    const val HEART_RATE = "HeartRate"
    const val SENSOR = "Sensor"
    const val DATA_LAYER = "DataLayer"
    const val WEBSOCKET = "WebSocket"
    const val BLE = "BLE"
    const val BATTERY = "Battery"
    const val CONNECTION = "Connection"
    const val VIEW_MODEL = "ViewModel"
}

/**
 * Feature flags for enabling/disabling features.
 */
object FeatureFlags {
    const val ENABLE_DYNAMIC_SAMPLING = true
    const val ENABLE_BATTERY_OPTIMIZATION_WARNING = true
    const val ENABLE_SOUND_ALERTS = false
    const val ENABLE_VIBRATION_ALERTS = false
    const val ENABLE_DATA_PERSISTENCE = false  // Phase 2 feature
    const val ENABLE_HEALTH_CONNECT = false    // Phase 3 feature
    const val ENABLE_ANALYTICS = false         // Future feature
}
