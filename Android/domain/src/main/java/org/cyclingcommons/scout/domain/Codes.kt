package org.cyclingcommons.scout.domain

/** Append-only poi_type codes — must match docs/DATA-FORMAT.md */
object PoiType {
    const val NONE = 0
    const val DANGER = 1
    const val SCENERY = 2
    const val WATER = 3
    const val OTHER = 4
    const val CLOSURE = 5
    const val SURFACE = 6
    const val FOOD = 7
    const val MECHANICAL = 8
    const val RESUPPLY = 9

    /** UI-only: never written to FIT */
    const val UI_RESUPPLY = 254
    const val UI_BACK = 255
}

/** Hazard kind when poi_type == DANGER */
object Danger {
    const val NONE = 0
    const val POTHOLES = 1
    const val CROSSING = 2
    const val CORNER = 3
    const val OTHER = 4
    const val UNKNOWN = 5
}

/** Closure duration when poi_type == CLOSURE */
object Duration {
    const val NONE = 0
    const val TODAY = 1
    const val DAYS = 2
    const val WEEKS = 3
    const val MONTHS = 4
    const val UNKNOWN = 5
}

/** Surface detail when poi_type == SURFACE */
object Surface {
    const val NONE = 0
    const val ASPHALT = 1
    const val CONCRETE = 2
    const val PAVING = 3
    const val SETT = 4
    const val COBBLES = 5
    const val GRAVEL = 6
    const val DIRT = 7
    const val SAND = 8
    const val END = 9
}

/** Scenery kind when poi_type == SCENERY */
object Scenery {
    const val NONE = 0
    const val NATURE = 1
    const val HISTORY = 2
    const val CULTURE = 3
    const val VIEW = 4
    const val ARCH = 5
    const val UNKNOWN = 6
}

/** Resupply kind when poi_type == RESUPPLY */
object Resupply {
    const val NONE = 0
    const val WATER = 1
    const val FOOD = 2
    const val MECHANICAL = 3
}

/** uint8 FIT invalid — radar not tracking */
const val RADAR_NA = 255

object Timings {
    const val FLASH_MS = 1_500L
    const val PICK_MS = 12_000L
    const val CORRECT_MS = 3_000L
    const val UNDO_MS = 3_000L
    const val QUEUE_MAX = 16
    /** Multiplier for undo / picker countdowns while TalkBack is on. */
    const val TALKBACK_TIMEOUT_SCALE = 2
}

fun undoMsFor(type: Int): Long = Timings.UNDO_MS

fun scaledMs(baseMs: Long, scale: Int): Long = baseMs * scale.coerceAtLeast(1)

fun scaledUndoMsFor(type: Int, scale: Int): Long = scaledMs(undoMsFor(type), scale)
