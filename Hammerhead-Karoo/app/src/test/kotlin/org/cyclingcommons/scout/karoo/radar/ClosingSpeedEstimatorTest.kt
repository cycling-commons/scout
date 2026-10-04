package org.cyclingcommons.scout.karoo.radar

import org.cyclingcommons.scout.domain.VehicleCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class ClosingSpeedEstimatorTest {
    @Test
    fun firstSampleHasNoEstimate() {
        val e = ClosingSpeedEstimator()
        assertEquals(-1, e.onSample(nearestRangeM = 60, count = 1, nowMs = 0))
    }

    @Test
    fun steadyApproachGivesClosingSpeed() {
        val e = ClosingSpeedEstimator()
        e.onSample(60, 1, 0)
        e.onSample(50, 1, 1_000)
        // 10 m/s = 36 km/h
        assertEquals(36, e.onSample(40, 1, 2_000))
    }

    @Test
    fun carFallingBackClampsToZero() {
        val e = ClosingSpeedEstimator()
        e.onSample(40, 1, 0)
        assertEquals(0, e.onSample(43, 1, 1_000))
    }

    @Test
    fun departureResetsEstimate() {
        val e = ClosingSpeedEstimator()
        e.onSample(60, 2, 0)
        e.onSample(50, 2, 1_000)
        // Nearest car passed: count drops and the next car is the new nearest.
        assertEquals(-1, e.onSample(70, 1, 2_000))
    }

    @Test
    fun outwardJumpResetsEstimate() {
        val e = ClosingSpeedEstimator()
        e.onSample(30, 1, 0)
        e.onSample(20, 1, 1_000)
        assertEquals(-1, e.onSample(80, 1, 2_000))
    }

    @Test
    fun noTargetsResets() {
        val e = ClosingSpeedEstimator()
        e.onSample(30, 1, 0)
        e.onSample(20, 1, 1_000)
        assertEquals(-1, e.onSample(-1, 0, 2_000))
    }

    @Test
    fun samplesTooCloseTogetherKeepBaseline() {
        val e = ClosingSpeedEstimator()
        e.onSample(60, 1, 0)
        assertEquals(-1, e.onSample(59, 1, 100))
        // Delta still measured from the t=0 baseline: 10 m over 1 s.
        assertEquals(36, e.onSample(50, 1, 1_000))
    }

    @Test
    fun longGapDoesNotProduceEstimate() {
        val e = ClosingSpeedEstimator()
        e.onSample(60, 1, 0)
        assertEquals(-1, e.onSample(20, 1, 5_000))
    }

    @Test
    fun implausibleDeltaIsDiscarded() {
        val e = ClosingSpeedEstimator()
        e.onSample(100, 1, 0)
        assertEquals(-1, e.onSample(30, 1, 1_000))
    }

    @Test
    fun rangeOnlyOvertakeIsCreditedByVehicleCounter() {
        val e = ClosingSpeedEstimator()
        val counter = VehicleCounter()
        val ranges = listOf(40, 30, 20, 10, 5)
        ranges.forEachIndexed { i, r ->
            val kph = e.onSample(r, 1, i * 1_000L)
            counter.onSample(
                tracking = true,
                occupiedCount = 1,
                nearestClosingKph = kph,
                nearestRangeM = r,
                riderKph = 25,
            )
        }
        val leaveKph = e.onSample(-1, 0, ranges.size * 1_000L)
        counter.onSample(
            tracking = true,
            occupiedCount = 0,
            nearestClosingKph = leaveKph,
            nearestRangeM = -1,
            riderKph = 25,
        )
        assertEquals(1, counter.carCount)
    }
}
