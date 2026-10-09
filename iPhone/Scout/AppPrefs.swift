// SPDX-License-Identifier: MIT
import Foundation
import SwiftUI

enum ThemeMode: String, CaseIterable, Identifiable, Sendable {
    case system
    case light
    case dark

    var id: String { rawValue }

    func isDark(systemDark: Bool) -> Bool {
        switch self {
        case .system: systemDark
        case .light: false
        case .dark: true
        }
    }
}

/// App-wide settings. Defaults match SPEC battery: keep-screen-on off.
final class AppPrefs {
    private let defaults: UserDefaults
    private static let suite = "scout_app"

    init() {
        defaults = UserDefaults(suiteName: Self.suite) ?? .standard
    }

    var imperial: Bool {
        get { defaults.bool(forKey: "imperial") }
        set { defaults.set(newValue, forKey: "imperial") }
    }

    var keepScreenOn: Bool {
        get { defaults.bool(forKey: "keep_screen_on") }
        set { defaults.set(newValue, forKey: "keep_screen_on") }
    }

    var themeMode: ThemeMode {
        get {
            defaults.string(forKey: "theme_mode").flatMap(ThemeMode.init(rawValue:)) ?? .system
        }
        set { defaults.set(newValue.rawValue, forKey: "theme_mode") }
    }

    var introSeen: Bool {
        get { defaults.bool(forKey: "intro_seen") }
        set { defaults.set(newValue, forKey: "intro_seen") }
    }
}
