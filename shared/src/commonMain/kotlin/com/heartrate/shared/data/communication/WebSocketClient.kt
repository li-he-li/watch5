package com.heartrate.shared.data.communication

import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.flow.Flow

/**
 * WebSocket client for Phone → Desktop communication
 *
 * This client handles sending heart rate data from the phone to the desktop
 * using WebSocket protocol.
 */
expect class WebSocketClient {

    /**
     * Flow of heart rate data received from the server
     */
    val heartRateDataFlow: Flow<HeartRateData>

    /**
     * Connect to a WebSocket server
     *
     * @param serverUrl The URL of the WebSocket server (e.g., "ws://192.168.1.100:8080")
     */
    suspend fun connect(serverUrl: String): Result<Unit>

    /**
     * Disconnect from the WebSocket server
     */
    suspend fun disconnect()

    /**
     * Send heart rate data to the connected server
     *
     * @param data The heart rate data to send
     * @return true if the data was sent successfully, false otherwise
     */
    suspend fun sendHeartRateData(data: HeartRateData): Result<Unit>

    /**
     * Check if the client is currently connected to a server
     */
    val isConnected: Boolean

    /**
     * Get the current connection state
     */
    val connectionState: ConnectionState
}

/**
 * Represents the current connection state of the WebSocket client
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
