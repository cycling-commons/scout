package org.cyclingcommons.scout.domain

enum class TimerState {
    IDLE,
    RUNNING,
    PAUSED,
}

data class TagFeedback(
    val undone: Boolean,
    val flashIdx: Int,
    val flashUntilMs: Long,
)

data class ScoutUiState(
    val mode: UiMode = UiMode.GRID,
    val timer: TimerState = TimerState.IDLE,
    val tiles: List<Tile> = Tiles.grid,
    val title: String? = null,
    /** Per-tile tallies for the current page; index matches [tiles]. */
    val tileCounts: List<Int> = List(Tiles.grid.size) { 0 },
    /** Every tag committed this ride, independent of which page is showing. */
    val tagTotal: Int = 0,
    val flashIdx: Int = -1,
    val flashUntilMs: Long = 0L,
    /** True while [flashIdx] is lit for an undo window (not a brief confirm flash). */
    val flashUndoWindow: Boolean = false,
    val pendingIdx: Int = -1,
    /** Deadline for pending submenu pick (CORRECT_MS); 0 when none. */
    val pendingUntilMs: Long = 0L,
    val parentIdx: Int = -1,
    val radarLive: Boolean = false,
    val carCount: Int = 0,
    val lastCarSpeedKph: Int = -1,
    val imperial: Boolean = false,
    /** Open surface stretch detail, or [Surface.NONE] when none. */
    val openSurfaceDetail: Int = Surface.NONE,
    val openSurfaceLabel: String? = null,
)

/**
 * Pure tagging / picker controller. Clock is injected so tests stay deterministic.
 */
