// SPDX-License-Identifier: MIT
import XCTest
@testable import Scout

final class TagTalliesTests: XCTestCase {
    func testDirectUndoWithinWindow() {
        let t = TagTallies()
        XCTAssertFalse(t.countTap(type: PoiType.other, detail: 0, nowMs: 1000))
        XCTAssertEqual(1, t.tileCount(PoiType.other))
        XCTAssertTrue(t.countTap(type: PoiType.other, detail: 0, nowMs: 2000))
        XCTAssertEqual(0, t.tileCount(PoiType.other))
    }

    func testDirectKeptOutsideWindow() {
        let t = TagTallies()
        t.countTap(type: PoiType.other, detail: 0, nowMs: 1000)
        XCTAssertFalse(t.countTap(type: PoiType.other, detail: 0, nowMs: 1000 + Timings.undoMs))
        XCTAssertEqual(2, t.tileCount(PoiType.other))
    }

    func testSubitemUndoWithinWindow() {
        let t = TagTallies()
        t.countTap(type: PoiType.danger, detail: Danger.potholes, nowMs: 1000)
        XCTAssertTrue(t.countTap(type: PoiType.danger, detail: Danger.corner, nowMs: 2000))
        XCTAssertEqual(0, t.tileCount(PoiType.danger))
    }

    func testSubitemKeptOutsideWindow() {
        let t = TagTallies()
        t.countTap(type: PoiType.closure, detail: Duration.today, nowMs: 1000)
        XCTAssertFalse(t.countTap(type: PoiType.closure, detail: Duration.days, nowMs: 1000 + Timings.undoMs + 1))
        XCTAssertEqual(2, t.tileCount(PoiType.closure))
    }

    func testSurfaceNeverUndoes() {
        let t = TagTallies()
        t.countTap(type: PoiType.surface, detail: Surface.cobbles, nowMs: 1000)
        XCTAssertFalse(t.countTap(type: PoiType.surface, detail: Surface.gravel, nowMs: 1500))
        XCTAssertEqual(2, t.tileCount(PoiType.surface))
    }

    func testSurfaceEndDoesNotTally() {
        let t = TagTallies()
        t.countTap(type: PoiType.surface, detail: Surface.end, nowMs: 1000)
        XCTAssertEqual(0, t.tileCount(PoiType.surface))
    }

    func testResupplyTileSumsLeaves() {
        let t = TagTallies()
        t.countTap(type: PoiType.resupply, detail: Resupply.water, nowMs: 1000)
        t.countTap(type: PoiType.resupply, detail: Resupply.food, nowMs: 5000)
        XCTAssertEqual(2, t.tileCount(PoiType.uiResupply))
        XCTAssertEqual(1, t.resupplyDetailCount(Resupply.water))
        XCTAssertEqual(1, t.resupplyDetailCount(Resupply.food))
    }

    func testClosureDetailTalliesKeptSeparately() {
        let t = TagTallies()
        let gap = Timings.undoMs + 1
        t.countTap(type: PoiType.closure, detail: Duration.months, nowMs: 1000)
        t.countTap(type: PoiType.closure, detail: Duration.months, nowMs: 1000 + gap)
        t.countTap(type: PoiType.closure, detail: Duration.today, nowMs: 1000 + gap * 2)
        XCTAssertEqual(2, t.closureDetailCount(Duration.months))
        XCTAssertEqual(1, t.closureDetailCount(Duration.today))
        XCTAssertEqual(3, t.tileCount(PoiType.closure))
    }

    func testClosureUndoDropsMatchingDetail() {
        let t = TagTallies()
        t.countTap(type: PoiType.closure, detail: Duration.months, nowMs: 1000)
        XCTAssertEqual(1, t.closureDetailCount(Duration.months))
        XCTAssertTrue(t.countTap(type: PoiType.closure, detail: Duration.days, nowMs: 2000))
        XCTAssertEqual(0, t.closureDetailCount(Duration.months))
        XCTAssertEqual(0, t.tileCount(PoiType.closure))
    }

