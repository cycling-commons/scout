// SPDX-License-Identifier: MIT
import Foundation

/**
 One in-ride photo, stored next to the FIT (not inside it).
 */
struct RidePhoto: Identifiable, Equatable, Sendable {
    var id: String
    var fileName: String
    var fileURL: URL
    var capturedAtMs: Int64
    var lat: Double?
    var lon: Double?
    var accuracyM: Double?
    var caption: String
}

/**
 On-device sidecar: `<stem>/photos.json` + JPEGs.
 Share export is the Scout ride bundle (`scout.json`) in [RideZipPackager].
 */
enum RidePhotoStore {
    static let manifestName = "photos.json"
    static let captionMax = 200

    static func companionDir(_ fitFile: URL) -> URL {
        fitFile.deletingLastPathComponent().appendingPathComponent(stem(fitFile), isDirectory: true)
    }

    static func stem(_ fitFile: URL) -> String {
        let name = fitFile.lastPathComponent
        if name.lowercased().hasSuffix(".fit") {
            return String(name.dropLast(4))
        }
        return name
    }

    static func count(_ fitFile: URL) -> Int { list(fitFile).count }

    static func list(_ fitFile: URL) -> [RidePhoto] {
        let dir = companionDir(fitFile)
        let manifest = dir.appendingPathComponent(manifestName)
        guard FileManager.default.fileExists(atPath: manifest.path),
              let text = try? String(contentsOf: manifest, encoding: .utf8)
        else { return [] }
        return (try? parseManifest(text, dir: dir))?.filter {
            FileManager.default.fileExists(atPath: $0.fileURL.path)
        } ?? []
    }

    @discardableResult
    static func addPreparedJpeg(
        fitFile: URL,
        jpegBytes: Data,
        capturedAtMs: Int64,
        lat: Double?,
        lon: Double?,
        accuracyM: Double?,
        caption: String
    ) throws -> RidePhoto {
        let dir = companionDir(fitFile)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let existing = list(fitFile)
        let next = nextId(existing)
        let fileName = "\(next).jpg"
        let jpeg = dir.appendingPathComponent(fileName)
        try jpegBytes.write(to: jpeg)
        let photo = RidePhoto(
            id: next,
            fileName: fileName,
            fileURL: jpeg,
            capturedAtMs: capturedAtMs,
            lat: lat,
            lon: lon,
            accuracyM: accuracyM,
            caption: String(caption.trimmingCharacters(in: .whitespacesAndNewlines).prefix(captionMax))
        )
        try writeManifest(dir: dir, photos: existing + [photo])
        return photo
    }

    @discardableResult
    static func deleteCompanion(_ fitFile: URL) -> Bool {
        let dir = companionDir(fitFile)
        if !FileManager.default.fileExists(atPath: dir.path) { return true }
        return (try? FileManager.default.removeItem(at: dir)) != nil
    }

    static func encodeManifest(_ photos: [RidePhoto]) throws -> String {
        var array: [[String: Any]] = []
        for photo in photos {
            var obj: [String: Any] = [
                "id": photo.id,
                "file": photo.fileName,
                "capturedAtMs": photo.capturedAtMs,
                "caption": photo.caption,
            ]
            if let lat = photo.lat { obj["lat"] = lat }
            if let lon = photo.lon { obj["lon"] = lon }
            if let accuracyM = photo.accuracyM { obj["accuracyM"] = accuracyM }
            array.append(obj)
        }
        let data = try JSONSerialization.data(withJSONObject: ["photos": array], options: [])
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    static func parseManifest(_ json: String, dir: URL) throws -> [RidePhoto] {
        guard let data = json.data(using: .utf8),
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let array = root["photos"] as? [[String: Any]]
        else { return [] }
        return array.compactMap { obj in
            guard let id = obj["id"] as? String, !id.isEmpty else { return nil }
            let fileName = (obj["file"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "\(id).jpg"
            return RidePhoto(
                id: id,
                fileName: fileName,
                fileURL: dir.appendingPathComponent(fileName),
                capturedAtMs: int64(obj["capturedAtMs"]),
                lat: optionalDouble(obj["lat"]),
                lon: optionalDouble(obj["lon"]),
                accuracyM: optionalDouble(obj["accuracyM"]),
                caption: obj["caption"] as? String ?? ""
            )
        }
    }

    private static func writeManifest(dir: URL, photos: [RidePhoto]) throws {
        try encodeManifest(photos).write(
            to: dir.appendingPathComponent(manifestName),
            atomically: true,
            encoding: .utf8
        )
    }

    private static func nextId(_ existing: [RidePhoto]) -> String {
        let maxId = existing.compactMap { photo -> Int? in
            let raw = photo.id.hasPrefix("p_") ? String(photo.id.dropFirst(2)) : photo.id
            return Int(raw)
        }.max() ?? 0
        return String(format: "p_%03d", maxId + 1)
    }

    private static func int64(_ value: Any?) -> Int64 {
        if let n = value as? NSNumber { return n.int64Value }
        if let i = value as? Int { return Int64(i) }
        if let i = value as? Int64 { return i }
        return 0
    }

    private static func optionalDouble(_ value: Any?) -> Double? {
        if value == nil || value is NSNull { return nil }
        if let n = value as? NSNumber { return n.doubleValue }
        if let d = value as? Double { return d.isNaN ? nil : d }
        return nil
    }
}
