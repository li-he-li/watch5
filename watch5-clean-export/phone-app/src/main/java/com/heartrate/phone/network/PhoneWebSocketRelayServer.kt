package com.heartrate.phone.network

import android.util.Log
import com.heartrate.shared.data.model.HeartRateData
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Collections

/**
 * Phone-side WebSocket relay server for desktop clients.
 */
class PhoneWebSocketRelayServer(
    private val port: Int
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sessions = Collections.synchronizedSet(mutableSetOf<DefaultWebSocketServerSession>())
    private var engine: EmbeddedServer<*, *>? = null

    @Synchronized
    fun start() {
        if (engine != null) return
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                webSocket("/heartrate") {
                    sessions += this
                    Log.i(TAG, "client connected totalSessions=${sessions.size}")
                    try {
                        for (incomingFrame in incoming) {
                            if (incomingFrame is Frame.Close) break
                        }
                    } finally {
                        sessions -= this
                        Log.i(TAG, "client disconnected totalSessions=${sessions.size}")
                    }
                }
            }
        }.start(wait = false)
        Log.i(TAG, "websocket relay started host=0.0.0.0 port=$port path=/heartrate")
    }

    suspend fun broadcast(data: HeartRateData) = withContext(Dispatchers.IO) {
        val sessionCount = sessions.size
        if (sessionCount == 0) {
            Log.d(TAG, "broadcast skipped no clients bpm=${data.heartRate}")
            return@withContext
        }
        val payload = json.encodeToString(HeartRateData.serializer(), data)
        val staleSessions = mutableListOf<DefaultWebSocketServerSession>()

        sessions.toList().forEach { session ->
            runCatching {
                session.send(Frame.Text(payload))
            }.onFailure {
                staleSessions += session
            }
        }

        if (staleSessions.isNotEmpty()) {
            sessions.removeAll(staleSessions.toSet())
            staleSessions.forEach { session ->
                runCatching { session.close() }
            }
        }
        Log.d(
            TAG,
            "broadcast bpm=${data.heartRate} ts=${data.timestamp} clients=$sessionCount stale=${staleSessions.size}"
        )
    }

    @Synchronized
    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1500)
        engine = null
        sessions.clear()
        Log.i(TAG, "websocket relay stopped")
    }

    val isRunning: Boolean
        get() = engine != null

    val clientCount: Int
        get() = sessions.size

    val hasClients: Boolean
        get() = sessions.isNotEmpty()

    val listenPort: Int
        get() = port

    companion object {
        private const val TAG = "P2A-PhoneWsRelay"
    }
}
