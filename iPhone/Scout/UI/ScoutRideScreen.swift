// SPDX-License-Identifier: MIT
import PhotosUI
import SwiftUI
import UIKit

private let gridColumns = 2
private let paleTileLuminance = 0.3
private let mphPerKph = 0.621371

struct ScoutRideScreen: View {
    @ObservedObject var model: RideViewModel
    var onOpenLink: (String) -> Void
    var onSharePacked: (PackedRide, RideShareTarget) -> Void

    @State private var showShare = false
    @State private var confirmStop = false
    @State private var showCamera = false
    @State private var caption = ""
    @Environment(\.scoutColors) private var colors

    var body: some View {
        let ui = model.ui
        let scout = ui.scout
        ZStack(alignment: .bottom) {
            VStack(spacing: ScoutSpacing.sm) {
                rideHeader
                TagGrid(
                    mode: scout.mode,
                    tiles: scout.tiles,
                    counts: scout.tileCounts,
                    flashIdx: scout.flashIdx,
                    flashUntilMs: scout.flashUntilMs,
                    flashUndoWindow: scout.flashUndoWindow,
                    pendingIdx: scout.pendingIdx,
                    pendingUntilMs: scout.pendingUntilMs,
                    title: scout.title,
                    openSurfaceLabel: scout.openSurfaceLabel,
                    onTileTap: model.onTileTap
                )
                if let label = scout.openSurfaceLabel {
                    OpenSurfaceBanner(label: label, onEnd: model.endOpenSurface)
                }
                RadarStrip(
                    live: scout.radarLive,
                    hasRadar: ui.radar.hasSavedRadar,
                    recording: scout.timer == .running,
                    seeking: ui.radar.seeking,
                    carCount: scout.carCount,
                    speedKph: scout.lastCarSpeedKph,
                    imperial: scout.imperial,
                    onRetry: model.retryRadar
                )
                rideControls
            }
            .padding(.horizontal, ScoutSpacing.md)
            .padding(.vertical, ScoutSpacing.sm)
        }
        .background(colors.screen)
        .alert(L10n.rideStopConfirmTitle, isPresented: $confirmStop) {
            Button(L10n.rideActionCancel, role: .cancel) {}
            Button(L10n.rideStopConfirmAction, role: .destructive) { model.stopRide() }
        } message: {
            Text(ui.ridePhotoCount > 0 ? L10n.rideStopConfirmMessageWithPhotos : L10n.rideStopConfirmMessage)
        }
        .overlay {
            if ui.showPhotoChooser {
                PhotoChooserOverlay(
                    onTake: { showCamera = true },
                    onPickedData: { data in
                        model.dismissPhotoChooser()
                        model.ingestPhotoData(data)
                    },
                    onDismiss: model.dismissPhotoChooser
                )
            }
        }
        .sheet(isPresented: $showCamera) {
            CameraPicker { data in
                showCamera = false
                model.dismissPhotoChooser()
                if let data { model.ingestPhotoData(data) }
            }
        }
        .sheet(isPresented: Binding(
            get: { ui.photoDraftPath != nil },
            set: { if !$0 { model.discardPhotoDraft() } }
        )) {
            if let path = ui.photoDraftPath {
                PhotoCaptionSheet(
                    draftPath: path,
                    caption: $caption,
                    onSave: {
                        model.savePhoto(caption: caption)
                        caption = ""
                    },
                    onDiscard: {
                        caption = ""
                        model.discardPhotoDraft()
                    }
                )
            }
        }
        .alert(L10n.shareRideTitle, isPresented: $showShare) {
            if let name = ui.atlasName {
                Button(name) {
                    Task {
                        if let packed = await model.shareLastFit(.atlas) {
                            onSharePacked(packed, .atlas)
                        }
                    }
                }
            }
            Button(L10n.shareOtherApps) {
                Task {
                    if let packed = await model.shareLastFit(.file) {
                        onSharePacked(packed, .file)
                    }
                }
            }
            Button(L10n.rideActionCancel, role: .cancel) {}
        } message: {
            if let name = ui.atlasName {
                Text(String(format: L10n.shareRideHint, name))
            }
        }
        .overlay(alignment: .bottom) {
            if let message = ui.userMessage {
                Snackbar(
                    message: message,
                    action: scout.timer == .idle ? L10n.rideActionStart : scout.timer == .paused ? L10n.rideActionResume : nil
                ) {
                    switch scout.timer {
                    case .idle: model.startRide()
                    case .paused: model.resumeRide()
                    case .running: break
                    }
                    model.clearUserMessage()
                } onDismiss: {
                    model.clearUserMessage()
                }
                .padding(.horizontal, ScoutSpacing.md)
                .padding(.bottom, ScoutDimens.controlHeight + 88)
            }
        }
    }

