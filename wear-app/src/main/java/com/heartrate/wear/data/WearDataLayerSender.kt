package com.heartrate.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.heartrate.shared.data.communication.TransmissionPaths
import com.heartrate.shared.data.model.HeartRateData
import kotlinx.serialization.json.Json

/**
 * Sends heart-rate payloads from watch to phone through Wear Data Layer.
 */
class WearDataLayerSender(
    context: Context
) {
    private val nodeClient = Wearable.getNodeClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun send(data: HeartRateData): Result<Unit> {
        val startedAt = System.currentTimeMillis()
        return runCatching {
            val connectedNodes = nodeClient.connectedNodes.awaitResult()
            Log.d(TAG, "connectedNodes=${connectedNodes.size} bpm=${data.heartRate} ts=${data.timestamp}")
            check(connectedNodes.isNotEmpty()) { "No paired phone is connected to this watch" }

            val payload = json.encodeToString(HeartRateData.serializer(), data).encodeToByteArray()
            connectedNodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    TransmissionPaths.HEART_RATE_MESSAGE_PATH,
                    payload
                ).awaitResult()
                Log.d(TAG, "sent to nodeId=${node.id} bpm=${data.heartRate}")
            }
            Log.d(TAG, "send complete bpm=${data.heartRate} costMs=${System.currentTimeMillis() - startedAt}")
            Unit
        }.onFailure { error ->
            Log.e(TAG, "send failed bpm=${data.heartRate} ts=${data.timestamp}", error)
        }
    }

    companion object {
        private const val TAG = "P2A-WearSender"
    }
}
