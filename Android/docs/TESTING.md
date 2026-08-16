# Scout — Android testing

How to verify the Android port before a ride and before calling a build “good
enough.” Behaviour contracts live in the shared specs; this page is the
**test plan**.

| Related doc | Role |
| --- | --- |
| [SETUP.md](SETUP.md) | Tooling / build |
| [PERMISSIONS.md](PERMISSIONS.md) | System permission dialogs (location / Bluetooth / notifications) |
| [TECHNICAL.md](TECHNICAL.md) | Stack, battery §5, phases |
| [Product SPEC](../../docs/SPEC.md) | What must happen |
| [DATA-FORMAT](../../docs/DATA-FORMAT.md) | On-disk / parser rules |

---

## 1. Layers

| Layer | Where | When |
| --- | --- | --- |
| **A. JVM unit tests** | `:domain`, `:fit` | Every change to tagging / FIT / radar decode |
| **B. FIT viewer check** | Node + `fit-viewer.html` | After FIT encoder changes; after a real ride |
| **C. Device smoke** | Physical phone (or emulator for non-radar) | Before a field ride |
| **D. Field ride** | Bar-mount, real GPS (± radar) | Before calling v1 “done” |
| **E. Battery** | TECHNICAL §5 measure table | Before shipping a battery-sensitive build |

There are **no instrumented UI tests** yet. GPS, BLE/ANT+, FGS, and permissions
are covered by C–E.

---

## 2. Automated (A + B)

### 2.1 Unit tests

