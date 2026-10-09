// SPDX-License-Identifier: MIT
import Foundation

/**
 Live vehicle-count mirror of the parser rule (display only; FIT stays raw).
 Call [onSample] once per second with the occupied target count while TRACKING.
 */
final class VehicleCounter {
    private(set) var carCount: Int = 0
    private var prevCount: Int = 0
    private var heldClosingKph: Int = -1
    private var heldRangeM: Int = -1
    private var minRangeInStretchM: Int = Int.max
    private var stretchLen: Int = 0
    private(set) var lastCarSpeedKph: Int = -1

    static let passConfirmM = 10
    static let passLeaveMaxM = 20

    func onSample(
        tracking: Bool,
        occupiedCount: Int,
        nearestClosingKph: Int,
        nearestRangeM: Int,
        riderKph: Int
    ) {
        if !tracking {
            clearStretch()
            prevCount = 0
            return
        }
        let count = max(occupiedCount, 0)
        let speedValid = nearestClosingKph >= 0
        let rangeValid = nearestRangeM >= 0

        let passLeave =
            count < prevCount &&
            heldClosingKph >= 0 &&
            stretchLen >= 2 &&
            minRangeInStretchM <= Self.passConfirmM &&
            (0...Self.passLeaveMaxM).contains(heldRangeM)
        if passLeave {
            carCount += prevCount - count
            lastCarSpeedKph = heldClosingKph + riderKph
        }

        if count == 0 {
            clearStretch()
        } else {
            stretchLen = prevCount == 0 ? 1 : stretchLen + 1
            if speedValid {
                heldClosingKph = nearestClosingKph
            }
            if rangeValid {
                heldRangeM = nearestRangeM
                if count < prevCount {
                    minRangeInStretchM = nearestRangeM
                } else if nearestRangeM < minRangeInStretchM {
                    minRangeInStretchM = nearestRangeM
                }
            }
        }

        prevCount = count
    }

    private func clearStretch() {
        heldClosingKph = -1
        heldRangeM = -1
        minRangeInStretchM = Int.max
        stretchLen = 0
    }

    func resetRide() {
        carCount = 0
        prevCount = 0
        clearStretch()
        lastCarSpeedKph = -1
    }

    func restore(carCount: Int, lastCarSpeedKph: Int) {
        self.carCount = max(carCount, 0)
        self.lastCarSpeedKph = lastCarSpeedKph
        prevCount = 0
        clearStretch()
    }
}
