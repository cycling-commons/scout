// SPDX-License-Identifier: MIT
import SwiftUI
import UIKit

@main
struct ScoutApp: App {
    @StateObject private var model = RideViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.colorScheme) private var colorScheme
    @State private var shareItems: [Any]?

    var body: some Scene {
        WindowGroup {
            RootView(model: model, onOpenLink: openLink, onSharePacked: sharePacked)
                .scoutTheme(dark: model.ui.themeMode.isDark(systemDark: colorScheme == .dark))
                .statusBarHidden(false)
                .onAppear {
                    UIApplication.shared.isIdleTimerDisabled =
                        model.ui.keepScreenOn && model.ui.scout.timer == .running
                }
                .onChange(of: model.ui.keepScreenOn) { _, _ in syncIdleTimer() }
                .onChange(of: model.ui.scout.timer) { _, _ in syncIdleTimer() }
                .sheet(isPresented: Binding(
                    get: { shareItems != nil },
                    set: { if !$0 { shareItems = nil } }
                )) {
                    if let items = shareItems {
                        ActivityView(items: items)
                    }
                }
        }
        .onChange(of: scenePhase) { _, phase in
            model.setUiVisible(phase == .active)
            if phase == .active { model.refreshPermissions() }
        }
    }

    private func syncIdleTimer() {
        UIApplication.shared.isIdleTimerDisabled =
            model.ui.keepScreenOn && model.ui.scout.timer == .running
    }

    private func openLink(_ url: String) {
        guard let u = URL(string: url) else { return }
        UIApplication.shared.open(u)
    }

    private func sharePacked(_ packed: PackedRide, target: RideShareTarget) {
        if target == .atlas, let url = model.ui.atlasReviewUrl, let u = URL(string: url) {
            UIApplication.shared.open(u)
            return
        }
        shareItems = [packed.fileURL]
    }
}

private struct RootView: View {
    @ObservedObject var model: RideViewModel
    var onOpenLink: (String) -> Void
    var onSharePacked: (PackedRide, RideShareTarget) -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        Group {
            switch model.ui.screen {
            case .intro:
                IntroScreen(
                    onContinue: {
                        model.dismissIntro()
                        model.requestLocationPermission()
                    },
                    onOpenLink: onOpenLink
                )
            case .recovery:
                if let prompt = model.ui.recovery {
                    RecoveryScreen(
                        prompt: prompt,
                        onResume: {
                            model.requestLocationPermission()
                            model.resumeRecoveredRide()
                        },
                        onDiscard: model.discardRecovery
                    )
                }
            case .pairRadar:
                PairRadarScreen(model: model)
            case .settings:
                SettingsScreen(model: model)
            case .rides:
                RidesScreen(model: model, onSharePacked: onSharePacked)
            case .ridePhotos:
                RidePhotosScreen(
                    rideName: model.ui.viewingRideName ?? "",
                    photos: model.ui.viewingPhotos,
                    onBack: model.closeRidePhotos
                )
            case .help:
                HelpScreen(onBack: model.closeHelp, onOpenLink: onOpenLink)
            case .ride:
                ScoutRideScreen(model: model, onOpenLink: onOpenLink, onSharePacked: onSharePacked)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.screen.ignoresSafeArea())
        .preferredColorScheme(nil)
    }
}

private struct ActivityView: UIViewControllerRepresentable {
    var items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
