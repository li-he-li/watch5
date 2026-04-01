package com.heartrate.shared.data.communication

import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Android implementation of WebSocketClient
 *
 * Note: This is a mock implementation for Phase 1.
 * Real implementation using OkHttp WebSocket will be done in Phase 3.
 */
actual class WebSocketClient {

    private val _heartRateFlow = MutableStateFlow<HeartRateData?>(null)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _isConnected = MutableStateFlow(false)

    /**
     * Flow of heart rate data received from the server
     */
    actual val heartRateDataFlow: Flow<HeartRateData>
        get() = _heartRateFlow.filterNotNull()

    /**
     * Connect to a WebSocket server
     *
     * MOCK: Returns success without actual implementation
     */
    actual suspend fun connect(serverUrl: String): Result<Unit> {
        // Mock implementation - Phase 3 will use actual OkHttp WebSocket
        _connectionState.value = ConnectionState.CONNECTED
        _isConnected.value = true
        return Result.success(Unit)
    }

    /**
     * Disconnect from the WebSocket server
     *
     * MOCK: Phase 3 will implement actual disconnection
     */
    actual suspend fun disconnect() {
        // Mock implementation
        _connectionState.value = ConnectionState.DISCONNECTED
        _isConnected.value = false
    }

    /**
     * Send heart rate data to the connected server
     *
     * MOCK: Returns success without actual implementation
     */
    actual suspend fun sendHeartRateData(data: HeartRateData): Result<Unit> {
        // Mock implementation - Phase 3 will use actual WebSocket send
        return Result.success(Unit)
    }

    /**
     * Check if the client is currently connected to a server
     */
    actual val isConnected: Boolean
        get() = _isConnected.value

    /**
     * Get the current connection state
     */
    actual val connectionState: ConnectionState
        get() = _connectionState.value
}
