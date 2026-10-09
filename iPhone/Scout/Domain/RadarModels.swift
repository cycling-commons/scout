// SPDX-License-Identifier: MIT
import Foundation

/// Normalized radar link state — TECHNICAL §7 / SPEC §8.3
enum RadarLinkState: Equatable, Sendable {
    case absent
    case scanning
    case connecting
    case tracking
    case disconnected
}

struct RadarTarget: Equatable, Sendable {
    var occupied: Bool
    /// Metres behind rider.
    var rangeM: Int
    /// Closing speed m/s when known; nil if the stream didn't provide it.
    var closingSpeedMps: Float?
}

/**
 Snapshot for one sample tick. Only [RadarLinkState.tracking] writes real FIT values;
 everything else → 255.
 */
struct RadarObservation: Equatable, Sendable {
    var state: RadarLinkState = .absent
    var targets: [RadarTarget] = []

    var tracking: Bool { state == .tracking }

    /// FIT (radar_count, radar_near, radar_speed) — SPEC §4.3 / §8.4
    func fitChannels() -> [Int] {
        if !tracking {
            return [radarNA, radarNA, radarNA]
        }
        var count = 0
        var near = Int.max
        var nearClosingKph = -1
        for t in targets {
            guard t.occupied else { continue }
            count += 1
            if t.rangeM < near {
                near = t.rangeM
                if let mps = t.closingSpeedMps {
                    nearClosingKph = Int(mps * 3.6)
                } else {
                    nearClosingKph = -1
                }
            }
        }
        let fitNear = count > 0 ? clampRadarByte(near) : radarNA
        let fitSpeed = count > 0 && nearClosingKph >= 0 ? clampRadarByte(nearClosingKph) : radarNA
        return [min(count, 8), fitNear, fitSpeed]
    }

    func occupiedCount() -> Int {
        targets.filter(\.occupied).count
    }

    func nearestRangeM() -> Int {
        var near = Int.max
        var found = false
        for t in targets {
            guard t.occupied else { continue }
            found = true
            if t.rangeM < near { near = t.rangeM }
        }
        return found ? near : -1
    }

    func nearestClosingKph() -> Int {
        var near = Int.max
        var kph = -1
        for t in targets {
            guard t.occupied else { continue }
            if t.rangeM < near {
                near = t.rangeM
                if let mps = t.closingSpeedMps {
                    kph = Int(mps * 3.6)
                } else {
                    kph = -1
                }
            }
        }
        return kph
    }
}
