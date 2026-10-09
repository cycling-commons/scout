// SPDX-License-Identifier: MIT
import Foundation

/// Latest fix while RUNNING. Cleared / stopped on pause.
struct LocationFix: Equatable, Sendable {
    var latitude: Double
    var longitude: Double
    /// Metres per second when the provider reported it; nil otherwise.
    var speedMps: Float?
    /// Horizontal accuracy in metres, when the provider reported it.
    var accuracyM: Float?
    var timeMs: Int64
}

/**
 Owns the open FIT file for one ride; flushes on pause/stop and periodically.
 Callers hand samples over from the sampling tick and never block.
 */
final class RideFitSession {
    static let flushEveryRecords = 30

    let outFile: URL
    private let queue = DispatchQueue(label: "org.cyclingcommons.scout.fit", qos: .utility)
    private let writer: ScoutFitWriter
    private var sinceFlush = 0
    private var counted: Int
    private var finished = false

    var recordCount: Int { counted }

    init(existingFile: URL? = nil, existingRecordCount: Int = 0) {
        if let existingFile {
            outFile = existingFile
        } else {
            outFile = RideFiles.dir().appendingPathComponent("scout-\(Self.stamp()).fit")
        }
        counted = max(existingRecordCount, 0)
        writer = ScoutFitWriter(outFile: outFile)
        if existingFile != nil {
            writer.resumeAppend(existingRecordCount: counted)
        }
    }

    func appendSample(
        nowMs: Int64,
        fix: LocationFix?,
        poiType: Int,
        poiDetail: Int,
        radarCount: Int = ScoutSample.radarNA,
        radarNear: Int = ScoutSample.radarNA,
        radarSpeed: Int = ScoutSample.radarNA
    ) {
        let speed: Int? = fix?.speedMps.map { min(max(Int($0 * 1000), 0), 0xFFFE) }
        counted += 1
        let sample = ScoutSample(
            timestampFit: ScoutSample.unixMsToFit(nowMs),
            latSemi: fix.map { ScoutSample.degreesToSemi($0.latitude) },
            lonSemi: fix.map { ScoutSample.degreesToSemi($0.longitude) },
            speedMmPerS: speed,
            poiType: poiType,
            poiDetail: poiDetail,
            radarCount: radarCount,
            radarNear: radarNear,
            radarSpeed: radarSpeed
        )
        queue.async { [weak self] in
            guard let self, !self.finished else { return }
            self.writer.append(sample)
            self.sinceFlush += 1
            if self.sinceFlush >= Self.flushEveryRecords {
                self.writer.flush()
                self.sinceFlush = 0
            }
        }
    }

    func flush() {
        queue.async { [weak self] in
            guard let self, !self.finished else { return }
            if self.writer.recordCount > 0 { self.writer.flush() }
            self.sinceFlush = 0
        }
    }

    /// Closes the file; [onDone] gets the saved ride, or nil when nothing was recorded.
    func finish(onDone: @escaping (URL?) -> Void) {
        queue.async { [weak self] in
            guard let self, !self.finished else {
                DispatchQueue.main.async { onDone(nil) }
                return
            }
            self.finished = true
            let saved: URL?
            if self.writer.recordCount > 0 {
                saved = self.writer.finish()
            } else {
                try? FileManager.default.removeItem(at: self.outFile)
                saved = nil
            }
            DispatchQueue.main.async { onDone(saved) }
        }
    }

    private static func stamp() -> String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.timeZone = .current
        fmt.dateFormat = "yyyyMMdd-HHmmss"
        return fmt.string(from: Date())
    }
}
