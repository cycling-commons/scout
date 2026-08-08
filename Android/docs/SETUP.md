# Scout — Android development setup

How to get a machine ready to build and run the Android port. Anyone should be
able to follow this from a clean checkout — Scout is **MIT-licensed** (see
[`LICENSE`](../../LICENSE) at the repo root).

Behaviour and file formats are defined in the shared docs; this page is only
**tooling and environment**.

| Related doc | Role |
| --- | --- |
| [TECHNICAL.md](TECHNICAL.md) | Stack, modules, battery, build phases |
| [TESTING.md](TESTING.md) | Unit, FIT, device, field, and battery test plan |
| [PERMISSIONS.md](PERMISSIONS.md) | What the system permission dialogs mean |
| [Product SPEC](../../docs/SPEC.md) | What the app must do |
| [DATA-FORMAT](../../docs/DATA-FORMAT.md) | FIT channels & parser rules |

---

## Verified build

These commands succeed on a machine with Android Studio’s embedded JBR and an
SDK that includes platform **37** (or **35** plus whatever Studio installs):

```powershell
cd Android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # Windows; use Studio JBR on other OSes
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"              # adjust if your SDK path differs
.\gradlew.bat :domain:test :fit:test :app:assembleDebug
```

```sh
# macOS / Linux
cd Android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # typical macOS path
export ANDROID_HOME="$HOME/Library/Android/sdk"   # or ~/Android/Sdk
./gradlew :domain:test :fit:test :app:assembleDebug
```

Debug APK output:

`Android/app/build/outputs/apk/debug/app-debug.apk`

Validate a FIT against the shipped viewer parser (after `:fit:test` writes the scenario file):

```sh
node Android/tools/validate-scout-fit.mjs tools/fit-viewer.html Android/fit/build/scout-scenario.fit
```

---

## 1. What you need

### Required

