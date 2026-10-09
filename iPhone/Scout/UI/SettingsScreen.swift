// SPDX-License-Identifier: MIT
import SwiftUI

struct SettingsScreen: View {
    @ObservedObject var model: RideViewModel
    @Environment(\.scoutColors) private var colors

    var body: some View {
        let ui = model.ui
        ScoutPage(title: L10n.settingsTitle, titleColor: colors.brand, onBack: model.closeSettings) {
            ScrollView {
                VStack(alignment: .leading, spacing: ScoutSpacing.xl) {
                    ScoutSection(title: L10n.settingsDisplay, titleColor: colors.brand) {
                        Text(L10n.settingsAppearance)
                            .font(ScoutType.body)
                            .foregroundStyle(colors.textPrimary)
                        HStack(spacing: ScoutSpacing.sm) {
                            ForEach(ThemeMode.allCases) { mode in
                                ChoiceChip(
                                    label: L10n.themeLabel(mode),
                                    selected: mode == ui.themeMode
                                ) { model.setThemeMode(mode) }
                            }
                        }
                        Text(L10n.settingsAppearanceHint)
                            .font(ScoutType.body)
                            .foregroundStyle(colors.textSecondary)
                        Divider().overlay(colors.outline)
                        Text(L10n.settingsUnits)
                            .font(ScoutType.body)
                            .foregroundStyle(colors.textPrimary)
                        HStack(spacing: ScoutSpacing.sm) {
                            ChoiceChip(label: L10n.settingsUnitsMetric, selected: !ui.imperial) {
                                model.setImperial(false)
                            }
                            ChoiceChip(label: L10n.settingsUnitsImperial, selected: ui.imperial) {
                                model.setImperial(true)
                            }
                        }
                        Divider().overlay(colors.outline)
                        ScoutToggleRow(
                            label: L10n.settingsKeepScreenOn,
                            hint: L10n.settingsKeepScreenOnHint,
                            checked: Binding(
                                get: { ui.keepScreenOn },
                                set: model.setKeepScreenOn
                            )
                        )
                    }
                    ScoutSection(
                        title: L10n.settingsRadar,
                        subtitle: L10n.settingsRadarHint,
                        titleColor: colors.brand
                    ) {
                        Text(
                            String(
                                format: L10n.settingsRadarPreferred,
                                ui.radar.savedName ?? ui.radar.savedAddress ?? L10n.radarNone,
                                ui.radar.transport.label
                            )
                        )
                        .font(ScoutType.body)
                        .foregroundStyle(colors.textPrimary)
                        ScoutButton(label: L10n.settingsRadarPair, action: model.openPairRadar)
                    }
                    ScoutSection(title: L10n.settingsAbout, titleColor: colors.brand) {
                        ScoutButton(label: L10n.settingsHelp) { model.openHelp() }
                        ScoutButton(label: L10n.settingsShowIntro, action: model.replayIntro)
                    }
                    Text(String(format: L10n.settingsVersion, Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"))
                        .font(ScoutType.bodySmall)
                        .foregroundStyle(colors.textSecondary)
                        .padding(.leading, ScoutSpacing.xs)
                }
                .padding(.horizontal, ScoutSpacing.lg)
                .padding(.bottom, ScoutSpacing.xxl)
            }
        }
    }
}
