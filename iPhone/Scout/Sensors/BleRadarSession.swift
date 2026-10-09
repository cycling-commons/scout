// SPDX-License-Identifier: MIT
import CoreBluetooth
import Foundation

/**
 BLE bike-radar session.
 - Varia-family: service `6a4e3200` / V1 `6a4e3203`
 - Magene L508-family: proprietary `8ce5cc01` / enable+notify on `8ce5cc02`
 iPhone identifies peripherals by UUID, not MAC — persist `CBPeripheral.identifier`.
 */
final class BleRadarSession: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    static let gapService = CBUUID(string: "1800")
    static let gapDeviceName = CBUUID(string: "2A00")
    static let garminMemberService = CBUUID(string: "FE1F")
    static let radarService = CBUUID(string: "6A4E3200-667B-11E3-949A-0800200C9A66")
    static let v1Characteristic = CBUUID(string: "6A4E3203-667B-11E3-949A-0800200C9A66")
    static let mageneService = CBUUID(string: "8CE5CC01-0A4D-11E9-AB14-D663BD873D93")
    static let mageneDataChar = CBUUID(string: "8CE5CC02-0A4D-11E9-AB14-D663BD873D93")
    static let mageneNotifyAlt = CBUUID(string: "8CE5CC03-0A4D-11E9-AB14-D663BD873D93")

    private static let reconnectSettleMs: Int64 = 450
    private static let connectTimeoutMs: Int64 = 20_000

    private let queue = DispatchQueue(label: "org.cyclingcommons.scout.ble")
    private var central: CBCentralManager!
    private let prefs = RadarPrefs()
    private let varia = VariaV1Decoder()
    private let magene = MageneBleDecoder()
    private var active: ActiveProtocol = .none
    private var state: RadarLinkState = .absent
    private var peripheral: CBPeripheral?
    private var scanning = false
    private var connectingId: UUID?
    private var pendingConnectId: UUID?
    private var lastCloseAtMs: Int64 = 0
    private var connectTimeoutWork: DispatchWorkItem?
    private var known: [UUID: CBPeripheral] = [:]

    var onStateChanged: ((RadarLinkState) -> Void)?
    var onDeviceFound: ((RadarDeviceRow) -> Void)?
    var onNameResolved: ((String, String) -> Void)?

    private enum ActiveProtocol { case none, varia, magene }

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: queue, options: [
            CBCentralManagerOptionShowPowerAlertKey: false,
        ])
    }

    func currentState() -> RadarLinkState { state }

    func observation() -> RadarObservation {
        if state != .tracking {
            return RadarObservation(state: state, targets: [])
        }
        let targets: [RadarTarget]
        switch active {
        case .varia: targets = varia.snapshot()
        case .magene: targets = magene.snapshot()
        case .none: targets = []
        }
        return RadarObservation(state: state, targets: targets)
    }

    func hasBluetoothPermission() -> Bool {
        if #available(iOS 13.1, *) {
            return CBCentralManager.authorization == .allowedAlways
        }
        return true
    }

    func bluetoothEnabled() -> Bool {
        central.state == .poweredOn
    }

    func startScan() {
        queue.async { [weak self] in
            guard let self else { return }
            guard self.hasBluetoothPermission(), self.central.state == .poweredOn else {
                self.setState(.absent)
                return
            }
            self.stopScanLocked()
            self.setState(.scanning)
            self.scanning = true
            self.central.scanForPeripherals(withServices: nil, options: [
                CBCentralManagerScanOptionAllowDuplicatesKey: true,
            ])
        }
    }

    func stopScan() {
        queue.async { [weak self] in self?.stopScanLocked() }
    }

    func connect(address: String) {
        queue.async { [weak self] in
            guard let self else { return }
            guard self.hasBluetoothPermission(), self.central.state == .poweredOn,
                  let uuid = UUID(uuidString: address)
            else {
                self.setState(.absent)
                return
            }
            self.stopScanLocked()
            self.varia.reset()
            self.magene.reset()
            self.active = .none
            self.connectingId = uuid
            self.setState(.connecting)
            if let existing = self.peripheral {
                self.pendingConnectId = uuid
                self.central.cancelPeripheralConnection(existing)
                return
            }
            self.openGatt(uuid)
        }
    }

    func connectBonded(_ address: String?) {
        guard let address, !address.isEmpty else {
            setState(.absent)
            return
        }
        connect(address: address)
    }

    func disconnect() {
        queue.async { [weak self] in
            guard let self else { return }
            self.stopScanLocked()
            self.connectTimeoutWork?.cancel()
            self.pendingConnectId = nil
            self.connectingId = nil
            let wasMagene = self.peripheral.map { self.activeWasMagene($0) } ?? false
            self.varia.reset()
            self.magene.reset()
            self.active = .none
            if let p = self.peripheral {
                if wasMagene {
                    self.writeMagene(p, MageneBleDecoder.disableRadar)
                }
                self.central.cancelPeripheralConnection(p)
            } else if self.state != .absent {
                self.setState(.disconnected)
            }
        }
    }

    private func activeWasMagene(_ p: CBPeripheral) -> Bool {
        p.services?.contains { $0.uuid == Self.mageneService } == true
    }

    private func stopScanLocked() {
        if scanning {
            scanning = false
            central.stopScan()
            if state == .scanning { setState(.disconnected) }
        }
    }

    private func openGatt(_ uuid: UUID) {
        let sinceClose = nowMs() - lastCloseAtMs
        if lastCloseAtMs > 0, sinceClose < Self.reconnectSettleMs {
            pendingConnectId = uuid
            queue.asyncAfter(deadline: .now() + .milliseconds(Int(Self.reconnectSettleMs - sinceClose))) { [weak self] in
                guard let self, self.peripheral == nil, let next = self.pendingConnectId else { return }
                self.pendingConnectId = nil
                self.openGattNow(next)
            }
            return
        }
        openGattNow(uuid)
    }

    private func openGattNow(_ uuid: UUID) {
        let retrieved = central.retrievePeripherals(withIdentifiers: [uuid]).first
            ?? known[uuid]
        guard let peripheral = retrieved else {
            setState(.disconnected)
            return
        }
        self.peripheral = peripheral
        peripheral.delegate = self
        central.connect(peripheral, options: nil)
        scheduleConnectTimeout()
    }

    private func scheduleConnectTimeout() {
        connectTimeoutWork?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self, self.state == .connecting else { return }
            if let p = self.peripheral {
                self.central.cancelPeripheralConnection(p)
            } else {
                self.pendingConnectId = nil
                self.connectingId = nil
                self.setState(.disconnected)
            }
        }
        connectTimeoutWork = work
        queue.asyncAfter(deadline: .now() + .milliseconds(Int(Self.connectTimeoutMs)), execute: work)
    }

    private func setState(_ s: RadarLinkState) {
        if s == .tracking { connectTimeoutWork?.cancel() }
        state = s
        DispatchQueue.main.async { self.onStateChanged?(s) }
    }

    private func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state != .poweredOn, state != .absent {
            setState(.disconnected)
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        known[peripheral.identifier] = peripheral
        let advertised = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let name = advertised ?? peripheral.name ?? prefs.rememberedName(peripheral.identifier.uuidString)
        let rssi = RSSI.intValue
        let services = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []
        let uuidHit = services.contains {
            $0 == Self.mageneService || $0 == Self.radarService || $0 == Self.garminMemberService
        }
        let likely = uuidHit || Self.nameLooksLikeRadar(name) || prefs.rememberedName(peripheral.identifier.uuidString) != nil
        if !likely && rssi < -70 { return }
        if !likely && (name == nil || name?.isEmpty == true) && rssi < -55 { return }
        emitFound(
            address: peripheral.identifier.uuidString,
            name: name,
            rssi: rssi,
            likely: likely
        )
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([Self.mageneService, Self.radarService, Self.gapService])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        finishClose(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        finishClose(peripheral)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if error != nil {
            setState(.disconnected)
            return
        }
        startRadarProtocol(peripheral)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard error == nil else { return }
        if service.uuid == Self.mageneService {
            if let data = service.characteristics?.first(where: { $0.uuid == Self.mageneDataChar }) {
                active = .magene
                peripheral.writeValue(Data(MageneBleDecoder.enableRadar), for: data, type: .withoutResponse)
                queue.asyncAfter(deadline: .now() + .milliseconds(120)) { [weak self] in
                    guard let self, self.peripheral === peripheral, self.active == .magene else { return }
                    peripheral.setNotifyValue(true, for: data)
                    if let alt = service.characteristics?.first(where: { $0.uuid == Self.mageneNotifyAlt }) {
                        peripheral.setNotifyValue(true, for: alt)
                    }
                    self.setState(.tracking)
                    self.scheduleGapNameRead(peripheral)
                }
            }
        } else if service.uuid == Self.radarService {
            if let v1 = service.characteristics?.first(where: { $0.uuid == Self.v1Characteristic }) {
                active = .varia
                peripheral.setNotifyValue(true, for: v1)
                setState(.tracking)
                scheduleGapNameRead(peripheral)
            }
        } else if service.uuid == Self.gapService {
            if let name = service.characteristics?.first(where: { $0.uuid == Self.gapDeviceName }) {
                peripheral.readValue(for: name)
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if characteristic.uuid == Self.gapDeviceName, let value = characteristic.value {
            let name = String(data: value, encoding: .utf8)?
                .trimmingCharacters(in: .whitespacesAndNewlines.union(CharacterSet(charactersIn: "\0")))
                ?? ""
            if !name.isEmpty {
                let addr = connectingId?.uuidString ?? peripheral.identifier.uuidString
                publishResolvedName(addr, name)
            }
            return
        }
        guard let value = characteristic.value else { return }
        let bytes = [UInt8](value)
        if characteristic.uuid == Self.v1Characteristic {
            varia.feed(bytes)
        } else if characteristic.uuid == Self.mageneDataChar || characteristic.uuid == Self.mageneNotifyAlt {
            magene.feed(bytes)
        }
    }

    private func startRadarProtocol(_ p: CBPeripheral) {
        if let magene = p.services?.first(where: { $0.uuid == Self.mageneService }) {
            p.discoverCharacteristics([Self.mageneDataChar, Self.mageneNotifyAlt], for: magene)
            return
        }
        if let varia = p.services?.first(where: { $0.uuid == Self.radarService }) {
            p.discoverCharacteristics([Self.v1Characteristic], for: varia)
            return
        }
        setState(.disconnected)
    }

    private func scheduleGapNameRead(_ p: CBPeripheral) {
        queue.asyncAfter(deadline: .now() + .milliseconds(400)) {
            if let gap = p.services?.first(where: { $0.uuid == Self.gapService }) {
                p.discoverCharacteristics([Self.gapDeviceName], for: gap)
            }
        }
    }

    private func writeMagene(_ p: CBPeripheral, _ bytes: [UInt8]) {
        guard let service = p.services?.first(where: { $0.uuid == Self.mageneService }),
              let char = service.characteristics?.first(where: { $0.uuid == Self.mageneDataChar })
        else { return }
        p.writeValue(Data(bytes), for: char, type: .withoutResponse)
    }

    private func finishClose(_ p: CBPeripheral) {
        connectTimeoutWork?.cancel()
        if peripheral === p { peripheral = nil }
        lastCloseAtMs = nowMs()
        varia.reset()
        magene.reset()
        active = .none
        if let next = pendingConnectId {
            pendingConnectId = nil
            connectingId = next
            setState(.connecting)
            queue.asyncAfter(deadline: .now() + .milliseconds(Int(Self.reconnectSettleMs))) { [weak self] in
                guard let self, self.peripheral == nil, self.connectingId == next else { return }
                self.openGattNow(next)
            }
        } else {
            connectingId = nil
            if state != .absent { setState(.disconnected) }
        }
    }

    private func emitFound(address: String, name: String?, rssi: Int, likely: Bool) {
        let remembered = prefs.rememberedName(address)
        let display = (name?.isEmpty == false ? name : nil) ?? remembered
        let row = RadarDeviceRow(
            address: address,
            name: display,
            rssi: rssi,
            likelyRadar: likely || Self.nameLooksLikeRadar(display)
        )
        DispatchQueue.main.async { self.onDeviceFound?(row) }
    }

    private func publishResolvedName(_ address: String, _ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return }
        prefs.rememberName(address, trimmed)
        DispatchQueue.main.async { self.onNameResolved?(address, trimmed) }
        emitFound(address: address, name: trimmed, rssi: RadarDeviceRow.rssiUnknown, likely: true)
    }

    static func nameLooksLikeRadar(_ name: String?) -> Bool {
        guard let n = name?.lowercased() else { return false }
        return ["magene", "varia", "garmin", "l508", "gardia", "bryton", "radar", "rtl515", "rtl516", "rct715", "sr30", "carback"]
            .contains { n.contains($0) }
    }
}
