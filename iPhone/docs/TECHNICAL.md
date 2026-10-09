# Scout — iPhone technical platform spec

Status: **normative for the iPhone port**  
Implements: [Product SPEC](../../docs/SPEC.md) · [DATA-FORMAT](../../docs/DATA-FORMAT.md)  
Battery policy: SPEC §12.1 (repeated here only where iOS APIs apply)

This document says *how* iPhone Scout is built. Behaviour and on-disk codes
stay in the root docs — do not fork them here. For installing tools, see
**[SETUP.md](SETUP.md)**.

---

## 1. Goals & non-goals

### Goals

- Full Scout tagging + optional BLE radar parity with Android / Garmin v1 behaviour.
- Multi-hour rides with **battery use as low as practical** on a phone.
- Original **FIT** output readable by `tools/fit-viewer.html` / Atlas ingest.
- Works with **no radar**; radar via **Bluetooth LE** (see §7).
- No account, no analytics, no required network.

### Non-goals (v1)

- ANT+ (iPhone has no native ANT+ radio). SPEC §8.2 already says BLE when ANT+
  is absent — this is not a behaviour fork.
- Kotlin Multiplatform extraction (domain is a Swift port of Android `domain/`
  + `fit/`; keep them separable).
- Maps, cloud sync, in-app Atlas upload.
- Supporting button-only / non-touch primary UI.

---

## 2. Stack

| Piece | Choice | Why |
| --- | --- | --- |
| Language | Swift 5 | iOS default |
| UI | SwiftUI | Touch-first grid; matches Android Compose screens |
| Min iOS | **17** | PhotosPicker, Font.Width.condensed, modern BLE |
| Architecture | Single `WindowGroup` + `RideViewModel` | Same façade as Android |
| FIT | `Scout/Fit` original-FIT encoder | SPEC §4.2 fields + Scout channels only |
| Tests | XCTest for domain / FIT / bundle | Must match parser rules |

---

## 3. Module layout

```
iPhone/
  docs/TECHNICAL.md
  Scout.xcodeproj
  Scout/
    Domain/          ← pure Swift: codes, queue, undo, radar decode
    Fit/             ← FIT encode/flush
    Recording/       ← FIT session, photos, zip, recovery
    Sensors/         ← Core Location + Core Bluetooth
    UI/              ← one file per screen
    Resources/       ← help.json, legal.json, instance.json
  ScoutTests/
```

Rules:

- `Domain/` and `Fit/` have **no UIKit imports**.
- UI talks to `RideViewModel`, not to BLE directly.
- Sensor adapters normalize to the SPEC radar model (`TRACKING` + targets[]).
- BLE Varia-family: service `6a4e3200`, V1 notify `6a4e3203`.
- BLE Magene L508-family: service `8ce5cc01`, unlock+notify on `8ce5cc02` (`57 09 01`).

---

## 4. Runtime architecture

Same as Android TECHNICAL §4: UI → RideSession (controller + tallies) → ~1 Hz
tick while RUNNING → Location + Radar → FitWriter.

### Recording states (map to SPEC §4.1)

| App state | GPS | Radar | FIT | Wake / screen |
| --- | --- | --- | --- | --- |
| Idle | Off | Off | — | Normal |
| RUNNING | On (~1 Hz) | On if enabled+paired | Append samples | Background location indicator; screen-on only if user enabled |
| PAUSED | Off | Disconnect | Flush; no samples | Radios down |
| STOPPED | Off | Off | Final flush + close | Tear down |

Taps while paused/stopped must not enqueue tags (SPEC §4.1).

iOS has no Android-style foreground-service notification. While RUNNING the
app uses `CLLocationManager.allowsBackgroundLocationUpdates` plus
`showsBackgroundLocationIndicator` (the system location pill). BLE stays
connected via the `bluetooth-central` background mode.

---

## 5. Battery (iOS mapping of SPEC §12.1)

### Must

1. **No high-accuracy GPS outside RUNNING.**
2. **No BLE scan loop and no radar connection outside RUNNING** (except the
   pair screen).
3. **Buffer FIT records**; flush on pause, stop, and every 30 records.
4. **Keep-screen-on is a setting** (default **off**): `isIdleTimerDisabled`
   only while RUNNING.
5. **No network** in the recording path.

The tick loop is demand-driven: 1 Hz while recording, 250 ms only while a
picker/undo countdown is on screen, and suspended when idle and the UI is
hidden.

---

## 6. Location

- Permission: When In Use. Background updates while a ride is open (the
  location indicator), not “Always” as a product requirement.
- While RUNNING, one Scout sample per tick with timestamp, lat/lon (if
  available), speed, drained tag (or zeros), radar fields.
- If no fix yet: still write Scout channels; do not invent coordinates.

---

## 7. Radar

iPhone identifies BLE peripherals by `CBPeripheral.identifier` (UUID), **not**
a MAC address. Pairing persists that UUID.

Transport: BLE only. Pairing UX, 45 s seek on Start/Resume, tap-to-seek, and
exponential reconnect backoff match Android / Garmin.

---

## 8. UI

Same design tokens as Android (`ScoutColors`, spacing, condensed numerals).
System fonts stand in for Barlow Condensed / Quicksand until those faces are
bundled (rounded for prose, condensed width for tiles).

Portrait-locked. English v1 copy.

Help / legal / instance JSON: same schema as Android `help/`.

---

## 9. FIT output

Identical to Android TECHNICAL §9: original FIT, five Scout developer fields,
app-private `Application Support/rides/scout-….fit`. Photos live next to the
FIT. Share is the FIT, or a [Scout ride bundle](../../docs/SCOUT-BUNDLE.md)
when the ride has photos.

---

## 10. Permissions

- Location (When In Use) + background location mode while recording
- Bluetooth for BLE radar
- Camera — only when the rider taps **Take photo**; gallery uses the system
  picker (no photo-library permission)

Privacy copy: local recording only; no account; radar optional; no ads /
analytics.

---

## 11. Doc control

When iPhone must diverge from SPEC behaviour, write the delta here and add a
one-line pointer in root `docs/SPEC.md` §13. Current deltas: **no ANT+**
(hardware), **BLE identifier is UUID not MAC**.
