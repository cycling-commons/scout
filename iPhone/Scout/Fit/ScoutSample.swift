// SPDX-License-Identifier: MIT
import Foundation

/**
 One Scout `record` (~1 s while RUNNING) — SPEC §4.2 only:
 timestamp, lat/lon (when available), speed (when available), five Scout channels.
 */
struct ScoutSample: Equatable, Sendable {
    /// FIT epoch seconds (since 1989-12-31).
    var timestampFit: Int64
    /// Semicircles; nil → FIT sint32 invalid.
    var latSemi: Int32?
    var lonSemi: Int32?
    /// m/s × 1000; nil → FIT uint16 invalid.
    var speedMmPerS: Int?
    var poiType: Int = 0
    var poiDetail: Int = 0
    var radarCount: Int = radarNA
    var radarNear: Int = radarNA
    var radarSpeed: Int = radarNA

    static let radarNA = 255
    static let positionInvalid: Int32 = 0x7FFF_FFFF
    static let speedInvalid = 0xFFFF
    /// Seconds between Unix epoch and FIT epoch (1989-12-31 00:00:00 UTC).
    static let fitEpochOffsetS: Int64 = 631_065_600

    /// Degrees → FIT semicircles. Matches Kotlin: truncate toward zero, then wrap to Int32.
    static func degreesToSemi(_ degrees: Double) -> Int32 {
        let raw = degrees * Double(Int64(1) << 31) / 180.0
        return Int32(truncatingIfNeeded: Int64(raw))
    }

    /// Unix epoch millis → FIT timestamp (seconds since 1989-12-31).
    static func unixMsToFit(_ unixMs: Int64) -> Int64 {
        (unixMs / 1000) - fitEpochOffsetS
    }
}
