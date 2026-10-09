// SPDX-License-Identifier: MIT
import SwiftUI
import UIKit

struct RidePhotosScreen: View {
    var rideName: String
    var photos: [RidePhoto]
    var onBack: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        ScoutPage(title: L10n.settingsRidePhotosTitle, titleColor: colors.brand, onBack: onBack) {
            ScrollView {
                VStack(alignment: .leading, spacing: ScoutSpacing.lg) {
                    Text(rideName)
                        .font(ScoutType.body)
                        .foregroundStyle(colors.textSecondary)
                    if photos.isEmpty {
                        Text(L10n.settingsRidePhotosEmpty)
                            .font(ScoutType.body)
                            .foregroundStyle(colors.textSecondary)
                    } else {
                        ForEach(photos) { photo in
                            HStack(alignment: .top, spacing: ScoutSpacing.md) {
                                if let img = UIImage(contentsOfFile: photo.fileURL.path) {
                                    Image(uiImage: img)
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: ScoutDimens.photoThumb, height: ScoutDimens.photoThumb)
                                        .clipped()
                                        .clipShape(RoundedRectangle(cornerRadius: ScoutDimens.tileCorner))
                                }
                                VStack(alignment: .leading, spacing: ScoutSpacing.xs) {
                                    Text(photo.caption.isEmpty ? L10n.settingsRidePhotoNoCaption : photo.caption)
                                        .font(ScoutType.body)
                                        .foregroundStyle(colors.textPrimary)
                                    Text(DateFormatter.localizedString(
                                        from: Date(timeIntervalSince1970: TimeInterval(photo.capturedAtMs) / 1000),
                                        dateStyle: .medium,
                                        timeStyle: .short
                                    ))
                                    .font(ScoutType.bodySmall)
                                    .foregroundStyle(colors.textSecondary)
                                    if let lat = photo.lat, let lon = photo.lon {
                                        Text(String(format: L10n.settingsRidePhotoGps, lat, lon))
                                            .font(ScoutType.bodySmall)
                                            .foregroundStyle(colors.textSecondary)
                                    } else {
                                        Text(L10n.settingsRidePhotoNoGps)
                                            .font(ScoutType.bodySmall)
                                            .foregroundStyle(colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, ScoutSpacing.lg)
                .padding(.bottom, ScoutSpacing.xxl)
            }
        }
    }
}
