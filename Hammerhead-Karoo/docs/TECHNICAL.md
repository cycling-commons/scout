# Scout — Hammerhead Karoo technical platform spec

Status: **normative for the Karoo port** (documentation only — not yet implemented)  
Implements: [Product SPEC](../../docs/SPEC.md) · [DATA-FORMAT](../../docs/DATA-FORMAT.md)  
Reference ports: [Garmin Connect IQ](../../Garmin/) · [Android phone](../../Android/)

This document says *how* Scout should be built on Hammerhead Karoo. Behaviour and
on-disk codes stay in the root docs — do not fork them here. For tooling and
install steps, see **[SETUP.md](SETUP.md)**. For distribution, see
**[PUBLISHING.md](PUBLISHING.md)**.

---

## 1. Goals & non-goals

### Goals

- Full Scout tagging parity with Garmin Scout v1.0 (grid, pickers, timings, tallies,
  undo, surface stretches).
- Write the five Scout FIT developer fields into **Karoo's activity file** while
  the rider records a ride on Karoo.
- Optional rear-radar logging using **Karoo's native ANT+ radar stack** (rider pairs
  radar in Karoo Settings → Sensors).
- Reuse shared domain logic from [`Android/domain/`](../../Android/domain/) where
  possible (codes, queue, undo tallies, vehicle corroboration).
- No account, no analytics, no required network.

### Non-goals (v1)

- Standalone FIT file writer separate from Karoo's ride recorder.
- BLE radar pairing inside Scout (Karoo OS owns sensor pairing).
- Maps, cloud sync, in-app Atlas upload.
- Perfect `radar_speed` when Karoo's RADAR stream does not expose per-target closing
  speed (see §8 — platform delta).
- Supporting non-touch primary UI.

---

## 2. Hammerhead platform model

Karoo is an Android head unit. Third-party Scout is a **Karoo Extension**: an
ordinary Android app that also registers a `KarooExtension` service so Karoo OS can
invoke it during rides.

