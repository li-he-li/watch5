package com.heartrate.phone.data.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.heartrate.shared.data.model.HeartRateData

@Entity(tableName = "heart_rate_records")
data class HeartRateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val heartRate: Int,
    val deviceId: String,
    val batteryLevel: Int?,
    val signalQuality: Int?,
    val synced: Boolean,
    val createdAt: Long
) {
    fun toDomain(): HeartRateData = HeartRateData(
        timestamp = timestamp,
        heartRate = heartRate,
        deviceId = deviceId,
        batteryLevel = batteryLevel,
        signalQuality = signalQuality
    )

    companion object {
        fun fromDomain(
            data: HeartRateData,
            synced: Boolean
        ): HeartRateEntity = HeartRateEntity(
            timestamp = data.timestamp,
            heartRate = data.heartRate,
            deviceId = data.deviceId,
            batteryLevel = data.batteryLevel,
            signalQuality = data.signalQuality,
            synced = synced,
            createdAt = System.currentTimeMillis()
        )
    }
}

