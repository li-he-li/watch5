package com.heartrate.phone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.heartrate.phone.MainActivity
import com.heartrate.shared.domain.repository.HeartRateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground host for phone-side WebSocket relay lifecycle.
 */
class PhoneRelayForegroundService : Service(), KoinComponent {
    private val repository: HeartRateRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var relayJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(status = "Starting relay", heartRate = null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRelayAndSelf()
            else -> startRelayIfNeeded()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        relayJob?.cancel()
        super.onDestroy()
    }

    private fun startRelayIfNeeded() {
        if (relayJob != null) return
        relayJob = scope.launch {
            runCatching { repository.startListening() }
                .onFailure {
                    updateNotification(status = "Relay start failed", heartRate = null)
                    stopSelf()
                    return@launch
                }

            repository.observeHeartRate().collect { data ->
                updateNotification(status = "Relay active", heartRate = data.heartRate)
            }
        }
    }

    private fun stopRelayAndSelf() {
        relayJob?.cancel()
        relayJob = null
        scope.launch {
            runCatching { repository.stopListening() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Phone Relay Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground relay service for watch to desktop transmission"
        }
        manager.createNotificationChannel(channel)
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
            Intent(this, PhoneRelayForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (heartRate != null && heartRate > 0) "Relay $heartRate BPM" else "Heart Rate Relay"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText("Status: $status")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "phone_relay"
        private const val NOTIFICATION_ID = 2201
        private const val ACTION_START = "com.heartrate.phone.action.START_RELAY"
        private const val ACTION_STOP = "com.heartrate.phone.action.STOP_RELAY"

        fun start(context: Context) {
            val intent = Intent(context, PhoneRelayForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PhoneRelayForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
