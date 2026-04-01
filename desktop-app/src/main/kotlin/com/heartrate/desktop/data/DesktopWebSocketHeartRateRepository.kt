package com.heartrate.desktop.data

import com.heartrate.shared.data.communication.BleClient
import com.heartrate.shared.data.communication.WebSocketClient
import com.heartrate.shared.data.model.HeartRateData
import com.heartrate.shared.domain.repository.HeartRateRepository
import com.heartrate.shared.domain.repository.TransportModeController
import com.heartrate.shared.presentation.model.TransportMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Desktop-side repository backed by WebSocket stream with BLE fallback stream.
 */
class DesktopWebSocketHeartRateRepository(
    private val webSocketClient: WebSocketClient,
    private val bleClient: BleClient
) : HeartRateRepository, TransportModeController {
    private enum class Source {
        WS,
        BLE
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listening = false
    @Volatile
    private var transportMode: TransportMode = TransportMode.AUTO

    override fun observeHeartRate(): Flow<HeartRateData> {
        return if (listening) {
            channelFlow {
                var lastEmittedKey: String? = null
                suspend fun emitIfChanged(data: HeartRateData, source: Source) {
                    if (!isSourceAllowed(source)) return
                    val key = "${data.deviceId}|${data.heartRate}|${data.timestamp}"
                    if (key == lastEmittedKey) return
                    lastEmittedKey = key
                    trace("emit bpm=${data.heartRate} device=${data.deviceId} ts=${data.timestamp}")
                    send(data)
                }

                // Accept both streams; avoid hard-blocking BLE based on WS state alone.
                launch {
                    webSocketClient.heartRateDataFlow.collect { emitIfChanged(it, Source.WS) }
                }
                launch {
                    bleClient.heartRateDataFlow.collect { emitIfChanged(it, Source.BLE) }
                }
            }
        } else {
            emptyFlow()
        }
    }

    override suspend fun startListening() {
        listening = true
        trace("startListening")
        applyTransportModeNow()
    }

    override suspend fun stopListening() {
        listening = false
        trace("stopListening")
        runCatching { bleClient.stopAdvertising() }
    }

    override suspend fun getBatteryLevel(): Int? = null

    override fun isListening(): Boolean = listening

    override fun setTransportMode(mode: TransportMode) {
        if (transportMode == mode) return
        transportMode = mode
        trace("transport mode -> $mode")
        if (listening) {
            scope.launch {
                applyTransportModeNow()
            }
        }
    }

    override fun getTransportMode(): TransportMode = transportMode

    private fun isSourceAllowed(source: Source): Boolean {
        return when (transportMode) {
            TransportMode.AUTO -> true
            TransportMode.WS_ONLY -> source == Source.WS
            TransportMode.BLE_ONLY -> source == Source.BLE
        }
    }

    private suspend fun applyTransportModeNow() {
        when (transportMode) {
            TransportMode.WS_ONLY -> {
                runCatching { bleClient.stopAdvertising() }
            }

            TransportMode.AUTO,
            TransportMode.BLE_ONLY -> {
                // Force a clean BLE restart so stale scan sessions do not make Start BLE appear ineffective.
                runCatching { bleClient.stopAdvertising() }
                runCatching { bleClient.startAdvertising("HeartRateMonitor") }
            }
        }
    }

    private fun trace(message: String) {
        val line = "DESKTOP_REPO ${System.currentTimeMillis()} $message"
        println(line)
        runCatching {
            val dirPath = System.getProperty("java.io.tmpdir").orEmpty()
            if (dirPath.isBlank()) return@runCatching
            val dir = File(dirPath)
            if (!dir.exists()) dir.mkdirs()
            File(dir, "desktop_ble_trace.log").appendText("$line\n")
        }
    }
}