class ScoutController(
    private val queue: TagQueue = TagQueue(),
    private val tallies: TagTallies = TagTallies(),
) {
    var timer: TimerState = TimerState.IDLE
        private set

    private var mode: UiMode = UiMode.GRID
    private var parentIdx: Int = -1
    private var pickUntilMs: Long = 0L

    private var pendingType: Int = PoiType.NONE
    private var pendingDetail: Int = Duration.NONE
    private var pendingIdx: Int = -1
    private var pendingUntilMs: Long = 0L

    private var flashIdx: Int = -1
    private var flashUntilMs: Long = 0L
    private var flashUndoWindow: Boolean = false

    var lastFeedback: TagFeedback? = null
        private set

    private var timeoutScale: Int = 1

    /**
     * Lengthens undo / picker countdowns (e.g. ×2 while TalkBack is on).
     * Extends any deadline that is already running when the scale increases.
     */
    fun setTimeoutScale(scale: Int, nowMs: Long) {
        val normalized = scale.coerceAtLeast(1)
        if (normalized == timeoutScale) return
        val extending = normalized > timeoutScale
        timeoutScale = normalized
        tallies.timeoutScale = normalized
        if (extending) extendActiveDeadlines(nowMs)
    }

    private fun extendActiveDeadlines(nowMs: Long) {
        if (flashUndoWindow && nowMs < flashUntilMs) {
            flashUntilMs += flashUntilMs - nowMs
        }
        if (pendingType != PoiType.NONE && nowMs < pendingUntilMs) {
            pendingUntilMs += pendingUntilMs - nowMs
        }
        if (mode != UiMode.GRID && nowMs < pickUntilMs) {
            pickUntilMs += pickUntilMs - nowMs
        }
    }

    fun start() {
        timer = TimerState.RUNNING
        lastFeedback = null
    }

    fun pause() {
        timer = TimerState.PAUSED
    }

    fun resume() {
        if (timer == TimerState.PAUSED) timer = TimerState.RUNNING
    }

    fun stop() {
        timer = TimerState.IDLE
        closePage()
        queue.clear()
        tallies.clear()
        lastFeedback = null
    }

    fun restoreSession(snapshot: RideSessionSnapshot) {
        timer = snapshot.timer
        closePage()
        queue.restore(snapshot.queuedTags)
        tallies.restore(snapshot.tallies)
        lastFeedback = null
    }

    fun sessionSnapshot(
        elapsedMs: Long,
        sampleCount: Long,
        carCount: Int,
        lastCarSpeedKph: Int,
    ): RideSessionSnapshot =
        RideSessionSnapshot(
            timer = timer,
            queuedTags = queue.snapshot(),
            tallies = tallies.snapshot(),
            elapsedMs = elapsedMs,
            sampleCount = sampleCount,
            carCount = carCount,
            lastCarSpeedKph = lastCarSpeedKph,
        )

    /** Consume pending confirm/undo feedback (null if none since last consume). */
    fun takeFeedback(): TagFeedback? {
        val f = lastFeedback
        lastFeedback = null
        return f
    }

    fun snapshot(nowMs: Long = 0L): ScoutUiState {
        val tiles = Tiles.forMode(mode)
        val counts =
            when (mode) {
                UiMode.GRID -> tiles.map { tallies.tileCount(it.code) }
                UiMode.NOTICE ->
                    tiles.map {
                        if (it.code == PoiType.UI_BACK) 0
                        else tallies.dangerDetailCount(it.code)
                    }
                UiMode.DURATION ->
                    tiles.map {
                        if (it.code == PoiType.UI_BACK) 0
                        else tallies.closureDetailCount(it.code)
                    }
                UiMode.RESUPPLY ->
                    tiles.map {
                        if (it.code == PoiType.UI_BACK) 0
                        else tallies.resupplyDetailCount(it.code)
                    }
                UiMode.SURFACE ->
                    tiles.map {
                        if (it.code == PoiType.UI_BACK) 0
                        else tallies.surfaceDetailCount(it.code)
                    }
                UiMode.SCENERY ->
                    tiles.map {
                        if (it.code == PoiType.UI_BACK) 0
                        else tallies.sceneryDetailCount(it.code)
                    }
            }
        return ScoutUiState(
            mode = mode,
            timer = timer,
            tiles = tiles,
            title = Tiles.titleFor(mode),
            tileCounts = counts,
            tagTotal = Tiles.grid.sumOf { tallies.tileCount(it.code) },
            flashIdx = if (nowMs < flashUntilMs) flashIdx else -1,
            flashUntilMs = flashUntilMs,
            flashUndoWindow = nowMs < flashUntilMs && flashUndoWindow,
            pendingIdx = if (pendingType != PoiType.NONE) pendingIdx else -1,
            pendingUntilMs = if (pendingType != PoiType.NONE) pendingUntilMs else 0L,
            parentIdx = parentIdx,
            openSurfaceDetail = tallies.openSurfaceDetail,
            openSurfaceLabel = Tiles.surfaceLabel(tallies.openSurfaceDetail),
        )
    }

    /**
     * True while a picker page, a held pick, or a lit tile still needs sub-second
     * updates. When this is false and the timer is idle, nothing has to tick at all.
     */
    fun needsTick(nowMs: Long): Boolean =
        mode != UiMode.GRID || pendingType != PoiType.NONE || nowMs < flashUntilMs

    /**
     * Advance picker timeouts / pending commits. Call ~every frame or on a 250ms tick.
     * @return true if UI should refresh
     */
    fun onTick(nowMs: Long): Boolean {
        if (mode == UiMode.GRID) return false
        if (pendingType != PoiType.NONE) {
            if (nowMs > pendingUntilMs) {
                val pt = pendingType
                val pd = pendingDetail
                val pi = parentIdx
                clearPending()
                tag(pt, pd, pi, nowMs)
                return true
            }
            return false
        }
        if (nowMs > pickUntilMs) {
            when (mode) {
                UiMode.NOTICE -> tag(PoiType.DANGER, Danger.UNKNOWN, parentIdx, nowMs)
                UiMode.DURATION -> tag(PoiType.CLOSURE, Duration.UNKNOWN, parentIdx, nowMs)
                UiMode.SURFACE -> tag(PoiType.SURFACE, Surface.NONE, parentIdx, nowMs)
                UiMode.SCENERY -> tag(PoiType.SCENERY, Scenery.UNKNOWN, parentIdx, nowMs)
                UiMode.RESUPPLY -> closePage()
                UiMode.GRID -> Unit
            }
            return true
        }
        return false
    }

    fun onTileTap(index: Int, nowMs: Long) {
        if (timer != TimerState.RUNNING) return
        val set = Tiles.forMode(mode)
        if (index !in set.indices) return
        val code = set[index].code

        when (mode) {
            UiMode.GRID -> when (code) {
                PoiType.DANGER -> openPage(UiMode.NOTICE, index, nowMs)
                PoiType.CLOSURE -> openPage(UiMode.DURATION, index, nowMs)
                PoiType.SURFACE -> openPage(UiMode.SURFACE, index, nowMs)
                PoiType.UI_RESUPPLY -> openPage(UiMode.RESUPPLY, index, nowMs)
                PoiType.SCENERY -> openPage(UiMode.SCENERY, index, nowMs)
                else -> tag(code, Duration.NONE, index, nowMs)
            }
            else -> when (code) {
                PoiType.UI_BACK -> closePage()
                else -> when (mode) {
                    UiMode.NOTICE -> holdPick(PoiType.DANGER, code, index, nowMs)
                    UiMode.DURATION -> holdPick(PoiType.CLOSURE, code, index, nowMs)
                    UiMode.SURFACE -> holdPick(PoiType.SURFACE, code, index, nowMs)
                    UiMode.SCENERY -> holdPick(PoiType.SCENERY, code, index, nowMs)
                    UiMode.RESUPPLY -> holdPick(PoiType.RESUPPLY, code, index, nowMs)
                    UiMode.GRID -> Unit
                }
            }
        }
    }

    /** Drain one queued tag for the current sample (null → write NONE). */
    fun drainTag(): QueuedTag? =
        if (timer == TimerState.RUNNING) queue.poll() else null

    fun queueSize(): Int = queue.size

    /** One-tap END for the open surface stretch (banner / shortcut). */
    fun endOpenSurface(nowMs: Long) {
        if (timer != TimerState.RUNNING) return
        if (tallies.openSurfaceDetail == Surface.NONE) return
        val flash =
            Tiles.grid.indexOfFirst { it.code == PoiType.SURFACE }.coerceAtLeast(0)
        tag(PoiType.SURFACE, Surface.END, flash, nowMs)
    }

    private fun tag(type: Int, detail: Int, flashIdx: Int, nowMs: Long) {
        queue.offer(type, detail)
        val undone = tallies.countTap(type, detail, nowMs)
        val lit =
            if (undone || type == PoiType.SURFACE) Timings.FLASH_MS
            else scaledUndoMsFor(type, timeoutScale)
        this.flashIdx = flashIdx
        this.flashUntilMs = nowMs + lit
        this.flashUndoWindow = !undone && type != PoiType.SURFACE
        lastFeedback = TagFeedback(undone, flashIdx, this.flashUntilMs)
        closePage()
    }

    private fun openPage(newMode: UiMode, parent: Int, nowMs: Long) {
        mode = newMode
        parentIdx = parent
        pickUntilMs = nowMs + scaledMs(Timings.PICK_MS, timeoutScale)
        clearPending()
    }

    private fun closePage() {
        mode = UiMode.GRID
        parentIdx = -1
        pickUntilMs = 0L
        clearPending()
    }

    private fun clearPending() {
        pendingType = PoiType.NONE
        pendingDetail = Duration.NONE
        pendingIdx = -1
        pendingUntilMs = 0L
    }

    private fun holdPick(type: Int, detail: Int, idx: Int, nowMs: Long) {
        pendingType = type
        pendingDetail = detail
        pendingIdx = idx
        pendingUntilMs = nowMs + scaledMs(Timings.CORRECT_MS, timeoutScale)
    }
}
