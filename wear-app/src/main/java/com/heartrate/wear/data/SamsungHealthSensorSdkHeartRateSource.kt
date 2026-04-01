package com.heartrate.wear.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Optional Samsung real-time path.
 * Works only when samsung-health-sensor-api is bundled and Health Platform is available.
 */
class SamsungHealthSensorSdkHeartRateSource(
    private val context: Context
) : WearHeartRateDataSource {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _heartRateReadings = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var listening = false
    private var service: Any? = null
    private var tracker: Any? = null
    private var trackerListener: Any? = null
    private var flushJob: Job? = null

    override val name: String = "samsung-health-sensor-sdk"
    override val heartRateReadings: Flow<Int> = _heartRateReadings.asSharedFlow()

    fun isSupportedOnDevice(): Boolean {
        if (!isLikelySamsungWatch()) return false
        return runCatching {
            loadClass(CLASS_HEALTH_TRACKING_SERVICE)
            loadClass(CLASS_CONNECTION_LISTENER)
            loadClass(CLASS_HEALTH_TRACKER_TYPE)
            loadClass(CLASS_TRACKER_EVENT_LISTENER)
            loadClass(CLASS_DATA_POINT)
            loadClass(CLASS_VALUE_KEY)
            loadClass(CLASS_VALUE_KEY_HEART_RATE_SET)
        }.isSuccess
    }

    override fun start(): Result<Unit> = runCatching {
        checkBodySensorsPermission()
        val trackerInstance = connectAndAcquireTracker()
        registerTrackerListener(trackerInstance)
        listening = true
        startFlushLoopIfNeeded(trackerInstance)
        Log.i(TAG, "Samsung heart-rate tracker started")
    }

    override fun stop() {
        listening = false
        flushJob?.cancel()
        flushJob = null

        runCatching {
            val trackerInstance = tracker
            if (trackerInstance != null) {
                trackerInstance.javaClass.getMethod("unsetEventListener").invoke(trackerInstance)
            }
        }.onFailure { error ->
            Log.w(TAG, "failed to unset Samsung tracker listener", error)
        }

        runCatching {
            service?.javaClass?.getMethod("disconnectService")?.invoke(service)
        }.onFailure { error ->
            Log.w(TAG, "failed to disconnect Samsung tracking service", error)
        }

        trackerListener = null
        tracker = null
        service = null
    }

    override fun isListening(): Boolean = listening

    override fun updateSamplingRate(targetHz: Int): Result<Unit> = Result.success(Unit)

    override fun currentSamplingRateHz(): Int = REALTIME_SAMPLING_HINT_HZ

    private fun connectAndAcquireTracker(): Any {
        if (tracker != null && service != null) return tracker!!

        val trackerTypeClass = loadClass(CLASS_HEALTH_TRACKER_TYPE)
        val targetTrackerType = trackerTypeClass.getField("HEART_RATE_CONTINUOUS").get(null)
        val connectionListenerClass = loadClass(CLASS_CONNECTION_LISTENER)
        val healthTrackingServiceClass = loadClass(CLASS_HEALTH_TRACKING_SERVICE)
        val completedTracker = AtomicReference<Any?>()
        val completedError = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)

        val listener = Proxy.newProxyInstance(
            connectionListenerClass.classLoader,
            arrayOf(connectionListenerClass)
        ) { _, method, args ->
            when (method.name) {
                "onConnectionSuccess" -> {
                    try {
                        val serviceInstance = service
                            ?: error("Samsung HealthTrackingService missing after connection")
                        val capability = serviceInstance.javaClass
                            .getMethod("getTrackingCapability")
                            .invoke(serviceInstance)
                        val supportedTypes = capability.javaClass
                            .getMethod("getSupportHealthTrackerTypes")
                            .invoke(capability) as? Iterable<*>
                            ?: emptyList<Any?>()
                        check(supportedTypes.any { it == targetTrackerType }) {
                            "Samsung HEART_RATE_CONTINUOUS is not supported on this watch"
                        }
                        val trackerInstance = serviceInstance.javaClass
                            .getMethod("getHealthTracker", trackerTypeClass)
                            .invoke(serviceInstance, targetTrackerType)
                        completedTracker.set(trackerInstance)
                    } catch (error: Throwable) {
                        completedError.set(error)
                    } finally {
                        latch.countDown()
                    }
                    null
                }

                "onConnectionFailed" -> {
                    completedError.set(
                        (args?.firstOrNull() as? Throwable)
                            ?: IllegalStateException("Samsung Health Platform connection failed")
                    )
                    latch.countDown()
                    null
                }

                "onConnectionEnded" -> {
                    listening = false
                    Log.w(TAG, "Samsung Health Platform connection ended")
                    null
                }

                else -> null
            }
        }

        val ctor = healthTrackingServiceClass.getConstructor(connectionListenerClass, Context::class.java)
        val serviceInstance = ctor.newInstance(listener, context)
        service = serviceInstance
        serviceInstance.javaClass.getMethod("connectService").invoke(serviceInstance)

        check(latch.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Timed out waiting for Samsung Health Platform connection"
        }
        completedError.get()?.let { throw it }
        val trackerInstance = completedTracker.get() ?: error("Samsung tracker acquisition failed")
        tracker = trackerInstance
        return trackerInstance
    }

    private fun registerTrackerListener(trackerInstance: Any) {
        if (trackerListener != null) return

        val trackerListenerClass = loadClass(CLASS_TRACKER_EVENT_LISTENER)
        val dataPointClass = loadClass(CLASS_DATA_POINT)
        val valueKeyClass = loadClass(CLASS_VALUE_KEY)
        val heartRateSetClass = loadClass(CLASS_VALUE_KEY_HEART_RATE_SET)
        val heartRateKey = heartRateSetClass.getField("HEART_RATE").get(null)
        val heartRateStatusKey = heartRateSetClass.getField("HEART_RATE_STATUS").get(null)
        val validStatus = 1

        val listener = Proxy.newProxyInstance(
            trackerListenerClass.classLoader,
            arrayOf(trackerListenerClass)
        ) { _, method, args ->
            when (method.name) {
                "onDataReceived" -> {
                    val samples = args?.firstOrNull() as? Iterable<*> ?: emptyList<Any?>()
                    samples.forEach { dataPoint ->
                        if (dataPointClass.isInstance(dataPoint)) {
                            runCatching {
                                val status = dataPointClass
                                    .getMethod("getValue", valueKeyClass)
                                    .invoke(dataPoint, heartRateStatusKey) as? Number
                                val heartRate = dataPointClass
                                    .getMethod("getValue", valueKeyClass)
                                    .invoke(dataPoint, heartRateKey) as? Number
                                if (status?.toInt() == validStatus && heartRate != null && heartRate.toInt() > 0) {
                                    _heartRateReadings.tryEmit(heartRate.toInt())
                                }
                            }.onFailure { error ->
                                Log.w(TAG, "failed to parse Samsung data point", error)
                            }
                        }
                    }
                    null
                }

                "onFlushCompleted" -> null
                "onError" -> {
                    Log.w(TAG, "Samsung tracker reported error=${args?.firstOrNull()}")
                    null
                }

                else -> null
            }
        }

        trackerListener = listener
        trackerInstance.javaClass.getMethod("setEventListener", trackerListenerClass)
            .invoke(trackerInstance, listener)
    }

    private fun startFlushLoopIfNeeded(trackerInstance: Any) {
        if (flushJob != null) return
        flushJob = scope.launch {
            while (listening) {
                delay(FLUSH_INTERVAL_MS)
                runCatching {
                    trackerInstance.javaClass.getMethod("flush").invoke(trackerInstance)
                }
            }
        }
    }

    private fun checkBodySensorsPermission() {
        val requiredPermissions = listOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.BODY_SENSORS_BACKGROUND
        )
        val missing = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        check(missing.isEmpty()) {
            "Required Samsung sensor permissions are missing: ${missing.joinToString()}"
        }
    }

    private fun isLikelySamsungWatch(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty()
        val brand = android.os.Build.BRAND.orEmpty()
        return manufacturer.equals("samsung", ignoreCase = true) ||
            brand.equals("samsung", ignoreCase = true)
    }

    private fun loadClass(name: String): Class<*> = Class.forName(name)

    companion object {
        private const val TAG = "P2A-SamsungHr"
        private const val FLUSH_INTERVAL_MS = 1000L
        private const val CONNECTION_TIMEOUT_SECONDS = 20L
        private const val REALTIME_SAMPLING_HINT_HZ = 1

        private const val CLASS_HEALTH_TRACKING_SERVICE =
            "com.samsung.android.service.health.tracking.HealthTrackingService"
        private const val CLASS_CONNECTION_LISTENER =
            "com.samsung.android.service.health.tracking.ConnectionListener"
        private const val CLASS_TRACKER_EVENT_LISTENER =
            "com.samsung.android.service.health.tracking.HealthTracker\$TrackerEventListener"
        private const val CLASS_HEALTH_TRACKER_TYPE =
            "com.samsung.android.service.health.tracking.data.HealthTrackerType"
        private const val CLASS_DATA_POINT =
            "com.samsung.android.service.health.tracking.data.DataPoint"
        private const val CLASS_VALUE_KEY =
            "com.samsung.android.service.health.tracking.data.ValueKey"
        private const val CLASS_VALUE_KEY_HEART_RATE_SET =
            "com.samsung.android.service.health.tracking.data.ValueKey\$HeartRateSet"
    }
}
