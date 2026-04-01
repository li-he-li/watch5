package com.heartrate.wear.data

import kotlin.math.abs

/**
 * Dynamic sampling policy with transition guard and low-battery override.
 */
class WearSamplingPolicy(
    private val lowBatteryThreshold: Int = 20,
    private val lowBatteryRecoveryThreshold: Int = 25,
    private val transitionConfirmationCount: Int = 3,
    private val maxHistorySize: Int = 16
) {
    private val history = ArrayDeque<Int>()
    private var currentTier: SamplingTier = SamplingTier.LOW
    private var candidateTier: SamplingTier? = null
    private var candidateHits: Int = 0
    private var lowBatteryMode: Boolean = false

    fun evaluate(currentHeartRate: Int, batteryLevel: Int?): SamplingDecision {
        history.addLast(currentHeartRate)
        while (history.size > maxHistorySize) {
            history.removeFirst()
        }

        if (batteryLevel != null && batteryLevel <= lowBatteryThreshold) {
            lowBatteryMode = true
        } else if (batteryLevel != null && batteryLevel >= lowBatteryRecoveryThreshold) {
            lowBatteryMode = false
        }

        if (lowBatteryMode) {
            val changed = currentTier != SamplingTier.LOW
            currentTier = SamplingTier.LOW
            candidateTier = null
            candidateHits = 0
            return SamplingDecision(
                targetHz = currentTier.hz,
                changed = changed,
                lowBatteryMode = true,
                reason = "low-battery"
            )
        }

        val nextTier = tierFromVariance()
        if (nextTier == currentTier) {
            candidateTier = null
            candidateHits = 0
            return SamplingDecision(
                targetHz = currentTier.hz,
                changed = false,
                lowBatteryMode = false,
                reason = "steady"
            )
        }

        if (candidateTier == nextTier) {
            candidateHits += 1
        } else {
            candidateTier = nextTier
            candidateHits = 1
        }

        if (candidateHits >= transitionConfirmationCount) {
            currentTier = nextTier
            candidateTier = null
            candidateHits = 0
            return SamplingDecision(
                targetHz = currentTier.hz,
                changed = true,
                lowBatteryMode = false,
                reason = "transition-confirmed"
            )
        }

        return SamplingDecision(
            targetHz = currentTier.hz,
            changed = false,
            lowBatteryMode = false,
            reason = "transition-pending"
        )
    }

    private fun tierFromVariance(): SamplingTier {
        if (history.size < 4) return SamplingTier.LOW
        var sumDelta = 0.0
        var count = 0
        var previous = history.first()
        history.drop(1).forEach { value ->
            sumDelta += abs(value - previous).toDouble()
            previous = value
            count += 1
        }
        if (count == 0) return SamplingTier.LOW
        val meanDelta = sumDelta / count
        return when {
            meanDelta < 2.5 -> SamplingTier.LOW
            meanDelta < 6.0 -> SamplingTier.MEDIUM
            else -> SamplingTier.HIGH
        }
    }
}

data class SamplingDecision(
    val targetHz: Int,
    val changed: Boolean,
    val lowBatteryMode: Boolean,
    val reason: String
)

enum class SamplingTier(val hz: Int) {
    LOW(1),
    MEDIUM(3),
    HIGH(5)
}
