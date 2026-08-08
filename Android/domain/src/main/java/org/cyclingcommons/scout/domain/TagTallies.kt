package org.cyclingcommons.scout.domain

data class QueuedTag(
    val type: Int,
    val detail: Int,
)

/** Serializable tally mirror for ride recovery. */
data class TagTalliesSnapshot(
    val counts: IntArray,
    val dangerDetails: IntArray = IntArray(6),
    val closureDetails: IntArray,
    val surfaceDetails: IntArray,
    val sceneryDetails: IntArray = IntArray(7),
    val resupplyDetails: IntArray = IntArray(4),
    val lastTapType: Int,
    val lastTapDetail: Int,
    val lastTapAtMs: Long,
    val openSurfaceDetail: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TagTalliesSnapshot) return false
        return counts.contentEquals(other.counts) &&
            dangerDetails.contentEquals(other.dangerDetails) &&
            closureDetails.contentEquals(other.closureDetails) &&
            surfaceDetails.contentEquals(other.surfaceDetails) &&
            sceneryDetails.contentEquals(other.sceneryDetails) &&
            resupplyDetails.contentEquals(other.resupplyDetails) &&
            lastTapType == other.lastTapType &&
            lastTapDetail == other.lastTapDetail &&
            lastTapAtMs == other.lastTapAtMs &&
            openSurfaceDetail == other.openSurfaceDetail
    }

    override fun hashCode(): Int {
        var result = counts.contentHashCode()
        result = 31 * result + dangerDetails.contentHashCode()
        result = 31 * result + closureDetails.contentHashCode()
        result = 31 * result + surfaceDetails.contentHashCode()
        result = 31 * result + sceneryDetails.contentHashCode()
        result = 31 * result + resupplyDetails.contentHashCode()
        result = 31 * result + lastTapType
        result = 31 * result + lastTapDetail
        result = 31 * result + lastTapAtMs.hashCode()
        result = 31 * result + openSurfaceDetail
        return result
    }
}

/**
 * Live tally mirror of the parser undo rule (display only — both taps still enqueue).
 */
class TagTallies {
    /** 1 normally; [Timings.TALKBACK_TIMEOUT_SCALE] while TalkBack is on. */
    var timeoutScale: Int = 1

    private val counts = IntArray(10) // index = poi_type 1..9
    /** Hazard kind buckets (POTHOLES..UNKNOWN). Codes collide with poi_type. */
    private val dangerDetails = IntArray(6) // index = Danger 1..5
    /** Closure duration buckets (TODAY..UNKNOWN). Codes collide with poi_type. */
    private val closureDetails = IntArray(6) // index = Duration 1..5
    /** Surface detail buckets (ASPHALT..END). Codes collide with poi_type. */
    private val surfaceDetails = IntArray(10) // index = Surface 1..9
    /** Scenery kind buckets (NATURE..UNKNOWN). Codes collide with poi_type. */
    private val sceneryDetails = IntArray(7) // index = Scenery 1..6
    /** Resupply kind buckets (WATER..MECHANICAL). Codes collide with poi_type. */
    private val resupplyDetails = IntArray(4) // index = Resupply 1..3

    var lastTapType: Int = PoiType.NONE
        private set
    var lastTapDetail: Int = Duration.NONE
        private set
    var lastTapAtMs: Long = 0L
        private set

    /**
     * Currently open stretch detail (`ASPHALT`…`SAND`), or [Surface.NONE] if
     * the road is untagged. Display-only — mirrors commit transitions.
     */
    var openSurfaceDetail: Int = Surface.NONE
        private set

    fun closureDetailCount(detail: Int): Int =
        if (detail in Duration.TODAY..Duration.UNKNOWN) closureDetails[detail] else 0

    fun dangerDetailCount(detail: Int): Int =
        if (detail in Danger.POTHOLES..Danger.UNKNOWN) dangerDetails[detail] else 0

    fun surfaceDetailCount(detail: Int): Int =
        if (detail in Surface.ASPHALT..Surface.END) surfaceDetails[detail] else 0

    fun sceneryDetailCount(detail: Int): Int =
        if (detail in Scenery.NATURE..Scenery.UNKNOWN) sceneryDetails[detail] else 0

    fun resupplyDetailCount(detail: Int): Int =
        if (detail in Resupply.WATER..Resupply.MECHANICAL) resupplyDetails[detail] else 0

    fun clear() {
        counts.fill(0)
        dangerDetails.fill(0)
        closureDetails.fill(0)
        surfaceDetails.fill(0)
        sceneryDetails.fill(0)
        resupplyDetails.fill(0)
        lastTapType = PoiType.NONE
        lastTapDetail = Duration.NONE
        lastTapAtMs = 0L
        openSurfaceDetail = Surface.NONE
    }

    fun tileCount(code: Int): Int =
        when (code) {
            PoiType.UI_RESUPPLY -> counts[PoiType.RESUPPLY]
            in 1 until counts.size -> counts[code]
            else -> 0
        }

