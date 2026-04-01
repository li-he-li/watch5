package com.heartrate.shared

/**
 * Common platform interface with expect/actual mechanism
 */
expect object Platform {
    fun getPlatformName(): String
}
