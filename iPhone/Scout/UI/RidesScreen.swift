// SPDX-License-Identifier: MIT
import SwiftUI

struct RidesScreen: View {
    @ObservedObject var model: RideViewModel
    var onSharePacked: (PackedRide, RideShareTarget) -> Void
    @State private var sharing: RideFile?
    @Environment(\.scoutColors) private var colors

    var body: some View {
        let ui = model.ui
        ScoutPage(title: L10n.settingsRides, titleColor: colors.brand, onBack: model.closeRides) {
            ScrollView {
                VStack(alignment: .leading, spacing: ScoutSpacing.md) {
                    Text(L10n.settingsRidesHint)
                        .font(ScoutType.body)
                        .foregroundStyle(colors.textSecondary)
                    if ui.rides.isEmpty {
                        Text(L10n.settingsRidesEmpty)
                            .font(ScoutType.body)
                            .foregroundStyle(colors.textSecondary)
                    } else {
                        ForEach(ui.rides) { ride in
                            RideRow(
                                ride: ride,
                                onShare: {
                                    if ui.atlasReviewUrl != nil, ui.atlasName != nil {
                                        sharing = ride
                                    } else {
                                        Task {
                                            if let packed = await model.shareRide(ride, target: .file) {
                                                onSharePacked(packed, .file)
                                            }
                                        }
                                    }
                                },
                                onDelete: { model.deleteRide(ride) },
                                onOpenPhotos: { model.openRidePhotos(ride) }
                            )
                            Divider().overlay(colors.outline)
                        }
                    }
                }
                .padding(.horizontal, ScoutSpacing.lg)
                .padding(.bottom, ScoutSpacing.xxl)
            }
        }
        .alert(L10n.shareRideTitle, isPresented: Binding(
            get: { sharing != nil },
            set: { if !$0 { sharing = nil } }
        )) {
            if let ride = sharing, let name = ui.atlasName {
                Button(name) {
                    Task {
                        if let packed = await model.shareRide(ride, target: .atlas) {
                            onSharePacked(packed, .atlas)
                        }
                    }
                    sharing = nil
                }
                Button(L10n.shareOtherApps) {
                    Task {
                        if let packed = await model.shareRide(ride, target: .file) {
                            onSharePacked(packed, .file)
                        }
                    }
                    sharing = nil
                }
            }
            Button(L10n.rideActionCancel, role: .cancel) { sharing = nil }
        } message: {
            if let name = ui.atlasName {
                Text(String(format: L10n.shareRideHint, name))
            }
        }
    }
}

private struct RideRow: View {
    var ride: RideFile
    var onShare: () -> Void
    var onDelete: () -> Void
    var onOpenPhotos: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(ride.name)
                    .font(ScoutType.body)
                    .foregroundStyle(colors.textPrimary)
                    .lineLimit(1)
                Text(String(format: L10n.settingsRideMeta, formattedDate(ride.modifiedMs), String(format: "%.1f KB", Double(ride.sizeBytes) / 1024)))
                    .font(ScoutType.bodySmall)
                    .foregroundStyle(colors.textSecondary)
                if ride.photoCount > 0 {
                    Text(L10n.ridePhotoCount(ride.photoCount))
                        .font(ScoutType.bodySmall)
                        .foregroundStyle(colors.brand)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture { if ride.photoCount > 0 { onOpenPhotos() } }
            Button(action: onShare) {
                Image(systemName: "square.and.arrow.up")
                    .foregroundStyle(colors.textSecondary)
            }
            .accessibilityLabel(L10n.settingsShare)
            Button(action: onDelete) {
                Image(systemName: "trash")
                    .foregroundStyle(colors.brand)
            }
            .accessibilityLabel(L10n.settingsDelete)
        }
        .padding(.vertical, ScoutSpacing.sm)
    }

    private func formattedDate(_ ms: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(ms) / 1000)
        return DateFormatter.localizedString(from: date, dateStyle: .medium, timeStyle: .short)
    }
}
