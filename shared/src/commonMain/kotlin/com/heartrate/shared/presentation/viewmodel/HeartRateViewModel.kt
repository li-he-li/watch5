package com.heartrate.shared.presentation.viewmodel

import com.heartrate.shared.data.communication.BleClient
import com.heartrate.shared.data.communication.WebSocketClient
import com.heartrate.shared.domain.repository.TransportModeController
import com.heartrate.shared.domain.usecase.GetBatteryLevel
import com.heartrate.shared.domain.usecase.ObserveHeartRate
import com.heartrate.shared.presentation.model.ConnectionStatus
import com.heartrate.shared.presentation.model.HeartRateUiState
import com.heartrate.shared.presentation.model.TransportMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared ViewModel for Heart Rate monitoring
 *
 * This class provides the business logic for heart rate monitoring UI.
 * On Android platforms, this should be wrapped in a proper ViewModel.
 * On Desktop, this can be used directly.
 *
 * Note: For Phase 1, communication clients are simplified.
 * Phase 2 will integrate full Data Layer API, WebSocket, and BLE functionality.
 *
 * @param observeHeartRate Use case for observing heart rate data
 * @param getBatteryLevel Use case for getting battery level
 */
class HeartRateViewModel(
    private val observeHeartRate: ObserveHeartRate,
    private val getBatteryLevel: GetBatteryLevel,
    private val webSocketClient: WebSocketClient? = null,
    private val bleClient: BleClient? = null,
    private val transportModeController: TransportModeController? = null
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var heartRateCollectionJob: Job? = null
    private var batteryPollingJob: Job? = null
    private var webSocketReconnectJob: Job? = null
    @Volatile
    private var webSocketManualDisconnect: Boolean = false
    @Volatile
    private var transportMode: TransportMode = transportModeController?.getTransportMode() ?: TransportMode.AUTO

    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState: StateFlow<HeartRateUiState> = _uiState.asStateFlow()

    /**
     * Start monitoring heart rate
     */
    fun startMonitoring() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isMonitoring = true)
                transportModeController?.setTransportMode(transportMode)

                withContext(Dispatchers.Default) {
                    observeHeartRate.start()
                }

                startUiCollection()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMonitoring = false,
                    errorMessage = "Failed to start monitoring: ${e.message}"
                )
            }
        }
    }

    /**
     * Attach UI collectors to an already-running monitoring session without
     * starting the repository again. Used by Android activities around FGS ownership.
     */
    fun attachUiToMonitoring() {
        if (heartRateCollectionJob != null || batteryPollingJob != null) return
        _uiState.value = _uiState.value.copy(isMonitoring = observeHeartRate.isActive())
        startUiCollection()
    }

    /**
     * Stop monitoring heart rate
     */
    fun stopMonitoring() {
        viewModelScope.launch {
            try {
                heartRateCollectionJob?.cancel()
                heartRateCollectionJob = null
                batteryPollingJob?.cancel()
                batteryPollingJob = null
                withContext(Dispatchers.Default) {
                    observeHeartRate.stop()
                }

                _uiState.value = _uiState.value.copy(
                    isMonitoring = false,
                    currentHeartRate = 0,
                    lastHeartRateTimestamp = null,
                    connectionStatus = ConnectionStatus.DISCONNECTED
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to stop monitoring: ${e.message}"
                )
            }
        }
    }

    /**
     * Connect to WebSocket server (Phone â†?Desktop)
     * Phase 1: Mock implementation
     */
    fun connectWebSocket(serverUrl: String) {
        if (transportMode == TransportMode.BLE_ONLY) {
            _uiState.value = _uiState.value.copy(
                connectionStatus = ConnectionStatus.ERROR,
                errorMessage = "Current mode is BLE-only"
            )
            return
        }

        val client = webSocketClient
        if (client == null) {
            _uiState.value = _uiState.value.copy(
                connectionStatus = ConnectionStatus.CONNECTED,
                errorMessage = null
            )
            return
        }

        webSocketManualDisconnect = false
        webSocketReconnectJob?.cancel()
        webSocketReconnectJob = viewModelScope.launch {
            runWebSocketReconnectLoop(client = client, serverUrl = serverUrl)
        }
    }

    /**
     * Disconnect from WebSocket
     */
    fun disconnectWebSocket() {
        webSocketManualDisconnect = true
        webSocketReconnectJob?.cancel()
        webSocketReconnectJob = null
        viewModelScope.launch {
            runCatching { webSocketClient?.disconnect() }
            _uiState.value = _uiState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                errorMessage = null
            )
        }
    }

    /**
     * Start BLE advertising (Phone) or scanning (Desktop)
     * Phase 1: Mock implementation
     */
    fun startBLE(serviceName: String = "HeartRateMonitor") {
        if (transportMode == TransportMode.WS_ONLY) {
            _uiState.value = _uiState.value.copy(
                connectionStatus = ConnectionStatus.ERROR,
                errorMessage = "Current mode is WS-only"
            )
            return
        }

        viewModelScope.launch {
            startBleInternal(serviceName)
        }
    }

    /**
     * Stop BLE
     */
    fun stopBLE() {
        viewModelScope.launch {
            runCatching { bleClient?.stopAdvertising() }
            _uiState.value = _uiState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED
            )
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun setTransportMode(mode: TransportMode) {
        if (transportMode == mode) return
        transportMode = mode
        transportModeController?.setTransportMode(mode)
        _uiState.value = _uiState.value.copy(errorMessage = null)
        viewModelScope.launch {
            when (mode) {
                TransportMode.AUTO -> Unit
                TransportMode.WS_ONLY -> {
                    runCatching { bleClient?.stopAdvertising() }
                }
                TransportMode.BLE_ONLY -> {
                    startBleInternal(BLE_MANUAL_SERVICE_NAME)
                }
            }
        }
    }

    fun getTransportMode(): TransportMode = transportMode

    /**
     * Detach UI observers without shutting down repository/service-level monitoring.
     * Used by Android activities to avoid interrupting foreground services.
     */
    fun detachUi() {
        heartRateCollectionJob?.cancel()
        heartRateCollectionJob = null
        batteryPollingJob?.cancel()
        batteryPollingJob = null
        webSocketManualDisconnect = true
        webSocketReconnectJob?.cancel()
        webSocketReconnectJob = null
    }

    private fun startUiCollection() {
        heartRateCollectionJob?.cancel()
        heartRateCollectionJob = viewModelScope.launch {
            observeHeartRate()
                .catch { error: Throwable ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Monitoring error: ${error.message}"
                    )
                }
                .collect { data ->
                    println(
                        "HR_CAPTURE bpm=${data.heartRate} source=${data.deviceId} ts=${data.timestamp}"
                    )
                    _uiState.value = _uiState.value.copy(
                        isMonitoring = true,
                        currentHeartRate = data.heartRate,
                        deviceInfo = data.deviceId,
                        lastHeartRateTimestamp = data.timestamp,
                        connectionStatus = ConnectionStatus.CONNECTED,
                        errorMessage = null
                    )
                }
        }

        batteryPollingJob?.cancel()
        batteryPollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val battery = getBatteryLevel()
                    _uiState.value = _uiState.value.copy(batteryLevel = battery)
                    delay(5000)
                } catch (e: Throwable) {
                    delay(10000)
                }
            }
        }
    }

    /**
     * Cleanup resources
     */
    fun onCleared() {
        detachUi()
        webSocketReconnectJob?.cancel()
        webSocketReconnectJob = null
        heartRateCollectionJob?.cancel()
        heartRateCollectionJob = null
        batteryPollingJob?.cancel()
        batteryPollingJob = null
        stopMonitoring()
        disconnectWebSocket()
        stopBLE()
    }

    private suspend fun runWebSocketReconnectLoop(
        client: WebSocketClient,
        serverUrl: String
    ) {
        var backoffMs = WS_RECONNECT_INITIAL_MS
        var consecutiveWsFailures = 0
        var bleFallbackActive = false

        while (viewModelScope.coroutineContext.isActive && !webSocketManualDisconnect) {
            _uiState.value = _uiState.value.copy(
                connectionStatus = ConnectionStatus.CONNECTING,
                errorMessage = null
            )

            val connectResult = client.connect(serverUrl)
            if (connectResult.isSuccess) {
                consecutiveWsFailures = 0
                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.CONNECTED,
                    errorMessage = null
                )
                backoffMs = WS_RECONNECT_INITIAL_MS
                if (bleFallbackActive) {
                    runCatching { bleClient?.stopAdvertising() }
                    bleFallbackActive = false
                }

                while (viewModelScope.coroutineContext.isActive && !webSocketManualDisconnect && client.isConnected) {
                    delay(1000)
                }

                if (webSocketManualDisconnect) break

                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.CONNECTING,
                    errorMessage = "Connection lost, retrying..."
                )
            } else {
                consecutiveWsFailures += 1
                val reason = connectResult.exceptionOrNull()?.message ?: "unknown error"
                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    errorMessage = "WebSocket connect failed: $reason"
                )

                if (
                    !bleFallbackActive &&
                    transportMode == TransportMode.AUTO &&
                    consecutiveWsFailures >= WS_FAILURES_BEFORE_BLE &&
                    bleClient != null
                ) {
                    val bleFallbackResult = bleClient.startAdvertising(BLE_FALLBACK_SERVICE_NAME)
                    if (bleFallbackResult.isSuccess) {
                        bleFallbackActive = true
                        _uiState.value = _uiState.value.copy(
                            connectionStatus = ConnectionStatus.CONNECTED,
                            errorMessage = "WebSocket unavailable, BLE fallback active"
                        )
                    } else {
                        val bleReason = bleFallbackResult.exceptionOrNull()?.message ?: "unknown error"
                        _uiState.value = _uiState.value.copy(
                            connectionStatus = ConnectionStatus.ERROR,
                            errorMessage = "BLE fallback failed: $bleReason"
                        )
                    }
                }
            }

            if (webSocketManualDisconnect) break
            if (bleFallbackActive) {
                delay(WS_RETRY_WHILE_BLE_MS)
            } else {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(WS_RECONNECT_MAX_MS)
            }
        }
    }

    private suspend fun startBleInternal(serviceName: String) {
        _uiState.value = _uiState.value.copy(
            connectionStatus = ConnectionStatus.CONNECTING
        )

        // BLE manual start should not be overridden by WS reconnect loop state updates.
        webSocketManualDisconnect = true
        webSocketReconnectJob?.cancel()
        webSocketReconnectJob = null
        runCatching { webSocketClient?.disconnect() }

        val client = bleClient
        if (client == null) {
            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.CONNECTED)
            return
        }

        // Force restart to avoid stale BLE scan session when user clicks Start BLE repeatedly.
        runCatching { client.stopAdvertising() }
        delay(250)
        client.startAdvertising(serviceName)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.CONNECTED,
                    errorMessage = null
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    errorMessage = "BLE start failed: ${error.message}"
                )
            }
    }

    companion object {
        private const val WS_RECONNECT_INITIAL_MS = 2000L
        private const val WS_RECONNECT_MAX_MS = 30000L
        private const val WS_FAILURES_BEFORE_BLE = 3
        private const val WS_RETRY_WHILE_BLE_MS = 30000L
        private const val BLE_FALLBACK_SERVICE_NAME = "HeartRate Monitor"
        private const val BLE_MANUAL_SERVICE_NAME = "HeartRateMonitor"
    }
}



