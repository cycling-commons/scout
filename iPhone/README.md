# Scout — iPhone

iOS port of Scout. Follow the shared contracts:

- [Product spec](../docs/SPEC.md)
- [Data format](../docs/DATA-FORMAT.md)
- [Ride bundle](../docs/SCOUT-BUNDLE.md)
- [Sharing](../docs/SHARING.md)

## Quick start

1. On a **Mac**, follow **[docs/SETUP.md](docs/SETUP.md)** (Xcode 16+).
2. Open `iPhone/Scout.xcodeproj`.
3. Select your Team under Signing, then Run on a device or simulator.

## Contracts

| Doc | Role |
| --- | --- |
| [Product spec](../docs/SPEC.md) | Behaviour, timings, radar policy, battery principles |
| [Data format](../docs/DATA-FORMAT.md) | On-disk channels & parser rules |
| [Technical platform spec](docs/TECHNICAL.md) | SwiftUI stack, sensors, FIT, iOS deltas |
| [Setup](docs/SETUP.md) | Install Xcode, run on device/simulator |
| [Testing](docs/TESTING.md) | Unit, FIT, device, and field test plan |

## Layout

| Path | Role |
| --- | --- |
| `Scout/Domain/` | Pure Swift: codes, queue, undo tallies, pickers, radar decode / vehicle mirror |
| `Scout/Fit/` | Original FIT encoder (SPEC sample fields only) |
| `Scout/Recording/` | FIT session, ride files, photo sidecar, zip share, recovery |
| `Scout/Sensors/` | Core Location + BLE radar |
| `Scout/UI/` | SwiftUI screens |
| `ScoutTests/` | Domain, FIT, bundle, decoder tests |

## Versioning

`MARKETING_VERSION` lives in `Scout.xcodeproj`; release notes in
[CHANGELOG.md](CHANGELOG.md). On each App Store (or sideload) release, tag the
commit **`iphone/vX.Y.Z`** (see root [README — Release tags](../README.md#release-tags)).

## License

[MIT](../LICENSE) © BikeCoders.

## Status

- [x] Shared product + data specs
- [x] iPhone technical + setup docs
- [x] Ride session, tag grid/pickers, domain tests
- [x] GPS + FIT writer
- [x] BLE radar (Varia V1 + Magene L508)
- [x] Settings, export, photos / Scout zip bundle
- [ ] Device + field battery pass (needs a Mac and a phone)
