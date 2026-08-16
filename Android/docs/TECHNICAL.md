# Scout — Android technical platform spec

Status: **normative for the Android port**  
Implements: [Product SPEC](../../docs/SPEC.md) · [DATA-FORMAT](../../docs/DATA-FORMAT.md)  
Battery policy: SPEC §12.1 (repeated here only where Android APIs apply)

This document says *how* Android Scout is built. Behaviour and on-disk codes
stay in the root docs — do not fork them here. For installing tools, see
**[SETUP.md](SETUP.md)**.

---

## 1. Goals & non-goals

### Goals

- Full Scout tagging + optional radar parity with Garmin v1.0 behaviour.
- Multi-hour rides with **battery use as low as practical** on a phone.
- Original **FIT** output readable by `tools/fit-viewer.html` / Atlas ingest.
- Works with **no radar**; radar via **ANT+ if the device has it, else BLE**.
- No account, no analytics, no required network.

### Non-goals (v1)

- iOS build (no Mac in the workflow yet) — leave `iPhone/` stubbed.
- Kotlin Multiplatform extraction (may come later; keep domain code separable).
- Maps, cloud sync, in-app Atlas upload.
- Supporting button-only / non-touch primary UI.
- Perfect traffic volume / oncoming detection.

---

## 2. Stack

| Piece | Choice | Why |
| --- | --- | --- |
| Language | Kotlin (AGP 9 built-in + Compose compiler plugin) | Android default; clear path to shared KMP later |
| UI | Jetpack Compose + Material 3 | Touch-first grid; low UI complexity |
| Min SDK | **26 (Android 8.0)** | BLE + foreground services without ancient edge cases; still wide device reach |
| Target / compile SDK | **37** | Matches current Studio SDK; AGP 9.3 max |
| Gradle / AGP | **Gradle 9.6+ / AGP 9.3** | Latest Studio JBR is a **supported** Gradle JDK |
| JDK | **Latest Studio JBR (25+; 26 when available)** | Runtime + toolchain + bytecode major version |
| Architecture | Single-activity Compose app | Simple navigation |
| Async | Kotlin coroutines + `Flow` | Sampling, sensors, writers |
| DI | Manual / small factory first; Hilt only if it earns its keep | Keep cold start light |
| FIT | `:fit` minimal original-FIT encoder (no Garmin SDK) | SPEC §4.2 fields + Scout channels only |
| Tests | JVM unit tests for domain; instrumented later for location/BLE | Domain must match parser rules |

**Not used in v1:** React Native, Flutter, Cordova/Capacitor, heavy analytics SDKs.

---

## 3. Module layout

```
Android/
  docs/TECHNICAL.md          ← this file
  .env.example               ← Atlas URL template (instance copy: .env.dev.local, gitignored)
  help/                      ← in-app help JSON (example committed, instance copy gitignored)
  app/                       ← Compose UI, permissions, location + BLE/ANT+ adapters, settings
    help/                    ← HelpContent loader
    instance/                ← InstanceConfig loader (sharing, when implemented)
    ui/theme/                ← design tokens + MaterialTheme wiring (§8.1)
    ui/components/           ← shared Scout chrome (page, section, card, button, pill, mark)
    ui/                      ← one file per screen (intro, ride, settings, pair radar, help)
    sensors/                 ← LocationSampler + radar/ (transports, coordinator, prefs)
    recording/               ← foreground service, FIT session, ride files
  domain/                    ← pure Kotlin: codes, queue, undo tallies, radar decode / counters
  fit/                       ← FIT encode/flush (JVM; no Android deps)
  tools/validate-scout-fit.mjs
```

Rules:

- `domain/` and `fit/` have **no Android framework imports** — easiest to test and to share later.
- UI talks to a ride façade (`RideViewModel`), not to BLE/ANT+ directly.
- `RideViewModel` owns UI state only; radar connect/retry lives in `RadarCoordinator`,
  haptics/tones in `RideFeedback`, file I/O in `RideFitSession`. Keep it that way —
  the ViewModel is the file that grows if nobody watches it.
- Sensor adapters normalize to the SPEC radar model (`TRACKING` + targets[]).
- Location + BLE/ANT+ live under `app/.../sensors` (recording/FGS under `app/.../recording`).
- BLE Varia-family: service `6a4e3200`, V1 notify `6a4e3203` (community protocol). V2/`6a4e3204` later if needed.
- BLE Magene L508-family: service `8ce5cc01`, unlock+notify on `8ce5cc02` (`57 09 01`).
- ANT+ bike radar: AntLib (`android_antlib_4-16-0.aar`) + ANT Radio Service; device type 40, pages 48/49. Requires ANT Radio Service (or USB ANT stick). PluginLib has no radar plugin — raw channel + decoder.