    func testSceneryDetailTalliesKeptSeparately() {
        let t = TagTallies()
        let gap = Timings.undoMs + 1
        t.countTap(type: PoiType.scenery, detail: Scenery.view, nowMs: 1000)
        t.countTap(type: PoiType.scenery, detail: Scenery.view, nowMs: 1000 + gap)
        t.countTap(type: PoiType.scenery, detail: Scenery.nature, nowMs: 1000 + gap * 2)
        XCTAssertEqual(2, t.sceneryDetailCount(Scenery.view))
        XCTAssertEqual(1, t.sceneryDetailCount(Scenery.nature))
        XCTAssertEqual(3, t.tileCount(PoiType.scenery))
    }

    func testSurfaceDetailTalliesIncludeEnd() {
        let t = TagTallies()
        t.countTap(type: PoiType.surface, detail: Surface.cobbles, nowMs: 1000)
        t.countTap(type: PoiType.surface, detail: Surface.gravel, nowMs: 1500)
        t.countTap(type: PoiType.surface, detail: Surface.end, nowMs: 2000)
        XCTAssertEqual(1, t.surfaceDetailCount(Surface.cobbles))
        XCTAssertEqual(1, t.surfaceDetailCount(Surface.gravel))
        XCTAssertEqual(1, t.surfaceDetailCount(Surface.end))
        XCTAssertEqual(2, t.tileCount(PoiType.surface))
    }

    func testOpenSurfaceTracksActiveStretch() {
        let t = TagTallies()
        XCTAssertEqual(Surface.none, t.openSurfaceDetail)
        t.countTap(type: PoiType.surface, detail: Surface.cobbles, nowMs: 1000)
        XCTAssertEqual(Surface.cobbles, t.openSurfaceDetail)
        t.countTap(type: PoiType.surface, detail: Surface.gravel, nowMs: 1500)
        XCTAssertEqual(Surface.gravel, t.openSurfaceDetail)
        t.countTap(type: PoiType.surface, detail: Surface.end, nowMs: 2000)
        XCTAssertEqual(Surface.none, t.openSurfaceDetail)
    }
}

