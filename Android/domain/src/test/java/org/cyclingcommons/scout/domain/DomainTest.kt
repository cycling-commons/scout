package org.cyclingcommons.scout.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagTalliesTest {
    @Test
    fun directUndoWithinWindow() {
        val t = TagTallies()
        assertFalse(t.countTap(PoiType.OTHER, 0, 1000))
        assertEquals(1, t.tileCount(PoiType.OTHER))
        assertTrue(t.countTap(PoiType.OTHER, 0, 2000))
        assertEquals(0, t.tileCount(PoiType.OTHER))
    }

    @Test
    fun directKeptOutsideWindow() {
        val t = TagTallies()
        t.countTap(PoiType.OTHER, 0, 1000)
        assertFalse(t.countTap(PoiType.OTHER, 0, 1000 + Timings.UNDO_MS))
        assertEquals(2, t.tileCount(PoiType.OTHER))
    }

    @Test
    fun subitemUndoWithinWindow() {
        val t = TagTallies()
        t.countTap(PoiType.DANGER, Danger.POTHOLES, 1000)
        assertTrue(t.countTap(PoiType.DANGER, Danger.CORNER, 2000))
        assertEquals(0, t.tileCount(PoiType.DANGER))
    }

    @Test
    fun subitemKeptOutsideWindow() {
        val t = TagTallies()
        t.countTap(PoiType.CLOSURE, Duration.TODAY, 1000)
        assertFalse(t.countTap(PoiType.CLOSURE, Duration.DAYS, 1000 + Timings.UNDO_MS + 1))
        assertEquals(2, t.tileCount(PoiType.CLOSURE))
    }

    @Test
    fun surfaceNeverUndoes() {
        val t = TagTallies()
        t.countTap(PoiType.SURFACE, Surface.COBBLES, 1000)
        assertFalse(t.countTap(PoiType.SURFACE, Surface.GRAVEL, 1500))
        assertEquals(2, t.tileCount(PoiType.SURFACE))
    }

    @Test
    fun surfaceEndDoesNotTally() {
        val t = TagTallies()
        t.countTap(PoiType.SURFACE, Surface.END, 1000)
        assertEquals(0, t.tileCount(PoiType.SURFACE))
    }

    @Test
    fun resupplyTileSumsLeaves() {
        val t = TagTallies()
        t.countTap(PoiType.RESUPPLY, Resupply.WATER, 1000)
        t.countTap(PoiType.RESUPPLY, Resupply.FOOD, 5000)
        assertEquals(2, t.tileCount(PoiType.UI_RESUPPLY))
        assertEquals(1, t.resupplyDetailCount(Resupply.WATER))
        assertEquals(1, t.resupplyDetailCount(Resupply.FOOD))
    }

    @Test
    fun closureDetailTalliesKeptSeparately() {
        val t = TagTallies()
        val gap = Timings.UNDO_MS + 1 // outside undo window
        t.countTap(PoiType.CLOSURE, Duration.MONTHS, 1000)
        t.countTap(PoiType.CLOSURE, Duration.MONTHS, 1000 + gap)
        t.countTap(PoiType.CLOSURE, Duration.TODAY, 1000 + gap * 2)
        assertEquals(2, t.closureDetailCount(Duration.MONTHS))
        assertEquals(1, t.closureDetailCount(Duration.TODAY))
        assertEquals(3, t.tileCount(PoiType.CLOSURE))
    }

    @Test
    fun closureUndoDropsMatchingDetail() {
        val t = TagTallies()
        t.countTap(PoiType.CLOSURE, Duration.MONTHS, 1000)
        assertEquals(1, t.closureDetailCount(Duration.MONTHS))
        assertTrue(t.countTap(PoiType.CLOSURE, Duration.DAYS, 2000))
        assertEquals(0, t.closureDetailCount(Duration.MONTHS))
        assertEquals(0, t.tileCount(PoiType.CLOSURE))
    }

    @Test
    fun sceneryDetailTalliesKeptSeparately() {
        val t = TagTallies()
        val gap = Timings.UNDO_MS + 1
        t.countTap(PoiType.SCENERY, Scenery.VIEW, 1000)
        t.countTap(PoiType.SCENERY, Scenery.VIEW, 1000 + gap)
        t.countTap(PoiType.SCENERY, Scenery.NATURE, 1000 + gap * 2)
        assertEquals(2, t.sceneryDetailCount(Scenery.VIEW))
        assertEquals(1, t.sceneryDetailCount(Scenery.NATURE))
        assertEquals(3, t.tileCount(PoiType.SCENERY))
    }

    @Test
    fun surfaceDetailTalliesIncludeEnd() {
        val t = TagTallies()
        t.countTap(PoiType.SURFACE, Surface.COBBLES, 1000)
        t.countTap(PoiType.SURFACE, Surface.GRAVEL, 1500)
        t.countTap(PoiType.SURFACE, Surface.END, 2000)
        assertEquals(1, t.surfaceDetailCount(Surface.COBBLES))
        assertEquals(1, t.surfaceDetailCount(Surface.GRAVEL))
        assertEquals(1, t.surfaceDetailCount(Surface.END))
        assertEquals(2, t.tileCount(PoiType.SURFACE)) // starts only
    }

    @Test
    fun openSurfaceTracksActiveStretch() {
        val t = TagTallies()
        assertEquals(Surface.NONE, t.openSurfaceDetail)
        t.countTap(PoiType.SURFACE, Surface.COBBLES, 1000)
        assertEquals(Surface.COBBLES, t.openSurfaceDetail)
        t.countTap(PoiType.SURFACE, Surface.GRAVEL, 1500)
        assertEquals(Surface.GRAVEL, t.openSurfaceDetail)
        t.countTap(PoiType.SURFACE, Surface.END, 2000)
        assertEquals(Surface.NONE, t.openSurfaceDetail)
    }
}

