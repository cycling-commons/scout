package org.cyclingcommons.scout.karoo.radar

import kotlin.math.roundToInt

/**
 * Estimates the nearest car's closing speed from how fast its range shrinks.
 *
 * Karoo's RADAR stream has ranges only (TECHNICAL §8.3), but [org.cyclingcommons.scout.domain.VehicleCounter]
 * needs a valid closing speed to credit a pass. This estimate feeds the live car
 * tally only — FIT `radar_speed` stays 255 so no derived value is presented as measured.
 *
 * Only the nearest target is followed: slot order across samples is not stable, so
 * a departure (count drops) or a jump outward resets the estimate.
 */
class ClosingSpeedEstimator {
    private var prevRangeM: Int = -1
    private var prevCount: Int = 0
    private var prevAtMs: Long = 0L
    private var smoothedMps: Double = -1.0

    /** Latest estimate in km/h, or -1 when unknown. */
    var closingKph: Int = -1
        private set

    fun onSample(
        nearestRangeM: Int,
        count: Int,
        nowMs: Long,
    ): Int {
        if (count <= 0 || nearestRangeM < 0) {
            reset()
            return closingKph
        }
        val sameCar =
            prevRangeM >= 0 &&
                count >= prevCount &&
                nearestRangeM <= prevRangeM + JUMP_TOLERANCE_M
        val dtMs = nowMs - prevAtMs
        if (sameCar && dtMs < MIN_DT_MS) {
            // Too soon for a meaningful delta; keep the older baseline.
            return closingKph
        }
        if (!sameCar) {
            smoothedMps = -1.0
        } else if (dtMs <= MAX_DT_MS) {
            val mps = ((prevRangeM - nearestRangeM) * 1000.0 / dtMs).coerceAtLeast(0.0)
            if (mps <= MAX_MPS) {
                smoothedMps =
                    if (smoothedMps < 0) mps else smoothedMps * (1 - ALPHA) + mps * ALPHA
            }
        }
        prevRangeM = nearestRangeM
        prevCount = count
        prevAtMs = nowMs
        closingKph = if (smoothedMps >= 0) (smoothedMps * 3.6).roundToInt() else -1
        return closingKph
    }

    fun reset() {
        prevRangeM = -1
        prevCount = 0
        prevAtMs = 0L
        smoothedMps = -1.0
        closingKph = -1
    }

    companion object {
        /** Ignore samples closer together than this (range quantisation dominates). */
        const val MIN_DT_MS = 200L

        /** A gap longer than this means we can't trust the delta. */
        const val MAX_DT_MS = 3_000L

        /** Nearest range may grow this much and still be treated as the same car. */
        const val JUMP_TOLERANCE_M = 5

        /** Discard implausible deltas (~180 km/h closing). */
        const val MAX_MPS = 50.0

        /** Exponential smoothing weight for the newest delta. */
        const val ALPHA = 0.5
    }
}
