package com.heartrate.shared.data.communication

import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.flow.Flow

/**
 * BLE (Bluetooth Low Energy) client for Phone → Desktop communication
 *
 * This client provides an alternative communication method using BLE GATT protocol.
 * It serves as a fallback when WebSocket is not available.
 *
 * Phone side: Acts as a BLE GATT Server, broadcasting heart rate data
 * Desktop side: Acts as a BLE GATT Client, scanning and connecting to the phone
 */
expect class BleClient {

    /**
     * Flow of heart rate data received via BLE
     */
    val heartRateDataFlow: Flow<HeartRateData>

    /**
     * Start BLE advertising (for phone) or scanning (for desktop)
     *
     * @param serviceName The name to advertise or scan for
     */
    suspend fun startAdvertising(serviceName: String = "HeartRateMonitor"): Result<Unit>

    /**
     * Stop BLE advertising or scanning
     */
    suspend fun stopAdvertising()

    /**
     * Send heart rate data via BLE (Phone side)
     *
     * @param data The heart rate data to send
     * @return true if the data was sent successfully, false otherwise
     */
    suspend fun sendHeartRateData(data: HeartRateData): Result<Unit>

    /**
     * Check if BLE is currently advertising or connected
     */
    val isAdvertising: Boolean

    /**
     * Check if connected to a BLE device
     */
    val isConnected: Boolean

    /**
     * Get the current BLE state
     */
    val bleState: BleState
}

/**
 * Represents the current BLE state
 */
enum class BleState {
    IDLE,
    ADVERTISING,
    SCANNING,
    CONNECTED,
    ERROR
}
