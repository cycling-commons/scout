// SPDX-License-Identifier: MIT
import Foundation
import UIKit

enum Screen: Equatable, Sendable {
    case intro
    case recovery
    case ride
    case settings
    case help
    case pairRadar
    case ridePhotos
    case rides
}

struct RecoveryPrompt: Equatable, Sendable {
    var elapsedLabel: String
    var sampleCount: Int64
    var fitFileName: String
}

@MainActor
final class RideViewModel: ObservableObject {
    @Published private(set) var ui: RideUiModel

    private let controller = ScoutController()
    private let vehicles = VehicleCounter()
    private let location = LocationSampler()
    private let radar = RadarCoordinator()
    private let appPrefs = AppPrefs()
    private let instance = InstanceConfigLoader.load()

    private var fitSession: RideFitSession?
    private var pendingRecovery: RideRecoveryStore.PendingRecovery?
    private var helpReturnScreen: Screen = .settings

    private var uiVisible = false
    private var lastSampleAt: Int64 = 0
    private var sampleCount: Int64 = 0
    private var rideStartedAtMs: Int64 = 0
    private var elapsedBeforePauseMs: Int64 = 0
    private var tickTask: Task<Void, Never>?
    private var wakeContinuations: [CheckedContinuation<Void, Never>] = []
    private var voiceOverObserver: NSObjectProtocol?

    private static let sampleIntervalMs: Int64 = 1_000
    private static let animationIntervalMs: Int64 = 250
    private static let kphPerMps: Float = 3.6
    private static let feetPerMetre: Float = 3.28084