---

## 4. Runtime architecture

```
┌─────────────────────────────────────────────┐
│  Compose UI (grid / pickers / strip / HUD)  │
└─────────────────────┬───────────────────────┘
                      │ intents (tap, start/pause/stop, pair)
                      ▼
┌─────────────────────────────────────────────┐
│  RideSession                                │
│  timer state · tag queue · live tallies     │
│  mirrors SPEC undo / vehicle display rules  │
└──────┬───────────────────────────┬──────────┘
       │ ~1 Hz tick while RUNNING  │
       ▼                           ▼
┌──────────────┐            ┌─────────────────┐
│ LocationSrc  │            │ RadarSession    │
│ fix + speed  │            │ ANT+ or BLE     │
└──────┬───────┘            └────────┬────────┘
       │                             │
       ▼                             ▼
┌─────────────────────────────────────────────┐
│  SampleAssembler → FitWriter (buffered)     │
└─────────────────────────────────────────────┘
```

### Recording states (map to SPEC §4.1)

| App state | GPS | Radar | FIT | Wake / screen |
| --- | --- | --- | --- | --- |
| Idle (no ride) | Off | Off | — | Normal |
| Recording RUNNING | On (~1 Hz) | On if enabled+paired | Append samples | Foreground service; screen-on only if user enabled |
| Recording PAUSED | Off or passive | Disconnect / no scan | Flush; no samples | Service may stay for “ride open” but radios down |
| Recording STOPPED | Off | Off | Final flush + close file | Tear down service |

Taps while paused/stopped must not enqueue tags into the file (SPEC §4.1) and
must not flash tiles or open pickers — the UI shows “Start recording first”.

---

## 5. Battery (Android mapping of SPEC §12.1)

### Must

1. **No high-accuracy GPS outside RUNNING.**
2. **No BLE scan loop and no ANT+/BLE radar connection outside RUNNING** (except an explicit, user-started pairing screen).
3. **Use a foreground service only while a ride is open** (RUNNING or briefly PAUSED if required for notification continuity). Stop it on STOPPED / dismiss.
4. **Prefer `FusedLocationProviderClient`** with the lowest priority that still yields usable tag coordinates and speed while RUNNING. Do not also poll GNSS + network “for redundancy.”
5. **Radar:** prefer ANT+ APIs / USB accessories when present; else BLE GATT connection + notifications. After pair, **connect — do not continuous-scan** during the ride.
6. **Buffer FIT records**; flush on pause, stop, and periodically (e.g. every N seconds or N records), not necessarily every sample if unsafe only on crash — balance durability vs flash wear (flush at least on pause/stop and on a short interval).
7. **Keep-screen-on is a setting** (default **off**). When off, system timeout applies; tagging page should still be one tap from the notification.
8. **No network** in the recording path. No ads, no analytics SDKs.

### Should

- Use `PRIORITY_BALANCED_POWER_ACCURACY` if field tests show tags still land well enough; fall back to `PRIORITY_HIGH_ACCURACY` only if required.
- Request location interval ~1000 ms, min update distance 0 while RUNNING (Cadence matters more than distance for Scout channels).
- On PAUSED: `removeLocationUpdates` immediately; close radar GATT / release ANT+ channel.
- Avoid perpetual `PARTIAL_WAKE_LOCK`; rely on foreground service + location callbacks. If a wake lock is unavoidable, hold only while RUNNING.
- Do not force max brightness. Dark is the cheaper appearance on OLED, but it is the rider's setting, not ours to impose (§8.1).

### Measure

Before calling v1 “done,” run at least:

- 2+ hour recording, **no radar**, keep-screen-on **off**
- 2+ hour recording, **BLE or ANT+ radar connected**, keep-screen-on **off**
- Compare idle drain: app force-stopped vs ride **paused** (radios must be down)

Treat large idle / paused regressions as bugs. Field log template:

| Test | Duration | Start SoC % | End SoC % | Notes |
| --- | --- | --- | --- | --- |
| Idle force-stop | 2 h | | | baseline |
| Ride paused | 2 h | | | GPS/radar off |
| Recording, no radar | 2 h | | | |
| Recording + radar | 2 h | | | transport: ANT+ / BLE |

### Shipped

