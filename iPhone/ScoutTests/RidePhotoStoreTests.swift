// SPDX-License-Identifier: MIT
import XCTest
@testable import Scout

final class RidePhotoStoreTests: XCTestCase {
    var tmp: URL!

    override func setUp() {
        super.setUp()
        tmp = FileManager.default.temporaryDirectory.appendingPathComponent("scout-photos-\(UUID().uuidString)", isDirectory: true)
        try! FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: tmp)
        super.tearDown()
    }

    func testSaveListAndDeleteCompanion() throws {
        let fit = tmp.appendingPathComponent("scout-20260918-214400.fit")
        try Data("fit".utf8).write(to: fit)
        let first = try RidePhotoStore.addPreparedJpeg(
            fitFile: fit,
            jpegBytes: Data([0xFF, 0xD8]),
            capturedAtMs: 1_726_680_000_000,
            lat: 52.0016,
            lon: 4.0024,
            accuracyM: 4.2,
            caption: "closure sign"
        )
        XCTAssertEqual("p_001", first.id)
        XCTAssertEqual("p_001.jpg", first.fileName)
        XCTAssertTrue(FileManager.default.fileExists(atPath: first.fileURL.path))

        let second = try RidePhotoStore.addPreparedJpeg(
            fitFile: fit,
            jpegBytes: Data([0xFF, 0xD8, 0x01]),
            capturedAtMs: 1_726_680_001_000,
            lat: nil,
            lon: nil,
            accuracyM: nil,
            caption: "  trimmed  "
        )
        XCTAssertEqual("p_002", second.id)
        XCTAssertEqual("trimmed", second.caption)

        let listed = RidePhotoStore.list(fit)
        XCTAssertEqual(2, listed.count)
        XCTAssertEqual(52.0016, listed[0].lat)
        XCTAssertEqual("trimmed", listed[1].caption)
        let bundled = RideFiles.toRideFile(fit)
        XCTAssertEqual(2, bundled.photoCount)
        XCTAssertEqual("scout-20260918-214400.zip", bundled.name)

        XCTAssertTrue(RidePhotoStore.deleteCompanion(fit))
        XCTAssertEqual(0, RidePhotoStore.count(fit))
        XCTAssertTrue(FileManager.default.fileExists(atPath: fit.path))
        XCTAssertEqual("scout-20260918-214400.fit", RideFiles.toRideFile(fit).name)
    }

    func testCaptionIsCappedAtBundleLimit() throws {
        let fit = tmp.appendingPathComponent("scout-cap.fit")
        try Data().write(to: fit)
        let photo = try RidePhotoStore.addPreparedJpeg(
            fitFile: fit,
            jpegBytes: Data([1]),
            capturedAtMs: 1,
            lat: nil,
            lon: nil,
            accuracyM: nil,
            caption: String(repeating: "x", count: 250)
        )
        XCTAssertEqual(200, RidePhotoStore.captionMax)
        XCTAssertEqual(200, photo.caption.count)
    }

    func testDeleteRideRemovesFitAndPhotos() throws {
        let fit = tmp.appendingPathComponent("scout-del.fit")
        try Data().write(to: fit)
        _ = try RidePhotoStore.addPreparedJpeg(
            fitFile: fit,
            jpegBytes: Data([1]),
            capturedAtMs: 1,
            lat: 1.0,
            lon: 2.0,
            accuracyM: 3.0,
            caption: "a"
        )
        let ride = RideFiles.toRideFile(fit)
        XCTAssertEqual(1, ride.photoCount)
        XCTAssertTrue(RideFiles.delete(ride))
        XCTAssertFalse(FileManager.default.fileExists(atPath: fit.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: RidePhotoStore.companionDir(fit).path))
    }

    func testZipIsScoutBundleWithRideFitAndScoutJson() throws {
        let fit = tmp.appendingPathComponent("scout-zip.fit")
        try Data("FITBYTES".utf8).write(to: fit)
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        let atMs = Int64((formatter.date(from: "2026-09-12T14:46:33Z")!.timeIntervalSince1970) * 1000)
        _ = try RidePhotoStore.addPreparedJpeg(
            fitFile: fit,
            jpegBytes: Data([9, 8, 7]),
            capturedAtMs: atMs,
            lat: 51.0,
            lon: 4.0,
            accuracyM: 5.0,
            caption: "view"
        )
        let zipFile = tmp.appendingPathComponent("out.zip")
        _ = try RideZipPackager.pack(fit, destZip: zipFile)
        XCTAssertTrue(RideZipPackager.needsZip(fit))

        let names = try zipEntryNames(zipFile)
        XCTAssertEqual(Set(["ride.fit", "scout.json", "photos/20260912T144633Z-1.jpg"]), Set(names))
        let json = try zipString(zipFile, "scout.json")
        let obj = try JSONSerialization.jsonObject(with: Data(json.utf8)) as! [String: Any]
        XCTAssertEqual("scout-bundle", obj["format"] as? String)
        XCTAssertEqual(1, obj["version"] as? Int)
        XCTAssertEqual("ride.fit", obj["fit"] as? String)
        let tag = (obj["tags"] as! [[String: Any]])[0]
        XCTAssertEqual("2026-09-12T14:46:33Z", tag["at"] as? String)
        XCTAssertEqual("view", tag["note"] as? String)
        XCTAssertEqual("photos/20260912T144633Z-1.jpg", (tag["photos"] as? [String])?[0])
        XCTAssertNil(tag["n"])
    }

    func testTwoPhotosInTheSameSecondShareOneTag() throws {
        let fit = tmp.appendingPathComponent("scout-same-sec.fit")
        try Data("x".utf8).write(to: fit)
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        let atMs = Int64((formatter.date(from: "2026-09-12T14:46:33Z")!.timeIntervalSince1970) * 1000)
        _ = try RidePhotoStore.addPreparedJpeg(
            fitFile: fit, jpegBytes: Data([1]), capturedAtMs: atMs,
            lat: nil, lon: nil, accuracyM: nil, caption: "first"
        )
        _ = try RidePhotoStore.addPreparedJpeg(
            fitFile: fit, jpegBytes: Data([2]), capturedAtMs: atMs + 400,
            lat: nil, lon: nil, accuracyM: nil, caption: "second"
        )
        let zipFile = tmp.appendingPathComponent("same.zip")
        _ = try RideZipPackager.pack(fit, destZip: zipFile)
        let json = try zipString(zipFile, "scout.json")
        let obj = try JSONSerialization.jsonObject(with: Data(json.utf8)) as! [String: Any]
        let tags = obj["tags"] as! [[String: Any]]
        XCTAssertEqual(1, tags.count)
        let photos = tags[0]["photos"] as! [String]
        XCTAssertEqual(2, photos.count)
        XCTAssertEqual("photos/20260912T144633Z-1.jpg", photos[0])
        XCTAssertEqual("photos/20260912T144633Z-2.jpg", photos[1])
        XCTAssertEqual("first", tags[0]["note"] as? String)
    }

    func testRidesWithoutPhotosDoNotNeedZip() throws {
        let fit = tmp.appendingPathComponent("scout-plain.fit")
        try Data("x".utf8).write(to: fit)
        XCTAssertFalse(RideZipPackager.needsZip(fit))
    }

    func testSampleSizeHalvesUntilMaxEdge() {
        XCTAssertEqual(1, RideImagePrep.sampleSize(width: 1024, height: 768, maxEdge: 2048))
        XCTAssertEqual(2, RideImagePrep.sampleSize(width: 4096, height: 3072, maxEdge: 2048))
        XCTAssertEqual(4, RideImagePrep.sampleSize(width: 8192, height: 6144, maxEdge: 2048))
    }

    func testRecoveryRoundTrip() {
        let tallies = TagTalliesSnapshot(
            counts: [0, 1, 0, 0, 0, 0, 0, 0, 0, 0],
            dangerDetails: Array(repeating: 0, count: 6),
            closureDetails: Array(repeating: 0, count: 6),
            surfaceDetails: Array(repeating: 0, count: 10),
            sceneryDetails: Array(repeating: 0, count: 7),
            resupplyDetails: Array(repeating: 0, count: 4),
            lastTapType: PoiType.other,
            lastTapDetail: 0,
            lastTapAtMs: 12,
            openSurfaceDetail: 0
        )
        let session = RideSessionSnapshot(
            timer: .running,
            queuedTags: [QueuedTag(type: PoiType.other, detail: 0)],
            tallies: tallies,
            elapsedMs: 1000,
            sampleCount: 3,
            carCount: 1,
            lastCarSpeedKph: 40
        )
        let json = RideRecoveryStore.encode(session)
        let decoded = RideRecoveryStore.decode(json)
        XCTAssertEqual(session, decoded)
    }

    private func zipEntryNames(_ url: URL) throws -> [String] {
        let data = try Data(contentsOf: url)
        var names: [String] = []
        var i = 0
        let bytes = [UInt8](data)
        while i + 30 < bytes.count {
            let sig = u32(bytes, i)
            if sig == 0x04034b50 {
                let nameLen = Int(u16(bytes, i + 26))
                let extra = Int(u16(bytes, i + 28))
                let comp = Int(u32(bytes, i + 18))
                let nameData = Data(bytes[(i + 30)..<(i + 30 + nameLen)])
                names.append(String(data: nameData, encoding: .utf8) ?? "")
                i += 30 + nameLen + extra + comp
            } else {
                break
            }
        }
        return names
    }

    private func zipString(_ url: URL, _ name: String) throws -> String {
        let data = try Data(contentsOf: url)
        let bytes = [UInt8](data)
        var i = 0
        while i + 30 < bytes.count {
            let sig = u32(bytes, i)
            if sig != 0x04034b50 { break }
            let nameLen = Int(u16(bytes, i + 26))
            let extra = Int(u16(bytes, i + 28))
            let comp = Int(u32(bytes, i + 18))
            let entry = String(data: Data(bytes[(i + 30)..<(i + 30 + nameLen)]), encoding: .utf8) ?? ""
            let start = i + 30 + nameLen + extra
            if entry == name {
                return String(data: Data(bytes[start..<(start + comp)]), encoding: .utf8) ?? ""
            }
            i = start + comp
        }
        return ""
    }

    private func u16(_ b: [UInt8], _ i: Int) -> UInt16 {
        UInt16(b[i]) | (UInt16(b[i + 1]) << 8)
    }

    private func u32(_ b: [UInt8], _ i: Int) -> UInt32 {
        UInt32(b[i]) | (UInt32(b[i + 1]) << 8) | (UInt32(b[i + 2]) << 16) | (UInt32(b[i + 3]) << 24)
    }
}
