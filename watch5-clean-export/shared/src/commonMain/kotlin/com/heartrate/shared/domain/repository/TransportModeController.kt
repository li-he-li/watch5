package com.heartrate.shared.domain.repository

import com.heartrate.shared.presentation.model.TransportMode

/**
 * Optional capability interface for repositories that support strict transport mode switching.
 */
interface TransportModeController {
    fun setTransportMode(mode: TransportMode)
    fun getTransportMode(): TransportMode
}

