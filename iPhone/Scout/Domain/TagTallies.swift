// SPDX-License-Identifier: MIT
import Foundation

/// Serializable tally mirror for ride recovery.
struct TagTalliesSnapshot: Equatable, Sendable {
    var counts: [Int]
    var dangerDetails: [Int]
    var closureDetails: [Int]
    var surfaceDetails: [Int]
    var sceneryDetails: [Int]
    var resupplyDetails: [Int]
    var lastTapType: Int
    var lastTapDetail: Int
    var lastTapAtMs: Int64
    var openSurfaceDetail: Int
}

/// Live tally mirror of the parser undo rule (display only — both taps still enqueue).
final class TagTallies {
    /// 1 normally; [Timings.talkBackTimeoutScale] while VoiceOver is on.
    var timeoutScale: Int = 1

    private var counts = Array(repeating: 0, count: 10) // index = poi_type 1..9
    private var dangerDetails = Array(repeating: 0, count: 6) // Danger 1..5
    private var closureDetails = Array(repeating: 0, count: 6) // Duration 1..5
    private var surfaceDetails = Array(repeating: 0, count: 10) // Surface 1..9
    private var sceneryDetails = Array(repeating: 0, count: 7) // Scenery 1..6
    private var resupplyDetails = Array(repeating: 0, count: 4) // Resupply 1..3

    private(set) var lastTapType: Int = PoiType.none
    private(set) var lastTapDetail: Int = Duration.none
    private(set) var lastTapAtMs: Int64 = 0

    /// Currently open stretch detail (`ASPHALT`…`SAND`), or [Surface.none] if untagged.
    private(set) var openSurfaceDetail: Int = Surface.none

    func closureDetailCount(_ detail: Int) -> Int {
        (Duration.today...Duration.unknown).contains(detail) ? closureDetails[detail] : 0
    }

    func dangerDetailCount(_ detail: Int) -> Int {
        (Danger.potholes...Danger.unknown).contains(detail) ? dangerDetails[detail] : 0
    }

    func surfaceDetailCount(_ detail: Int) -> Int {
        (Surface.asphalt...Surface.end).contains(detail) ? surfaceDetails[detail] : 0
    }

    func sceneryDetailCount(_ detail: Int) -> Int {
        (Scenery.nature...Scenery.unknown).contains(detail) ? sceneryDetails[detail] : 0
    }

    func resupplyDetailCount(_ detail: Int) -> Int {
        (Resupply.water...Resupply.mechanical).contains(detail) ? resupplyDetails[detail] : 0
    }

    func clear() {
        counts = Array(repeating: 0, count: counts.count)
        dangerDetails = Array(repeating: 0, count: dangerDetails.count)
        closureDetails = Array(repeating: 0, count: closureDetails.count)
        surfaceDetails = Array(repeating: 0, count: surfaceDetails.count)
        sceneryDetails = Array(repeating: 0, count: sceneryDetails.count)
        resupplyDetails = Array(repeating: 0, count: resupplyDetails.count)
        lastTapType = PoiType.none
        lastTapDetail = Duration.none
        lastTapAtMs = 0
        openSurfaceDetail = Surface.none
    }

    func tileCount(_ code: Int) -> Int {
        if code == PoiType.uiResupply { return counts[PoiType.resupply] }
        if code >= 1 && code < counts.count { return counts[code] }
        return 0
    }

    /// SPEC §6.7: the grid SURFACE tally counts stretch starts, never END.
    func countsTowardGridTile(type: Int, detail: Int) -> Bool {
        type != PoiType.surface || (Surface.asphalt...Surface.sand).contains(detail)
    }

    /// - Returns: true if this tap cancelled a pair (undo)
    @discardableResult
    func countTap(type: Int, detail: Int, nowMs: Int64) -> Bool {
        var undone = false
        if type != PoiType.surface,
           type == lastTapType,
           (nowMs - lastTapAtMs) < scaledUndoMsFor(type, scale: timeoutScale)
        {
            counts[type] = max(counts[type] - 1, 0)
            if lastTapType == PoiType.closure {
                let d = lastTapDetail
                if (Duration.today...Duration.unknown).contains(d) {
                    closureDetails[d] = max(closureDetails[d] - 1, 0)
                }
            } else if lastTapType == PoiType.danger {
                let d = lastTapDetail
                if (Danger.potholes...Danger.unknown).contains(d) {
                    dangerDetails[d] = max(dangerDetails[d] - 1, 0)
                }
            } else if lastTapType == PoiType.scenery {
                let d = lastTapDetail
                if (Scenery.nature...Scenery.unknown).contains(d) {
                    sceneryDetails[d] = max(sceneryDetails[d] - 1, 0)
                }
            } else if lastTapType == PoiType.resupply {
                let d = lastTapDetail
                if (Resupply.water...Resupply.mechanical).contains(d) {
                    resupplyDetails[d] = max(resupplyDetails[d] - 1, 0)
                }
            }
            lastTapType = PoiType.none
            lastTapDetail = Duration.none
            undone = true
        } else {
            if countsTowardGridTile(type: type, detail: detail) {
                counts[type] += 1
            }
            if type == PoiType.danger, (Danger.potholes...Danger.unknown).contains(detail) {
                dangerDetails[detail] += 1
            } else if type == PoiType.closure, (Duration.today...Duration.unknown).contains(detail) {
                closureDetails[detail] += 1
            } else if type == PoiType.scenery, (Scenery.nature...Scenery.unknown).contains(detail) {
                sceneryDetails[detail] += 1
            } else if type == PoiType.resupply, (Resupply.water...Resupply.mechanical).contains(detail) {
                resupplyDetails[detail] += 1
            } else if type == PoiType.surface, (Surface.asphalt...Surface.end).contains(detail) {
                surfaceDetails[detail] += 1
                openSurfaceDetail = detail == Surface.end ? Surface.none : detail
            } else if type == PoiType.surface, detail == Surface.none {
                openSurfaceDetail = Surface.none
            }
            lastTapType = type
            lastTapDetail = detail
        }
        lastTapAtMs = nowMs
        return undone
    }

    func snapshot() -> TagTalliesSnapshot {
        TagTalliesSnapshot(
            counts: counts,
            dangerDetails: dangerDetails,
            closureDetails: closureDetails,
            surfaceDetails: surfaceDetails,
            sceneryDetails: sceneryDetails,
            resupplyDetails: resupplyDetails,
            lastTapType: lastTapType,
            lastTapDetail: lastTapDetail,
            lastTapAtMs: lastTapAtMs,
            openSurfaceDetail: openSurfaceDetail
        )
    }

    func restore(_ snapshot: TagTalliesSnapshot) {
        copyInto(&counts, snapshot.counts)
        copyInto(&dangerDetails, snapshot.dangerDetails)
        copyInto(&closureDetails, snapshot.closureDetails)
        copyInto(&surfaceDetails, snapshot.surfaceDetails)
        copyInto(&sceneryDetails, snapshot.sceneryDetails)
        copyInto(&resupplyDetails, snapshot.resupplyDetails)
        lastTapType = snapshot.lastTapType
        lastTapDetail = snapshot.lastTapDetail
        lastTapAtMs = snapshot.lastTapAtMs
        openSurfaceDetail = snapshot.openSurfaceDetail
    }

    private func copyInto(_ dest: inout [Int], _ src: [Int]) {
        dest = Array(repeating: 0, count: dest.count)
        let n = min(src.count, dest.count)
        for i in 0..<n { dest[i] = src[i] }
    }
}
