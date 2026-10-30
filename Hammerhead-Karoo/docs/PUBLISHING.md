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
| **GitHub Releases** | OSS community, beta testers | Push a `karoo/vX.Y.Z` tag; CI publishes the release ([below](#github-releases-ci)) |
| **Native Extension Library** | General Karoo riders | Hammerhead-curated on-device catalog (submission TBD) |

Curated list context: [awesome-karoo](https://github.com/timklge/awesome-karoo),
[DC Rainmaker overview](https://www.dcrainmaker.com/2025/03/hammerhead-karoo-adds-app-store-a-few-thoughts.html).

---

## Sideloading

### Karoo 3 — Hammerhead Companion (preferred)

Official guide: [Companion App Sideloading](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading)

Typical flow:

1. Publish the update manifest at a stable URL (CI does this, see
   [below](#github-releases-ci)):
   `https://github.com/cycling-commons/scout/releases/download/karoo/stable/manifest.json`
   (testers: `karoo/beta` instead of `karoo/stable`)
2. On the phone, open the manifest link → **Share** → **Hammerhead Companion**.
   Sharing a bare APK link also installs, but that Karoo never sees updates.
3. On Karoo, review the prompt → **Install**.
4. Launch Scout from the app drawer.

### Karoo 2 — ADB

1. Enable USB debugging on Karoo (developer setup).
2. `adb install -r app-release.apk`
3. Launch from the app drawer.

Historical walkthrough: [DC Rainmaker sideload guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).

### Updates

A Karoo installed from the manifest URL checks its `latestVersionCode` and offers
`latestApkUrl` as an update when it is higher than the installed `versionCode`
(format: karoo-ext's `app/manifest.json`). So every release must bump
`versionCode` and be signed with the same key. APK-link and ADB installs don't
update: reinstall, or install once from the manifest URL.

---

## Release builds

```sh
cd Hammerhead-Karoo
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (R8 mapping in
`app/build/outputs/mapping/release/mapping.txt`).

Signing: when `KAROO_KEYSTORE_FILE`, `KAROO_KEYSTORE_PASSWORD`, `KAROO_KEY_ALIAS`
and `KAROO_KEY_PASSWORD` are set, the release is signed with that key; otherwise
it falls back to the local debug key. Android only installs an update over an
existing app signed with the **same** key, so every published release must use
one key. Store the keystore outside the repo and back it up.

### GitHub Releases (CI)

[`.github/workflows/karoo-release.yml`](../../.github/workflows/karoo-release.yml)
runs on every pushed `karoo/v*` tag. It checks the tag against `versionName`,
builds a signed release APK, and publishes a GitHub release with:

- `scout-karoo-<version>.apk`: its download link is what testers open for the
  Companion install
- `Scout-Karoo-<version>.zip`: APK + [`MANUAL.html`](MANUAL.html) (user
  manual, with a beta-testing section) + `fit-viewer.html`
It then writes the update manifest and uploads it with the icon to fixed
**update channel** releases (each created on its first run), replacing the
previous files. Their manifest URLs are the install links:

| Channel | Gets | Install link |
| --- | --- | --- |
| `karoo/beta` | every tag, beta or stable | `…/releases/download/karoo/beta/manifest.json` |
| `karoo/stable` | tags without a suffix only | `…/releases/download/karoo/stable/manifest.json` |

Testers install from the beta link, riders from the stable link. The stable
link 404s until the first tag without a suffix. `releases/latest/download/…`
can't serve this: GitHub's "latest" skips pre-releases and is shared with the
other Scout ports in this repo.

A tag with a suffix (`karoo/v0.1.0-beta`) becomes a pre-release. The R8 mapping
is kept as a workflow artifact for decoding crash traces.

One-time setup: add these repository secrets (Settings → Secrets and variables →
Actions):

| Secret | Value |
| --- | --- |
| `KAROO_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `KAROO_KEYSTORE_PASSWORD` | keystore password |
| `KAROO_KEY_ALIAS` | key alias |
| `KAROO_KEY_PASSWORD` | key password |

Create a key with
`keytool -genkeypair -keystore scout-karoo.keystore -alias scout-karoo -keyalg RSA -keysize 2048 -validity 10000`,
then encode it with `base64 -w0 scout-karoo.keystore` (PowerShell:
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("scout-karoo.keystore"))`).

Releasing:

```sh
# bump versionCode / versionName in app/build.gradle.kts first
git tag -a karoo/v0.1.0-beta -m "Karoo 0.1.0 beta"
git push origin karoo/v0.1.0-beta
```

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