P4 battery hygiene: GPS-only while RUNNING; radar connect only while RUNNING (or pair screen); disconnect on pause/stop; no BLE scan during ride; FIT flush on pause/stop + every 30 records.

P6 battery pass — the app should cost nothing while nothing is happening:

| Was | Now | Why it matters |
| --- | --- | --- |
| ViewModel loop ticked every 250 ms forever | Demand-driven loop: suspends on a conflated wake channel when idle, 1 Hz while recording, 250 ms **only** while a picker/undo countdown is on screen, and never while the UI is not visible | A phone parked in a jersey pocket for a 4 h ride was doing ~57 k wakeups for nothing |
| Permission / Bluetooth / ANT probes on every tick | Probed on lifecycle events and cached | Each probe was a binder round-trip, 4×/s |
| `ScoutFitWriter` re-encoded and rewrote the whole file on every flush | Streaming append to a `RandomAccessFile`; only the header, new bytes and trailing CRC are written | Cost was O(n²) bytes: a 3 h ride pushed ~200 kB of samples through ~40 MB of rewrites, plus a full re-encode of every record each time |
| FIT writes on the main thread | `RideFitSession` is a channel-backed actor on `Dispatchers.IO` | Keeps taps responsive and lets the writer batch |
| Fixed-interval radar reconnect | Exponential backoff (2 s → 60 s), reset on a successful connect | Failed reconnects were hammering the radio |
| Ride history listed by the composable | Listed once on `Dispatchers.IO` when Settings opens | No disk walk per recomposition |
| `FLAG_KEEP_SCREEN_ON` tied to the setting | Held only when the setting is on **and** the timer is RUNNING | Paused rides let the screen sleep |

Measured on an API 37 emulator by sampling `utime + stime` from `/proc/<pid>/stat`:
**0 jiffies over 30 s** sitting on the ride screen with no ride open, and 54 jiffies
(0.54 s CPU) over 30 s while recording with GPS. Idle really is idle; if that first
number is ever non-zero again, something started polling.

Keep the rule when adding features: **nothing may poll.** If something needs the
loop to run, call `wake()`; if it needs a fixed cadence, add it to
`nextTickDelayMs` so the cost is visible in one place.

---

## 6. Location

- Permission: `ACCESS_FINE_LOCATION` (+ `ACCESS_COARSE` as appropriate).  
  Background: use foreground service with `location` type on API 29+ / 34+ as required; request background location **only if** product later needs recording with UI dismissed — **v1 may require the ride notification + app-visible session** to avoid background-location Play friction. Prefer: recording continues with FGS when app is backgrounded, without separate “all the time” permission if Play policy allows for the FGS path.
- While RUNNING, assemble one Scout sample per tick with: timestamp, lat/lon (if available), speed, drained tag (or zeros), radar fields.
- If no fix yet: still write Scout channels; position may be invalid/omitted per FIT rules — do not invent coordinates.
- Rider speed for the strip: from location speed when available (SPEC display = closing + rider).

---

## 7. Radar

### API shape (normalize both transports)

```text
RadarSession
  state: ABSENT | SCANNING | CONNECTING | TRACKING | DISCONNECTED
  targets: List<{ occupied, rangeM, closingSpeedMps }>  // ≤ 8 occupied slots
```

Only `TRACKING` writes real `radar_*` values; everything else → `255` (SPEC §8).

### Transport selection

1. If ANT+ bike radar is usable on this device → ANT+ adapter.  
2. Else → BLE adapter (Varia-compatible / documented BLE radar).  
3. User may pick preferred device in settings; persist id + transport.

### Pairing UX

- Dedicated settings / “Pair radar” flow (scan only here).
- Persist bonded/preferred address.
- Clear copy for: no adapter, permission denied, not tracking, battery tip (“radar uses more power”).

### Live strip

Same leave-gate as SPEC / Garmin `writeRadar` (count + speed on pass after ≥2 s). Domain module owns the math; UI only renders.

Start/Resume seeks the saved device for 45 s, then stops if it never tracked. Tap **No radar** (or **Connecting…**) to open another 45 s seek — same as Garmin. A drop after `TRACKING` still auto-retries with backoff. No mid-ride scan.

---

## 8. UI

### 8.1 Design system

Every screen is built from tokens in `ui/theme/Theme.kt` and chrome in
`ui/components/ScoutUi.kt`. No screen hard-codes a colour, a text size or a
padding — if a value is missing, add a token rather than a literal.

