// SPDX-License-Identifier: MIT
import SwiftUI

struct PairRadarScreen: View {
    @ObservedObject var model: RideViewModel
    @Environment(\.scoutColors) private var colors

    var body: some View {
        let status = model.ui.radar
        let scanning = status.link == .scanning
        ScoutPage(title: L10n.pairTitle, onBack: {
            model.stopRadarScan()
            model.closePairRadar()
        }) {
            ScrollView {
                VStack(alignment: .leading, spacing: ScoutSpacing.xl) {
                    ScoutSection(title: L10n.pairTransport, subtitle: statusHint(status)) {
                        HStack(spacing: ScoutSpacing.sm) {
                            ChoiceChip(
                                label: L10n.pairTransportAuto,
                                selected: status.transport == .auto
                            ) { model.setTransport(.auto) }
                            ChoiceChip(
                                label: L10n.pairTransportAnt,
                                selected: false,
                                enabled: false
                            ) {}
                            ChoiceChip(
                                label: L10n.pairTransportBle,
                                selected: status.transport == .ble || status.transport == .auto
                            ) { model.setTransport(.ble) }
                        }
                        Text(L10n.pairAntMissing)
                            .font(ScoutType.body)
                            .foregroundStyle(colors.textSecondary)
                        if let address = status.savedAddress {
                            Divider().overlay(colors.outline)
                            Text(String(format: L10n.pairSaved, status.savedName ?? L10n.radarLabel, address))
                                .font(ScoutType.body)
                                .foregroundStyle(colors.textPrimary)
                            ScoutButton(label: L10n.pairForget, action: model.forgetRadar)
                        }
                    }
                    ScoutSection(title: L10n.pairDevices, subtitle: L10n.pairExclusiveHint) {
                        HStack(spacing: ScoutSpacing.sm) {
                            ScoutButton(
                                label: scanning ? L10n.pairBleScanning : L10n.pairBleScan,
                                primary: !scanning,
                                enabled: status.bluetoothPermission && status.bluetoothOn
                            ) { model.startRadarScan() }
                            if scanning {
                                ScoutButton(label: L10n.pairBleStop, action: model.stopRadarScan)
                            }
                        }
                        if status.devices.isEmpty {
                            Text(L10n.pairDevicesEmpty)
                                .font(ScoutType.body)
                                .foregroundStyle(colors.textSecondary)
                        } else {
                            ForEach(status.devices) { row in
                                Button {
                                    model.selectRadar(row)
                                } label: {
                                    VStack(alignment: .leading, spacing: ScoutSpacing.xs) {
                                        Text(row.name?.isEmpty == false ? row.name! : (row.likelyRadar ? L10n.pairPossibleRadar : L10n.pairUnknownDevice))
                                            .font(ScoutType.body)
                                            .foregroundStyle(row.likelyRadar ? colors.brand : colors.textPrimary)
                                        Text("\(row.address)  ·  " + (
                                            row.rssi > RadarDeviceRow.rssiUnknown
                                                ? String(format: L10n.pairSignalDbm, row.rssi)
                                                : L10n.pairSignalPaired
                                        ))
                                        .font(ScoutType.bodySmall)
                                        .foregroundStyle(colors.textSecondary)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.vertical, ScoutSpacing.md)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(.horizontal, ScoutSpacing.lg)
                .padding(.bottom, ScoutSpacing.xxl)
            }
        }
    }

    private func statusHint(_ status: RadarStatus) -> String {
        if !status.bluetoothPermission { return L10n.pairNeedBluetoothPermission }
        if !status.bluetoothOn { return L10n.pairNeedBluetoothOn }
        if status.savedAddress != nil, status.link == .disconnected || status.link == .absent {
            return L10n.pairSavedReady
        }
        switch status.link {
        case .absent: return L10n.pairStateAbsent
        case .scanning: return L10n.pairStateScanning
        case .connecting: return L10n.pairStateConnecting
        case .tracking: return L10n.pairStateTracking
        case .disconnected: return L10n.pairStateDisconnected
        }
    }
}
