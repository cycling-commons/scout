// SPDX-License-Identifier: MIT
import Foundation

struct TagFeedback: Equatable, Sendable {
    var undone: Bool
    var flashIdx: Int
    var flashUntilMs: Int64
}

struct ScoutUiState: Equatable, Sendable {
    var mode: UiMode = .grid
    var timer: TimerState = .idle
    var tiles: [Tile] = Tiles.grid
    var title: String? = nil
    /// Per-tile tallies for the current page; index matches [tiles].
    var tileCounts: [Int] = Array(repeating: 0, count: Tiles.grid.count)
    /// Every tag committed this ride, independent of which page is showing.
    var tagTotal: Int = 0
    var flashIdx: Int = -1
    var flashUntilMs: Int64 = 0
    /// True while [flashIdx] is lit for an undo window (not a brief confirm flash).
    var flashUndoWindow: Bool = false
    var pendingIdx: Int = -1
    /// Deadline for pending submenu pick (CORRECT_MS); 0 when none.
    var pendingUntilMs: Int64 = 0
    var parentIdx: Int = -1
    var radarLive: Bool = false
    var carCount: Int = 0
    var lastCarSpeedKph: Int = -1
    var imperial: Bool = false
    var openSurfaceDetail: Int = Surface.none
    var openSurfaceLabel: String? = nil
}

/// Pure tagging / picker controller. Clock is injected so tests stay deterministic.
final class ScoutController {
    private let queue: TagQueue
    private let tallies: TagTallies

    private(set) var timer: TimerState = .idle

    private var mode: UiMode = .grid
    private var parentIdx: Int = -1
    private var pickUntilMs: Int64 = 0

    private var pendingType: Int = PoiType.none
    private var pendingDetail: Int = Duration.none
    private var pendingIdx: Int = -1
    private var pendingUntilMs: Int64 = 0

    private var flashIdx: Int = -1
    private var flashUntilMs: Int64 = 0
    private var flashUndoWindow: Bool = false

    private(set) var lastFeedback: TagFeedback?

    private var timeoutScale: Int = 1

    init(queue: TagQueue = TagQueue(), tallies: TagTallies = TagTallies()) {
        self.queue = queue
        self.tallies = tallies
    }

    /**
     Lengthens undo / picker countdowns (e.g. ×2 while VoiceOver is on).
     Extends any deadline that is already running when the scale increases.
     */
    func setTimeoutScale(_ scale: Int, nowMs: Int64) {
        let normalized = max(scale, 1)
        if normalized == timeoutScale { return }
        let extending = normalized > timeoutScale
        timeoutScale = normalized
        tallies.timeoutScale = normalized
        if extending { extendActiveDeadlines(nowMs) }
    }

    private func extendActiveDeadlines(_ nowMs: Int64) {
        if flashUndoWindow, nowMs < flashUntilMs {
            flashUntilMs += flashUntilMs - nowMs
        }
        if pendingType != PoiType.none, nowMs < pendingUntilMs {
            pendingUntilMs += pendingUntilMs - nowMs
        }
        if mode != .grid, nowMs < pickUntilMs {
            pickUntilMs += pickUntilMs - nowMs
        }
    }

    func start() {
        timer = .running
        lastFeedback = nil
    }

    func pause() {
        timer = .paused
    }

    func resume() {
        if timer == .paused { timer = .running }
    }

    func stop() {
        timer = .idle
        closePage()
        queue.clear()
        tallies.clear()
        lastFeedback = nil
    }

    func restoreSession(_ snapshot: RideSessionSnapshot) {
        timer = snapshot.timer
        closePage()
        queue.restore(snapshot.queuedTags)
        tallies.restore(snapshot.tallies)
        lastFeedback = nil
    }

    func sessionSnapshot(
        elapsedMs: Int64,
        sampleCount: Int64,
        carCount: Int,
        lastCarSpeedKph: Int
    ) -> RideSessionSnapshot {
        RideSessionSnapshot(
            timer: timer,
            queuedTags: queue.snapshot(),
            tallies: tallies.snapshot(),
            elapsedMs: elapsedMs,
            sampleCount: sampleCount,
            carCount: carCount,
            lastCarSpeedKph: lastCarSpeedKph
        )
    }

    /// Consume pending confirm/undo feedback (nil if none since last consume).
    func takeFeedback() -> TagFeedback? {
        let f = lastFeedback
        lastFeedback = nil
        return f
    }

    func snapshot(nowMs: Int64 = 0) -> ScoutUiState {
        let tiles = Tiles.forMode(mode)
        let counts: [Int]
        switch mode {
        case .grid:
            counts = tiles.map { tallies.tileCount($0.code) }
        case .notice:
            counts = tiles.map { $0.code == PoiType.uiBack ? 0 : tallies.dangerDetailCount($0.code) }
        case .duration:
            counts = tiles.map { $0.code == PoiType.uiBack ? 0 : tallies.closureDetailCount($0.code) }
        case .resupply:
            counts = tiles.map { $0.code == PoiType.uiBack ? 0 : tallies.resupplyDetailCount($0.code) }
        case .surface:
            counts = tiles.map { $0.code == PoiType.uiBack ? 0 : tallies.surfaceDetailCount($0.code) }
        case .scenery:
            counts = tiles.map { $0.code == PoiType.uiBack ? 0 : tallies.sceneryDetailCount($0.code) }
        }
        return ScoutUiState(
            mode: mode,
            timer: timer,
            tiles: tiles,
            title: Tiles.titleFor(mode),
            tileCounts: counts,
            tagTotal: Tiles.grid.reduce(0) { $0 + tallies.tileCount($1.code) },
            flashIdx: nowMs < flashUntilMs ? flashIdx : -1,
            flashUntilMs: flashUntilMs,
            flashUndoWindow: nowMs < flashUntilMs && flashUndoWindow,
            pendingIdx: pendingType != PoiType.none ? pendingIdx : -1,
            pendingUntilMs: pendingType != PoiType.none ? pendingUntilMs : 0,
            parentIdx: parentIdx,
            openSurfaceDetail: tallies.openSurfaceDetail,
            openSurfaceLabel: Tiles.surfaceLabel(tallies.openSurfaceDetail)
        )
    }

