// SPDX-License-Identifier: MIT
import Foundation

struct Tile: Equatable, Sendable {
    /// POI code, UI_RESUPPLY, UI_BACK, or surface/duration detail codes for pickers
    let code: Int
    let label: String
    /// RGB packed 0xRRGGBB
    let rgb: Int
}

enum UiMode: Equatable, Sendable {
    case grid
    case notice
    case duration
    case resupply
    case surface
    case scenery
}

enum Tiles {
    static let grid: [Tile] = [
        Tile(code: PoiType.uiResupply, label: "RESUPPLY", rgb: 0x1E7FC0),
        Tile(code: PoiType.closure, label: "CLOSURE", rgb: 0x8E44AD),
        Tile(code: PoiType.surface, label: "SURFACE", rgb: 0x8E5A2B),
        Tile(code: PoiType.danger, label: "NOTICE", rgb: 0xD1421F),
        Tile(code: PoiType.scenery, label: "SCENERY", rgb: 0x2E8B57),
        Tile(code: PoiType.other, label: "OTHER", rgb: 0xB58900),
    ]

    static let notice: [Tile] = [
        Tile(code: Danger.potholes, label: "POTHOLES", rgb: 0xD1421F),
        Tile(code: Danger.crossing, label: "CROSSING", rgb: 0xE67E22),
        Tile(code: Danger.corner, label: "CORNER", rgb: 0xB58900),
        Tile(code: Danger.other, label: "OTHER", rgb: 0x8E44AD),
        Tile(code: PoiType.uiBack, label: "BACK", rgb: 0x444444),
    ]

    static let duration: [Tile] = [
        Tile(code: Duration.today, label: "TODAY", rgb: 0x2E8B57),
        Tile(code: Duration.days, label: "DAYS", rgb: 0x1E7FC0),
        Tile(code: Duration.weeks, label: "WEEKS", rgb: 0xB58900),
        Tile(code: Duration.months, label: "MONTHS", rgb: 0xD1421F),
        Tile(code: Duration.unknown, label: "UNKNOWN", rgb: 0x777777),
        Tile(code: PoiType.uiBack, label: "BACK", rgb: 0x444444),
    ]

    static let resupply: [Tile] = [
        Tile(code: Resupply.water, label: "WATER", rgb: 0x1E7FC0),
        Tile(code: Resupply.food, label: "FOOD", rgb: 0xE67E22),
        Tile(code: Resupply.mechanical, label: "REPAIR", rgb: 0x7F8C8D),
        Tile(code: PoiType.uiBack, label: "BACK", rgb: 0x444444),
    ]

    static let surface: [Tile] = [
        Tile(code: Surface.asphalt, label: "ASPHALT", rgb: 0x555555),
        Tile(code: Surface.concrete, label: "CONCRETE", rgb: 0x8A8A8A),
        Tile(code: Surface.paving, label: "PAVING", rgb: 0xC0392B),
        Tile(code: Surface.sett, label: "SETT", rgb: 0x9B7653),
        Tile(code: Surface.cobbles, label: "COBBLES", rgb: 0x6E4B3A),
        Tile(code: Surface.gravel, label: "GRAVEL", rgb: 0xB58900),
        Tile(code: Surface.dirt, label: "DIRT", rgb: 0x8E5A2B),
        Tile(code: Surface.sand, label: "SAND", rgb: 0xD2B48C),
        Tile(code: Surface.end, label: "END", rgb: 0x2E8B57),
        Tile(code: PoiType.uiBack, label: "BACK", rgb: 0x444444),
    ]

    static let scenery: [Tile] = [
        Tile(code: Scenery.nature, label: "NATURE", rgb: 0x2E8B57),
        Tile(code: Scenery.history, label: "HISTORY", rgb: 0x8E44AD),
        Tile(code: Scenery.culture, label: "CULTURE", rgb: 0xE67E22),
        Tile(code: Scenery.view, label: "VIEW", rgb: 0x1E7FC0),
        Tile(code: Scenery.arch, label: "ARCHITECT", rgb: 0xB58900),
        Tile(code: Scenery.unknown, label: "UNKNOWN", rgb: 0x777777),
        Tile(code: PoiType.uiBack, label: "BACK", rgb: 0x444444),
    ]

    static func forMode(_ mode: UiMode) -> [Tile] {
        switch mode {
        case .grid: grid
        case .notice: notice
        case .duration: duration
        case .resupply: resupply
        case .surface: surface
        case .scenery: scenery
        }
    }

    static func titleFor(_ mode: UiMode) -> String? {
        switch mode {
        case .notice: "NOTICE?"
        case .duration: "CLOSED FOR?"
        case .resupply: "WHAT KIND?"
        case .scenery: "SCENERY?"
        default: nil
        }
    }

    /// Label for an open stretch detail, or nil when none / unknown.
    static func surfaceLabel(_ detail: Int) -> String? {
        guard (Surface.asphalt...Surface.sand).contains(detail) else { return nil }
        return surface.first { $0.code == detail }?.label
    }
}
