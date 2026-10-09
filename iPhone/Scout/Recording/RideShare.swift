// SPDX-License-Identifier: MIT
import Foundation

enum RideShareTarget {
    case atlas
    case file
}

struct PackedRide: Equatable, Sendable {
    var fileURL: URL
    var mime: String
}

enum RideShare {
    static func packed(_ fitFile: URL) throws -> PackedRide {
        if RideZipPackager.needsZip(fitFile) {
            let zip = FileManager.default.temporaryDirectory
                .appendingPathComponent("shares", isDirectory: true)
                .appendingPathComponent(RideZipPackager.zipNameFor(fitFile))
            return PackedRide(fileURL: try RideZipPackager.pack(fitFile, destZip: zip), mime: "application/zip")
        }
        return PackedRide(fileURL: fitFile, mime: "application/octet-stream")
    }

    /// Copy into the app Documents folder so Files.app can see it (Atlas drop-in flow).
    @discardableResult
    static func copyToDocuments(_ packed: PackedRide) -> URL? {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dest = docs.appendingPathComponent(packed.fileURL.lastPathComponent)
        do {
            if FileManager.default.fileExists(atPath: dest.path) {
                try FileManager.default.removeItem(at: dest)
            }
            try FileManager.default.copyItem(at: packed.fileURL, to: dest)
            return dest
        } catch {
            return nil
        }
    }
}
