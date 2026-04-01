package com.heartrate.shared.data.communication

import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.flow.Flow

/**
 * Data Layer API client for Wear OS → Phone communication
 *
 * This client handles sending heart rate data from the watch to the phone
 * using the Wear OS Data Layer API.
 */
expect class DataLayerClient {

    /**
     * Flow of heart rate data received from the watch
     */
    val heartRateDataFlow: Flow<HeartRateData>

    /**
     * Send heart rate data from the watch to the phone
     *
     * @param data The heart rate data to send
     * @return true if the data was sent successfully, false otherwise
     */
    suspend fun sendHeartRateData(data: HeartRateData): Result<Unit>

    /**
     * Start listening for data from the connected device
     * Should be called when the app starts
     */
    suspend fun startListening()

    /**
     * Stop listening for data
     * Should be called when the app is paused or destroyed
     */
    fun stopListening()

    /**
     * Check if the device is connected to a paired device
     */
    val isConnected: Boolean
}
