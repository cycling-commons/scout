// SPDX-License-Identifier: MIT
import Foundation

/**
 Magene L508-family proprietary BLE radar stream (char `8ce5cc02`).
 */
final class MageneBleDecoder {
    static let staleMs: Int64 = 2_000
    static let enableRadar: [UInt8] = [0x57, 0x09, 0x01]
    static let disableRadar: [UInt8] = [0x57, 0x09, 0x00]
    /// Range nibble step ≈ 3.125 m
    private static let rangeStepCm = 312
    private static let speedStepMps: Float = 3.04

    private struct Track {
        var rangeM: Int
        var closingSpeedMps: Float?
        var lastSeen: Int64
    }

    private let nowMs: () -> Int64
    private let staleMs: Int64
    private var slots: [Track?] = Array(repeating: nil, count: 8)
    private let lock = NSLock()

    init(nowMs: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }, staleMs: Int64 = MageneBleDecoder.staleMs) {
        self.nowMs = nowMs
        self.staleMs = staleMs
    }

    func reset() {
        lock.lock()
        slots = Array(repeating: nil, count: 8)
        lock.unlock()
    }

    func feed(_ payload: [UInt8]) {
        lock.lock()
        defer { lock.unlock() }
        guard payload.count >= 11 else { return }
        guard payload[0] == 0x57, payload[1] == 0x09 else { return }
        let page = Int(payload[3])
        let base: Int
        switch page {
        case 0x30: base = 0
        case 0x31: base = 4
        default: return
        }
        let now = nowMs()
        let threats = Int(payload[4])
        let r0 = Int(payload[6])
        let r1 = Int(payload[7])
        let r2 = Int(payload[8])
        let s0 = Int(payload[9])
        let s1 = Int(payload[10])

        let ranges = [
            (r0 & 0x3F) * Self.rangeStepCm / 100,
            (((r0 >> 6) & 0x03) | ((r1 & 0x0F) << 2)) * Self.rangeStepCm / 100,
            (((r1 >> 4) & 0x0F) | ((r2 & 0x03) << 4)) * Self.rangeStepCm / 100,
            ((r2 >> 2) & 0x3F) * Self.rangeStepCm / 100,
        ]
        let speeds = [
            s0 & 0x0F,
            (s0 >> 4) & 0x0F,
            s1 & 0x0F,
            (s1 >> 4) & 0x0F,
        ]

        for i in 0..<4 {
            let threat = (threats >> (i * 2)) & 0x03
            let slot = base + i
            if threat == 0 {
                slots[slot] = nil
                continue
            }
            let rangeM = min(max(ranges[i], 0), 254)
            let speedNibble = speeds[i]
            let mps: Float? = speedNibble > 0 ? Float(speedNibble) * Self.speedStepMps : nil
            slots[slot] = Track(rangeM: rangeM, closingSpeedMps: mps, lastSeen: now)
        }
        pruneStale(now)
    }

    func snapshot() -> [RadarTarget] {
        lock.lock()
        defer { lock.unlock() }
        pruneStale(nowMs())
        return slots.compactMap { t in
            t.map { RadarTarget(occupied: true, rangeM: $0.rangeM, closingSpeedMps: $0.closingSpeedMps) }
        }
        .sorted { $0.rangeM < $1.rangeM }
        .prefix(8)
        .map { $0 }
    }

    private func pruneStale(_ now: Int64) {
        for i in slots.indices {
            guard let t = slots[i] else { continue }
            if now - t.lastSeen > staleMs { slots[i] = nil }
        }
    }
}
