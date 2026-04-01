package com.heartrate.phone.data

/**
 * Control plane for real phone-side BLE relay (Phone -> Desktop).
 */
interface PhoneBleRelayController {
    fun startBleRelay(): Result<Unit>
    fun stopBleRelay()
    fun isBleRelayEnabled(): Boolean
}
