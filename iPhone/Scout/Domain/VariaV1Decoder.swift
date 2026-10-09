// SPDX-License-Identifier: MIT
import Foundation

/**
 Stateful decoder for Varia-family V1 notifications on characteristic `6a4e3203`.
 */
final class VariaV1Decoder {
    static let staleMs: Int64 = 2_000

    private struct Track {
        var rangeM: Int
        var closingSpeedMps: Float?
        var lastSeen: Int64
    }

    private let nowMs: () -> Int64
    private let staleMs: Int64
    private var tracks: [Int: Track] = [:]
    private let lock = NSLock()

    init(nowMs: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }, staleMs: Int64 = VariaV1Decoder.staleMs) {
        self.nowMs = nowMs
        self.staleMs = staleMs
    }

    func reset() {
        lock.lock()
        tracks.removeAll()
        lock.unlock()
    }

    func feed(_ payload: [UInt8]) {
        lock.lock()
        defer { lock.unlock() }
        let now = nowMs()
        if payload.count >= 4, (payload.count - 1) % 3 == 0, (payload[0] & 0x0F) == 0x02 {
            ingestThreat(payload, now: now)
        }
        pruneStale(now)
    }

    func snapshot() -> [RadarTarget] {
        lock.lock()
        defer { lock.unlock() }
        pruneStale(nowMs())
        return tracks.values
            .sorted { $0.rangeM < $1.rangeM }
            .prefix(8)
            .map {
                RadarTarget(occupied: true, rangeM: $0.rangeM, closingSpeedMps: $0.closingSpeedMps)
            }
    }

    private func ingestThreat(_ payload: [UInt8], now: Int64) {
        var i = 1
        while i + 2 < payload.count {
            let vid = Int(payload[i])
            let dist = Int(payload[i + 1])
            let third = Int(payload[i + 2])
            i += 3
            if vid == 0x00 || vid == 0xFD || vid < 0x80 { continue }
            if dist == 0xFF { continue }
            let id = vid & 0x7F
            let speedMps: Float? = third > 1 ? Float(third) / 3.6 : nil
            tracks[id] = Track(rangeM: dist, closingSpeedMps: speedMps, lastSeen: now)
        }
    }

    private func pruneStale(_ now: Int64) {
        tracks = tracks.filter { now - $0.value.lastSeen <= staleMs }
    }
}