    init() {
        pendingRecovery = RideRecoveryStore.load()
        let recoveryPrompt = pendingRecovery.map {
            RecoveryPrompt(
                elapsedLabel: Self.formatElapsedMs($0.session.elapsedMs),
                sampleCount: $0.session.sampleCount,
                fitFileName: $0.fileURL.lastPathComponent
            )
        }
        let screen: Screen
        if recoveryPrompt != nil {
            screen = .recovery
        } else if !appPrefs.introSeen {
            screen = .intro
        } else {
            screen = .ride
        }
        ui = RideUiModel(
            screen: screen,
            recovery: recoveryPrompt,
            atlasName: instance?.instanceName,
            atlasReviewUrl: instance?.reviewUrl
        )
        radar.onStatusChanged = { [weak self] in
            Task { @MainActor in self?.wake() }
        }
        syncVoiceOverTimeouts()
        refreshPermissions()
        tickTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                await self.tickLoop()
            }
        }
        voiceOverObserver = NotificationCenter.default.addObserver(
            forName: UIAccessibility.voiceOverStatusDidChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.syncVoiceOverTimeouts() }
        }
    }

    deinit {
        tickTask?.cancel()
        if let voiceOverObserver {
            NotificationCenter.default.removeObserver(voiceOverObserver)
        }
        location.stop()
        radar.onRideStop()
    }

    private func tickLoop() async {
        let now = nowMs()
        tick(now)
        if let delay = nextTickDelayMs(now) {
            try? await Task.sleep(nanoseconds: UInt64(delay) * 1_000_000)
        } else {
            await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
                wakeContinuations.append(cont)
            }
        }
    }

    private func tick(_ nowMs: Int64) {
        if controller.onTick(nowMs: nowMs) {
            if let f = controller.takeFeedback() {
                RideFeedback.confirm(undone: f.undone)
            }
        }
        if controller.timer == .running, nowMs - lastSampleAt >= Self.sampleIntervalMs {
            lastSampleAt = nowMs
            writeSample(nowMs)
            radar.onTick(nowMs: nowMs)
        }
        publish(nowMs)
    }

    private func nextTickDelayMs(_ nowMs: Int64) -> Int64? {
        let animating = uiVisible && controller.needsTick(nowMs: nowMs)
        if controller.timer != .running {
            return animating ? Self.animationIntervalMs : nil
        }
        if animating { return Self.animationIntervalMs }
        return max(1, min(Self.sampleIntervalMs - (nowMs - lastSampleAt), Self.sampleIntervalMs))
    }

    private func wake() {
        let waiting = wakeContinuations
        wakeContinuations.removeAll()
        waiting.forEach { $0.resume() }
    }

    private func syncVoiceOverTimeouts(_ nowMs: Int64 = RideViewModel.nowMsStatic()) {
        let scale = UIAccessibility.isVoiceOverRunning ? Timings.talkBackTimeoutScale : 1
        controller.setTimeoutScale(scale, nowMs: nowMs)
    }

    func setUiVisible(_ visible: Bool) {
        uiVisible = visible
        if visible { wake() }
    }

    func refreshPermissions() {
        syncVoiceOverTimeouts()
        radar.refreshCapabilities()
        ui.hasLocationPermission = location.hasPermission
        ui.radar = radar.status
        ui.imperial = appPrefs.imperial
        ui.keepScreenOn = appPrefs.keepScreenOn
        ui.themeMode = appPrefs.themeMode
    }

    func requestLocationPermission() {
        location.requestPermission()
    }

    func dismissIntro() {
        appPrefs.introSeen = true
        show(.ride)
    }

    func replayIntro() {
        appPrefs.introSeen = false
        show(.intro)
    }

    func openSettings() {
        show(.settings)
        refreshPermissions()
    }

    func closeSettings() { show(.ride) }

    func openRides() {
        loadRides()
        show(.rides)
    }

    func closeRides() { show(.ride) }

    func openHelp(returnTo: Screen = .settings) {
        helpReturnScreen = returnTo
        show(.help)
    }

    func closeHelp() { show(helpReturnScreen) }

    func openPairRadar() {
        radar.openPairing()
        show(.pairRadar)
    }

    func closePairRadar() {
        radar.closePairing(rideIdle: controller.timer == .idle)
        show(.settings)
        loadRides()
    }

    func setImperial(_ value: Bool) {
        appPrefs.imperial = value
        ui.imperial = value
        ui.scout.imperial = value
    }

    func setKeepScreenOn(_ value: Bool) {
        appPrefs.keepScreenOn = value
        ui.keepScreenOn = value
    }

    func setThemeMode(_ value: ThemeMode) {
        appPrefs.themeMode = value
        ui.themeMode = value
    }

    func setTransport(_ transport: RadarTransport) { radar.setTransport(transport) }
    func startRadarScan() { radar.startScan() }
    func stopRadarScan() { radar.stopScan() }
    func selectRadar(_ row: RadarDeviceRow) { radar.select(row) }
    func forgetRadar() { radar.forget() }

    func retryRadar() {
        if controller.timer != .running { return }
        radar.searchFromTap()
        RideFeedback.confirm(undone: false)
        publish()
        wake()
    }

    func startRide() {
        controller.start()
        vehicles.resetRide()
        lastSampleAt = 0
        sampleCount = 0
        rideStartedAtMs = nowMs()
        elapsedBeforePauseMs = 0
        fitSession = RideFitSession()
        if location.hasPermission { location.start() }
        radar.onRideStart()
        persistRideState()
        publish()
        wake()
    }

    func pauseRide() {
        controller.pause()
        elapsedBeforePauseMs += nowMs() - rideStartedAtMs
        location.stop()
        radar.onRideStop()
        fitSession?.flush()
        persistRideState()
        publish()
    }

    func resumeRide() {
        controller.resume()
        rideStartedAtMs = nowMs()
        if location.hasPermission { location.start() }
        radar.onRideStart()
        persistRideState()
        publish()
        wake()
    }

    func stopRide() {
        controller.stop()
        vehicles.resetRide()
        location.stop()
        radar.onRideStop()
        elapsedBeforePauseMs = 0
        sampleCount = 0
        RideRecoveryStore.clear()
        let session = fitSession
        let outFile = session?.outFile
        session?.finish { [weak self] saved in
            if saved == nil, let outFile {
                RidePhotoStore.deleteCompanion(outFile)
            }
            self?.ui.lastFitPath = saved?.path
            self?.loadRides()
        }
        fitSession = nil
        ui.fixLabel = nil
        ui.recovery = nil
        publish()
    }

    func resumeRecoveredRide() {
        guard let pending = pendingRecovery else { return }
        pendingRecovery = nil
        let session = pending.session
        controller.restoreSession(session)
        vehicles.restore(carCount: session.carCount, lastCarSpeedKph: session.lastCarSpeedKph)
        sampleCount = session.sampleCount
        lastSampleAt = 0
        elapsedBeforePauseMs = session.elapsedMs
        rideStartedAtMs = nowMs()
        fitSession = RideFitSession(
            existingFile: pending.fileURL,
            existingRecordCount: Int(session.sampleCount)
        )
        switch session.timer {
        case .running:
            if location.hasPermission { location.start() }
            radar.onRideStart()
        case .paused, .idle:
            break
        }
        persistRideState()
        ui.screen = .ride
        ui.recovery = nil
        publish()
        wake()
    }

    func discardRecovery() {
        let pending = pendingRecovery
        pendingRecovery = nil
        RideRecoveryStore.clear()
        if let file = pending?.fileURL {
            _ = RideFiles.deleteFitAndPhotos(file)
        }
        ui.screen = appPrefs.introSeen ? .ride : .intro
        ui.recovery = nil
    }

    func onTileTap(_ index: Int) {
        if controller.timer != .running {
            ui.userMessage = controller.timer == .paused
                ? L10n.rideResumeToTag
                : L10n.rideStartToTag
            return
        }
        let now = nowMs()
        controller.onTileTap(index: index, nowMs: now)
        if let f = controller.takeFeedback() {
            RideFeedback.confirm(undone: f.undone)
        }
        persistRideState()
        publish(now)
        wake()
    }

    func clearUserMessage() { ui.userMessage = nil }

    func endOpenSurface() {
        if controller.timer != .running {
            ui.userMessage = controller.timer == .paused
                ? L10n.rideResumeToTag
                : L10n.rideStartToTag
            return
        }
        let now = nowMs()
        controller.endOpenSurface(nowMs: now)
        if let f = controller.takeFeedback() {
            RideFeedback.confirm(undone: f.undone)
        }
        persistRideState()
        publish(now)
        wake()
    }

    func openPhotoChooser() {
        if controller.timer != .running {
            ui.userMessage = controller.timer == .paused
                ? L10n.rideResumeToTag
                : L10n.rideStartToPhoto
            return
        }
        ui.showPhotoChooser = true
    }

    func dismissPhotoChooser() { ui.showPhotoChooser = false }

    func onCameraPermissionDenied() {
        ui.showPhotoChooser = false
        ui.userMessage = L10n.ridePhotoCameraDenied
    }

    func ingestPhotoData(_ data: Data) {
        Task {
            let draft: URL?
            do {
                let jpeg = try RideImagePrep.compress(data)
                draft = try writeDraft(jpeg)
            } catch {
                draft = nil
            }
            finishIngest(draft)
        }
    }

    func discardPhotoDraft() {
        if let path = ui.photoDraftPath {
            try? FileManager.default.removeItem(atPath: path)
        }
        ui.photoDraftPath = nil
        ui.showPhotoChooser = false
    }

    func savePhoto(caption: String) {
        guard let draftPath = ui.photoDraftPath else { return }
        guard let session = fitSession, controller.timer == .running else {
            discardPhotoDraft()
            ui.userMessage = L10n.rideStartToPhoto
            return
        }
        Task {
            let saved: RidePhoto?
            do {
                let jpeg = try Data(contentsOf: URL(fileURLWithPath: draftPath))
                let fix = location.latest
                saved = try RidePhotoStore.addPreparedJpeg(
                    fitFile: session.outFile,
                    jpegBytes: jpeg,
                    capturedAtMs: nowMs(),
                    lat: fix?.latitude,
                    lon: fix?.longitude,
                    accuracyM: fix?.accuracyM.map(Double.init),
                    caption: caption
                )
            } catch {
                saved = nil
            }
            try? FileManager.default.removeItem(atPath: draftPath)
            ui.photoDraftPath = nil
            ui.ridePhotoCount = RidePhotoStore.count(session.outFile)
            ui.userMessage = saved == nil ? L10n.ridePhotoFailed : L10n.ridePhotoSaved
        }
    }

    func openRidePhotos(_ ride: RideFile) {
        ui.screen = .ridePhotos
        ui.viewingPhotos = RidePhotoStore.list(ride.fileURL)
        ui.viewingRideName = ride.name
    }

    func closeRidePhotos() {
        ui.screen = .rides
        ui.viewingPhotos = []
        ui.viewingRideName = nil
        loadRides()
    }

    func shareLastFit(_ target: RideShareTarget) async -> PackedRide? {
        guard let path = ui.lastFitPath else { return nil }
        return await shareRideFile(URL(fileURLWithPath: path), target: target)
    }

    func shareRide(_ ride: RideFile, target: RideShareTarget) async -> PackedRide? {
        await shareRideFile(ride.fileURL, target: target)
    }

    func deleteRide(_ ride: RideFile) {
        let path = ride.fileURL.path
        _ = RideFiles.delete(ride)
        if ui.lastFitPath == path { ui.lastFitPath = nil }
        loadRides()
    }

    private func show(_ screen: Screen) { ui.screen = screen }

    private func loadRides() {
        Task {
            let rides = await Task.detached { RideFiles.list() }.value
            ui.rides = rides
        }
    }

    private func writeSample(_ nowMs: Int64) {
        let tag = controller.drainTag()
        let fix = location.latest
        let observation = radar.observation()
        let channels = observation.fitChannels()
        let riderKph = fix?.speedMps.map { Int($0 * Self.kphPerMps) } ?? 0
        vehicles.onSample(
            tracking: observation.tracking,
            occupiedCount: observation.occupiedCount(),
            nearestClosingKph: observation.nearestClosingKph(),
            nearestRangeM: observation.nearestRangeM(),
            riderKph: riderKph
        )
        fitSession?.appendSample(
            nowMs: nowMs,
            fix: fix,
            poiType: tag?.type ?? 0,
            poiDetail: tag?.detail ?? 0,
            radarCount: channels[0],
            radarNear: channels[1],
            radarSpeed: channels[2]
        )
        sampleCount += 1
        persistRideState(nowMs)
    }

    private func persistRideState(_ nowMs: Int64? = nil) {
        let now = nowMs ?? self.nowMs()
        if controller.timer == .idle { return }
        guard let session = fitSession else { return }
        RideRecoveryStore.save(
            fitPath: session.outFile.path,
            session: controller.sessionSnapshot(
                elapsedMs: currentElapsedMs(now),
                sampleCount: sampleCount,
                carCount: vehicles.carCount,
                lastCarSpeedKph: vehicles.lastCarSpeedKph
            )
        )
    }

    private func currentElapsedMs(_ nowMs: Int64) -> Int64 {
        switch controller.timer {
        case .idle: 0
        case .paused: elapsedBeforePauseMs
        case .running: elapsedBeforePauseMs + (nowMs - rideStartedAtMs)
        }
    }

    private func publish(_ nowMs: Int64? = nil) {
        let now = nowMs ?? self.nowMs()
        let timer = controller.timer
        let elapsedMs = currentElapsedMs(now)
        ui.scout = withRadar(controller.snapshot(nowMs: now))
        ui.elapsedSec = elapsedMs / 1000
        ui.sampleCount = sampleCount
        ui.pendingTags = controller.queueSize()
        if timer != .idle { ui.fixLabel = fixLabel() }
        ui.radar = radar.status
        ui.ridePhotoCount = fitSession.map { RidePhotoStore.count($0.outFile) } ?? 0
    }

    private func withRadar(_ base: ScoutUiState) -> ScoutUiState {
        var copy = base
        copy.radarLive = radar.status.tracking
        copy.carCount = vehicles.carCount
        copy.lastCarSpeedKph = vehicles.lastCarSpeedKph
        copy.imperial = appPrefs.imperial
        return copy
    }

    private func fixLabel() -> String? {
        guard let fix = location.latest else { return nil }
        guard let accuracy = fix.accuracyM else { return L10n.rideGpsReady }
        if appPrefs.imperial {
            return String(format: L10n.rideGpsAccuracyFt, Int((accuracy * Self.feetPerMetre).rounded()))
        }
        return String(format: L10n.rideGpsAccuracyM, Int(accuracy.rounded()))
    }

    private func writeDraft(_ jpeg: Data) throws -> URL {
        let draft = FileManager.default.temporaryDirectory
            .appendingPathComponent("camera", isDirectory: true)
            .appendingPathComponent("draft.jpg")
        try FileManager.default.createDirectory(at: draft.deletingLastPathComponent(), withIntermediateDirectories: true)
        try jpeg.write(to: draft)
        return draft
    }

    private func finishIngest(_ draft: URL?) {
        if let draft {
            ui.showPhotoChooser = false
            ui.photoDraftPath = draft.path
        } else {
            ui.showPhotoChooser = false
            ui.photoDraftPath = nil
            ui.userMessage = L10n.ridePhotoFailed
        }
    }

    private func shareRideFile(_ fitFile: URL, target: RideShareTarget) async -> PackedRide? {
        guard FileManager.default.fileExists(atPath: fitFile.path) else { return nil }
        do {
            let packed = try await Task.detached {
                try RideShare.packed(fitFile)
            }.value
            if target == .atlas {
                _ = RideShare.copyToDocuments(packed)
            }
            return packed
        } catch {
            ui.userMessage = L10n.rideShareFailed
            return nil
        }
    }

    private func nowMs() -> Int64 { Self.nowMsStatic() }

    private static func nowMsStatic() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }

    private static func formatElapsedMs(_ elapsedMs: Int64) -> String {
        let totalSec = max(elapsedMs / 1000, 0)
        return String(format: "%d:%02d", totalSec / 60, totalSec % 60)
    }
}

struct RideUiModel: Equatable {
    var screen: Screen = .ride
    var scout: ScoutUiState = ScoutUiState()
    var elapsedSec: Int64 = 0
    var sampleCount: Int64 = 0
    var pendingTags: Int = 0
    var hasLocationPermission: Bool = false
    var fixLabel: String? = nil
    var lastFitPath: String? = nil
    var radar: RadarStatus = RadarStatus()
    var imperial: Bool = false
    var keepScreenOn: Bool = false
    var themeMode: ThemeMode = .system
    var rides: [RideFile] = []
    var userMessage: String? = nil
    var recovery: RecoveryPrompt? = nil
    var showPhotoChooser: Bool = false
    var photoDraftPath: String? = nil
    var viewingPhotos: [RidePhoto] = []
    var viewingRideName: String? = nil
    var ridePhotoCount: Int = 0
    var atlasName: String? = nil
    var atlasReviewUrl: String? = nil
}