    /** SPEC §6.7: the grid SURFACE tally counts stretch starts, never END. */
    fun countsTowardGridTile(type: Int, detail: Int): Boolean =
        type != PoiType.SURFACE || detail in Surface.ASPHALT..Surface.SAND

    /** @return true if this tap cancelled a pair (undo) */
    fun countTap(type: Int, detail: Int, nowMs: Long): Boolean {
        var undone = false
        if (type != PoiType.SURFACE &&
            type == lastTapType &&
            (nowMs - lastTapAtMs) < scaledUndoMsFor(type, timeoutScale)
        ) {
            counts[type] = (counts[type] - 1).coerceAtLeast(0)
            if (lastTapType == PoiType.CLOSURE) {
                val d = lastTapDetail
                if (d in Duration.TODAY..Duration.UNKNOWN) {
                    closureDetails[d] = (closureDetails[d] - 1).coerceAtLeast(0)
                }
            } else if (lastTapType == PoiType.DANGER) {
                val d = lastTapDetail
                if (d in Danger.POTHOLES..Danger.UNKNOWN) {
                    dangerDetails[d] = (dangerDetails[d] - 1).coerceAtLeast(0)
                }
            } else if (lastTapType == PoiType.SCENERY) {
                val d = lastTapDetail
                if (d in Scenery.NATURE..Scenery.UNKNOWN) {
                    sceneryDetails[d] = (sceneryDetails[d] - 1).coerceAtLeast(0)
                }
            } else if (lastTapType == PoiType.RESUPPLY) {
                val d = lastTapDetail
                if (d in Resupply.WATER..Resupply.MECHANICAL) {
                    resupplyDetails[d] = (resupplyDetails[d] - 1).coerceAtLeast(0)
                }
            }
            lastTapType = PoiType.NONE
            lastTapDetail = Duration.NONE
            undone = true
        } else {
            if (countsTowardGridTile(type, detail)) {
                counts[type]++
            }
            when {
                type == PoiType.DANGER && detail in Danger.POTHOLES..Danger.UNKNOWN ->
                    dangerDetails[detail]++
                type == PoiType.CLOSURE && detail in Duration.TODAY..Duration.UNKNOWN ->
                    closureDetails[detail]++
                type == PoiType.SCENERY && detail in Scenery.NATURE..Scenery.UNKNOWN ->
                    sceneryDetails[detail]++
                type == PoiType.RESUPPLY && detail in Resupply.WATER..Resupply.MECHANICAL ->
                    resupplyDetails[detail]++
                type == PoiType.SURFACE && detail in Surface.ASPHALT..Surface.END -> {
                    // Includes END for the submenu tile; grid SURFACE ignores END.
                    surfaceDetails[detail]++
                    openSurfaceDetail =
                        if (detail == Surface.END) Surface.NONE else detail
                }
                type == PoiType.SURFACE && detail == Surface.NONE ->
                    openSurfaceDetail = Surface.NONE
            }
            lastTapType = type
            lastTapDetail = detail
        }
        lastTapAtMs = nowMs
        return undone
    }

    fun snapshot(): TagTalliesSnapshot =
        TagTalliesSnapshot(
            counts = counts.copyOf(),
            dangerDetails = dangerDetails.copyOf(),
            closureDetails = closureDetails.copyOf(),
            surfaceDetails = surfaceDetails.copyOf(),
            sceneryDetails = sceneryDetails.copyOf(),
            resupplyDetails = resupplyDetails.copyOf(),
            lastTapType = lastTapType,
            lastTapDetail = lastTapDetail,
            lastTapAtMs = lastTapAtMs,
            openSurfaceDetail = openSurfaceDetail,
        )

    fun restore(snapshot: TagTalliesSnapshot) {
        counts.fill(0)
        dangerDetails.fill(0)
        closureDetails.fill(0)
        surfaceDetails.fill(0)
        sceneryDetails.fill(0)
        resupplyDetails.fill(0)
        snapshot.counts.copyInto(counts, endIndex = minOf(snapshot.counts.size, counts.size))
        snapshot.dangerDetails.copyInto(
            dangerDetails,
            endIndex = minOf(snapshot.dangerDetails.size, dangerDetails.size),
        )
        snapshot.closureDetails.copyInto(
            closureDetails,
            endIndex = minOf(snapshot.closureDetails.size, closureDetails.size),
        )
        snapshot.surfaceDetails.copyInto(
            surfaceDetails,
            endIndex = minOf(snapshot.surfaceDetails.size, surfaceDetails.size),
        )
        snapshot.sceneryDetails.copyInto(
            sceneryDetails,
            endIndex = minOf(snapshot.sceneryDetails.size, sceneryDetails.size),
        )
        snapshot.resupplyDetails.copyInto(
            resupplyDetails,
            endIndex = minOf(snapshot.resupplyDetails.size, resupplyDetails.size),
        )
        lastTapType = snapshot.lastTapType
        lastTapDetail = snapshot.lastTapDetail
        lastTapAtMs = snapshot.lastTapAtMs
        openSurfaceDetail = snapshot.openSurfaceDetail
    }
}
