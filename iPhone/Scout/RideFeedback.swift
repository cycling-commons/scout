// SPDX-License-Identifier: MIT
import AudioToolbox
import Foundation
import UIKit

/**
 Eyes-on-road confirmation (SPEC §6.9): a short buzz plus a tone, with a distinct
 pattern for undo. Failures are swallowed — tagging must never block.
 */
enum RideFeedback {
    static func confirm(undone: Bool) {
        vibrate(undone: undone)
        playTone(undone: undone)
    }

    private static func vibrate(undone: Bool) {
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(undone ? .warning : .success)
        if undone {
            let impact = UIImpactFeedbackGenerator(style: .rigid)
            impact.impactOccurred()
        }
    }

    private static func playTone(undone: Bool) {
        // System clicks — same idea as Garmin / Android: confirm vs reset.
        AudioServicesPlaySystemSound(undone ? 1053 : 1104)
    }
}
