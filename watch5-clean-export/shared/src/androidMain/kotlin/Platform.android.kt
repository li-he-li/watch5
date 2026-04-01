package com.heartrate.shared

/**
 * Android-specific implementation
 */
actual object Platform {
    actual fun getPlatformName(): String {
        return "Android"
    }
}