| Concept | Scout usage |
| --- | --- |
| **karoo-ext** | Official Android library ([docs](https://hammerheadnav.github.io/karoo-ext/index.html), [source](https://github.com/hammerheadnav/karoo-ext)) |
| **KarooExtension** | `ScoutExtension` service — FIT writing, optional data types, bonus actions |
| **KarooSystemService** | Connect from Activity or Extension; consume `RideState`, stream sensors, dispatch effects |
| **ExtensionInfo XML** | Declares extension id, display name, data types, bonus actions |
| **Process isolation** | Extension code runs in Scout's app process (unlike legacy karoo-sdk) |

Do **not** start from legacy [karoo-sdk](https://github.com/hammerheadnav/karoo-sdk)
unless a specific API is missing from karoo-ext. Prefer karoo-ext only.

### Official references

| Resource | URL |
| --- | --- |
| API docs (Dokka) | https://hammerheadnav.github.io/karoo-ext/index.html |
| Library README | https://github.com/hammerheadnav/karoo-ext/blob/master/README.md |
| Project template | https://github.com/hammerheadnav/karoo-ext-template |
| Sample app + extension | https://github.com/hammerheadnav/karoo-ext/tree/master/app |
| Developer community | https://support.hammerhead.io/hc/en-us/community/topics/31298804001435-Hammerhead-Extensions-Developers |
| Sideloading (Karoo 3) | https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading |
| Extensions library | https://support.hammerhead.io/hc/en-us/articles/34676015530907-Karoo-OS-Extensions-Library |

Open-source Karoo extensions worth reading:

- [ClipRide](https://github.com/yrkan/clipride) — BLE device + BonusAction + Glance data fields
- [eiRadar](https://github.com/yrkan/eiradar) — `DataType.Type.RADAR` consumption + FIT recording

---

## 3. Stack (proposed)

| Piece | Choice | Why |
| --- | --- | --- |
| Language | Kotlin | Matches template, sample, and Android Scout |
| UI (settings) | Jetpack Compose | Same as ClipRide / karoo-ext sample; not required by SDK |
| UI (in-ride tagging) | Full-screen Activity **or** graphical `DataTypeImpl` | Scout needs a large touch grid + pickers; RemoteViews alone are too constrained for v1 (§9) |
| Min Karoo OS | Extension SDK firmware (**≥ 1.538.2049** per community extensions) | karoo-ext availability |
| SDK | **karoo-ext** (track latest 1.x from GitHub Packages) | FIT effects, ride state, RADAR stream |
| Architecture | Extension service + tagging Activity + settings Activity | Matches Hammerhead's recommended split |
| Domain | Depend on `:domain` from `Android/domain` (JVM module) | Parser-aligned undo / queue / vehicle math |
| FIT output | karoo-ext `startFit()` → `WriteToRecordMesg` | Karoo merges into the ride FIT; Scout does not ship `:fit` encoder |
| DI | Manual or Hilt | Sample uses Hilt; keep cold start light |

**Not used in v1:** legacy karoo-sdk, React Native, separate FIT writer, phone BLE/ANT+
radar stacks from Android Scout.

---

## 4. Module layout (proposed)

```
Hammerhead-Karoo/
  docs/ …
  tools/bootstrap-karoo-deps.ps1
  domain/build.gradle.kts     ← JVM 17 wrapper over Android/domain sources
  app/
    src/main/kotlin/org/cyclingcommons/scout/karoo/
      extension/ScoutExtension.kt
      fit/ScoutFitFields.kt
      karoo/KarooFlows.kt
      radar/KarooRadarAdapter.kt
      session/ScoutSession.kt
      tagging/TaggingActivity.kt   ← placeholder (K2)
      screens/MainScreen.kt
  .deps/karoo-ext/              ← gitignored; composite build for karoo-ext :lib
```

Rules:

- `ScoutExtension` owns FIT emission and ride-state subscription.
- Tagging UI never talks to karoo-ext directly — it drives shared `ScoutController`
  (or a thin Karoo façade over it).
- Radar adapter normalizes Karoo `DataType.Type.RADAR` → `RadarObservation` (same
  shape as Android `domain`).

---

## 5. Extension registration

Follow [karoo-ext README](https://github.com/hammerheadnav/karoo-ext/blob/master/README.md#hello-extension).

### 5.1 ScoutExtension service

```kotlin
class ScoutExtension : KarooExtension("scout", "1.0.0") {
    // override startFit(emitter) — §7
    // optional: override onBonusAction — open TaggingActivity
}
```

Extension id `"scout"` must match `ExtensionInfo` `@id` and stay stable across
updates.

### 5.2 AndroidManifest service entry

```xml
<service android:name=".extension.ScoutExtension"
         android:exported="true">
    <intent-filter>
        <action android:name="io.hammerhead.karooext.KAROO_EXTENSION" />
    </intent-filter>
    <meta-data
        android:name="io.hammerhead.karooext.EXTENSION_INFO"
        android:resource="@xml/extension_info" />
</service>
```

### 5.3 extension_info.xml (sketch)

```xml
<ExtensionInfo
    id="scout"
    displayName="@string/extension_name"
    icon="@drawable/ic_scout"
    scansDevices="false">
    <BonusAction
        actionId="open-tagging"
        displayName="@string/action_tag" />
</ExtensionInfo>
```

`scansDevices="false"` — Scout does not implement `startScan` / `connectDevice`;
radar is paired through Karoo sensor settings.

Optional later: a graphical `DataType` for a compact ride-page widget; v1 can ship
BonusAction + launcher only.

---

## 6. Runtime architecture

```
┌─────────────────────────────────────────────────────────┐
│  TaggingActivity (Compose grid / pickers / strip)       │
└──────────────────────────┬──────────────────────────────┘
                           │ taps, undo, tallies
                           ▼
┌─────────────────────────────────────────────────────────┐
│  ScoutController (shared Android/domain)                  │
│  tag queue · live tallies · surface state               │
└──────────────┬───────────────────────────┬──────────────┘
               │                             │
               ▼                             ▼
┌──────────────────────────┐    ┌───────────────────────────┐
│  Karoo ride clock        │    │  KarooRadarAdapter      │
│  RideState + 1 Hz tick   │    │  DataType.Type.RADAR    │
└──────────────┬───────────┘    └─────────────┬─────────────┘
               │                              │
               ▼                              ▼
┌─────────────────────────────────────────────────────────┐
│  ScoutExtension.startFit()                              │
│  WriteToRecordMesg(poi_*, radar_*) while Recording      │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
              Karoo OS activity FIT file
```

### 6.1 Recording states (map to SPEC §4.1)

Scout **follows Karoo's ride timer**, not a private timer.

| Karoo `RideState` | Scout behaviour |
| --- | --- |
| `Idle` | No samples written; tile taps show idle prompt only (§4.1) |
| `Recording` | ~1 Hz FIT writes; tag queue drains one tag per tick |
| `Paused(auto)` | Same as idle for tagging — no flashes, pickers, or queue drain |

Subscribe via `karooSystem.addConsumer { event: RideState -> … }` or
`consumerFlow<RideState>()`.

Do **not** call `PauseRide` / `ResumeRide` from Scout unless explicitly adding
ride-control features later.

### 6.2 Sample cadence

Karoo's FIT path expects **~1 Hz** `WriteToRecordMesg` while recording (see
[SampleExtension.startFit](https://github.com/hammerheadnav/karoo-ext/blob/master/app/src/main/kotlin/io/hammerhead/sampleext/extension/SampleExtension.kt)).

Align Scout's tag drain with that tick:

- Combine `RideState.Recording` with a 1 s clock (or elapsed-time stream).
- Drain at most one queued tag per tick (queue capacity ≥ 16).
- Read latest `RadarObservation` snapshot for that tick.

---

## 7. FIT output (karoo-ext)

Since karoo-ext **1.1.4**, extensions augment Karoo's activity FIT via
`override fun startFit(emitter: Emitter<FitEffect>)`.

### 7.1 Scout developer fields

Match Garmin [`fit_contributions.xml`](../../Garmin/resources/fit/fit_contributions.xml)
and [DATA-FORMAT](../../docs/DATA-FORMAT.md):

| Field def # | FIT name | Type | Notes |
| --- | --- | --- | --- |
| 0 | `poi_type` | uint8 (FIT base 2) | 0 = none |
| 1 | `poi_detail` | uint8 | Qualifier per type |
| 2 | `radar_count` | uint8 | 255 = not tracking |
| 3 | `radar_near` | uint8 | metres; 255 invalid |
| 4 | `radar_speed` | uint8 | closing kph; 255 invalid |

Define once per extension (lazy vals):

```kotlin
private val poiTypeField = DeveloperField(
    fieldDefinitionNumber = 0,
    fitBaseTypeId = 2, // uint8 — verify against FIT SDK enum
    fieldName = "poi_type",
    units = "",
)
// … poi_detail, radar_count, radar_near, radar_speed
```

Emit while `RideState.Recording`:

```kotlin
emitter.onNext(
    WriteToRecordMesg(
        FieldValue(poiTypeField, type.toDouble()),
        FieldValue(poiDetailField, detail.toDouble()),
        FieldValue(radarCountField, count.toDouble()),
        FieldValue(radarNearField, near.toDouble()),
        FieldValue(radarSpeedField, speed.toDouble()),
    ),
)
```

Karoo auto-writes `WriteDeveloperDataIdMesg` / field-description messages on first
use — extensions must not emit those manually
([docs](https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.models/-write-developer-data-id-mesg/index.html)).

While `RideState.Paused`, the sample extension writes infrequent session messages
instead of records; Scout should **not** write Scout channels when Karoo is paused
(tagging is already blocked in §6.1).

### 7.2 Validation

After a test ride on Karoo, export/sync the activity FIT and run:

```sh
node ../../tools/test-fit-parser.mjs ../../tools/fit-viewer.html
# plus manual drop on fit-viewer.html
```

Acceptance: same parser expectations as Garmin / Android Scout rides.

---

## 8. Radar (platform delta)

### 8.1 Transport

Karoo pairs ANT+ bike radar in **Settings → Sensors** (Varia, Magene L508, etc.).
Scout does not scan, pair, or own the radio.

Read the normalized stream:

```kotlin
karooSystem.addConsumer(
    OnStreamState.StartStreaming(DataType.Type.RADAR)
) { event: OnStreamState ->
    when (val state = event.state) {
        is StreamState.Streaming -> process(state.dataPoint.values)
        is StreamState.NotAvailable -> notTracking()
        // …
    }
}
```

Documented fields ([DataType.Field](https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.models/-data-type/-field/index.html)):

| Field | Use |
| --- | --- |
| `RADAR_THREAT_LEVEL` | Connection / threat hint |
| `RADAR_TARGET_1_RANGE` … `RADAR_TARGET_8_RANGE` | Per-target range (metres) |
| `RADAR_ERROR` | Hardware error → not tracking |

Reference implementation: [eiRadar RadarEngine.kt](https://github.com/yrkan/eiradar/blob/v1.0.6/app/src/main/kotlin/io/github/ykn/variaradarpro/engine/RadarEngine.kt).

### 8.2 Mapping to Scout `RadarObservation`

| Scout field | Karoo source |
| --- | --- |
| `TRACKING` | `StreamState.Streaming` and no `RADAR_ERROR` |
| `targets[].occupied` | range field present and > 0 |
| `targets[].rangeM` | `RADAR_TARGET_n_RANGE` |
| `targets[].closingSpeedMps` | **Not exposed** by karoo-ext RADAR fields |

### 8.3 Delta vs SPEC §8 (documented)

When closing speed is unavailable from Karoo's stream:

- Write `radar_count` and `radar_near` from occupied target ranges.
- Write `radar_speed = 255` (invalid) even when targets are present.
- Live strip may show car count without speed, or hide speed when invalid — must
  not invent closing speed.
- Vehicle corroboration in `VehicleCounter` already tolerates invalid speed for
  credit gating (same as BLE paths without speed).

Pairing UX: settings screen explains “pair your radar in Karoo Settings → Sensors”;
no in-app BLE scan.

---

## 9. UI

### 9.1 In-ride tagging surface

**Recommended v1:** full-screen `TaggingActivity` launched from:

- App launcher (pre-ride setup / help)
- `BonusAction` “Tag” during a ride
- Optional: return to ride via Karoo back stack

Use Jetpack Compose mirroring Android Scout layouts (2-column grid, pickers, radar
strip, recording dot). Colours and timings are normative in SPEC §6.

Karoo ride pages use **RemoteViews** / Glance for custom data fields. A grid with
five pickers and undo countdowns is possible but high effort for v1; defer a ride-page
data field to a later phase unless prototyping shows acceptable touch targets.

### 9.2 Recording indicator

Map Karoo `RideState.Recording` → green dot; `Idle` / `Paused` → red dot (SPEC §9).

### 9.3 Confirmation feedback

Use karoo-ext effects for haptics/audio where available (sample MainActivity
demonstrates beeper control). Distinct pattern for undo. Failure must not block
tagging (SPEC §6.11).

### 9.4 Settings

Minimal v1:

- Link to Karoo sensor settings for radar
- Help (reuse `help/help.example.json` pattern from Android if desired)
- Extension version / about

No keep-screen-on toggle needed — Karoo manages display during rides.

---

## 10. Shared code reuse

| Android Scout module | Karoo reuse |
| --- | --- |
| `domain/Codes.kt`, `Tiles.kt`, `TagQueue.kt`, `TagTallies.kt`, `ScoutController.kt` | Direct dependency |
| `domain/VehicleCounter.kt` | Direct — feed from Karoo radar adapter |
| `domain/RadarModels.kt` | Direct — populate from Karoo stream |
| `domain/*Decoder.kt` (BLE/ANT+) | **Not used** |
| `fit/` encoder | **Not used** — karoo-ext writes into Karoo FIT |
| `app/ui/*` Compose screens | Port selectively into `TaggingActivity` |

Gradle: include `Android/domain` as a composite build or copied module — prefer
**single source** under `Android/domain` to keep parser parity tests shared.

---

## 11. Permissions

Declare only what Scout uses:

| Permission | Why |
| --- | --- |
| None for radar | Karoo OS owns ANT+ sensor access |
| Standard app permissions | Only if BonusAction launches Activity over lock screen, etc. |

Follow karoo-ext sample for any `RequestBluetooth` — Scout v1 should **not** request
Bluetooth from the extension (no `scansDevices`).

---

## 12. Implementation phases

| Phase | Deliverable |
| --- | --- |
| **K0** | Project from karoo-ext-template; `ScoutExtension` stub; sideload to Karoo | **Done** |
| **K1** | `RideState` wiring; `startFit` writes Scout fields at 1 Hz while recording | **Done** |
| **K2** | Tagging Activity: grid + pickers + queue + tallies + haptics (no radar pairing UI) | **Done** |
| **K3** | Karoo RADAR adapter + strip + FIT radar channels | **Done** (adapter in K1; strip in K2) |
| **K4** | FIT validation vs `fit-viewer.html`; field ride on K2/K3 |
| **K5** | Help, settings polish, publishing doc exercised |

Ship gate: K4 passes reference parser assertions for tags, undo, surfaces, and radar
coverage rules.

---

## 13. Testing

| Layer | What |
| --- | --- |
| JVM | Reuse `Android/domain` unit tests |
| Device | Sideload debug APK; record short ride; export FIT |
| Parser | `tools/fit-viewer.html` + `tools/test-fit-parser.mjs` |
| Radar | Ride with Varia paired in Karoo sensors; verify count/near in FIT |

---

## 14. Doc control

| Item | Value |
| --- | --- |
| Document | Karoo technical platform spec |
| Owns | Stack, extension model, FIT via karoo-ext, Karoo radar delta |
| Does not own | `poi_*` codes, undo/surface/vehicle parser rules (root docs) |

When Karoo behaviour must diverge from SPEC, keep the delta here and add a one-line
pointer in root `docs/SPEC.md` §13.
