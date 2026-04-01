package com.heartrate.phone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.heartrate.phone.BuildConfig
import com.heartrate.phone.data.PhoneHeartRateRelayBus
import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug-only broadcast receiver for injecting synthetic heart-rate samples on phone.
 */
class PhoneDebugInjectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INJECT_HEART_RATE) return
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "ignoring debug injection on non-debug build")
            return
        }

        val bpm = intent.getIntExtra(EXTRA_BPM, DEFAULT_BPM).coerceIn(1, 240)
        val count = intent.getIntExtra(EXTRA_COUNT, DEFAULT_COUNT).coerceIn(1, MAX_COUNT)
        val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS)
            .coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

        val pendingResult = goAsync()
        scope.launch {
            runCatching {
                repeat(count) { index ->
                    val data = HeartRateData(
                        timestamp = System.currentTimeMillis(),
                        heartRate = bpm,
                        deviceId = DEBUG_DEVICE_ID,
                        batteryLevel = null,
                        signalQuality = 100
                    )
                    PhoneHeartRateRelayBus.publish(data)
                    Log.i(
                        TAG,
                        "injected sample index=${index + 1}/$count bpm=$bpm intervalMs=$intervalMs"
                    )
                    if (index < count - 1) {
                        delay(intervalMs)
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "debug injection failed", error)
            }
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "PhoneDebugInject"
        private const val DEBUG_DEVICE_ID = "phone-debug"

        const val ACTION_INJECT_HEART_RATE = "com.heartrate.phone.action.DEBUG_INJECT_HEART_RATE"
        const val EXTRA_BPM = "bpm"
        const val EXTRA_COUNT = "count"
        const val EXTRA_INTERVAL_MS = "intervalMs"

        private const val DEFAULT_BPM = 72
        private const val DEFAULT_COUNT = 1
        private const val MAX_COUNT = 120
        private const val DEFAULT_INTERVAL_MS = 1_000L
        private const val MIN_INTERVAL_MS = 100L
        private const val MAX_INTERVAL_MS = 10_000L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
