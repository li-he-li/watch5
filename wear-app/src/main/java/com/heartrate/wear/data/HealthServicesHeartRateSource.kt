package com.heartrate.wear.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Health Services fallback path.
 * On many devices, updates become batched after screen off / ambient mode.
 */
class HealthServicesHeartRateSource(
    private val context: Context
) : WearHeartRateDataSource {
    private val exerciseClient = HealthServices.getClient(context).exerciseClient
    private val callbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hr-exercise-callback").apply { isDaemon = true }
    }
    private val _heartRateReadings = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var listening = false
    private var currentSamplingHz: Int = DEFAULT_SAMPLING_HZ
    private var callbackRegistered = false
    private var exerciseStarted = false
    private var selectedExerciseType: ExerciseType? = null

    override val name: String = "health-services"
    override val heartRateReadings: Flow<Int> = _heartRateReadings.asSharedFlow()

    private val exerciseUpdateCallback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val sample = update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull()
            val heartRate = sample?.value?.toInt() ?: return
            if (heartRate > 0) {
                _heartRateReadings.tryEmit(heartRate)
            }
        }

        override fun onAvailabilityChanged(
            dataType: androidx.health.services.client.data.DataType<*, *>,
            availability: Availability
        ) {
            if (dataType == DataType.HEART_RATE_BPM) {
                Log.d(TAG, "heart-rate availability changed: $availability")
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onRegistered() {
            Log.i(TAG, "exercise update callback registered")
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "exercise update callback registration failed", throwable)
        }
    }

    override fun start(): Result<Unit> = runCatching {
        checkBodySensorsPermission()
        val exerciseType = selectedExerciseType ?: resolveExerciseTypeForHeartRate().also {
            selectedExerciseType = it
        }

        if (!callbackRegistered) {
            exerciseClient.setUpdateCallback(callbackExecutor, exerciseUpdateCallback)
            callbackRegistered = true
        }

        if (!exerciseStarted) {
            val config = ExerciseConfig.builder(exerciseType)
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .build()
            exerciseClient.startExerciseAsync(config).await()
            exerciseStarted = true
            Log.i(TAG, "exercise started type=$exerciseType metric=HEART_RATE_BPM")
        }
        listening = true
    }

    override fun stop() {
        if (!listening && !exerciseStarted && !callbackRegistered) return

        if (exerciseStarted) {
            runCatching { exerciseClient.endExerciseAsync().await() }
                .onFailure { error ->
                    Log.w(TAG, "failed to end exercise session", error)
                }
            exerciseStarted = false
        }

        if (callbackRegistered) {
            runCatching { exerciseClient.clearUpdateCallbackAsync(exerciseUpdateCallback).await() }
                .onFailure { error ->
                    Log.w(TAG, "failed to clear exercise callback", error)
                }
            callbackRegistered = false
        }
        listening = false
        Log.i(TAG, "health-services monitoring stopped")
    }

    override fun isListening(): Boolean = listening

    override fun updateSamplingRate(targetHz: Int): Result<Unit> = runCatching {
        val safeHz = targetHz.coerceIn(MIN_SAMPLING_HZ, MAX_SAMPLING_HZ)
        if (safeHz == currentSamplingHz) return@runCatching
        currentSamplingHz = safeHz
        Log.i(TAG, "sampling hint updated to ${currentSamplingHz}Hz (managed by Health Services)")
    }

    override fun currentSamplingRateHz(): Int = currentSamplingHz

    private fun checkBodySensorsPermission() {
        val requiredPermissions = listOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.BODY_SENSORS_BACKGROUND
        )
        val missing = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        check(missing.isEmpty()) {
            "Required sensor permissions are missing: ${missing.joinToString()}"
        }
    }

    private fun resolveExerciseTypeForHeartRate(): ExerciseType {
        val capabilities = exerciseClient.getCapabilitiesAsync().await()
        val preferred = listOf(ExerciseType.WALKING, ExerciseType.RUNNING)
        preferred.forEach { type ->
            if (type in capabilities.supportedExerciseTypes) {
                val typeCapabilities = capabilities.getExerciseTypeCapabilities(type)
                if (DataType.HEART_RATE_BPM in typeCapabilities.supportedDataTypes) {
                    return type
                }
            }
        }

        capabilities.typeToCapabilities.entries.firstOrNull { (_, typeCapabilities) ->
            DataType.HEART_RATE_BPM in typeCapabilities.supportedDataTypes
        }?.let { (type, _) ->
            return type
        }

        error("No Health Services exercise type supports HEART_RATE_BPM on this watch")
    }

    private fun <T> ListenableFuture<T>.await(): T {
        return get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        private const val TAG = "P2A-HsHeartRate"
        private const val MIN_SAMPLING_HZ = 1
        private const val MAX_SAMPLING_HZ = 5
        private const val DEFAULT_SAMPLING_HZ = 1
        private const val FUTURE_TIMEOUT_SECONDS = 20L
    }
}
