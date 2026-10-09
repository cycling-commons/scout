// SPDX-License-Identifier: MIT
import Foundation

/// FIT CRC-16 — same table as tools/make-test-fit.mjs / fit-viewer.
enum FitCrc {
    private static let table: [UInt16] = [
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    ]

    /// [seed] continues a CRC across chunks; 0 starts a fresh one.
    static func crc16(_ bytes: [UInt8], start: Int = 0, end: Int? = nil, seed: Int = 0) -> Int {
        let finish = end ?? bytes.count
        var crc = UInt16(truncatingIfNeeded: seed) & 0xFFFF
        var i = start
        while i < finish {
            let b = UInt16(bytes[i])
            var t = table[Int(crc & 0xF)]
            crc = ((crc >> 4) & 0x0FFF) ^ t ^ table[Int(b & 0xF)]
            t = table[Int(crc & 0xF)]
            crc = ((crc >> 4) & 0x0FFF) ^ t ^ table[Int((b >> 4) & 0xF)]
            i += 1
        }
        return Int(crc & 0xFFFF)
    }

    static func crc16(data: Data, seed: Int = 0) -> Int {
        crc16([UInt8](data), seed: seed)
    }
}
