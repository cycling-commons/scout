// SPDX-License-Identifier: MIT
import Foundation

struct QueuedTag: Equatable, Sendable {
    let type: Int
    let detail: Int
}

/// FIFO of tags drained one per sample (~1 Hz). Capacity is load-bearing for undo.
final class TagQueue {
    private let max: Int
    private var items: [QueuedTag] = []

    var size: Int { items.count }

    init(max: Int = Timings.queueMax) {
        self.max = max
    }

    @discardableResult
    func offer(type: Int, detail: Int) -> Bool {
        if items.count >= max { return false }
        items.append(QueuedTag(type: type, detail: detail))
        return true
    }

    func poll() -> QueuedTag? {
        guard !items.isEmpty else { return nil }
        return items.removeFirst()
    }

    func clear() {
        items.removeAll()
    }

    func snapshot() -> [QueuedTag] { items }

    func restore(_ tags: [QueuedTag]) {
        items.removeAll()
        for tag in tags {
            if items.count >= max { break }
            items.append(tag)
        }
    }
}
