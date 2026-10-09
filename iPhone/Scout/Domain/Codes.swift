// SPDX-License-Identifier: MIT
import Foundation

/// Append-only poi_type codes — must match docs/SPEC.md §5 and docs/DATA-FORMAT.md
enum PoiType {
    static let none = 0
    static let danger = 1
    static let scenery = 2
    static let water = 3
    static let other = 4
    static let closure = 5
    static let surface = 6
    static let food = 7
    static let mechanical = 8
    static let resupply = 9

    /// UI-only: never written to FIT
    static let uiResupply = 254
    static let uiBack = 255
}

/// Hazard kind when poi_type == DANGER
enum Danger {
    static let none = 0
    static let potholes = 1
    static let crossing = 2
    static let corner = 3
    static let other = 4
    static let unknown = 5
}

/// Closure duration when poi_type == CLOSURE
enum Duration {
    static let none = 0
    static let today = 1
    static let days = 2
    static let weeks = 3
    static let months = 4
    static let unknown = 5
}

/// Surface detail when poi_type == SURFACE
enum Surface {
    static let none = 0
    static let asphalt = 1
    static let concrete = 2
    static let paving = 3
    static let sett = 4
    static let cobbles = 5
    static let gravel = 6
    static let dirt = 7
    static let sand = 8
    static let end = 9
}

/// Scenery kind when poi_type == SCENERY
enum Scenery {
    static let none = 0
    static let nature = 1
    static let history = 2
    static let culture = 3
    static let view = 4
    static let arch = 5
    static let unknown = 6
}

/// Resupply kind when poi_type == RESUPPLY
enum Resupply {
    static let none = 0
    static let water = 1
    static let food = 2
    static let mechanical = 3
}

/// uint8 FIT invalid — radar not tracking
let radarNA = 255

enum Timings {
    static let flashMs: Int64 = 1_500
    static let pickMs: Int64 = 12_000
    static let correctMs: Int64 = 3_000
    static let undoMs: Int64 = 3_000
    static let queueMax = 16
    /// Multiplier for undo / picker countdowns while VoiceOver is on.
    static let talkBackTimeoutScale = 2
}

func undoMsFor(_ type: Int) -> Int64 { Timings.undoMs }

func scaledMs(_ baseMs: Int64, scale: Int) -> Int64 {
    baseMs * Int64(max(scale, 1))
}

func scaledUndoMsFor(_ type: Int, scale: Int) -> Int64 {
    scaledMs(undoMsFor(type), scale: scale)
}

func clampRadarByte(_ v: Int) -> Int {
    min(max(v, 0), 254)
}
