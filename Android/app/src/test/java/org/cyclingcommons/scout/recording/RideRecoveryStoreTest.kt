package org.cyclingcommons.scout.recording

import org.cyclingcommons.scout.domain.PoiType
import org.cyclingcommons.scout.domain.QueuedTag
import org.cyclingcommons.scout.domain.RideSessionSnapshot
import org.cyclingcommons.scout.domain.Surface
import org.cyclingcommons.scout.domain.TagTallies
import org.cyclingcommons.scout.domain.TagTalliesSnapshot
import org.cyclingcommons.scout.domain.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RideRecoveryStoreTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val tallies = TagTallies().apply {
            countTap(PoiType.SURFACE, Surface.GRAVEL, 1000)
        }
        val original = RideSessionSnapshot(
            timer = TimerState.PAUSED,
            queuedTags = listOf(QueuedTag(PoiType.DANGER, 0)),
            tallies = tallies.snapshot(),
            elapsedMs = 123_456L,
            sampleCount = 99,
            carCount = 3,
            lastCarSpeedKph = 42,
        )
        val json = RideRecoveryStore.encodeForTest(original)
        val decoded = RideRecoveryStore.decodeForTest(json)
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun decodeRejectsIdleTimer() {
        val json = RideRecoveryStore.encodeForTest(
            RideSessionSnapshot(
                timer = TimerState.IDLE,
                queuedTags = emptyList(),
                tallies = TagTalliesSnapshot(
                    counts = IntArray(10),
                    dangerDetails = IntArray(6),
                    closureDetails = IntArray(6),
                    surfaceDetails = IntArray(10),
                    sceneryDetails = IntArray(7),
                    resupplyDetails = IntArray(4),
                    lastTapType = 0,
                    lastTapDetail = 0,
                    lastTapAtMs = 0,
                    openSurfaceDetail = 0,
                ),
                elapsedMs = 0,
                sampleCount = 0,
                carCount = 0,
                lastCarSpeedKph = -1,
            ),
        )
        assertNull(RideRecoveryStore.decodeForTest(json))
    }
}
