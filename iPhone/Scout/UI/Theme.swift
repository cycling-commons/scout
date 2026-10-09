// SPDX-License-Identifier: MIT
import SwiftUI

struct ScoutColorScheme {
    let brand: Color
    let brandDim: Color
    let recording: Color
    let screen: Color
    let surface: Color
    let surfaceRaised: Color
    let outline: Color
    let outlineStrong: Color
    let textPrimary: Color
    let textSecondary: Color
    let textOnBrand: Color
    let textOnPale: Color
    let warning: Color
    let tileIdleAlpha: Double
}

private let vermillion = Color(red: 240 / 255, green: 50 / 255, blue: 28 / 255)
private let ink = Color(red: 20 / 255, green: 20 / 255, blue: 26 / 255)

private let darkColors = ScoutColorScheme(
    brand: vermillion,
    brandDim: Color(red: 140 / 255, green: 30 / 255, blue: 16 / 255),
    recording: Color(red: 46 / 255, green: 139 / 255, blue: 87 / 255),
    screen: Color(red: 11 / 255, green: 11 / 255, blue: 12 / 255),
    surface: Color(red: 22 / 255, green: 22 / 255, blue: 24 / 255),
    surfaceRaised: Color(red: 32 / 255, green: 32 / 255, blue: 36 / 255),
    outline: Color(red: 48 / 255, green: 48 / 255, blue: 54 / 255),
    outlineStrong: Color(red: 106 / 255, green: 106 / 255, blue: 116 / 255),
    textPrimary: Color(red: 245 / 255, green: 245 / 255, blue: 246 / 255),
    textSecondary: Color(red: 166 / 255, green: 166 / 255, blue: 173 / 255),
    textOnBrand: .white,
    textOnPale: ink,
    warning: Color(red: 232 / 255, green: 163 / 255, blue: 61 / 255),
    tileIdleAlpha: 0.18
)

private let lightColors = ScoutColorScheme(
    brand: vermillion,
    brandDim: Color(red: 179 / 255, green: 36 / 255, blue: 16 / 255),
    recording: Color(red: 27 / 255, green: 122 / 255, blue: 69 / 255),
    screen: Color(red: 244 / 255, green: 244 / 255, blue: 241 / 255),
    surface: .white,
    surfaceRaised: Color(red: 230 / 255, green: 230 / 255, blue: 226 / 255),
    outline: Color(red: 214 / 255, green: 214 / 255, blue: 208 / 255),
    outlineStrong: Color(red: 138 / 255, green: 138 / 255, blue: 130 / 255),
    textPrimary: ink,
    textSecondary: Color(red: 85 / 255, green: 85 / 255, blue: 94 / 255),
    textOnBrand: .white,
    textOnPale: ink,
    warning: Color(red: 154 / 255, green: 91 / 255, blue: 0),
    tileIdleAlpha: 0.16
)

private struct ScoutColorsKey: EnvironmentKey {
    static let defaultValue = darkColors
}

extension EnvironmentValues {
    var scoutColors: ScoutColorScheme {
        get { self[ScoutColorsKey.self] }
        set { self[ScoutColorsKey.self] = newValue }
    }
}

enum ScoutSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
}

enum ScoutDimens {
    static let controlHeight: CGFloat = 52
    static let tileCorner: CGFloat = 14
    static let cardCorner: CGFloat = 18
    static let photoThumb: CGFloat = 72
}

enum ScoutType {
    static let tileLabel = Font.system(size: 22, weight: .bold, design: .default).width(.condensed)
    static let tileCount = Font.system(size: 34, weight: .bold, design: .default).width(.condensed)
    static let countdown = Font.system(size: 16, weight: .semibold, design: .default).width(.condensed)
    static let rideClock = Font.system(size: 30, weight: .bold, design: .default).width(.condensed).monospacedDigit()
    static let metric = Font.system(size: 26, weight: .bold, design: .default).width(.condensed)
    static let overline = Font.system(size: 13, weight: .semibold, design: .default).width(.condensed)
    static let display = Font.system(size: 22, weight: .bold, design: .rounded)
    static let body = Font.system(size: 16, weight: .medium, design: .rounded)
    static let bodySmall = Font.system(size: 12, weight: .medium, design: .rounded)
    static let label = Font.system(size: 15, weight: .bold, design: .rounded)
}

struct ScoutTheme: ViewModifier {
    var dark: Bool

    func body(content: Content) -> some View {
        let colors = dark ? darkColors : lightColors
        content
            .environment(\.scoutColors, colors)
            .tint(colors.brand)
            .preferredColorScheme(dark ? .dark : .light)
    }
}

extension View {
    func scoutTheme(dark: Bool) -> some View {
        modifier(ScoutTheme(dark: dark))
    }
}

func scoutHexColor(_ rgb: Int) -> Color {
    Color(
        red: Double((rgb >> 16) & 0xFF) / 255,
        green: Double((rgb >> 8) & 0xFF) / 255,
        blue: Double(rgb & 0xFF) / 255
    )
}

func tileLuminance(_ rgb: Int) -> Double {
    let r = Double((rgb >> 16) & 0xFF) / 255
    let g = Double((rgb >> 8) & 0xFF) / 255
    let b = Double(rgb & 0xFF) / 255
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