    private var rideHeader: some View {
        let ui = model.ui
        let scout = ui.scout
        let timerLabel: String = {
            switch scout.timer {
            case .idle: L10n.rideStateIdle
            case .running: L10n.rideStateRecording
            case .paused: L10n.rideStatePaused
            }
        }()
        return VStack(alignment: .leading, spacing: ScoutSpacing.sm) {
            HStack {
                ScoutLogo(markSize: 48)
                Spacer()
                StatusPill(
                    label: timerLabel,
                    dotColor: scout.timer == .running ? colors.recording : colors.brand
                )
                if scout.timer != .running {
                    IconBtn(system: "folder", label: L10n.rideActionRides, action: model.openRides)
                }
                if scout.timer == .idle, ui.lastFitPath != nil {
                    IconBtn(system: "square.and.arrow.up", label: L10n.rideActionShare) {
                        if ui.atlasReviewUrl != nil, ui.atlasName != nil {
                            showShare = true
                        } else {
                            Task {
                                if let packed = await model.shareLastFit(.file) {
                                    onSharePacked(packed, .file)
                                }
                            }
                        }
                    }
                }
                IconBtn(system: "questionmark.circle", label: L10n.rideActionHelp) {
                    model.openHelp(returnTo: .ride)
                }
                IconBtn(system: "gearshape", label: L10n.rideActionSettings, action: model.openSettings)
            }
            ZStack(alignment: .bottom) {
                HStack(alignment: .bottom) {
                    Metric(label: L10n.rideClockLabel, value: formatElapsed(ui.elapsedSec), style: ScoutType.rideClock)
                    Spacer()
                    Metric(label: L10n.rideTagsLabel, value: "\(scout.tagTotal)", style: ScoutType.metric, alignEnd: true)
                }
                .padding(.horizontal, ScoutSpacing.lg)
                .padding(.vertical, ScoutSpacing.sm)
                if scout.timer == .running {
                    Button(action: model.openPhotoChooser) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 22))
                            .foregroundStyle(colors.textPrimary)
                            .frame(width: 52, height: 52)
                    }
                    .offset(x: 28, y: 5)
                    .accessibilityLabel(L10n.rideActionPhoto)
                }
            }
            .background(colors.surface, in: RoundedRectangle(cornerRadius: ScoutDimens.cardCorner))
            Text(statusLine)
                .font(ScoutType.bodySmall)
                .foregroundStyle(ui.hasLocationPermission ? colors.textSecondary : colors.warning)
                .lineLimit(1)
                .padding(.horizontal, ScoutSpacing.xs)
                .onTapGesture {
                    if scout.timer == .idle, ui.lastFitPath != nil { model.openRides() }
                }
        }
    }

    private var statusLine: String {
        let ui = model.ui
        if !ui.hasLocationPermission { return L10n.ridePermissionNeeded }
        if let fix = ui.fixLabel { return fix }
        if ui.scout.timer != .idle { return L10n.rideWaitingForFix }
        if let path = ui.lastFitPath {
            return String(format: L10n.rideLastFile, RideFiles.displayName(URL(fileURLWithPath: path)))
        }
        return L10n.rideWaitingForFix
    }

    private var rideControls: some View {
        HStack(spacing: ScoutSpacing.sm) {
            switch model.ui.scout.timer {
            case .idle:
                ScoutButton(label: L10n.rideActionStart, primary: true) {
                    model.requestLocationPermission()
                    model.startRide()
                }
            case .running:
                ScoutButton(label: L10n.rideActionPause, action: model.pauseRide)
                ScoutButton(label: L10n.rideActionStop, primary: true) { confirmStop = true }
            case .paused:
                ScoutButton(label: L10n.rideActionResume, primary: true) {
                    model.requestLocationPermission()
                    model.resumeRide()
                }
                ScoutButton(label: L10n.rideActionStop) { confirmStop = true }
            }
        }
    }
}