final class ScoutControllerTests: XCTestCase {
    func testNoticePickerCommitsAfterCorrectWindow() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 3, nowMs: 1000)
        XCTAssertEqual(UiMode.notice, c.snapshot().mode)
        c.onTileTap(index: 0, nowMs: 1100)
        XCTAssertEqual(0, c.queueSize())
        c.onTick(nowMs: 1100 + Timings.correctMs + 1)
        let tag = c.drainTag()
        XCTAssertEqual(PoiType.danger, tag?.type)
        XCTAssertEqual(Danger.potholes, tag?.detail)
        XCTAssertEqual(UiMode.grid, c.snapshot().mode)
    }

    func testNoticeTimeoutWritesUnknown() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 3, nowMs: 1000)
        c.onTick(nowMs: 1000 + Timings.pickMs + 1)
        let tag = c.drainTag()
        XCTAssertEqual(PoiType.danger, tag?.type)
        XCTAssertEqual(Danger.unknown, tag?.detail)
    }

    func testDoesNotEnqueueWhenPaused() {
        let c = ScoutController()
        c.start()
        c.pause()
        c.onTileTap(index: 0, nowMs: 1000)
        XCTAssertEqual(0, c.queueSize())
        XCTAssertEqual(0, c.snapshot().tileCounts[0])
    }

    func testIdleTapDoesNotTallyOrEnqueue() {
        let c = ScoutController()
        c.onTileTap(index: 3, nowMs: 1000)
        XCTAssertEqual(0, c.queueSize())
        XCTAssertEqual(0, c.snapshot().tileCounts[3])
        XCTAssertNil(c.takeFeedback())
        XCTAssertEqual(UiMode.grid, c.snapshot().mode)
    }

    func testIdleTapDoesNotOpenPicker() {
        let c = ScoutController()
        c.onTileTap(index: 1, nowMs: 1000)
        XCTAssertEqual(UiMode.grid, c.snapshot().mode)
    }

    func testClosurePickerCommitsAfterCorrectWindow() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 1, nowMs: 1000)
        XCTAssertEqual(UiMode.duration, c.snapshot().mode)
        c.onTileTap(index: 0, nowMs: 1100)
        XCTAssertEqual(0, c.queueSize())
        c.onTick(nowMs: 1100 + Timings.correctMs + 1)
        let tag = c.drainTag()
        XCTAssertEqual(PoiType.closure, tag?.type)
        XCTAssertEqual(Duration.today, tag?.detail)
    }

    func testClosureTimeoutWritesUnknown() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 1, nowMs: 1000)
        c.onTick(nowMs: 1000 + Timings.pickMs + 1)
        let tag = c.drainTag()
        XCTAssertEqual(PoiType.closure, tag?.type)
        XCTAssertEqual(Duration.unknown, tag?.detail)
    }

    func testSceneryPickerCommitsAfterCorrectWindow() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 4, nowMs: 1000)
        XCTAssertEqual(UiMode.scenery, c.snapshot().mode)
        c.onTileTap(index: 0, nowMs: 1100)
        c.onTick(nowMs: 1100 + Timings.correctMs + 1)
        let tag = c.drainTag()
        XCTAssertEqual(PoiType.scenery, tag?.type)
        XCTAssertEqual(Scenery.nature, tag?.detail)
    }

    func testSceneryTimeoutWritesUnknown() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 4, nowMs: 1000)
        c.onTick(nowMs: 1000 + Timings.pickMs + 1)
        let tag = c.drainTag()
        XCTAssertEqual(PoiType.scenery, tag?.type)
        XCTAssertEqual(Scenery.unknown, tag?.detail)
    }

    func testResupplyTimeoutDrops() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 0, nowMs: 1000)
        c.onTick(nowMs: 1000 + Timings.pickMs + 1)
        XCTAssertEqual(0, c.queueSize())
        XCTAssertEqual(UiMode.grid, c.snapshot().mode)
    }

    func testResupplyPickerCommitsAfterCorrectWindow() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 0, nowMs: 1000)
        XCTAssertEqual(UiMode.resupply, c.snapshot().mode)
        c.onTileTap(index: 0, nowMs: 1100)
        c.onTick(nowMs: 1100 + Timings.correctMs + 1)
        let tag = c.drainTag()
        XCTAssertEqual(PoiType.resupply, tag?.type)
        XCTAssertEqual(Resupply.water, tag?.detail)
    }

    func testBackAbortsPending() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 1, nowMs: 1000)
        c.onTileTap(index: 0, nowMs: 1100)
        c.onTileTap(index: 5, nowMs: 1200)
        XCTAssertEqual(0, c.queueSize())
        XCTAssertEqual(UiMode.grid, c.snapshot().mode)
    }

    func testFifoPreservesDoubleTap() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 5, nowMs: 1000)
        c.onTileTap(index: 5, nowMs: 1500)
        XCTAssertEqual(2, c.queueSize())
        XCTAssertEqual(PoiType.other, c.drainTag()?.type)
        XCTAssertEqual(PoiType.other, c.drainTag()?.type)
    }

    func testEndOpenSurfaceWritesEnd() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 2, nowMs: 1000)
        c.onTileTap(index: 4, nowMs: 1100)
        c.onTick(nowMs: 1100 + Timings.correctMs + 1)
        XCTAssertEqual(Surface.cobbles, c.snapshot().openSurfaceDetail)
        _ = c.drainTag()
        c.endOpenSurface(nowMs: 5000)
        let end = c.drainTag()
        XCTAssertEqual(PoiType.surface, end?.type)
        XCTAssertEqual(Surface.end, end?.detail)
        XCTAssertEqual(Surface.none, c.snapshot().openSurfaceDetail)
    }

    func testSessionSnapshotRoundTrip() {
        let c = ScoutController()
        c.start()
        c.onTileTap(index: 3, nowMs: 1000)
        c.onTileTap(index: 0, nowMs: 1100)
        c.onTick(nowMs: 1100 + Timings.correctMs + 1)
        _ = c.drainTag()
        let snap = c.sessionSnapshot(elapsedMs: 42_000, sampleCount: 7, carCount: 2, lastCarSpeedKph: 35)
        let restored = ScoutController()
        restored.restoreSession(snap)
        XCTAssertEqual(TimerState.running, restored.timer)
        XCTAssertEqual(1, restored.snapshot().tagTotal)
        XCTAssertEqual(0, restored.queueSize())
    }

    func testTalliesSnapshotRoundTrip() {
        let t = TagTallies()
        t.countTap(type: PoiType.surface, detail: Surface.gravel, nowMs: 1000)
        let snap = t.snapshot()
        let restored = TagTallies()
        restored.restore(snap)
        XCTAssertEqual(Surface.gravel, restored.openSurfaceDetail)
        XCTAssertEqual(1, restored.surfaceDetailCount(Surface.gravel))
    }
}

