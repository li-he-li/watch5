package com.heartrate.wear.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Tracks whether the watch is currently worn.
 */
class OffBodySensorMonitor(
    context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val offBodySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
    private var listener: SensorEventListener? = null

    fun start(onWornStateChanged: (Boolean) -> Unit) {
        if (sensorManager == null || offBodySensor == null || listener != null) return
        val eventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = event.values.firstOrNull() ?: return
                val worn = value.toInt() == 1
                onWornStateChanged(worn)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = eventListener
        val registered = sensorManager.registerListener(
            eventListener,
            offBodySensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        Log.i(TAG, "off-body sensor registered=$registered")
    }

    fun stop() {
        val currentListener = listener ?: return
        sensorManager?.unregisterListener(currentListener)
        listener = null
    }

    companion object {
        private const val TAG = "P2A-OffBody"
    }
}