private struct IconBtn: View {
    var system: String
    var label: String
    var action: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(systemName: system)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(colors.textSecondary)
                .frame(width: 40, height: 40)
        }
        .accessibilityLabel(label)
    }
}

private struct Metric: View {
    var label: String
    var value: String
    var style: Font
    var alignEnd: Bool = false
    @Environment(\.scoutColors) private var colors

    var body: some View {
        VStack(alignment: alignEnd ? .trailing : .leading, spacing: 0) {
            Text(label.uppercased())
                .font(ScoutType.overline)
                .foregroundStyle(colors.textSecondary)
            Text(value)
                .font(style)
                .foregroundStyle(colors.textPrimary)
                .lineLimit(1)
        }
    }
}

private struct TagGrid: View {
    var mode: UiMode
    var tiles: [Tile]
    var counts: [Int]
    var flashIdx: Int
    var flashUntilMs: Int64
    var flashUndoWindow: Bool
    var pendingIdx: Int
    var pendingUntilMs: Int64
    var title: String?
    var openSurfaceLabel: String?
    var onTileTap: (Int) -> Void

    @State private var nowMs = Int64(Date().timeIntervalSince1970 * 1000)
    @Environment(\.scoutColors) private var colors

    var body: some View {
        let rows = max(Int(ceil(Double(tiles.count) / Double(gridColumns))), 1)
        VStack(spacing: ScoutSpacing.sm) {
            if let title {
                Text(title)
                    .font(ScoutType.overline)
                    .foregroundStyle(colors.textSecondary)
                    .frame(maxWidth: .infinity)
            }
            ForEach(0..<rows, id: \.self) { row in
                HStack(spacing: ScoutSpacing.sm) {
                    ForEach(0..<gridColumns, id: \.self) { col in
                        let i = row * gridColumns + col
                        if i >= tiles.count {
                            Color.clear.frame(maxWidth: .infinity, maxHeight: .infinity)
                        } else {
                            let tile = tiles[i]
                            let lit = i == flashIdx || (mode != .grid && i == pendingIdx)
                            let openStretch = (mode == .grid && tile.code == PoiType.surface) ? openSurfaceLabel : nil
                            let untilMs: Int64 = {
                                if i == flashIdx, flashUntilMs > nowMs { return flashUntilMs }
                                if mode != .grid, i == pendingIdx, pendingUntilMs > nowMs { return pendingUntilMs }
                                return 0
                            }()
                            TagTile(
                                label: openStretch ?? tile.label,
                                overline: openStretch == nil ? nil : tile.label,
                                count: counts.indices.contains(i) ? counts[i] : 0,
                                countdownSec: untilMs > 0 ? max(Int((untilMs - nowMs + 999) / 1000), 1) : 0,
                                rgb: tile.rgb,
                                filled: lit || openStretch != nil,
                                onClick: { onTileTap(i) }
                            )
                        }
                    }
                }
            }
        }
        .onChange(of: flashUntilMs) { _, _ in
            nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        }
        .onChange(of: pendingUntilMs) { _, _ in
            nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        }
        .background {
            let needs = (flashIdx >= 0 && flashUntilMs > nowMs) || (pendingIdx >= 0 && pendingUntilMs > nowMs)
            if needs {
                TimelineView(.periodic(from: .now, by: 0.1)) { context in
                    Color.clear
                        .onChange(of: context.date) { _, _ in
                            nowMs = Int64(Date().timeIntervalSince1970 * 1000)
                        }
                }
                .frame(width: 0, height: 0)
                .accessibilityHidden(true)
            }
        }
    }
}