| Tool | Notes |
| --- | --- |
| **Git** | Clone this repository |
| **[Android Studio](https://developer.android.com/studio)** (current stable) | IDE + **bundled JBR** + SDK Manager + emulator |
| **Android SDK** | Via Studio’s SDK Manager (§3) — need **API 37** for `compileSdk 37` |
| **Device or emulator** | Physical phone (USB debugging) **or** an AVD |

### Supported JDK (Gradle)

Use the **latest Android Studio JBR** as the Gradle JDK. That is the supported
configuration (not an older side install).

| JDK | Gradle requirement ([compat table](https://docs.gradle.org/current/userguide/compatibility.html#java)) |
| --- | --- |
| **25** | Gradle **9.1.0+** |
| **26** | Gradle **9.4.0+** |

This repo ships **Gradle 9.6.1**, so **25 and 26 are supported**.

| Tool | Version in repo |
| --- | --- |
| Gradle | **9.6.1** |
| Android Gradle Plugin | **9.3.0** (built-in Kotlin) |
| Kotlin Compose plugin | **2.3.21** |
| `compileSdk` / `targetSdk` | **37** |
| Bytecode / toolchain | **Same major as the Gradle JDK** (no older pin) |

In Studio: **Settings → Build Tools → Gradle → Gradle JDK → Embedded JDK**.

### Optional

| Tool | When you need it |
| --- | --- |
| **Node.js 20+** | Shared FIT parser / viewer under `tools/` |
| **Bike radar** (e.g. Garmin Varia) | BLE and/or ANT+ radar testing |
| **[ANT Radio Service](https://play.google.com/store/apps/details?id=com.dsi.ant.service.socket)** (or USB ANT stick) | ANT+ radar path on phones without built-in ANT |
| **ANT+ capable phone or USB ANT stick** | Hardware for the ANT+ transport |

---

## 2. Clone the repo

```sh
git clone https://github.com/cycling-commons/scout.git
cd scout
```

Android project: `Android/`. Shared specs: `docs/`.

> Remote name may change if the monorepo is renamed; the layout
> (`Android/`, `docs/`, `Garmin/`) is what matters.

---

## 3. Install Android Studio & SDK

1. Install **[Android Studio](https://developer.android.com/studio)**.
2. First launch: **Standard** setup (SDK + default emulator image).
3. **Settings → Languages & Frameworks → Android SDK** and install:

   | Package | Why |
   | --- | --- |
   | **Android SDK Platform 37** | Matches `compileSdk 37` |
   | **Android SDK Platform-Tools** | `adb` |
   | **Android SDK Build-Tools** | Build (35+ is fine) |
   | **Android Emulator** + a system image | Run without a phone |

4. SDK path (Studio shows it on the SDK page). Typical defaults:
   - Windows: `%LOCALAPPDATA%\Android\Sdk`
   - macOS: `~/Library/Android/sdk`
   - Linux: `~/Android/Sdk`

For CLI Gradle outside Studio:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"   # or ~/Android/Sdk
export JAVA_HOME="…/Android Studio…/jbr…"        # Studio’s JBR
```

---

## 4. Open the Android project

1. **File → Open…** → select `Android/` (folder with `settings.gradle.kts` and
   `gradlew` / `gradlew.bat`).
2. Let Gradle sync (internet required the first time).
3. If sync complains about the SDK: **Project Structure → SDK Location**.
4. Run the `app` configuration.

**P0–P5 (current):**

- Start / Pause / Resume / Stop + recording notification (FGS location)
- Tag grid + pickers, tallies, haptics
- ~1 Hz GPS + FIT under `files/rides/`; Share FIT after Stop
- **Settings** (idle): km/h|mph, keep-screen-on (default off), pair radar (Auto/ANT+/BLE), list past rides (**Share** / **Delete**)
- Radar reconnects on Start while TRACKING; strip + FIT `radar_*`
- Battery: GPS only while RUNNING; no radar/scan while paused; measure checklist in TECHNICAL §5

How to verify: **[TESTING.md](TESTING.md)**.

---

## 5. Run on an emulator

1. **Tools → Device Manager → Create device**.
2. System image with **Google APIs** or **Google Play**.
3. Start the AVD → **Run** `app`.

**Fake GPS:** emulator **… → Location**, or `adb emu geo fix <lon> <lat>`.

**BLE radar:** use a **physical phone**; the emulator is a poor stand-in.

---

## 6. Run on a physical phone

1. Enable developer options (tap **Build number** seven times).
2. Enable **USB debugging**.
3. Connect USB; accept the trust prompt.
4. `adb devices` should list the phone.
5. Select the device in Studio → **Run**.

On first run Android will ask for permissions. What each dialog means and what
to choose: **[PERMISSIONS.md](PERMISSIONS.md)**.

Short version: **Location → Precise**, **Notifications → Allow**, **Nearby
devices → Allow** if you use BLE radar.

---

## 7. Command-line build

From `Android/`:

```sh
./gradlew :domain:test :app:assembleDebug    # macOS / Linux
gradlew.bat :domain:test :app:assembleDebug  # Windows
```

Install on a connected device:

```sh
./gradlew :app:installDebug
```

Domain-only tests:

```sh
./gradlew :domain:test
```

---

## 8. Shared FIT tooling (optional)

From the **Scout repo root** (needs Node 20+):

```sh
node tools/test-fit-parser.mjs tools/fit-viewer.html
```

Open `tools/fit-viewer.html` in a browser and drop a `.fit` on it (stays
local). No Android SDK required for this step.

---

## 9. License

MIT — see [`LICENSE`](../../LICENSE). Keep the copyright and permission notice
when redistributing.

---

## 10. Troubleshooting

| Problem | What to try |
| --- | --- |
| Gradle sync / build: SDK / platform 37 missing | SDK Manager → install **Android API 37** |
| `Failed to find Platform SDK … android-37` | Install platform 37; Studio may show it as `android-37` or `android-37.0` |
| Java / Gradle version errors | Gradle JDK = **Studio Embedded JBR**; don’t pin an old JDK |
| `adb devices` empty | Cable, OEM USB driver (Windows), USB debugging, trust prompt |
| Emulator slow | Hardware acceleration (Hyper-V / WHPX, KVM, or macOS default) |
| Emulator location stuck | Extended Controls → Location |
| BLE radar | Real phone + in-app pairing (P3+) |
| FIT parser test fails | Node 20+; run from repo **root** with paths above |

---

## 11. Checklist

- [ ] Repo cloned
- [ ] Android Studio installed
- [ ] SDK: Platform **37**, Platform-Tools, Build-Tools
- [ ] Emulator **or** phone with USB debugging
- [ ] Gradle JDK = Studio **Embedded JDK** (25+ / 26)
- [ ] `.\gradlew.bat :domain:test :app:assembleDebug` (or `./gradlew …`) succeeds
- [ ] (Optional) Node.js for FIT tools
- [ ] (Optional) `JAVA_HOME` / `ANDROID_HOME` for CLI
