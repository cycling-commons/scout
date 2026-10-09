// SPDX-License-Identifier: MIT
import Foundation

/**
 Minimal original-FIT encoder for Scout (SPEC §4.2 / §4.3).
 Record fields: timestamp, lat, lon, speed, plus the five Scout developer channels.
 Records are appended onto the open file; a flush patches header + CRC only.
 */
final class ScoutFitWriter {
    private static let headerBytes = 12
    private static let crcBytes = 2
    private static let pendingBufferBytes = 4096
    private static let crcChunkBytes = 8192
    private static let protocolVersion = 0x20
    private static let profileVersion = 2140
    private static let devFields: [(Int, String)] = [
        (0, "poi_type"),
        (1, "poi_detail"),
        (2, "radar_count"),
        (3, "radar_near"),
        (4, "radar_speed"),
    ]

    private let outURL: URL
    private var pending = Data()
    private var handle: FileHandle?
    private var dataSize = 0
    private(set) var recordCount = 0

    var fileURL: URL { outURL }

    init(outFile: URL) {
        self.outURL = outFile
    }

    func append(_ sample: ScoutSample) {
        var rec = Data()
        Self.writeRecord(&rec, sample)
        pending.append(rec)
        recordCount += 1
        if pending.count >= Self.pendingBufferBytes {
            flush()
        }
    }

    /// Persist everything appended so far and leave a valid, parseable FIT on disk.
    func flush() {
        let fh = open()
        if !pending.isEmpty {
            fh.seek(toFileOffset: UInt64(Self.headerBytes + dataSize))
            fh.write(pending)
            dataSize += pending.count
            pending.removeAll(keepingCapacity: true)
        }
        fh.seek(toFileOffset: 0)
        fh.write(Data(Self.header(bodySize: dataSize)))
        fh.seek(toFileOffset: UInt64(Self.headerBytes + dataSize))
        fh.write(Data(Self.crcBytes(fileCrc(fh))))
        try? fh.truncate(atOffset: UInt64(Self.headerBytes + dataSize + Self.crcBytes))
    }

    @discardableResult
    func finish() -> URL {
        flush()
        try? handle?.close()
        handle = nil
        return outURL
    }

    /// Re-open a flushed partial FIT and append more records (ride recovery).
    func resumeAppend(existingRecordCount: Int) {
        let attrs = try? FileManager.default.attributesOfItem(atPath: outURL.path)
        let length = (attrs?[.size] as? NSNumber)?.intValue ?? 0
        if !FileManager.default.fileExists(atPath: outURL.path) || length < Self.headerBytes {
            return
        }
        let fh = try! FileHandle(forUpdating: outURL)
        fh.seek(toFileOffset: 4)
        let sizeBytes = fh.readData(ofLength: 4)
        dataSize =
            Int(sizeBytes[0]) |
            (Int(sizeBytes[1]) << 8) |
            (Int(sizeBytes[2]) << 16) |
            (Int(sizeBytes[3]) << 24)
        recordCount = max(existingRecordCount, 0)
        try? fh.truncate(atOffset: UInt64(Self.headerBytes + dataSize))
        handle = fh
    }

