package com.heartrate.shared.data.communication

import com.heartrate.shared.data.model.HeartRateData
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Desktop implementation of WebSocketClient using Ktor + OkHttp.
 */
actual class WebSocketClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        install(WebSockets)
    }
    private val _heartRateFlow = MutableStateFlow<HeartRateData?>(null)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _isConnected = MutableStateFlow(false)
    private var session: DefaultClientWebSocketSession? = null
    private var receiveJob: Job? = null

    /**
     * Flow of heart rate data received from the server
     */
    actual val heartRateDataFlow: Flow<HeartRateData>
        get() = _heartRateFlow.filterNotNull()

    /**
     * Connect to a WebSocket server
     */
    actual suspend fun connect(serverUrl: String): Result<Unit> {
        return runCatching {
            if (_isConnected.value) {
                disconnect()
            }
            _connectionState.value = ConnectionState.CONNECTING

            val socketSession = withContext(Dispatchers.IO) {
                client.webSocketSession(serverUrl)
            }

            session = socketSession
            _isConnected.value = true
            _connectionState.value = ConnectionState.CONNECTED

            receiveJob?.cancel()
            receiveJob = scope.launch {
                try {
                    for (frame in socketSession.incoming) {
                        if (frame is Frame.Text) {
                            val heartRateData = json.decodeFromString(
                                HeartRateData.serializer(),
                                frame.readText()
                            )
                            _heartRateFlow.value = heartRateData
                        }
                    }
                } catch (_: Throwable) {
                    if (_isConnected.value) {
                        _connectionState.value = ConnectionState.ERROR
                    }
                } finally {
                    _isConnected.value = false
                    if (_connectionState.value != ConnectionState.ERROR) {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        }.onFailure {
            _isConnected.value = false
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Disconnect from the WebSocket server
     */
    actual suspend fun disconnect() {
        receiveJob?.cancelAndJoin()
        receiveJob = null
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        session = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _isConnected.value = false
    }

    /**
     * Send heart rate data to the connected server
     */
    actual suspend fun sendHeartRateData(data: HeartRateData): Result<Unit> {
        val activeSession = session ?: return Result.failure(
            IllegalStateException("WebSocket is not connected")
        )
        return runCatching {
            val payload = json.encodeToString(HeartRateData.serializer(), data)
            activeSession.send(Frame.Text(payload))
        }
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
