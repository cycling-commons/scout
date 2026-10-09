# Scout — iPhone development setup

How to get a Mac ready to build and run the iPhone port. Anyone should be able
to follow this from a clean checkout — Scout is **MIT-licensed** (see
[`LICENSE`](../../LICENSE) at the repo root).

Behaviour and file formats are defined in the shared docs; this page is only
**tooling and environment**.

| Related doc | Role |
| --- | --- |
| [TECHNICAL.md](TECHNICAL.md) | Stack, modules, battery, iOS deltas |
| [TESTING.md](TESTING.md) | Unit, FIT, device, field test plan |
| [Product SPEC](../../docs/SPEC.md) | What the app must do |
| [DATA-FORMAT](../../docs/DATA-FORMAT.md) | FIT channels & parser rules |

---

## 1. What you need

| Tool | Notes |
| --- | --- |
| **A Mac** | Xcode does not build iOS apps on Windows or Linux |
| **Git** | Clone this repository |
| **[Xcode](https://developer.apple.com/xcode/)** 16 or later | iOS 17 SDK |
| **Apple ID** | Free for device install; paid team for TestFlight / App Store |
| **Device or simulator** | A physical iPhone for GPS + BLE radar; simulator for UI |

---

## 2. Open and run

```sh
cd iPhone
open Scout.xcodeproj
```

1. Select the **Scout** scheme and an iPhone simulator or a plugged-in device.
2. Under **Signing & Capabilities**, pick your Team. The bundle id is
   `org.cyclingcommons.scout` — change it if that id is already taken on your
   team.
3. Product → Run (⌘R).

First launch on a phone: iOS will ask for **Location** after **Get started**.
Bluetooth and Camera are asked only when you pair a radar or take a photo.

---

## 3. Tests

In Xcode: Product → Test (⌘U), or:

```sh
cd iPhone
xcodebuild test -scheme Scout -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoutTests
```

Domain, FIT, radar-decode, and ride-bundle tests are JVM-free Swift XCTest
ports of the Android suites. They do not need a radar or GPS.

Validate a FIT against the shipped viewer parser (after a ride, or after
writing a scenario file from the FIT tests):

```sh
node tools/test-fit-parser.mjs tools/fit-viewer.html path/to/ride.fit
```

---

## 4. Instance / help / brand

Same pattern as Android. Committed templates; gitignored overrides.

| What | Template | Override |
| --- | --- | --- |
| Help | `iPhone/help/help.example.json` | `iPhone/help/help.json` |
| Legal | `iPhone/help/legal.example.json` | `iPhone/help/legal.json` |
| Atlas URL | `iPhone/.env.example` | `iPhone/.env.dev.local` |
| Welcome lockup | `iPhone/brand-fallback/welcome-logo.svg` | `Brand/welcome-logo.svg` |

Copy the JSON you want into `Scout/Resources/` (`help.json`, `legal.json`,
`instance.json`) before building, or edit those files in place. `instance.json`
defaults to the `.env.example` values.

---

## 5. Portrait

The tag grid is portrait-locked on purpose (same as Android): a rotation
mid-ride would move the tiles under a rider's hand.