| Token set | Holds |
| --- | --- |
| `ScoutColors` | The active `ScoutColorScheme`: screen/surface/outline, brand, text, radar states |
| `ScoutSpacing` | `xs`…`xxl` step scale used for every padding and gap |
| `ScoutDimens` | Corner radii, stroke widths, tile heights, brand-mark sizes |
| `ScoutType` | Named roles (display, tile label, metric, pill, body) over the two brand faces |

- **Type:** Barlow Condensed (semibold/bold) for numerals, tile labels and
  anything glanceable at arm's length; Quicksand (medium/bold) for prose and
  buttons. Both ship as bundled `res/font` assets — no downloadable fonts, no
  network on first run.
- **Light and dark, rider's choice.** Neither is right for a bar mount all day.
  Under direct sun a dark field has too little emitted light to beat the ambient
  and the screen becomes a mirror, so light reads better; at dusk and on OLED,
  dark is easier on the eyes and cheaper to hold (SPEC §12.1). `ScoutColors` is a
  `CompositionLocal` over two `ScoutColorScheme` values and `AppPrefs.themeMode`
  picks one — default `SYSTEM`. Three consequences worth knowing:
  - `ScoutColors` is a `@Composable` getter, so palette reads only work inside
    composition. Nothing outside the UI layer may read it.
  - Status/navigation glyphs are inverted from composition
    (`MainActivity.SystemBarIcons`); the bars themselves stay transparent.
  - `windowBackground` can only follow the *system* night mode, so
    `MainActivity.paintColdStart` repaints it when the rider has overridden the
    appearance. `@color/screen_background_*` must track `ScoutColorScheme.Screen`.
    This covers the cold-start frame up to API 30; from API 31 the system splash
    window is created before `onCreate` and still takes the system's night mode,
    so an overriding rider gets a brief splash in the other colour. Living with
    that beats pulling in the splash-screen library for one frame.
- **Shared chrome:** `ScoutPage` (title + back + scroll), `ScoutSection`,
  `ScoutCard`, `ScoutButton` (filled/tonal/outline/danger), `ScoutToggleRow` and
  `StatusPill` (GPS, radar, recording). Screens compose these; they do not
  restyle Material.
- **Controls must survive both palettes.** Material's default off-state switch
  track and chip border are `surfaceVariant`/`outline`, which on a Scout card are
  indistinguishable from the card itself. Anything interactive gets
  `ScoutColors.OutlineStrong` and, where the state matters, a word next to it —
  never colour alone.
- **Brand mark:** `ic_scout_logo.xml` is the full red-disc logo (launcher + in-app
  lockup via `ScoutLogo`); `ic_scout_mark.xml` is the tintable pin-and-ripple;
  `ic_scout_notification.xml` is the flat pin silhouette for the status bar.
- **Strings:** all user-facing copy lives in `res/values/strings.xml`. Composables
  use `stringResource` — that is also the hook for a future translation.

### 8.2 Tagging UI

- Full-screen (immersive enough for bar mount): 2-column grid + bottom radar strip + recording dot.
- Timings exact to SPEC §6 (`PICK_MS` 12s, `CORRECT_MS` 3s, undo 3s, queue ≥ 16).
- Colours and labels per SPEC (English v1). The hues are normative and identical
  in both appearances; only the treatment moves. Unlit is the hue washed over the
  page at `tileIdleAlpha` and labelled in page ink, lit is the hue itself labelled
  in white — except where the hue's luminance passes `PALE_TILE_LUMINANCE`, at
  which point white drops under 3:1 (SAND, FOOD) and the label flips to dark ink.
- Haptic via `HapticFeedback` / `Vibrator` when enabled; distinct pattern for undo; never block tagging if haptic fails.
- Hit-testing: strip taps resolve to the tile above (SPEC).

- **Stop confirm:** ending a ride requires an explicit second tap in a dialog
  (SPEC §9).
- **Idle tiles:** taps while idle/paused show a snackbar only; domain controller
  ignores them (SPEC §4.1).

### 8.3 Help page

Rider-facing help is **not** hard-coded. `help/help.example.json` is the
committed template; each instance keeps its own `help/help.json` (gitignored —
see root `.gitignore`). The `prepareHelpContent` Gradle task bundles whichever
exists into `assets/help.json` before every build.

Forks change instance name, URLs and the optional sharing section without
touching Kotlin. Settings → **How Scout works** opens `HelpScreen`, which renders
the JSON sections and opens any `links` in the system browser.

Schema and setup: `help/README.md`.

