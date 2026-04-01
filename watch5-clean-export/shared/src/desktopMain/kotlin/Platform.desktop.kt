package com.heartrate.shared

/**
 * Desktop-specific implementation
 */
actual object Platform {
    actual fun getPlatformName(): String {
        val os = System.getProperty("os.name")
        return when {
            os.contains("Windows", ignoreCase = true) -> "Windows Desktop"
            os.contains("Mac", ignoreCase = true) || os.contains("Darwin") -> "macOS Desktop"
            os.contains("Linux", ignoreCase = true) -> "Linux Desktop"
            else -> "Desktop ($os)"
        }
    }
}