class ScoutControllerTest {
    @Test
    fun noticePickerCommitsAfterCorrectWindow() {
        val c = ScoutController()
        c.start()
        c.onTileTap(3, 1000) // NOTICE
        assertEquals(UiMode.NOTICE, c.snapshot().mode)
        c.onTileTap(0, 1100) // POTHOLES
        assertEquals(0, c.queueSize())
        c.onTick(1100 + Timings.CORRECT_MS + 1)
        val tag = c.drainTag()
        assertEquals(PoiType.DANGER, tag!!.type)
        assertEquals(Danger.POTHOLES, tag.detail)
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun noticeTimeoutWritesUnknown() {
        val c = ScoutController()
        c.start()
        c.onTileTap(3, 1000)
        c.onTick(1000 + Timings.PICK_MS + 1)
        val tag = c.drainTag()
        assertEquals(PoiType.DANGER, tag!!.type)
        assertEquals(Danger.UNKNOWN, tag.detail)
    }

    @Test
    fun doesNotEnqueueWhenPaused() {
        val c = ScoutController()
        c.start()
        c.pause()
        c.onTileTap(0, 1000)
        assertEquals(0, c.queueSize())
        assertEquals(0, c.snapshot().tileCounts[0])
    }

    @Test
    fun idleTapDoesNotTallyOrEnqueue() {
        val c = ScoutController()
        c.onTileTap(3, 1000) // NOTICE while idle
        assertEquals(0, c.queueSize())
        assertEquals(0, c.snapshot().tileCounts[3])
        assertNull(c.takeFeedback())
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun idleTapDoesNotOpenPicker() {
        val c = ScoutController()
        c.onTileTap(1, 1000) // CLOSURE while idle
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun closurePickerCommitsAfterCorrectWindow() {
        val c = ScoutController()
        c.start()
        c.onTileTap(1, 1000) // CLOSURE
        assertEquals(UiMode.DURATION, c.snapshot().mode)
        c.onTileTap(0, 1100) // TODAY
        assertEquals(0, c.queueSize())
        c.onTick(1100 + Timings.CORRECT_MS + 1)
        val tag = c.drainTag()
        assertEquals(PoiType.CLOSURE, tag!!.type)
        assertEquals(Duration.TODAY, tag.detail)
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun closureTimeoutWritesUnknown() {
        val c = ScoutController()
        c.start()
        c.onTileTap(1, 1000)
        c.onTick(1000 + Timings.PICK_MS + 1)
        val tag = c.drainTag()
        assertEquals(PoiType.CLOSURE, tag!!.type)
        assertEquals(Duration.UNKNOWN, tag.detail)
    }

    @Test
    fun sceneryPickerCommitsAfterCorrectWindow() {
        val c = ScoutController()
        c.start()
        c.onTileTap(4, 1000) // SCENERY
        assertEquals(UiMode.SCENERY, c.snapshot().mode)
        c.onTileTap(0, 1100) // NATURE
        assertEquals(0, c.queueSize())
        c.onTick(1100 + Timings.CORRECT_MS + 1)
        val tag = c.drainTag()
        assertEquals(PoiType.SCENERY, tag!!.type)
        assertEquals(Scenery.NATURE, tag.detail)
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun sceneryTimeoutWritesUnknown() {
        val c = ScoutController()
        c.start()
        c.onTileTap(4, 1000)
        c.onTick(1000 + Timings.PICK_MS + 1)
        val tag = c.drainTag()
        assertEquals(PoiType.SCENERY, tag!!.type)
        assertEquals(Scenery.UNKNOWN, tag.detail)
    }

    @Test
    fun resupplyTimeoutDrops() {
        val c = ScoutController()
        c.start()
        c.onTileTap(0, 1000) // RESUPPLY
        c.onTick(1000 + Timings.PICK_MS + 1)
        assertEquals(0, c.queueSize())
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun resupplyPickerCommitsAfterCorrectWindow() {
        val c = ScoutController()
        c.start()
        c.onTileTap(0, 1000) // RESUPPLY
        assertEquals(UiMode.RESUPPLY, c.snapshot().mode)
        c.onTileTap(0, 1100) // WATER
        assertEquals(0, c.queueSize())
        c.onTick(1100 + Timings.CORRECT_MS + 1)
        val tag = c.drainTag()
        assertEquals(PoiType.RESUPPLY, tag!!.type)
        assertEquals(Resupply.WATER, tag.detail)
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun backAbortsPending() {
        val c = ScoutController()
        c.start()
        c.onTileTap(1, 1000)
        c.onTileTap(0, 1100) // TODAY pending
        c.onTileTap(5, 1200) // BACK
        assertEquals(0, c.queueSize())
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun fifoPreservesDoubleTap() {
        val c = ScoutController()
        c.start()
        c.onTileTap(5, 1000) // OTHER
        c.onTileTap(5, 1500) // OTHER
        assertEquals(2, c.queueSize())
        assertEquals(PoiType.OTHER, c.drainTag()!!.type)
        assertEquals(PoiType.OTHER, c.drainTag()!!.type)
    }

    @Test
    fun endOpenSurfaceWritesEnd() {
        val c = ScoutController()
        c.start()
        c.onTileTap(2, 1000) // SURFACE
        c.onTileTap(4, 1100) // COBBLES (index in surface picker)
        c.onTick(1100 + Timings.CORRECT_MS + 1)
        assertEquals(Surface.COBBLES, c.snapshot().openSurfaceDetail)
        c.drainTag() // consume cobbles start
        c.endOpenSurface(5000)
        val end = c.drainTag()
        assertEquals(PoiType.SURFACE, end!!.type)
        assertEquals(Surface.END, end.detail)
        assertEquals(Surface.NONE, c.snapshot().openSurfaceDetail)
    }

    @Test
    fun sessionSnapshotRoundTrip() {
        val c = ScoutController()
        c.start()
        c.onTileTap(3, 1000) // NOTICE
        c.onTileTap(0, 1100) // POTHOLES
        c.onTick(1100 + Timings.CORRECT_MS + 1)
        c.drainTag()
        val snap = c.sessionSnapshot(
            elapsedMs = 42_000L,
            sampleCount = 7,
            carCount = 2,
            lastCarSpeedKph = 35,
        )
        val restored = ScoutController()
        restored.restoreSession(snap)
        assertEquals(TimerState.RUNNING, restored.timer)
        assertEquals(1, restored.snapshot().tagTotal)
        assertEquals(0, restored.queueSize())
    }

    @Test
    fun talliesSnapshotRoundTrip() {
        val t = TagTallies()
        t.countTap(PoiType.SURFACE, Surface.GRAVEL, 1000)
        val snap = t.snapshot()
        val restored = TagTallies()
        restored.restore(snap)
        assertEquals(Surface.GRAVEL, restored.openSurfaceDetail)
        assertEquals(1, restored.surfaceDetailCount(Surface.GRAVEL))
    }
}

class VehicleCounterTest {
    @Test
    fun oneSecondBlipNotCounted() {
        val v = VehicleCounter()
        v.onSample(true, 1, 20, 80, 20)
        v.onSample(true, 0, -1, -1, 20)
        assertEquals(0, v.carCount)
        assertEquals(-1, v.lastCarSpeedKph)
    }

    @Test
    fun midRangeTurnAwayNotCounted() {
        val v = VehicleCounter()
        v.onSample(true, 0, -1, -1, 20)
        v.onSample(true, 1, 40, 80, 20)
        v.onSample(true, 1, 38, 70, 20)
        v.onSample(true, 0, -1, -1, 20) // left while still far
        assertEquals(0, v.carCount)
        assertEquals(-1, v.lastCarSpeedKph)
    }

    @Test
    fun countAndSpeedOnClosePassTogether() {
        val v = VehicleCounter()
        v.onSample(true, 0, -1, -1, 20)
        v.onSample(true, 1, 40, 80, 20)
        assertEquals(0, v.carCount)
        v.onSample(true, 1, 35, 40, 20)
        v.onSample(true, 1, 25, 8, 20) // within PASS_CONFIRM_M
        v.onSample(true, 1, 22, 5, 20) // last second before pass, ≤PASS_LEAVE_MAX_M
        assertEquals(0, v.carCount)
        v.onSample(true, 0, -1, -1, 20)
        assertEquals(1, v.carCount)
        assertEquals(42, v.lastCarSpeedKph) // 22 + 20
    }

    @Test
    fun convoySecondTurnsFarAfterFirstPass() {
        val v = VehicleCounter()
        // First car closes and passes.
        v.onSample(true, 0, -1, -1, 0)
        v.onSample(true, 2, 40, 20, 0)
        v.onSample(true, 2, 30, 10, 0)
        v.onSample(true, 1, 35, 60, 0) // first left close; second still far
        assertEquals(1, v.carCount)
        // Second turns away far from bike — must not inherit first car's min-range.
        v.onSample(true, 1, 35, 55, 0)
        v.onSample(true, 0, -1, -1, 0)
        assertEquals(1, v.carCount)
    }

    @Test
    fun turnAwayWhileConvoyRemainsNotCounted() {
        val v = VehicleCounter()
        // Nearest approaches to ~25 m then turns; second car still far behind.
        v.onSample(true, 0, -1, -1, 0)
        v.onSample(true, 2, 40, 40, 0)
        v.onSample(true, 2, 35, 25, 0)
        v.onSample(true, 1, 30, 80, 0) // nearest left mid-range — not a pass
        assertEquals(0, v.carCount)
        // Remaining car must still get within 10 m before its leave counts.
        v.onSample(true, 1, 30, 70, 0)
        v.onSample(true, 0, -1, -1, 0)
        assertEquals(0, v.carCount)
    }

    @Test
    fun dropoutClearsWithoutCrediting() {
        val v = VehicleCounter()
        v.onSample(true, 0, -1, -1, 0)
        v.onSample(true, 1, 20, 15, 0)
        v.onSample(true, 1, 18, 10, 0)
        v.onSample(false, 0, -1, -1, 0)
        assertEquals(0, v.carCount)
    }
}
