package com.heartrate.phone.data

import android.util.Log
import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process bridge between Data Layer listener service and relay repository.
 */
object PhoneHeartRateRelayBus {
    private val _heartRateFlow = MutableSharedFlow<HeartRateData>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val heartRateFlow: SharedFlow<HeartRateData> = _heartRateFlow.asSharedFlow()

    fun publish(data: HeartRateData) {
        val accepted = _heartRateFlow.tryEmit(data)
        Log.d(
            TAG,
            "publish bpm=${data.heartRate} ts=${data.timestamp} accepted=$accepted"
        )
    }

    private const val TAG = "P2A-PhoneRelayBus"
}