### 8.4 Atlas instance (sharing)

When sharing is implemented, the client talks to exactly one Atlas instance per
build. Configuration mirrors help (§8.3):

- `Android/.env.example` — committed template (`SCOUT_INSTANCE_URL`,
  `SCOUT_INSTANCE_NAME`)
- `Android/.env.dev.local` — gitignored override for this fork
- `prepareInstanceConfig` writes `assets/instance.json` before every build
- `InstanceConfigLoader` reads it; OAuth/upload endpoints still come from
  `/.well-known/scout-upload.json` at runtime (SHARING §4)

Recording does not need a network or this file. Sharing UI will use
`instance_name` for labels only — the URL is never shown to the rider unless
they follow a link returned after upload.

---

## 9. FIT output

- Write **original FIT** with developer fields:

  | id | name | type |
  | --- | --- | --- |
  | 0 | `poi_type` | uint8 |
  | 1 | `poi_detail` | uint8 |
  | 2 | `radar_count` | uint8 |
  | 3 | `radar_near` | uint8 |
  | 4 | `radar_speed` | uint8 |

- One record ≈ one second while RUNNING; invalid radar = 255.
- **Android record payload (SPEC §4.2 only — privacy):** `timestamp`, `position_lat`,
  `position_long`, `speed` (when available), plus the five Scout developer fields.
  No HR, cadence, altitude, device serials, or other extras.
- File location: app-private `files/rides/scout-….fit`. Settings lists history with **Share** / **Delete**; share sheet after Stop as well.
- Acceptance: drop output on `tools/fit-viewer.html` and pass the same expectations as Garmin rides (tags, undo pairs, surfaces, radar coverage).

---

## 10. Permissions & Play considerations

User-facing explanations of the system dialogs:
**[PERMISSIONS.md](PERMISSIONS.md)**.

Declare only what we use:

- Location (**fine** / precise) + FGS location while recording — approximate alone is not enough for tags  
- Bluetooth Connect / Scan (API 31+; system copy often says “nearby devices”) for BLE radar  
- Notifications (API 33+) for the **recording** foreground-service notification only  
- ANT+ via ANT Radio Service when present (no extra Play “nearby” dialog for ANT itself)  
- Vibrate (optional)

Privacy copy: local recording only; no account; radar optional; no ads / analytics.

---

## 11. Implementation phases

| Phase | Deliverable | Status |
| --- | --- | --- |
| **P0** | App shell, permissions, RideSession start/pause/stop, FGS notification | Done |
| **P1** | Tag grid + pickers + queue + tallies + haptics (no radar) | Done |
| **P2** | Location sampling + FIT writer; viewer-validated file | Done |
| **P3** | BLE radar pair + TRACKING samples + strip | Done |
| **P4** | ANT+ path when hardware present; battery pass (measure §5) | Done |
| **P5** | Settings (units, keep-screen-on, preferred radar), export/share polish | Done |
| **P6** | Battery pass §5 “Shipped”, design system §8.1, screen rework | Done |

Ship gating: P2 is already useful without radar; P3–P4 match Garmin’s optional radar story; P5 finishes v1 UX.

---

## 12. Testing

Full plan: **[TESTING.md](TESTING.md)** (unit, FIT viewer, device smoke, field ride, battery).

| Layer | What |
| --- | --- |
| Domain / fit unit tests | Undo, pickers, queue, vehicle corroboration, BLE/ANT+ decode, FIT CRC |
| FIT golden | `Android/tools/validate-scout-fit.mjs` + `tools/fit-viewer.html` |
| Manual ride | Real device, bar mount, with/without radar, pause/resume |
| Battery | §5 measure checklist |

---

## 13. Out of scope / later

See **[ROADMAP.md](ROADMAP.md)** for tracked ideas (e.g. recover interrupted ride
on relaunch). Summary:

- Extract `domain/` + `fit/` to KMP for a future iOS SwiftUI UI.
- In-app Atlas upload ([SHARING.md](../../docs/SHARING.md)).
- Open-surface strip indicator (root ROADMAP idea).
- Karoo-specific packaging (separate platform folder).

---

## 14. Doc control

| Item | Value |
| --- | --- |
| Document | Android technical platform spec |
| Owns | Stack, modules, Android battery mapping, phases |
| Does not own | `poi_*` codes, undo/surface/vehicle parser rules (root docs) |

When Android must diverge from SPEC behaviour, write the delta here and add a
one-line pointer in root `docs/SPEC.md` §13.
