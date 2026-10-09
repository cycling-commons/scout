// SPDX-License-Identifier: MIT
import Foundation

/// Persists an in-progress ride so a process kill can offer resume on cold start.
enum RideRecoveryStore {
    static let defaultsKeyFit = "fit_path"
    static let defaultsKeySession = "session_json"
    private static let suite = "scout_ride_recovery"

    struct PendingRecovery: Equatable {
        var fileURL: URL
        var session: RideSessionSnapshot
    }

    static func save(fitPath: String, session: RideSessionSnapshot) {
        let defaults = UserDefaults(suiteName: suite) ?? .standard
        defaults.set(fitPath, forKey: defaultsKeyFit)
        defaults.set(encode(session), forKey: defaultsKeySession)
    }

    static func load() -> PendingRecovery? {
        let defaults = UserDefaults(suiteName: suite) ?? .standard
        guard let fitPath = defaults.string(forKey: defaultsKeyFit),
              let sessionJson = defaults.string(forKey: defaultsKeySession)
        else { return nil }
        let url = URL(fileURLWithPath: fitPath)
        guard FileManager.default.fileExists(atPath: url.path) else {
            clear()
            return nil
        }
        guard let session = decode(sessionJson), session.timer != .idle else {
            clear()
            return nil
        }
        return PendingRecovery(fileURL: url, session: session)
    }

    static func clear() {
        let defaults = UserDefaults(suiteName: suite) ?? .standard
        defaults.removeObject(forKey: defaultsKeyFit)
        defaults.removeObject(forKey: defaultsKeySession)
    }

    static func encode(_ session: RideSessionSnapshot) -> String {
        let root: [String: Any] = [
            "timer": session.timer.rawValue,
            "elapsedMs": session.elapsedMs,
            "sampleCount": session.sampleCount,
            "carCount": session.carCount,
            "lastCarSpeedKph": session.lastCarSpeedKph,
            "queue": session.queuedTags.map { [$0.type, $0.detail] },
            "counts": session.tallies.counts,
            "dangerDetails": session.tallies.dangerDetails,
            "closureDetails": session.tallies.closureDetails,
            "surfaceDetails": session.tallies.surfaceDetails,
            "sceneryDetails": session.tallies.sceneryDetails,
            "resupplyDetails": session.tallies.resupplyDetails,
            "lastTapType": session.tallies.lastTapType,
            "lastTapDetail": session.tallies.lastTapDetail,
            "lastTapAtMs": session.tallies.lastTapAtMs,
            "openSurfaceDetail": session.tallies.openSurfaceDetail,
        ]
        let data = try? JSONSerialization.data(withJSONObject: root, options: [])
        return String(data: data ?? Data(), encoding: .utf8) ?? "{}"
    }

    static func decode(_ json: String) -> RideSessionSnapshot? {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let timerName = root["timer"] as? String,
              let timer = TimerState(rawValue: timerName),
              timer != .idle
        else { return nil }
        let queue: [QueuedTag] = (root["queue"] as? [[Any]])?.compactMap { pair in
            guard pair.count >= 2 else { return nil }
            return QueuedTag(type: intVal(pair[0]), detail: intVal(pair[1]))
        } ?? []
        let tallies = TagTalliesSnapshot(
            counts: intArray(root["counts"]),
            dangerDetails: intArray(root["dangerDetails"], fallbackCount: 6),
            closureDetails: intArray(root["closureDetails"]),
            surfaceDetails: intArray(root["surfaceDetails"]),
            sceneryDetails: intArray(root["sceneryDetails"], fallbackCount: 7),
            resupplyDetails: intArray(root["resupplyDetails"], fallbackCount: 4),
            lastTapType: intVal(root["lastTapType"]),
            lastTapDetail: intVal(root["lastTapDetail"]),
            lastTapAtMs: int64Val(root["lastTapAtMs"]),
            openSurfaceDetail: intVal(root["openSurfaceDetail"])
        )
        return RideSessionSnapshot(
            timer: timer,
            queuedTags: queue,
            tallies: tallies,
            elapsedMs: int64Val(root["elapsedMs"]),
            sampleCount: int64Val(root["sampleCount"]),
            carCount: intVal(root["carCount"]),
            lastCarSpeedKph: intVal(root["lastCarSpeedKph"])
        )
    }

    private static func intVal(_ value: Any?) -> Int {
        if let n = value as? NSNumber { return n.intValue }
        if let i = value as? Int { return i }
        return 0
    }

    private static func int64Val(_ value: Any?) -> Int64 {
        if let n = value as? NSNumber { return n.int64Value }
        if let i = value as? Int64 { return i }
        if let i = value as? Int { return Int64(i) }
        return 0
    }

    private static func intArray(_ value: Any?, fallbackCount: Int = 0) -> [Int] {
        if let arr = value as? [Int] { return arr }
        if let arr = value as? [NSNumber] { return arr.map(\.intValue) }
        return Array(repeating: 0, count: fallbackCount)
    }
}