    /**
     True while a picker page, a held pick, or a lit tile still needs sub-second
     updates. When this is false and the timer is idle, nothing has to tick at all.
     */
    func needsTick(nowMs: Int64) -> Bool {
        mode != .grid || pendingType != PoiType.none || nowMs < flashUntilMs
    }

    /// Advance picker timeouts / pending commits. Call ~every frame or on a 250ms tick.
    /// - Returns: true if UI should refresh
    @discardableResult
    func onTick(nowMs: Int64) -> Bool {
        if mode == .grid { return false }
        if pendingType != PoiType.none {
            if nowMs > pendingUntilMs {
                let pt = pendingType
                let pd = pendingDetail
                let pi = parentIdx
                clearPending()
                tag(type: pt, detail: pd, flashIdx: pi, nowMs: nowMs)
                return true
            }
            return false
        }
        if nowMs > pickUntilMs {
            switch mode {
            case .notice: tag(type: PoiType.danger, detail: Danger.unknown, flashIdx: parentIdx, nowMs: nowMs)
            case .duration: tag(type: PoiType.closure, detail: Duration.unknown, flashIdx: parentIdx, nowMs: nowMs)
            case .surface: tag(type: PoiType.surface, detail: Surface.none, flashIdx: parentIdx, nowMs: nowMs)
            case .scenery: tag(type: PoiType.scenery, detail: Scenery.unknown, flashIdx: parentIdx, nowMs: nowMs)
            case .resupply: closePage()
            case .grid: break
            }
            return true
        }
        return false
    }

    func onTileTap(index: Int, nowMs: Int64) {
        if timer != .running { return }
        let set = Tiles.forMode(mode)
        guard set.indices.contains(index) else { return }
        let code = set[index].code

        if mode == .grid {
            switch code {
            case PoiType.danger: openPage(.notice, parent: index, nowMs: nowMs)
            case PoiType.closure: openPage(.duration, parent: index, nowMs: nowMs)
            case PoiType.surface: openPage(.surface, parent: index, nowMs: nowMs)
            case PoiType.uiResupply: openPage(.resupply, parent: index, nowMs: nowMs)
            case PoiType.scenery: openPage(.scenery, parent: index, nowMs: nowMs)
            default: tag(type: code, detail: Duration.none, flashIdx: index, nowMs: nowMs)
            }
        } else if code == PoiType.uiBack {
            closePage()
        } else {
            switch mode {
            case .notice: holdPick(type: PoiType.danger, detail: code, idx: index, nowMs: nowMs)
            case .duration: holdPick(type: PoiType.closure, detail: code, idx: index, nowMs: nowMs)
            case .surface: holdPick(type: PoiType.surface, detail: code, idx: index, nowMs: nowMs)
            case .scenery: holdPick(type: PoiType.scenery, detail: code, idx: index, nowMs: nowMs)
            case .resupply: holdPick(type: PoiType.resupply, detail: code, idx: index, nowMs: nowMs)
            case .grid: break
            }
        }
    }

    /// Drain one queued tag for the current sample (nil → write NONE).
    func drainTag() -> QueuedTag? {
        timer == .running ? queue.poll() : nil
    }

    func queueSize() -> Int { queue.size }

    /// One-tap END for the open surface stretch (banner / shortcut).
    func endOpenSurface(nowMs: Int64) {
        if timer != .running { return }
        if tallies.openSurfaceDetail == Surface.none { return }
        let flash = max(Tiles.grid.firstIndex { $0.code == PoiType.surface } ?? 0, 0)
        tag(type: PoiType.surface, detail: Surface.end, flashIdx: flash, nowMs: nowMs)
    }

    private func tag(type: Int, detail: Int, flashIdx: Int, nowMs: Int64) {
        queue.offer(type: type, detail: detail)
        let undone = tallies.countTap(type: type, detail: detail, nowMs: nowMs)
        let lit = (undone || type == PoiType.surface)
            ? Timings.flashMs
            : scaledUndoMsFor(type, scale: timeoutScale)
        self.flashIdx = flashIdx
        self.flashUntilMs = nowMs + lit
        self.flashUndoWindow = !undone && type != PoiType.surface
        lastFeedback = TagFeedback(undone: undone, flashIdx: flashIdx, flashUntilMs: self.flashUntilMs)
        closePage()
    }

    private func openPage(_ newMode: UiMode, parent: Int, nowMs: Int64) {
        mode = newMode
        parentIdx = parent
        pickUntilMs = nowMs + scaledMs(Timings.pickMs, scale: timeoutScale)
        clearPending()
    }

    private func closePage() {
        mode = .grid
        parentIdx = -1
        pickUntilMs = 0
        clearPending()
    }

    private func clearPending() {
        pendingType = PoiType.none
        pendingDetail = Duration.none
        pendingIdx = -1
        pendingUntilMs = 0
    }

    private func holdPick(type: Int, detail: Int, idx: Int, nowMs: Int64) {
        pendingType = type
        pendingDetail = detail
        pendingIdx = idx
        pendingUntilMs = nowMs + scaledMs(Timings.correctMs, scale: timeoutScale)
    }
}