private struct TagTile: View {
    var label: String
    var overline: String?
    var count: Int
    var countdownSec: Int
    var rgb: Int
    var filled: Bool
    var onClick: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        let color = scoutHexColor(rgb)
        let content: Color = {
            if !filled { return colors.textPrimary }
            if tileLuminance(rgb) > paleTileLuminance { return colors.textOnPale }
            return colors.textOnBrand
        }()
        Button(action: onClick) {
            ZStack(alignment: .topTrailing) {
                VStack(spacing: 2) {
                    if let overline {
                        Text(overline)
                            .font(ScoutType.overline)
                            .foregroundStyle(content.opacity(0.75))
                    }
                    Text(label)
                        .font(ScoutType.tileLabel)
                        .foregroundStyle(content)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(ScoutSpacing.sm)
                if count > 0 {
                    Text("\(count)")
                        .font(ScoutType.tileCount)
                        .foregroundStyle(content)
                        .padding(.horizontal, ScoutSpacing.md)
                }
                if countdownSec > 0 {
                    Text("\(countdownSec)s")
                        .font(ScoutType.countdown)
                        .foregroundStyle(content)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                        .padding(.horizontal, ScoutSpacing.md)
                        .padding(.vertical, ScoutSpacing.sm)
                }
            }
            .background(
                RoundedRectangle(cornerRadius: ScoutDimens.tileCorner)
                    .fill(filled ? color : color.opacity(colors.tileIdleAlpha))
            )
            .overlay(
                RoundedRectangle(cornerRadius: ScoutDimens.tileCorner)
                    .stroke(color, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel(label)
    }
}

private struct OpenSurfaceBanner: View {
    var label: String
    var onEnd: () -> Void
    @State private var dimmed = false
    @Environment(\.scoutColors) private var colors

    var body: some View {
        Button(action: onEnd) {
            HStack {
                Text(String(format: L10n.surfaceOpenBanner, label))
                    .font(ScoutType.tileLabel)
                    .foregroundStyle(colors.textOnBrand)
                Spacer()
                Text(L10n.surfaceOpenAction.uppercased())
                    .font(ScoutType.overline)
                    .foregroundStyle(colors.textOnBrand)
            }
            .padding(.horizontal, ScoutSpacing.lg)
            .padding(.vertical, ScoutSpacing.md)
            .background(dimmed ? colors.brandDim : colors.brand, in: RoundedRectangle(cornerRadius: ScoutDimens.tileCorner))
        }
        .buttonStyle(.plain)
        .task(id: label) {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                for _ in 0..<2 {
                    dimmed = true
                    try? await Task.sleep(nanoseconds: 160_000_000)
                    dimmed = false
                    try? await Task.sleep(nanoseconds: 160_000_000)
                }
            }
        }
    }
}

private struct RadarStrip: View {
    var live: Bool
    var hasRadar: Bool
    var recording: Bool
    var seeking: Bool
    var carCount: Int
    var speedKph: Int
    var imperial: Bool
    var onRetry: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        let detail: String? = {
            if live { return nil }
            if hasRadar && !recording { return L10n.radarReady }
            if hasRadar && recording && seeking { return L10n.radarConnecting }
            return L10n.radarNone
        }()
        let canRetry = hasRadar && recording && !live
        HStack(spacing: ScoutSpacing.md) {
            Circle()
                .fill(live ? colors.recording : colors.outline)
                .frame(width: 10, height: 10)
            Text(L10n.radarLabel.uppercased())
                .font(ScoutType.overline)
                .foregroundStyle(colors.textSecondary)
            Spacer()
            if let detail {
                Text(detail)
                    .font(ScoutType.body)
                    .foregroundStyle(colors.textSecondary)
            } else {
                Text(L10n.radarCars(carCount))
                    .font(ScoutType.metric)
                    .foregroundStyle(colors.textPrimary)
                if speedKph >= 0 {
                    Text(
                        imperial
                            ? String(format: L10n.radarSpeedMph, Int(Double(speedKph) * mphPerKph))
                            : String(format: L10n.radarSpeedKph, speedKph)
                    )
                    .font(ScoutType.body)
                    .foregroundStyle(colors.textSecondary)
                }
            }
        }
        .padding(.horizontal, ScoutSpacing.lg)
        .padding(.vertical, ScoutSpacing.md)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: ScoutDimens.cardCorner))
        .overlay(
            RoundedRectangle(cornerRadius: ScoutDimens.cardCorner)
                .stroke(live ? colors.recording : colors.outline, lineWidth: 1)
        )
        .onTapGesture { if canRetry { onRetry() } }
    }
}

