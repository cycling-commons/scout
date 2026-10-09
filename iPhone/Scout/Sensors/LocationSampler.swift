// SPDX-License-Identifier: MIT
import CoreLocation
import Foundation

/// ~1 Hz GPS while recording. Updates only between start and stop so idle costs nothing.
final class LocationSampler: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private(set) var latest: LocationFix?
    private var listening = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        manager.distanceFilter = kCLDistanceFilterNone
        manager.pausesLocationUpdatesAutomatically = false
        manager.activityType = .fitness
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
    }

    var hasPermission: Bool {
        let status = manager.authorizationStatus
        return status == .authorizedAlways || status == .authorizedWhenInUse
    }

    func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    func start() {
        if listening || !hasPermission { return }
        latest = nil
        listening = true
        manager.startUpdatingLocation()
        if let loc = manager.location {
            latest = loc.toFix()
        }
    }

    func stop() {
        guard listening else { return }
        manager.stopUpdatingLocation()
        listening = false
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        latest = loc.toFix()
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if !hasPermission, listening { stop() }
    }
}

private extension CLLocation {
    func toFix() -> LocationFix {
        LocationFix(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            speedMps: speed >= 0 ? Float(speed) : nil,
            accuracyM: horizontalAccuracy >= 0 ? Float(horizontalAccuracy) : nil,
            timeMs: Int64(timestamp.timeIntervalSince1970 * 1000)
        )
    }
}
