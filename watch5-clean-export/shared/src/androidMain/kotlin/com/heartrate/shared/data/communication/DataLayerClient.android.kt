package com.heartrate.shared.data.communication

import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Android implementation of DataLayerClient
 *
 * Note: This is a mock implementation for Phase 1.
 * Real implementation using Wearable Data Layer API will be done in Phase 2.
 */
actual class DataLayerClient {

    private val _heartRateFlow = MutableStateFlow<HeartRateData?>(null)
    private val _isConnected = MutableStateFlow(false)

    /**
     * Flow of heart rate data received from the watch
     */
    actual val heartRateDataFlow: Flow<HeartRateData>
        get() = _heartRateFlow.filterNotNull()

    /**
     * Send heart rate data from the watch to the phone
     *
     * MOCK: Returns success without actual implementation
     */
    actual suspend fun sendHeartRateData(data: HeartRateData): Result<Unit> {
        // Mock implementation - Phase 2 will use actual Data Layer API
        return Result.success(Unit)
    }

    /**
     * Start listening for data from the connected device
     *
     * MOCK: Phase 2 will implement actual listening
     */
    actual suspend fun startListening() {
        // Mock implementation
        _isConnected.value = true
    }

    /**
     * Stop listening for data
     *
     * MOCK: Phase 2 will implement actual stopping
     */
    actual fun stopListening() {
        // Mock implementation
        _isConnected.value = false
    }

    /**
     * Check if the device is connected to a paired device
     */
    actual val isConnected: Boolean
        get() = _isConnected.value
}