private struct PhotoChooserOverlay: View {
    var onTake: () -> Void
    var onPickedData: (Data) -> Void
    var onDismiss: () -> Void
    @State private var item: PhotosPickerItem?
    @Environment(\.scoutColors) private var colors

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture(perform: onDismiss)
            VStack(alignment: .leading, spacing: ScoutSpacing.md) {
                Text(L10n.ridePhotoTitle)
                    .font(ScoutType.display)
                    .foregroundStyle(colors.textPrimary)
                Text(L10n.ridePhotoChooserHint)
                    .font(ScoutType.body)
                    .foregroundStyle(colors.textSecondary)
                if UIImagePickerController.isSourceTypeAvailable(.camera) {
                    ScoutButton(label: L10n.ridePhotoTake, primary: true, action: onTake)
                }
                PhotosPicker(selection: $item, matching: .images) {
                    Text(L10n.ridePhotoPick)
                        .font(ScoutType.label)
                        .frame(maxWidth: .infinity)
                        .frame(height: ScoutDimens.controlHeight)
                        .foregroundStyle(colors.textPrimary)
                        .overlay(
                            RoundedRectangle(cornerRadius: ScoutDimens.tileCorner)
                                .stroke(colors.outlineStrong, lineWidth: 1.5)
                        )
                }
                Button(L10n.rideActionCancel, action: onDismiss)
                    .foregroundStyle(colors.textPrimary)
                    .frame(maxWidth: .infinity)
            }
            .padding(ScoutSpacing.lg)
            .background(colors.surface, in: RoundedRectangle(cornerRadius: ScoutDimens.cardCorner))
            .padding(ScoutSpacing.xl)
        }
        .onChange(of: item) { _, new in
            guard let new else { return }
            Task {
                if let data = try? await new.loadTransferable(type: Data.self) {
                    onPickedData(data)
                }
            }
        }
    }
}

private struct PhotoCaptionSheet: View {
    var draftPath: String
    @Binding var caption: String
    var onSave: () -> Void
    var onDiscard: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        NavigationStack {
            VStack(spacing: ScoutSpacing.md) {
                if let img = UIImage(contentsOfFile: draftPath) {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(maxHeight: 220)
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: ScoutDimens.cardCorner))
                }
                TextField(L10n.ridePhotoCaptionHint, text: $caption, axis: .vertical)
                    .lineLimit(1...3)
                    .onChange(of: caption) { _, v in
                        if v.count > RidePhotoStore.captionMax {
                            caption = String(v.prefix(RidePhotoStore.captionMax))
                        }
                    }
                    .padding()
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(colors.outlineStrong, lineWidth: 1)
                    )
                Spacer()
            }
            .padding()
            .background(colors.screen)
            .navigationTitle(L10n.ridePhotoCaptionTitle)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.ridePhotoDiscard, action: onDiscard)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.ridePhotoSave, action: onSave)
                }
            }
        }
    }
}

private struct CameraPicker: UIViewControllerRepresentable {
    var onFinish: (Data?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        var onFinish: (Data?) -> Void
        init(onFinish: @escaping (Data?) -> Void) { self.onFinish = onFinish }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onFinish(nil)
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            let image = (info[.originalImage] as? UIImage)
            onFinish(image?.jpegData(compressionQuality: 0.9))
        }
    }
}

private struct Snackbar: View {
    var message: String
    var action: String?
    var onAction: () -> Void
    var onDismiss: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        HStack {
            Text(message)
                .font(ScoutType.body)
                .foregroundStyle(colors.textPrimary)
            Spacer()
            if let action {
                Button(action, action: onAction)
                    .foregroundStyle(colors.brand)
            }
        }
        .padding()
        .background(colors.surfaceRaised, in: RoundedRectangle(cornerRadius: 12))
        .task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            onDismiss()
        }
    }
}

private func formatElapsed(_ seconds: Int64) -> String {
    let h = seconds / 3600
    let m = (seconds % 3600) / 60
    let s = seconds % 60
    return String(format: "%02d:%02d:%02d", h, m, s)
}
