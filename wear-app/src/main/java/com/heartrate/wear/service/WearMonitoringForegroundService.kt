package com.heartrate.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.heartrate.shared.domain.repository.HeartRateRepository
import com.heartrate.wear.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps heart-rate monitoring alive in background with a persistent notification.
 */
class WearMonitoringForegroundService : Service(), KoinComponent {
    private val repository: HeartRateRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForegroundWithHealthType(buildNotification(status = "Starting", heartRate = null))
        acquireCpuWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoringAndSelf()
            }

            else -> {
                startMonitoringIfNeeded()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.launch {
            runCatching { repository.stopListening() }
        }
        releaseCpuWakeLock()
        super.onDestroy()
    }

    private fun startMonitoringIfNeeded() {
        if (monitorJob != null) return
        monitorJob = scope.launch {
            runCatching { repository.startListening() }
                .onFailure { error ->
                    Log.e(TAG, "startListening failed", error)
                    updateNotification(status = "Sensor unavailable", heartRate = null)
                    stopSelf()
                    monitorJob = null
                    return@launch
                }

            repository.observeHeartRate().collect { data ->
                val batteryLevel = data.batteryLevel
                updateNotification(
                    status = if (batteryLevel != null && batteryLevel <= LOW_BATTERY_THRESHOLD) {
                        "Low battery mode"
                    } else {
                        "Monitoring"
                    },
                    heartRate = data.heartRate
                )
            }
        }
    }

    private fun stopMonitoringAndSelf() {
        monitorJob?.cancel()
        monitorJob = null
        scope.launch {
            runCatching { repository.stopListening() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            releaseCpuWakeLock()
            stopSelf()
        }
    }

    private fun acquireCpuWakeLock() {
        if (cpuWakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        cpuWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseCpuWakeLock() {
        cpuWakeLock?.let { wakeLock ->
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
        cpuWakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Heart Rate Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground monitoring status for Wear heart-rate sampling"
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundWithHealthType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String, heartRate: Int?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status = status, heartRate = heartRate))
    }

    private fun buildNotification(status: String, heartRate: Int?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WearMonitoringForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (heartRate != null && heartRate > 0) "$heartRate BPM" else "Heart Rate Monitor"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText("Status: $status")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "P2A-WearFgs"
        private const val CHANNEL_ID = "wear_monitoring"
        private const val NOTIFICATION_ID = 1201
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val WAKE_LOCK_TAG = "com.heartrate.wear:monitoring-cpu"
        private const val ACTION_START = "com.heartrate.wear.action.START_MONITORING"
        private const val ACTION_STOP = "com.heartrate.wear.action.STOP_MONITORING"

        fun start(context: Context) {
            val intent = Intent(context, WearMonitoringForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WearMonitoringForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
