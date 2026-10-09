// SPDX-License-Identifier: MIT
import Foundation

enum RadarTransport: Int, CaseIterable, Sendable {
    case auto = 0
    case antPlus = 1
    case ble = 2

    var label: String {
        switch self {
        case .auto: "auto"
        case .antPlus: "ant+"
        case .ble: "bluetooth"
        }
    }
}

struct RadarDeviceRow: Identifiable, Equatable, Sendable {
    var address: String
    var name: String?
    var rssi: Int = rssiUnknown
    var likelyRadar: Bool = false

    var id: String { address }

    static let rssiUnknown = -999
}

struct RadarStatus: Equatable, Sendable {
    var link: RadarLinkState = .absent
    var seeking: Bool = false
    var devices: [RadarDeviceRow] = []
    var savedName: String?
    var savedAddress: String?
    var antDeviceNumber: Int?
    var antAvailable: Bool = false
    var bluetoothOn: Bool = false
    var bluetoothPermission: Bool = false
    var transport: RadarTransport = .auto

    var hasSavedRadar: Bool { savedAddress != nil }
    var tracking: Bool { link == .tracking }
}

/// Persists preferred radar transport + device ids.
final class RadarPrefs {
    private let defaults: UserDefaults
    private static let suite = "scout_radar"

    init() {
        defaults = UserDefaults(suiteName: Self.suite) ?? .standard
    }

    var transport: RadarTransport {
        get { RadarTransport(rawValue: defaults.integer(forKey: "transport")) ?? .auto }
        set { defaults.set(newValue.rawValue, forKey: "transport") }
    }

    var address: String? {
        get { defaults.string(forKey: "ble_address") }
        set { defaults.set(newValue, forKey: "ble_address") }
    }

    var name: String? {
        get { defaults.string(forKey: "ble_name") }
        set { defaults.set(newValue, forKey: "ble_name") }
    }

    func rememberedName(_ address: String) -> String? {
        defaults.string(forKey: "known_name_" + address.uppercased())
    }

    func rememberName(_ address: String, _ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return }
        defaults.set(trimmed, forKey: "known_name_" + address.uppercased())
    }

    func clear() {
        defaults.removePersistentDomain(forName: Self.suite)
    }
}
