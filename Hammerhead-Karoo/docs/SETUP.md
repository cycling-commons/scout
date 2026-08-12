# Scout — Hammerhead Karoo development setup

How to get a machine ready to build and sideload Scout for Karoo. Behaviour and
file formats are defined in the shared docs; this page is **tooling and environment
only**.

| Related doc | Role |
| --- | --- |
| [TECHNICAL.md](TECHNICAL.md) | Extension model, FIT, radar, phases |
| [PUBLISHING.md](PUBLISHING.md) | Sideloading and Extensions Library |
| [Product SPEC](../../docs/SPEC.md) | What Scout must do |
| [DATA-FORMAT](../../docs/DATA-FORMAT.md) | FIT channels & parser rules |

---

## 1. What you need

### Required

| Tool | Notes |
| --- | --- |
| **Git** | Clone this repository |
| **[Android Studio](https://developer.android.com/studio)** (current stable) | IDE + bundled JBR + SDK Manager |
| **Android SDK** | API level matching the Karoo template target (align with karoo-ext sample when the `app/` module exists) |
| **Hammerhead Karoo 2 or 3** | Physical device for real testing |
| **GitHub account** | karoo-ext is hosted on GitHub Packages (auth required) |

### Karoo device firmware

Community extensions document **Karoo OS ≥ 1.538.2049** for the Extension SDK.
Update the head unit before testing Scout.

### Optional

| Tool | Notes |
| --- | --- |
| **ADB + USB debugging** | Karoo 2 manual sideload; developer diagnostics |
| **[Hammerhead Companion app](https://www.hammerhead.io/)** | Karoo 3 sideloading via phone |
| **ANT+ rear radar** | Varia / Magene L508 paired in Karoo Settings → Sensors |

---

## 2. karoo-ext dependency

Scout Karoo uses a **composite Gradle build** of the upstream `karoo-ext` library
(no GitHub Packages token required for day-to-day builds).

### First-time setup

```powershell
cd Hammerhead-Karoo
.\tools\bootstrap-karoo-deps.ps1
```

This clones [hammerheadnav/karoo-ext](https://github.com/hammerheadnav/karoo-ext)
into `.deps/karoo-ext` (gitignored). `settings.gradle.kts` substitutes
`io.hammerhead:karoo-ext` with the local `:lib` module from that checkout.

### Alternative — GitHub Packages

If you prefer a published artifact instead of the composite build, remove
`includeBuild(".deps/karoo-ext")` from `settings.gradle.kts` and add credentials
to `local.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

Package page: https://github.com/hammerheadnav/karoo-ext/packages/2175616

### Alternative — mavenLocal()

```bash
git clone https://github.com/hammerheadnav/karoo-ext.git
cd karoo-ext
./gradlew :lib:publishToMavenLocal   # requires JDK 17+
```

Then ensure `mavenLocal()` is first in `settings.gradle.kts` repositories and
remove the `includeBuild` block.

---

## 3. Build

```powershell
cd Hammerhead-Karoo
.\tools\bootstrap-karoo-deps.ps1   # first time only
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

```sh
cd Hammerhead-Karoo
./tools/bootstrap-karoo-deps.ps1    # or: git clone … into .deps/karoo-ext
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

Debug APK: `Hammerhead-Karoo/app/build/outputs/apk/debug/app-debug.apk`

Shared domain tests (source under `Android/domain/`):

```sh
cd Hammerhead-Karoo
./gradlew :domain:test
```

---

## 5. Install on Karoo

See **[PUBLISHING.md](PUBLISHING.md)** for full detail. Short version:

### Karoo 3 (Companion sideload)

1. Build or download `app-debug.apk`.
2. On your phone, open the APK link and **Share → Hammerhead Companion**.
3. On Karoo, confirm **Install**.
4. Open **Scout** from the app drawer; add BonusAction to a ride profile if offered.

### Karoo 2 (ADB)

1. Enable developer mode / USB debugging on Karoo.
2. `adb install -r app-debug.apk`
3. Open Scout from the launcher.

### Update

Long-press the app icon on the Karoo home screen → **Update** (after installing a
newer APK).

---

## 6. Verify FIT output

1. Pair GPS / start a short outdoor or simulated ride on Karoo with Scout extension
   active and recording.
2. Tag a few tiles (OTHER, one picker flow, one SURFACE start + END).
3. End ride; sync or export the activity FIT (Hammerhead dashboard / USB / linked
   service — use whichever path your test setup supports).
4. Open the file in [`tools/fit-viewer.html`](../../tools/fit-viewer.html).

Expect five developer fields on each `record` message while recording.

---

## 7. Troubleshooting

| Symptom | Check |
| --- | --- |
| Gradle cannot resolve `karoo-ext` | `gpr.user` / `gpr.key` or `mavenLocal()` publish |
| Extension not listed in Karoo | `KAROO_EXTENSION` intent filter; `extension_info.xml` id matches service |
| No FIT developer fields | `startFit` running; `RideState.Recording`; field defs use uint8 base type |
| Radar always invalid | Radar paired in Karoo Settings → Sensors; `DataType.Type.RADAR` streaming |
| Tags missing | Scout only writes while Karoo is recording, not merely when Activity is open |

Community help: [Hammerhead Extensions Developers forum](https://support.hammerhead.io/hc/en-us/community/topics/31298804001435-Hammerhead-Extensions-Developers).

---

## 8. Doc control

| Item | Value |
| --- | --- |
| Document | Karoo development setup |
| Owns | SDK install, build, sideload smoke test |
| Does not own | Product behaviour (root SPEC) |
