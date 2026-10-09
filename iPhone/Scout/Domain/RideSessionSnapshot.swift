// SPDX-License-Identifier: MIT
import Foundation

enum TimerState: String, Equatable, Sendable {
    case idle = "IDLE"
    case running = "RUNNING"
    case paused = "PAUSED"
}

/// In-memory ride state that can survive a process kill when persisted.
struct RideSessionSnapshot: Equatable, Sendable {
    var timer: TimerState
    var queuedTags: [QueuedTag]
    var tallies: TagTalliesSnapshot
    /// Wall-clock elapsed at the last persist (frozen while the app was dead).
    var elapsedMs: Int64
    var sampleCount: Int64
    var carCount: Int
    var lastCarSpeedKph: Int
}
