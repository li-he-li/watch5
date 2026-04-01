package com.heartrate.shared.data.communication

import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Android implementation of BleClient
 *
 * Note: This is a mock implementation for Phase 1.
 * Real implementation using Android BluetoothManager will be done in Phase 3.
 */
actual class BleClient {

    private val _heartRateFlow = MutableStateFlow<HeartRateData?>(null)
    private val _bleState = MutableStateFlow(BleState.IDLE)
    private val _isAdvertising = MutableStateFlow(false)
    private val _isConnected = MutableStateFlow(false)

    /**
     * Flow of heart rate data received via BLE
     */
    actual val heartRateDataFlow: Flow<HeartRateData>
        get() = _heartRateFlow.filterNotNull()

    /**
     * Start BLE advertising (for phone) or scanning (for desktop)
     *
     * MOCK: Returns success without actual implementation
     */
    actual suspend fun startAdvertising(serviceName: String): Result<Unit> {
        // Mock implementation - Phase 3 will use actual BluetoothManager
        _bleState.value = BleState.ADVERTISING
        _isAdvertising.value = true
        return Result.success(Unit)
    }

    /**
     * Stop BLE advertising or scanning
     *
     * MOCK: Phase 3 will implement actual stopping
     */
    actual suspend fun stopAdvertising() {
        // Mock implementation
        _bleState.value = BleState.IDLE
        _isAdvertising.value = false
        _isConnected.value = false
    }

    /**
     * Send heart rate data via BLE (Phone side)
     *
     * MOCK: Returns success without actual implementation
     */
    actual suspend fun sendHeartRateData(data: HeartRateData): Result<Unit> {
        // Mock implementation - Phase 3 will use actual BLE GATT
        return Result.success(Unit)
    }

    /**
     * Check if BLE is currently advertising or connected
     */
    actual val isAdvertising: Boolean
        get() = _isAdvertising.value

    /**
     * Check if connected to a BLE device
     */
    actual val isConnected: Boolean
        get() = _isConnected.value

    /**
     * Get the current BLE state
     */
    actual val bleState: BleState
        get() = _bleState.value
}
