// SPDX-License-Identifier: MIT
import SwiftUI

struct RecoveryScreen: View {
    var prompt: RecoveryPrompt
    var onResume: () -> Void
    var onDiscard: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: ScoutSpacing.xl) {
            ScoutLogo(markSize: 72, vertical: true)
            Text(L10n.recoveryTitle)
                .font(ScoutType.display)
                .foregroundStyle(colors.brand)
            Text(
                String(
                    format: L10n.recoveryMessage,
                    prompt.elapsedLabel,
                    prompt.sampleCount,
                    prompt.fitFileName
                )
            )
            .font(ScoutType.body)
            .foregroundStyle(colors.textSecondary)
            ScoutButton(label: L10n.recoveryResume, primary: true, action: onResume)
            ScoutButton(label: L10n.recoveryDiscard, action: onDiscard)
            Text(L10n.recoveryDiscardHint)
                .font(ScoutType.bodySmall)
                .foregroundStyle(colors.textSecondary)
                .frame(maxWidth: .infinity, alignment: .center)
            Spacer()
        }
        .padding(.horizontal, ScoutSpacing.lg)
        .padding(.vertical, ScoutSpacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.screen)
    }
}
