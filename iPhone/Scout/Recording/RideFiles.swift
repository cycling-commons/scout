// SPDX-License-Identifier: MIT
import Foundation

struct RideFile: Identifiable, Equatable, Sendable {
    var fileURL: URL
    var name: String
    var sizeBytes: Int64
    var modifiedMs: Int64
    var photoCount: Int = 0

    var id: String { fileURL.path }
}

enum RideFiles {
    static func dir() -> URL {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("rides", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static func list() -> [RideFile] {
        let contents = (try? FileManager.default.contentsOfDirectory(
            at: dir(),
            includingPropertiesForKeys: [.isRegularFileKey, .fileSizeKey, .contentModificationDateKey]
        )) ?? []
        return contents
            .filter { $0.pathExtension.lowercased() == "fit" }
            .map { toRideFile($0) }
            .sorted { $0.modifiedMs > $1.modifiedMs }
    }

    static func toRideFile(_ file: URL) -> RideFile {
        let photos = RidePhotoStore.count(file)
        let attrs = try? FileManager.default.attributesOfItem(atPath: file.path)
        let modified = (attrs?[.modificationDate] as? Date)?.timeIntervalSince1970 ?? 0
        return RideFile(
            fileURL: file,
            name: photos > 0 ? RideZipPackager.zipNameFor(file) : file.lastPathComponent,
            sizeBytes: storedSize(file, photoCount: photos),
            modifiedMs: Int64(modified * 1000),
            photoCount: photos
        )
    }

    static func displayName(_ fitFile: URL) -> String {
        toRideFile(fitFile).name
    }

    private static func storedSize(_ fitFile: URL, photoCount: Int) -> Int64 {
        var total = fileSize(fitFile)
        if photoCount <= 0 { return total }
        let companion = RidePhotoStore.companionDir(fitFile)
        if let enumerator = FileManager.default.enumerator(at: companion, includingPropertiesForKeys: [.fileSizeKey]) {
            for case let child as URL in enumerator {
                total += fileSize(child)
            }
        }
        return total
    }

    static func fileSize(_ url: URL) -> Int64 {
        let attrs = try? FileManager.default.attributesOfItem(atPath: url.path)
        return (attrs?[.size] as? NSNumber)?.int64Value ?? 0
    }

    @discardableResult
    static func delete(_ ride: RideFile) -> Bool {
        RidePhotoStore.deleteCompanion(ride.fileURL)
        if !FileManager.default.fileExists(atPath: ride.fileURL.path) { return true }
        return (try? FileManager.default.removeItem(at: ride.fileURL)) != nil
    }

    @discardableResult
    static func deleteFitAndPhotos(_ fitFile: URL) -> Bool {
        delete(toRideFile(fitFile))
    }
}
