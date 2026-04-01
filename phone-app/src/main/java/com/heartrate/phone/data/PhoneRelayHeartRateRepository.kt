package com.heartrate.phone.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.util.Log
import com.heartrate.phone.data.persistence.HeartRateDao
import com.heartrate.phone.data.persistence.HeartRateEntity
import com.heartrate.phone.network.PhoneBleGattServer
import com.heartrate.phone.network.PhoneWebSocketRelayServer
import com.heartrate.shared.data.model.HeartRateData
import com.heartrate.shared.domain.repository.HeartRateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Phone-side repository: consumes watch data and relays to desktop via WebSocket.
 */
class PhoneRelayHeartRateRepository(
    private val appContext: Context,
    private val relayServer: PhoneWebSocketRelayServer,
    private val bleGattServer: PhoneBleGattServer,
    private val heartRateDao: HeartRateDao
) : HeartRateRepository, PhoneBleRelayController, PhoneWebSocketRelayController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var relayJob: Job? = null
    private var flushJob: Job? = null
    private var listening = false
    @Volatile
    private var wsRelayEnabled = false
    @Volatile
    private var bleRelayEnabled = false

    override fun observeHeartRate(): Flow<HeartRateData> = PhoneHeartRateRelayBus.heartRateFlow

    override suspend fun startListening() {
        if (listening) return
        listening = true
        if (wsRelayEnabled) {
            startWebSocketServerIfNeeded().onFailure {
                Log.w(TAG, "WebSocket relay start failed while listening start", it)
            }
        }
        if (bleRelayEnabled) {
            startBleServerIfNeeded().onFailure {
                Log.w(TAG, "BLE relay start failed while listening start", it)
            }
        }
        Log.i(TAG, "startListening: relay server started")
        relayJob?.cancel()
        relayJob = scope.launch {
            PhoneHeartRateRelayBus.heartRateFlow.collect { data ->
                runCatching {
                    val rowId = heartRateDao.insert(
                        HeartRateEntity.fromDomain(
                            data = data,
                            synced = false
                        )
                    )
                    if (relayViaBestChannel(data)) {
                        heartRateDao.markSynced(listOf(rowId))
                    }
                    Log.d(TAG, "relayed+bqueued bpm=${data.heartRate} ts=${data.timestamp}")
                }.onFailure { error ->
                    Log.e(TAG, "relay handling failed bpm=${data.heartRate}", error)
                }
            }
        }

        flushJob?.cancel()
        flushJob = scope.launch {
            flushPendingLoop()
        }
    }

    override suspend fun stopListening() {
        listening = false
        relayJob?.cancel()
        relayJob = null
        flushJob?.cancel()
        flushJob = null
        relayServer.stop()
        bleGattServer.stop()
        Log.i(TAG, "stopListening: relay server stopped")
    }

    override fun startBleRelay(): Result<Unit> {
        bleRelayEnabled = true
        val result = startBleServerIfNeeded()
        result.onSuccess {
            Log.i(TAG, "BLE relay enabled")
        }.onFailure {
            Log.w(TAG, "BLE relay enable failed", it)
        }
        return result
    }

    override fun stopBleRelay() {
        bleRelayEnabled = false
        bleGattServer.stop()
        Log.i(TAG, "BLE relay disabled")
    }

    override fun isBleRelayEnabled(): Boolean = bleRelayEnabled

    override fun startWebSocketRelay(): Result<Unit> {
        wsRelayEnabled = true
        val result = startWebSocketServerIfNeeded()
        result.onSuccess {
            Log.i(TAG, "WebSocket relay enabled")
        }.onFailure {
            Log.w(TAG, "WebSocket relay enable failed", it)
        }
        return result
    }

    override fun stopWebSocketRelay() {
        wsRelayEnabled = false
        relayServer.stop()
        Log.i(TAG, "WebSocket relay disabled")
    }

    override fun isWebSocketRelayEnabled(): Boolean = wsRelayEnabled

    override fun getCurrentLanIpv4Address(): String? {
        val activeNetworkIpv4 = runCatching { resolveActiveNetworkIpv4() }.getOrNull()
        if (!activeNetworkIpv4.isNullOrBlank()) {
            return activeNetworkIpv4
        }
        return runCatching { resolveInterfaceIpv4() }.getOrNull()
    }

    override fun getCurrentWebSocketEndpoint(): String? {
        val lanIpv4 = getCurrentLanIpv4Address() ?: return null
        return "ws://$lanIpv4:${relayServer.listenPort}/heartrate"
    }

    override suspend fun getBatteryLevel(): Int? {
        val statusIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = statusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = statusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100) / scale
    }

    override fun isListening(): Boolean = listening

    private suspend fun flushPendingLoop() {
        while (scope.coroutineContext.isActive) {
            runCatching {
                val pending = heartRateDao.getPending(FLUSH_BATCH_SIZE)
                if (pending.isEmpty()) {
                    delay(FLUSH_IDLE_DELAY_MS)
                    return@runCatching
                }

                val syncedIds = mutableListOf<Long>()
                pending.forEach { entity ->
                    val sent = relayViaBestChannel(entity.toDomain())
                    if (sent) {
                        syncedIds += entity.id
                    } else {
                        return@forEach
                    }
                }

                if (syncedIds.isNotEmpty()) {
                    heartRateDao.markSynced(syncedIds)
                    Log.d(TAG, "flushed pending=${syncedIds.size}")
                    delay(FLUSH_CONTINUE_DELAY_MS)
                } else {
                    delay(FLUSH_RETRY_DELAY_MS)
                }
            }.onFailure { error ->
                Log.e(TAG, "flushPendingLoop failed", error)
                delay(FLUSH_RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun relayViaBestChannel(data: HeartRateData): Boolean {
        var wsSent = false
        if (wsRelayEnabled) {
            if (startWebSocketServerIfNeeded().isFailure) {
                Log.w(TAG, "WebSocket relay unavailable, fallback to BLE")
            } else if (relayServer.hasClients) {
                val wsResult = runCatching { relayServer.broadcast(data) }
                if (wsResult.isSuccess) {
                    wsSent = true
                } else {
                    Log.w(TAG, "WebSocket relay failed, fallback to BLE", wsResult.exceptionOrNull())
                }
            }
        }

        if (!bleRelayEnabled) {
            return wsSent
        }

        if (startBleServerIfNeeded().isFailure) {
            return wsSent
        }
        val bleSent = bleGattServer.sendHeartRate(data).isSuccess
        return wsSent || bleSent
    }

    private fun startBleServerIfNeeded(): Result<Unit> {
        if (bleGattServer.isRunning) {
            return Result.success(Unit)
        }
        return bleGattServer.start()
    }

    private fun startWebSocketServerIfNeeded(): Result<Unit> {
        return runCatching {
            if (!relayServer.isRunning) {
                relayServer.start()
            }
        }
    }

    private fun resolveActiveNetworkIpv4(): String? {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null
        return linkProperties.linkAddresses
            .asSequence()
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    private fun resolveInterfaceIpv4(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                continue
            }
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    return address.hostAddress
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "P2A-PhoneRepo"
        private const val FLUSH_BATCH_SIZE = 100
        private const val FLUSH_IDLE_DELAY_MS = 3_000L
        private const val FLUSH_CONTINUE_DELAY_MS = 300L
        private const val FLUSH_RETRY_DELAY_MS = 1_500L
    }
}
