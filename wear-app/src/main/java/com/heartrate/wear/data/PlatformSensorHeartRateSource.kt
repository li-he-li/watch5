package com.heartrate.wear.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Direct platform sensor path. This avoids Health Services exercise batching on some watches.
 */
class PlatformSensorHeartRateSource(
    private val context: Context
) : WearHeartRateDataSource {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val _heartRateReadings = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var listening = false
    private var targetHz = DEFAULT_SAMPLING_HZ
    private var activeSensor: Sensor? = null

    override val name: String = "platform-sensor"
    override val heartRateReadings: Flow<Int> = _heartRateReadings.asSharedFlow()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val heartRate = event.values.firstOrNull()?.toInt() ?: return
            if (heartRate > 0) {
                _heartRateReadings.tryEmit(heartRate)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.d(TAG, "accuracy changed sensor=${sensor?.name} accuracy=$accuracy")
        }
    }

    fun isSupportedOnDevice(): Boolean = resolvePreferredSensor() != null

    override fun start(): Result<Unit> = runCatching {
        checkBodySensorsPermission()
        val sensor = resolvePreferredSensor() ?: error("No platform heart-rate sensor available")
        register(sensor)
        activeSensor = sensor
        listening = true
        Log.i(
            TAG,
            "platform heart-rate sensor started name=${sensor.name} wakeUp=${sensor.isWakeUpSensor} type=${sensor.type}"
        )
    }

    override fun stop() {
        if (!listening) return
        sensorManager.unregisterListener(sensorListener)
        listening = false
        activeSensor = null
        Log.i(TAG, "platform heart-rate sensor stopped")
    }

    override fun isListening(): Boolean = listening

    override fun updateSamplingRate(targetHz: Int): Result<Unit> = runCatching {
        val safeHz = targetHz.coerceIn(MIN_SAMPLING_HZ, MAX_SAMPLING_HZ)
        if (safeHz == this.targetHz) return@runCatching
        this.targetHz = safeHz
        val sensor = activeSensor ?: return@runCatching
        sensorManager.unregisterListener(sensorListener)
        register(sensor)
        Log.i(TAG, "platform sensor sampling updated to ${this.targetHz}Hz")
    }

    override fun currentSamplingRateHz(): Int = targetHz

    private fun register(sensor: Sensor) {
        val samplingPeriodUs = (1_000_000 / targetHz.coerceAtLeast(1))
        val registered = sensorManager.registerListener(
            sensorListener,
            sensor,
            samplingPeriodUs,
            0
        )
        check(registered) { "Failed to register platform heart-rate sensor listener" }
    }

    private fun resolvePreferredSensor(): Sensor? {
        val candidates = sensorManager.getSensorList(Sensor.TYPE_HEART_RATE)
        if (candidates.isEmpty()) return null
        return candidates
            .sortedWith(
                compareByDescending<Sensor> { it.isWakeUpSensor }
                    .thenByDescending { isSamsungDevice() && it.vendor.contains("Samsung", ignoreCase = true) }
            )
            .firstOrNull()
    }

    private fun checkBodySensorsPermission() {
        val requiredPermissions = buildList {
            add(Manifest.permission.BODY_SENSORS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.BODY_SENSORS_BACKGROUND)
            }
        }
        val missing = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        check(missing.isEmpty()) {
            "Required platform sensor permissions are missing: ${missing.joinToString()}"
        }
    }

    private fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            Build.BRAND.equals("samsung", ignoreCase = true)
    }

    companion object {
        private const val TAG = "P2A-PlatformHr"
        private const val DEFAULT_SAMPLING_HZ = 1
        private const val MIN_SAMPLING_HZ = 1
        private const val MAX_SAMPLING_HZ = 5
    }
}
