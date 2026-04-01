package com.heartrate.shared.presentation.model

/**
 * UI State for Heart Rate monitoring
 */
data class HeartRateUiState(
    val currentHeartRate: Int = 0,
    val isMonitoring: Boolean = false,
    val batteryLevel: Int? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val errorMessage: String? = null,
    val deviceInfo: String? = null,
    val lastHeartRateTimestamp: Long? = null
)

/**
 * Connection status for communication layers
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Extension to check if connection is active
 */
val ConnectionStatus.isActive: Boolean
    get() = this == ConnectionStatus.CONNECTED

/**
 * Extension to get display text
 */
val ConnectionStatus.displayText: String
    get() = when (this) {
        ConnectionStatus.DISCONNECTED -> "Disconnected"
        ConnectionStatus.CONNECTING -> "Connecting..."
        ConnectionStatus.CONNECTED -> "Connected"
        ConnectionStatus.ERROR -> "Connection Error"
    }