    private func open() -> FileHandle {
        if let handle { return handle }
        try? FileManager.default.createDirectory(
            at: outURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        FileManager.default.createFile(atPath: outURL.path, contents: nil)
        let fh = try! FileHandle(forUpdating: outURL)
        try? fh.truncate(atOffset: 0)
        var definitions = Data()
        Self.writeDefinitions(&definitions)
        fh.write(Data(Self.header(bodySize: 0)))
        fh.write(definitions)
        dataSize = definitions.count
        handle = fh
        return fh
    }

    private func fileCrc(_ handle: FileHandle) -> Int {
        handle.seek(toFileOffset: 0)
        var remaining = Self.headerBytes + dataSize
        var crc = 0
        while remaining > 0 {
            let chunk = handle.readData(ofLength: min(remaining, Self.crcChunkBytes))
            if chunk.isEmpty { break }
            crc = FitCrc.crc16([UInt8](chunk), seed: crc)
            remaining -= chunk.count
        }
        return crc
    }

    static func encode(_ records: [ScoutSample]) -> [UInt8] {
        var body = Data()
        writeDefinitions(&body)
        for r in records {
            writeRecord(&body, r)
        }
        let bodyBytes = [UInt8](body)
        var all = [UInt8](repeating: 0, count: headerBytes + bodyBytes.count + crcBytes)
        let head = header(bodySize: bodyBytes.count)
        for i in 0..<headerBytes { all[i] = head[i] }
        for i in 0..<bodyBytes.count { all[headerBytes + i] = bodyBytes[i] }
        let crc = FitCrc.crc16(all, start: 0, end: headerBytes + bodyBytes.count)
        writeU16Le(&all, offset: headerBytes + bodyBytes.count, value: crc)
        return all
    }

    private static func writeDefinitions(_ out: inout Data) {
        // file_id (0), local 0 — type = activity (4)
        writeU8(&out, 0x40); writeU8(&out, 0); writeU8(&out, 0); writeU16(&out, 0); writeU8(&out, 1)
        writeU8(&out, 0); writeU8(&out, 1); writeU8(&out, 0x00)
        writeU8(&out, 0x00); writeU8(&out, 4)

        // developer_data_id (207), local 1
        writeU8(&out, 0x41); writeU8(&out, 0); writeU8(&out, 0); writeU16(&out, 207); writeU8(&out, 1)
        writeU8(&out, 3); writeU8(&out, 1); writeU8(&out, 0x02)
        writeU8(&out, 0x01); writeU8(&out, 0)

        // field_description (206), local 2
        writeU8(&out, 0x42); writeU8(&out, 0); writeU8(&out, 0); writeU16(&out, 206); writeU8(&out, 4)
        writeU8(&out, 0); writeU8(&out, 1); writeU8(&out, 0x02)
        writeU8(&out, 1); writeU8(&out, 1); writeU8(&out, 0x02)
        writeU8(&out, 2); writeU8(&out, 1); writeU8(&out, 0x02)
        writeU8(&out, 3); writeU8(&out, 16); writeU8(&out, 0x07)
        for (num, name) in devFields {
            writeU8(&out, 0x02)
            writeU8(&out, 0)
            writeU8(&out, num)
            writeU8(&out, 0x02)
            writeStr(&out, name, len: 16)
        }

        // record (20), local 3
        writeU8(&out, 0x63); writeU8(&out, 0); writeU8(&out, 0); writeU16(&out, 20); writeU8(&out, 4)
        writeU8(&out, 253); writeU8(&out, 4); writeU8(&out, 0x86)
        writeU8(&out, 0); writeU8(&out, 4); writeU8(&out, 0x85)
        writeU8(&out, 1); writeU8(&out, 4); writeU8(&out, 0x85)
        writeU8(&out, 6); writeU8(&out, 2); writeU8(&out, 0x84)
        writeU8(&out, 5)
        for (num, _) in devFields {
            writeU8(&out, num); writeU8(&out, 1); writeU8(&out, 0)
        }
    }

    private static func writeRecord(_ out: inout Data, _ r: ScoutSample) {
        writeU8(&out, 0x03)
        writeU32(&out, r.timestampFit)
        writeI32(&out, r.latSemi ?? ScoutSample.positionInvalid)
        writeI32(&out, r.lonSemi ?? ScoutSample.positionInvalid)
        writeU16(&out, r.speedMmPerS ?? ScoutSample.speedInvalid)
        writeU8(&out, r.poiType)
        writeU8(&out, r.poiDetail)
        writeU8(&out, r.radarCount)
        writeU8(&out, r.radarNear)
        writeU8(&out, r.radarSpeed)
    }

    private static func header(bodySize: Int) -> [UInt8] {
        var head = [UInt8](repeating: 0, count: headerBytes)
        head[0] = UInt8(headerBytes)
        head[1] = UInt8(protocolVersion)
        writeU16Le(&head, offset: 2, value: profileVersion)
        writeU32Le(&head, offset: 4, value: bodySize)
        head[8] = UInt8(ascii: ".")
        head[9] = UInt8(ascii: "F")
        head[10] = UInt8(ascii: "I")
        head[11] = UInt8(ascii: "T")
        return head
    }

    private static func crcBytes(_ crc: Int) -> [UInt8] {
        var out = [UInt8](repeating: 0, count: crcBytes)
        writeU16Le(&out, offset: 0, value: crc)
        return out
    }

    private static func writeU8(_ out: inout Data, _ v: Int) {
        out.append(UInt8(truncatingIfNeeded: v))
    }

    private static func writeU16(_ out: inout Data, _ v: Int) {
        writeU8(&out, v)
        writeU8(&out, v >> 8)
    }

    private static func writeU32(_ out: inout Data, _ v: Int64) {
        writeU16(&out, Int(v & 0xFFFF))
        writeU16(&out, Int((v >> 16) & 0xFFFF))
    }

    private static func writeI32(_ out: inout Data, _ v: Int32) {
        writeU32(&out, Int64(UInt32(bitPattern: v)))
    }

    private static func writeStr(_ out: inout Data, _ s: String, len: Int) {
        let chars = Array(s.utf8)
        for i in 0..<len {
            writeU8(&out, i < chars.count ? Int(chars[i]) : 0)
        }
    }

    private static func writeU16Le(_ buf: inout [UInt8], offset: Int, value: Int) {
        buf[offset] = UInt8(truncatingIfNeeded: value)
        buf[offset + 1] = UInt8(truncatingIfNeeded: value >> 8)
    }

    private static func writeU32Le(_ buf: inout [UInt8], offset: Int, value: Int) {
        writeU16Le(&buf, offset: offset, value: value & 0xFFFF)
        writeU16Le(&buf, offset: offset + 2, value: (value >> 16) & 0xFFFF)
    }
}
