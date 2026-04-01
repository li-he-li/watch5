package com.heartrate.shared.presentation.model

/**
 * Transport selection policy for desktop heart-rate ingestion.
 */
enum class TransportMode {
    AUTO,
    WS_ONLY,
    BLE_ONLY
}

