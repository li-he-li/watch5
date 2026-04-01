package com.heartrate.wear.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.heartrate.shared.data.model.HeartRateData
import com.heartrate.shared.domain.repository.HeartRateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wear-side repository: reads real sensor values and forwards them to phone.
 */
class WearHeartRateRepository(
    private val appContext: Context,
    private val sensorManager: HeartRateSensorManager,
    private val offBodySensorMonitor: OffBodySensorMonitor,
    private val dataLayerSender: WearDataLayerSender
) : HeartRateRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val outgoingFlow = MutableSharedFlow<HeartRateData>(replay = 0, extraBufferCapacity = 32)
    private val sendBuffer = ArrayDeque<HeartRateData>()
    private val sendBufferMutex = Mutex()
    private val listeningLifecycleMutex = Mutex()
    private val sendSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private val samplingPolicy = WearSamplingPolicy()

    private var streamJob: Job? = null
    private var sendJob: Job? = null
    private var listening = false
    @Volatile
    private var deviceWorn: Boolean = true

    override fun observeHeartRate(): Flow<HeartRateData> = outgoingFlow.asSharedFlow()

    override suspend fun startListening() {
        listeningLifecycleMutex.withLock {
            if (listening) return
            offBodySensorMonitor.start { worn ->
                if (deviceWorn != worn) {
                    deviceWorn = worn
                    Log.i(TAG, "off-body changed worn=$worn")
                }
                if (!worn && listening) {
                    scope.launch {
                        Log.w(TAG, "watch removed from wrist, stopping active heart-rate source")
                        stopListening()
                    }
                }
            }
            check(deviceWorn) { "Watch is not being worn; refusing to start heart-rate monitoring" }
            sensorManager.start().getOrThrow()
            listening = true
            Log.i(
                TAG,
                "startListening: sensor active=${sensorManager.isListening()} source=${sensorManager.sourceName()}"
            )

            streamJob?.cancel()
            streamJob = scope.launch {
                sensorManager.heartRateReadings.collect { bpm ->
                    val batteryLevel = readBatteryLevel()
                    val samplingDecision = samplingPolicy.evaluate(
                        currentHeartRate = bpm,
                        batteryLevel = batteryLevel
                    )
                    if (samplingDecision.changed) {
                        sensorManager.updateSamplingRate(samplingDecision.targetHz)
                            .onSuccess {
                                Log.i(
                                    TAG,
                                    "sampling updated to ${samplingDecision.targetHz}Hz reason=${samplingDecision.reason}"
                                )
                            }
                            .onFailure { error ->
                                Log.e(TAG, "failed to apply sampling policy", error)
                            }
                    }

                    val payload = HeartRateData(
                        timestamp = System.currentTimeMillis(),
                        heartRate = bpm,
                        deviceId = Build.MODEL ?: "wear-device",
                        batteryLevel = batteryLevel,
                        signalQuality = 95
                    )
                    Log.d(
                        TAG,
                        "sensor sample bpm=${payload.heartRate} battery=${payload.batteryLevel} ts=${payload.timestamp} hz=${sensorManager.currentSamplingRateHz()}"
                    )
                    outgoingFlow.emit(payload)
                    enqueueForSend(payload)
                }
            }

            sendJob?.cancel()
            sendJob = scope.launch {
                runSendLoop()
            }
        }
    }

    override suspend fun stopListening() {
        listeningLifecycleMutex.withLock {
            listening = false
            streamJob?.cancel()
            streamJob = null
            sendJob?.cancel()
            sendJob = null
            sensorManager.stop()
            offBodySensorMonitor.stop()
            Log.i(TAG, "stopListening")
        }
    }

    override suspend fun getBatteryLevel(): Int? = readBatteryLevel()

    override fun isListening(): Boolean = listening

    private fun readBatteryLevel(): Int? {
        val statusIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = statusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = statusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100) / scale
    }

    private suspend fun enqueueForSend(payload: HeartRateData) {
        sendBufferMutex.withLock {
            if (sendBuffer.size >= SEND_BUFFER_CAPACITY) {
                sendBuffer.removeFirst()
            }
            sendBuffer.addLast(payload)
        }
        sendSignal.trySend(Unit)
    }

    private suspend fun runSendLoop() {
        var backoffMs = SEND_RETRY_INITIAL_MS
        while (scope.coroutineContext.isActive && listening) {
            val nextPayload = sendBufferMutex.withLock { sendBuffer.firstOrNull() }
            if (nextPayload == null) {
                sendSignal.receive()
                continue
            }

            val sendResult = dataLayerSender.send(nextPayload)
            if (sendResult.isSuccess) {
                sendBufferMutex.withLock {
                    if (sendBuffer.firstOrNull() == nextPayload) {
                        sendBuffer.removeFirst()
                    }
                }
                backoffMs = SEND_RETRY_INITIAL_MS
            } else {
                val error = sendResult.exceptionOrNull()
                Log.w(
                    TAG,
                    "send retry in ${backoffMs}ms bpm=${nextPayload.heartRate} reason=${error?.message}"
                )
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(SEND_RETRY_MAX_MS)
            }
        }
    }

    companion object {
        private const val TAG = "P2A-WearRepo"
        private const val SEND_BUFFER_CAPACITY = 100
        private const val SEND_RETRY_INITIAL_MS = 2000L
        private const val SEND_RETRY_MAX_MS = 30000L
    }
}
