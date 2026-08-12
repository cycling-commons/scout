# Publishing Scout for Hammerhead Karoo

Distribution is optional — development builds are sideloaded (see
[SETUP.md](SETUP.md)). This covers how riders get Scout onto a Karoo and how
maintainers ship updates.

Unlike Garmin Connect IQ, Hammerhead does not use a per-app signing key in your
repo. Distribution is **APK-based**.

---

## Distribution channels

| Channel | Audience | How |
| --- | --- | --- |
| **Sideload (debug / beta)** | Developers, early testers | Companion app or `adb install` |
| **GitHub Releases** | OSS community | Attach `app-release.apk` to a tagged release |
| **Native Extension Library** | General Karoo riders | Hammerhead-curated on-device catalog (submission TBD) |

Curated list context: [awesome-karoo](https://github.com/timklge/awesome-karoo),
[DC Rainmaker overview](https://www.dcrainmaker.com/2025/03/hammerhead-karoo-adds-app-store-a-few-thoughts.html).

---

## Sideloading

### Karoo 3 — Hammerhead Companion (preferred)

Official guide: [Companion App Sideloading](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading)

Typical flow:

1. Publish `app-release.apk` (or share a CI artifact) at a stable URL.
2. On the phone, open the APK link → **Share** → **Hammerhead Companion**.
3. On Karoo, review the prompt → **Install**.
4. Launch Scout from the app drawer.

### Karoo 2 — ADB

1. Enable USB debugging on Karoo (developer setup).
2. `adb install -r app-release.apk`
3. Launch from the app drawer.

Historical walkthrough: [DC Rainmaker sideload guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).

### Updates

Long-press the app icon on the Karoo home screen → **Update**, then install the
new APK via the same sideload path.

---

## Release builds

When `Hammerhead-Karoo/app/` exists:

```sh
cd Hammerhead-Karoo
./gradlew :app:assembleRelease
```

Sign with your chosen release keystore (standard Android). Store the keystore
outside the repo; back it up — losing it blocks Play-style upgrade paths even
though Karoo sideload does not enforce upload keys.

Output: `app/build/outputs/apk/release/app-release.apk`

### Versioning

- `versionCode` / `versionName` in `app/build.gradle.kts` — bump every release.
- Tag git as **`karoo/vX.Y.Z`** alongside other Scout ports (see root README).
- Track user-facing changes in a Karoo `CHANGELOG.md` when the port ships.

---

## Native Extension Library (official catalog)

Hammerhead maintains an on-device **Extensions Library** (also called Native
Extension Library / Native App Library). Riders tap Install without sideloading.

Official rider doc: [Karoo OS Extensions Library](https://support.hammerhead.io/hc/en-us/articles/34676015530907-Karoo-OS-Extensions-Library)

**Submission process** is not fully documented in the public karoo-ext repo. Before
v1 public launch:

1. Confirm current submission requirements with Hammerhead (developer community
   or partner contact).
2. Prepare store assets: icon, short description, privacy statement (Scout: local
   FIT only, no network).
3. Ship a signed release APK and extension manifest that matches
   [TECHNICAL.md](TECHNICAL.md).

Community extensions often ship via GitHub Releases first, then seek library
inclusion later.

---

## Forks

A fork published for riders must be **clearly distinct**:

- Different `ExtensionInfo` id (not `scout` if the canonical project owns that id)
- Different display name and icon
- Its own release APK and listing text

The shared FIT format and parser remain MIT — forks still write the same developer
field names for Atlas compatibility unless intentionally diverging.

---

## Privacy copy (rider-facing)

Suggested points for store / README:

- Scout writes tag and optional radar samples into **your ride FIT file** on the
  Karoo.
- No account, no analytics, no cloud upload from Scout.
- Radar is optional; pair compatible ANT+ radar in Karoo sensor settings.
- Upload to Cycling Commons or other projects is opt-in and happens outside Scout.

Align with [SPEC §2.4](../../docs/SPEC.md#24-privacy).

---

## Doc control

| Item | Value |
| --- | --- |
| Document | Karoo publishing & distribution |
| Owns | Sideload, releases, library submission notes |
| Does not own | Connect IQ store ([Garmin/docs/PUBLISHING.md](../../Garmin/docs/PUBLISHING.md)) |