final class VehicleCounterTests: XCTestCase {
    func testOneSecondBlipNotCounted() {
        let v = VehicleCounter()
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 20, nearestRangeM: 80, riderKph: 20)
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 20)
        XCTAssertEqual(0, v.carCount)
        XCTAssertEqual(-1, v.lastCarSpeedKph)
    }

    func testMidRangeTurnAwayNotCounted() {
        let v = VehicleCounter()
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 20)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 40, nearestRangeM: 80, riderKph: 20)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 38, nearestRangeM: 70, riderKph: 20)
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 20)
        XCTAssertEqual(0, v.carCount)
        XCTAssertEqual(-1, v.lastCarSpeedKph)
    }

    func testCountAndSpeedOnClosePassTogether() {
        let v = VehicleCounter()
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 20)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 40, nearestRangeM: 80, riderKph: 20)
        XCTAssertEqual(0, v.carCount)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 35, nearestRangeM: 40, riderKph: 20)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 25, nearestRangeM: 8, riderKph: 20)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 22, nearestRangeM: 5, riderKph: 20)
        XCTAssertEqual(0, v.carCount)
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 20)
        XCTAssertEqual(1, v.carCount)
        XCTAssertEqual(42, v.lastCarSpeedKph)
    }

    func testConvoySecondTurnsFarAfterFirstPass() {
        let v = VehicleCounter()
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 2, nearestClosingKph: 40, nearestRangeM: 20, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 2, nearestClosingKph: 30, nearestRangeM: 10, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 35, nearestRangeM: 60, riderKph: 0)
        XCTAssertEqual(1, v.carCount)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 35, nearestRangeM: 55, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 0)
        XCTAssertEqual(1, v.carCount)
    }

    func testTurnAwayWhileConvoyRemainsNotCounted() {
        let v = VehicleCounter()
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 2, nearestClosingKph: 40, nearestRangeM: 40, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 2, nearestClosingKph: 35, nearestRangeM: 25, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 30, nearestRangeM: 80, riderKph: 0)
        XCTAssertEqual(0, v.carCount)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 30, nearestRangeM: 70, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 0)
        XCTAssertEqual(0, v.carCount)
    }

    func testDropoutClearsWithoutCrediting() {
        let v = VehicleCounter()
        v.onSample(tracking: true, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 20, nearestRangeM: 15, riderKph: 0)
        v.onSample(tracking: true, occupiedCount: 1, nearestClosingKph: 18, nearestRangeM: 10, riderKph: 0)
        v.onSample(tracking: false, occupiedCount: 0, nearestClosingKph: -1, nearestRangeM: -1, riderKph: 0)
        XCTAssertEqual(0, v.carCount)
    }
}