From `Android/`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :domain:test :fit:test
```

```sh
export JAVA_HOME="…/Android Studio…/jbr…"
./gradlew :domain:test :fit:test
```

**Expect:** `BUILD SUCCESSFUL`, 0 failures.

What they cover today:

| Suite | Checks |
| --- | --- |
| `TagTalliesTest` | Undo windows, surface never undoes, END not tallied, resupply sum |
| `ScoutControllerTest` | Tag only when RUNNING, picker commit/timeout/back, FIFO double-tap |
| `VehicleCounterTest` | 1 s blip discarded, corroborated arrival, dropout clears pending |
| `VariaV1DecoderTest` | BLE V1 threats, speed/flag, FIT 255 vs empty-road 0 |
| `AntPlusBikeRadarDecoderTest` | Pages 48/49 range & closing speed |
| `ScoutFitWriterTest` | Header/CRC, scenario encode, streamed flushes byte-match the one-shot encode, mid-ride flush leaves a valid file |

What they **do not** cover: location hardware, GATT/ANT Radio Service, Compose UI,
Settings share/delete, FGS, permissions dialogs.

### 2.2 Synthetic FIT → reference parser

After `:fit:test` (writes `fit/build/scout-scenario.fit`):

```sh
# from Scout repo root
node Android/tools/validate-scout-fit.mjs tools/fit-viewer.html Android/fit/build/scout-scenario.fit
node Android/tools/validate-scout-fit.mjs tools/fit-viewer.html Android/fit/build/scout-scenario-streamed.fit
```

**Expect (both files):** CRC ok, 60 records, tags/undo, surface segments, 2 vehicles.
The second file is written the way the app writes — append plus periodic flush — so
run it whenever `ScoutFitWriter` changes.

Optional: open `tools/fit-viewer.html` in a browser and drop the same file.

Shared CI-style parser suite (Garmin reference):

```sh
node tools/test-fit-parser.mjs tools/fit-viewer.html
```

### 2.3 Lint

```powershell
.\gradlew.bat :app:lintDebug
```

**Expect:** `BUILD SUCCESSFUL`, no errors. Lint needs network the first time (it
resolves `lint-gradle`), so do not pass `--offline`.

---

## 3. Device smoke (C)

Install debug build (`:app:assembleDebug` or Studio **Run**).

Grant permissions as described in **[PERMISSIONS.md](PERMISSIONS.md)**
(**Precise** location, **Allow** notifications; **Allow** nearby devices if
testing BLE radar).

### 3.1 Without radar

| # | Step | Pass if |
| --- | --- | --- |
| 1 | Idle → **Start** | Red recording dot; FGS notification “recording” |
| 1b | Idle → tap any tile | Banner above controls: “Start your ride…” with **Start ride** action; no flash/picker |
| 1c | Paused → tap any tile | “Resume your ride…” with **Resume** action |
| 2 | Wait ~10 s outdoors / with fake GPS | Status shows `GPS · ±N m` (not stuck on `Waiting for GPS` forever outdoors) |
| 3 | Tap OTHER; NOTICE → pick POTHOLES; SCENERY → pick VIEW | Flash + tallies increment; pickers open |
| 4 | Tap NOTICE twice quickly (after two commits) | Tally undoes (SPEC undo) |
| 5 | CLOSURE → pick TODAY → wait 3 s | Beep/haptic; grid CLOSURE tally up; reopen picker → TODAY shows `1` |
| 5b | CLOSURE → MONTHS (later, outside undo) → reopen | MONTHS shows its own count; CLOSURE total = sum of durations |
| 6 | SURFACE → COBBLES → later END | Banner `surface open: COBBLES`; SURFACE tile lit with type; clears on END |
| 7 | RESUPPLY → leave 12 s | No resupply tag |
| 8 | **Pause** | Grey dot; GPS stops; taps do not enqueue |
| 9 | **Stop** | Notification gone; “saved scout-….fit”; confirm dialog shown first |
| 10 | **Share FIT** or Settings → ride → Share | Share sheet opens |
| 11 | Drop `.fit` on fit-viewer | Track + tags visible; radar coverage ~0 / all 255 |

### 3.2 Settings

| # | Step | Pass if |
| --- | --- | --- |
| 1 | Settings → mph | Strip uses mph (±3) when radar live later |
| 2 | Keep screen on **off** (default) | Screen can sleep while recording |
| 3 | Keep screen on **on** → Start | Screen stays awake while RUNNING only |
| 4 | Rides list | Past FITs listed without sharing first |
| 5 | Delete a ride | Gone from list; file removed |
| 6 | Appearance → **Light**, then **Dark** | Every screen flips, including the status-bar glyphs; no white or black flash on the way |
| 7 | Appearance → **Auto**, flip the phone's system theme | App follows |
| 8 | Force **Light**, kill the app, relaunch on a phone in dark mode | App comes up light (the API 31+ system splash still follows the phone — known, §8.1) |
| 9 | SURFACE → SAND in each appearance | The lit tile's label and countdown are dark ink, not white |
| 10 | About → **How Scout works** | Sections render; instance link opens browser |

### 3.3 Radar (optional hardware)

**BLE (Varia-family):** Settings → Pair / change radar → transport **BLE** → Scan → select → Done → Start.

**ANT+:** ANT Radio Service (or USB stick) installed → transport **ANT+** or **Auto** → Search ANT+ → Start.

| # | Step | Pass if |
| --- | --- | --- |
| 1 | Not paired / not TRACKING | Strip: `No radar` (never `0 cars`) |
| 1b | Paired, start with radar off, wait past Connecting…, turn radar on, tap **No radar** | Strip: Connecting… then live tally |
| 2 | TRACKING, empty road | Strip live; FIT `radar_count=0`, near/speed 255 |
| 3 | Car overtakes | Count rises after corroboration; speed ±5 kph / ±3 mph |
| 4 | Pause | Radar disconnects; strip back to `no radar` |
| 5 | Viewer on ride file | `radar_*` populated while tracking; coverage & vehicle count sane |

Emulator: GPS fake ok; **do not** rely on emulator for BLE/ANT+.

### 3.4 Getting a ride file off the device

The app writes to app-private storage, so pull it through `run-as`. Redirect from
`cmd`, not PowerShell — PowerShell's `>` re-encodes and corrupts the binary:

```powershell
adb emu geo fix 4.895 52.370          # emulator only: give it a fix to record
adb shell run-as org.cyclingcommons.scout ls -l files/rides
cmd /c "adb exec-out run-as org.cyclingcommons.scout cat files/rides/<name>.fit > ride.fit"
node Android/tools/validate-scout-fit.mjs tools/fit-viewer.html ride.fit
```

The record/tag/surface expectations in `validate-scout-fit.mjs` are pinned to the
synthetic scenario, so on a real ride file only read `CRC ok`, `has records` and
`dev field names`; check the rest in `fit-viewer.html`.

### 3.5 Screenshots

`tools/shot.ps1 -Name <name>` pulls a screenshot into `Android/build/shots/` and
writes a downscaled `.small.jpg` next to it; `tools/crop-shot.ps1` zooms a region
for checking icon and type detail.

---

## 4. Field ride (D)

Bar mount, real outdoor path, 15–60+ minutes.

Checklist:

- [ ] Tags land near the feature (GPS lag is normal; SPEC accepts picker delay)
- [ ] Pause mid-ride, resume — file continues, no invented radar zeros
- [ ] Stop → Settings → Share → open in fit-viewer
- [ ] With radar: strip matches “feel”; viewer vehicle count ≈ on-screen tally
- [ ] Without radar: all `radar_*` invalid (255); tagging still works

Acceptance (SPEC / TECHNICAL): file passes the same viewer expectations as a
Garmin Scout ride for tags, undo, surfaces, and radar coverage when radar was used.

---

## 5. Battery (E)

From [TECHNICAL.md](TECHNICAL.md) §5 — fill in on a real device:

| Test | Duration | Start SoC % | End SoC % | Notes |
| --- | --- | --- | --- | --- |
| Idle force-stop | 2 h | | | baseline |
| Ride **paused** | 2 h | | | GPS/radar must be off |
| Recording, no radar | 2 h | | | keep-screen-on **off** |
| Recording + radar | 2 h | | | transport: ANT+ / BLE |

Treat large idle/paused regressions as bugs.

Quick hygiene checks (no 2 h needed):

- [ ] Paused: location updates stopped (fix freezes / “no fix” path)
- [ ] Paused: radar disconnected
- [ ] Idle: no BLE scan loop (scan only on pair screen)
- [ ] Keep-screen-on off by default
- [ ] Idle: no CPU burn. Sit on the ride screen with no ride open and diff
      `utime + stime` (fields 14–15 of `/proc/<pid>/stat`) over 30 s — expect **0**.
      Recording with GPS is roughly 50 jiffies per 30 s for comparison.

---

## 6. Suggested order before a release

1. `:domain:test :fit:test`
2. `validate-scout-fit.mjs` on both scenario FITs
3. `:app:lintDebug`
4. Device smoke §3.1 + §3.2
5. Radar §3.3 if hardware available
6. One outdoor ride §4
7. Battery §5 when changing radios / GPS / wake behaviour

---

## 7. Capturing a failure

Include:

- App version (`versionName`, e.g. `0.5.0-p5`)
- Phone model + Android version
- Radar transport (none / BLE / ANT+) and device name
- Steps + approx time
- The `.fit` if recording-related (Settings → Share)
- Logcat slice if crash: `adb logcat --pid=$(adb shell pidof -s org.cyclingcommons.scout)`
