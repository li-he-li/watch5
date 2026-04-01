package com.heartrate.shared.data.communication

import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Desktop implementation of DataLayerClient
 *
 * Note: Desktop doesn't have Data Layer API (Wear OS specific).
 * This is a mock implementation for Phase 1.
 */
actual class DataLayerClient {

    private val _heartRateFlow = MutableStateFlow<HeartRateData?>(null)
    private val _isConnected = MutableStateFlow(false)

    /**
     * Flow of heart rate data received from the watch
     * Desktop doesn't use Data Layer API, so this is unused
     */
    actual val heartRateDataFlow: Flow<HeartRateData>
        get() = _heartRateFlow.filterNotNull()

    /**
     * Send heart rate data from the watch to the phone
     * Desktop implementation - not used
     */
    actual suspend fun sendHeartRateData(data: HeartRateData): Result<Unit> {
        // Desktop doesn't send data via Data Layer API
        return Result.success(Unit)
    }

    /**
     * Start listening for data from the connected device
     * Desktop implementation - mock
     */
    actual suspend fun startListening() {
        // Mock implementation
        _isConnected.value = true
    }

    /**
     * Stop listening for data
     * Desktop implementation - mock
     */
    actual fun stopListening() {
        // Mock implementation
        _isConnected.value = false
    }

    /**
     * Check if the device is connected to a paired device
     * Desktop always returns false for Data Layer
     */
    actual val isConnected: Boolean
        get() = false
}
