package com.heartrate.phone.data

/**
 * Control plane for real phone-side WebSocket relay (Phone -> Desktop).
 */
interface PhoneWebSocketRelayController {
    fun startWebSocketRelay(): Result<Unit>
    fun stopWebSocketRelay()
    fun isWebSocketRelayEnabled(): Boolean
    fun getCurrentLanIpv4Address(): String?
    fun getCurrentWebSocketEndpoint(): String?
}
