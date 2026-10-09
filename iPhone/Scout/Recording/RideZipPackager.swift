// SPDX-License-Identifier: MIT
import Foundation

/// Scout ride bundle: `ride.fit` + `scout.json` + `photos/` (docs/SCOUT-BUNDLE.md).
enum RideZipPackager {
    static let fitEntry = "ride.fit"
    static let manifestEntry = "scout.json"

    static func needsZip(_ fitFile: URL) -> Bool {
        RidePhotoStore.count(fitFile) > 0
    }

    @discardableResult
    static func pack(_ fitFile: URL, destZip: URL) throws -> URL {
        guard FileManager.default.fileExists(atPath: fitFile.path) else {
            throw NSError(domain: "Scout", code: 1, userInfo: [NSLocalizedDescriptionKey: "FIT missing: \(fitFile.path)"])
        }
        try FileManager.default.createDirectory(at: destZip.deletingLastPathComponent(), withIntermediateDirectories: true)
        if FileManager.default.fileExists(atPath: destZip.path) {
            try FileManager.default.removeItem(at: destZip)
        }

        let photos = RidePhotoStore.list(fitFile)
        let tags = bundleTags(photos)
        var entries: [(String, Data)] = []
        entries.append((fitEntry, try Data(contentsOf: fitFile)))
        entries.append((manifestEntry, Data(encodeScoutJson(tags).utf8)))
        for tag in tags {
            for (path, file) in tag.files {
                guard FileManager.default.fileExists(atPath: file.path) else { continue }
                entries.append((path, try Data(contentsOf: file)))
            }
        }
        try ZipWriter.write(entries: entries, to: destZip)
        return destZip
    }

    static func zipNameFor(_ fitFile: URL) -> String {
        RidePhotoStore.stem(fitFile) + ".zip"
    }

    static func utcIso(_ epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        return String(
            format: "%04d-%02d-%02dT%02d:%02d:%02dZ",
            c.year ?? 0, c.month ?? 0, c.day ?? 0,
            c.hour ?? 0, c.minute ?? 0, c.second ?? 0
        )
    }

    static func compactUtc(_ iso: String) -> String {
        iso.replacingOccurrences(of: "-", with: "").replacingOccurrences(of: ":", with: "")
    }

    static func bundleTags(_ photos: [RidePhoto]) -> [BundleTag] {
        let grouped = Dictionary(grouping: photos) { utcIso($0.capturedAtMs) }
        return grouped.keys.sorted().map { at in
            let group = grouped[at] ?? []
            let compact = compactUtc(at)
            let files = group.enumerated().map { index, photo in
                ("photos/\(compact)-\(index + 1).jpg", photo.fileURL)
            }
            let note = group
                .map { $0.caption.trimmingCharacters(in: .whitespacesAndNewlines) }
                .first { !$0.isEmpty }
                .map { String($0.prefix(RidePhotoStore.captionMax)) }
            return BundleTag(at: at, note: note, files: files)
        }
    }

    static func encodeScoutJson(_ tags: [BundleTag]) -> String {
        var tagsJson: [[String: Any]] = []
        for tag in tags {
            var obj: [String: Any] = ["at": tag.at]
            if let note = tag.note, !note.isEmpty { obj["note"] = note }
            if !tag.files.isEmpty {
                obj["photos"] = tag.files.map(\.path)
            }
            tagsJson.append(obj)
        }
        let root: [String: Any] = [
            "format": "scout-bundle",
            "version": 1,
            "fit": fitEntry,
            "tags": tagsJson,
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: root, options: []),
              let text = String(data: data, encoding: .utf8)
        else { return "{}" }
        return text
    }

    struct BundleTag {
        var at: String
        var note: String?
        var files: [(path: String, file: URL)]
    }
}

/// Minimal stored-zip (no extras, UTF-8 names) — enough for the Scout ride bundle.
enum ZipWriter {
    static func write(entries: [(String, Data)], to url: URL) throws {
        var body = Data()
        var central = Data()
        var offset: UInt32 = 0
        for (name, payload) in entries {
            let nameData = Data(name.utf8)
            let crc = crc32(payload)
            var local = Data()
            local.append(contentsOf: u32(0x04034b50))
            local.append(contentsOf: u16(20))
            local.append(contentsOf: u16(0))
            local.append(contentsOf: u16(0))
            local.append(contentsOf: u16(0))
            local.append(contentsOf: u16(0))
            local.append(contentsOf: u32(crc))
            local.append(contentsOf: u32(UInt32(payload.count)))
            local.append(contentsOf: u32(UInt32(payload.count)))
            local.append(contentsOf: u16(UInt16(nameData.count)))
            local.append(contentsOf: u16(0))
            local.append(nameData)
            local.append(payload)
            body.append(local)

            var dir = Data()
            dir.append(contentsOf: u32(0x02014b50))
            dir.append(contentsOf: u16(20))
            dir.append(contentsOf: u16(20))
            dir.append(contentsOf: u16(0))
            dir.append(contentsOf: u16(0))
            dir.append(contentsOf: u16(0))
            dir.append(contentsOf: u16(0))
            dir.append(contentsOf: u32(crc))
            dir.append(contentsOf: u32(UInt32(payload.count)))
            dir.append(contentsOf: u32(UInt32(payload.count)))
            dir.append(contentsOf: u16(UInt16(nameData.count)))
            dir.append(contentsOf: u16(0))
            dir.append(contentsOf: u16(0))
            dir.append(contentsOf: u16(0))
            dir.append(contentsOf: u16(0))
            dir.append(contentsOf: u32(0))
            dir.append(contentsOf: u32(offset))
            dir.append(nameData)
            central.append(dir)
            offset += UInt32(local.count)
        }
        var end = Data()
        end.append(contentsOf: u32(0x06054b50))
        end.append(contentsOf: u16(0))
        end.append(contentsOf: u16(0))
        end.append(contentsOf: u16(UInt16(entries.count)))
        end.append(contentsOf: u16(UInt16(entries.count)))
        end.append(contentsOf: u32(UInt32(central.count)))
        end.append(contentsOf: u32(offset))
        end.append(contentsOf: u16(0))
        var zip = body
        zip.append(central)
        zip.append(end)
        try zip.write(to: url)
    }

    private static func u16(_ v: UInt16) -> [UInt8] {
        [UInt8(truncatingIfNeeded: v), UInt8(truncatingIfNeeded: v >> 8)]
    }

    private static func u32(_ v: UInt32) -> [UInt8] {
        [
            UInt8(truncatingIfNeeded: v),
            UInt8(truncatingIfNeeded: v >> 8),
            UInt8(truncatingIfNeeded: v >> 16),
            UInt8(truncatingIfNeeded: v >> 24),
        ]
    }

    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        for b in data {
            crc = crc32Table[Int((crc ^ UInt32(b)) & 0xFF)] ^ (crc >> 8)
        }
        return crc ^ 0xFFFF_FFFF
    }

    private static let crc32Table: [UInt32] = {
        (0..<256).map { i -> UInt32 in
            var c = UInt32(i)
            for _ in 0..<8 {
                c = (c & 1) != 0 ? (0xEDB8_8320 ^ (c >> 1)) : (c >> 1)
            }
            return c
        }
    }()
}
