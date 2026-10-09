// SPDX-License-Identifier: MIT
import Foundation

/**
 Radar connection policy for a ride (SPEC §8): connect only while RUNNING or on
 the pair screen, never scan mid-ride, back off between retries.
 iPhone has no native ANT+ — BLE only.
 */
final class RadarCoordinator {
    private let session = BleRadarSession()
    private let prefs = RadarPrefs()
    private var found: [String: RadarDeviceRow] = [:]

    private(set) var status = RadarStatus()
    var onStatusChanged: (() -> Void)?

    private var seekUntilMs: Int64 = 0
    private var lastRetryAtMs: Int64 = 0
    private var retryGapMs: Int64 = Self.retryGapMinMs
    private var hadTracking = false
    private var rideActive = false

    private static let seekMs: Int64 = 45_000
    private static let retryGapMinMs: Int64 = 5_000
    private static let retryGapMaxMs: Int64 = 60_000

    init() {
        session.onStateChanged = { [weak self] link in
            guard let self else { return }
            if link == .tracking {
                self.hadTracking = true
                self.retryGapMs = Self.retryGapMinMs
            }
            self.status.link = link
            self.status.seeking = self.isSeeking(self.now())
            self.onStatusChanged?()
        }
        session.onDeviceFound = { [weak self] row in
            self?.mergeDevice(row)
        }
        session.onNameResolved = { [weak self] address, name in
            guard let self else { return }
            self.prefs.rememberName(address, name)
            if self.prefs.address?.caseInsensitiveCompare(address) == .orderedSame {
                self.prefs.name = name
            }
            self.mergeDevice(RadarDeviceRow(address: address, name: name, likelyRadar: true))
            if self.status.savedAddress?.caseInsensitiveCompare(address) == .orderedSame {
                self.status.savedName = name
            }
        }
        refreshCapabilities()
    }

    func observation() -> RadarObservation { session.observation() }

    func refreshCapabilities() {
        status.link = session.currentState()
        status.antAvailable = false
        status.bluetoothOn = session.bluetoothEnabled()
        status.bluetoothPermission = session.hasBluetoothPermission()
        status.transport = prefs.transport == .antPlus ? .ble : prefs.transport
        status.savedName = prefs.name
        status.savedAddress = prefs.address
        status.antDeviceNumber = nil
    }

    func onRideStart() {
        rideActive = true
        hadTracking = false
        beginSeek()
    }

    func searchFromTap() {
        if !rideActive || !hasSavedRadar() { return }
        beginSeek()
    }

    func onRideStop() {
        rideActive = false
        hadTracking = false
        seekUntilMs = 0
        session.disconnect()
        publishLink()
    }

    func onTick(nowMs: Int64) {
        retryIfDue(nowMs)
        let seeking = isSeeking(nowMs)
        if status.seeking != seeking {
            status.seeking = seeking
            status.link = session.currentState()
        }
    }

    func openPairing() {
        found.removeAll()
        status.devices = []
        refreshCapabilities()
    }

    func closePairing(rideIdle: Bool) {
        session.stopScan()
        if rideIdle { session.disconnect() }
        refreshCapabilities()
    }

    func startScan() {
        found.removeAll()
        status.devices = []
        session.startScan()
        refreshCapabilities()
    }

    func stopScan() {
        session.stopScan()
        refreshCapabilities()
    }

    func select(_ row: RadarDeviceRow) {
        prefs.transport = .ble
        prefs.address = row.address
        prefs.name = row.name
        session.stopScan()
        session.connect(address: row.address)
        refreshCapabilities()
    }

    func forget() {
        session.disconnect()
        prefs.clear()
        found.removeAll()
        status.link = .absent
        status.devices = []
        status.savedName = nil
        status.savedAddress = nil
        status.antDeviceNumber = nil
        status.seeking = false
    }

    func setTransport(_ transport: RadarTransport) {
        prefs.transport = transport == .antPlus ? .ble : transport
        refreshCapabilities()
    }

    private func beginSeek() {
        retryGapMs = Self.retryGapMinMs
        lastRetryAtMs = 0
        seekUntilMs = now() + Self.seekMs
        session.connectBonded(prefs.address)
        publishLink()
    }

    private func publishLink() {
        status.link = session.currentState()
        status.seeking = isSeeking(now())
        status.savedName = prefs.name
        status.savedAddress = prefs.address
        status.antDeviceNumber = nil
    }

    private func retryIfDue(_ nowMs: Int64) {
        if !rideActive || !hasSavedRadar() { return }
        switch session.currentState() {
        case .tracking:
            hadTracking = true
            retryGapMs = Self.retryGapMinMs
            return
        case .connecting, .scanning:
            if giveUpSeeking(nowMs) { session.disconnect() }
            return
        case .disconnected, .absent:
            break
        }
        if !mayRetry(nowMs) {
            if giveUpSeeking(nowMs) { session.disconnect() }
            return
        }
        if nowMs - lastRetryAtMs < retryGapMs { return }
        lastRetryAtMs = nowMs
        if hadTracking {
            retryGapMs = min(retryGapMs * 2, Self.retryGapMaxMs)
        }
        session.connectBonded(prefs.address)
    }

    private func mergeDevice(_ row: RadarDeviceRow) {
        let remembered = prefs.rememberedName(row.address)
        var named = row
        if (row.name == nil || row.name?.isEmpty == true), let remembered {
            named.name = remembered
            named.likelyRadar = true
        }
        if let previous = found[named.address] {
            named.name = named.name.flatMap { $0.isEmpty ? nil : $0 } ?? previous.name
            named.rssi = max(named.rssi, previous.rssi)
            named.likelyRadar = named.likelyRadar || previous.likelyRadar
        }
        found[named.address] = named
        status.devices = sortedDevices()
        onStatusChanged?()
    }

    private func sortedDevices() -> [RadarDeviceRow] {
        found.values.sorted {
            if $0.likelyRadar != $1.likelyRadar { return $0.likelyRadar && !$1.likelyRadar }
            if $0.rssi != $1.rssi { return $0.rssi > $1.rssi }
            return ($0.name ?? "~") < ($1.name ?? "~")
        }
    }

    private func hasSavedRadar() -> Bool { prefs.address != nil }

    private func isSeeking(_ nowMs: Int64) -> Bool {
        if !rideActive || !hasSavedRadar() { return false }
        if session.currentState() == .tracking { return false }
        return hadTracking || (seekUntilMs > 0 && nowMs < seekUntilMs)
    }

    private func mayRetry(_ nowMs: Int64) -> Bool {
        hadTracking || (seekUntilMs > 0 && nowMs < seekUntilMs)
    }

    private func giveUpSeeking(_ nowMs: Int64) -> Bool {
        if hadTracking || seekUntilMs == 0 || nowMs < seekUntilMs { return false }
        seekUntilMs = 0
        return true
    }

    private func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
