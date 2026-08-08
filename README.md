# Scout

In-ride tagging for hazards, closures, surfaces, resupply, scenery — and optional
rear-radar vehicle logging — written into your ride file. Built for the
[Cycling Commons Atlas](https://cyclingcommons.org); MIT-licensed and usable
standalone.

This repo is the **multi-platform Scout tree**. Shared behaviour and the on-disk
contract live at the root; each platform folder holds that port’s code and any
deltas.

## Shared docs (normative)

| Doc | Role |
| --- | --- |
| **[docs/SPEC.md](docs/SPEC.md)** | Product behaviour: UI, timings, undo, surfaces, radar transport policy |
| **[docs/DATA-FORMAT.md](docs/DATA-FORMAT.md)** | On-disk channels, codes, parser rules (undo / surfaces / vehicles) |
| **[docs/SHARING.md](docs/SHARING.md)** | Optional upload to an Atlas (client contract) |
| **[docs/ATLAS-SERVER.md](docs/ATLAS-SERVER.md)** | What an Atlas backend must implement |
| **[docs/CUSTOMIZATION.md](docs/CUSTOMIZATION.md)** | Help text, instance URL, and fork setup (no Kotlin) |

Platform ports implement these. Do not invent parallel semantics.

**Doc rule:** general specs stay here. If a platform must diverge, document the
delta under that platform’s folder and add a **short pointer** in the relevant
root doc (SPEC or DATA-FORMAT).

## Platforms

| Folder | Status |
| --- | --- |
| [Garmin/](Garmin/) | Connect IQ data field (reference implementation, v1.0 shipped) |
| [Android/](Android/) | Phone app · [setup](Android/docs/SETUP.md) · [permissions](Android/docs/PERMISSIONS.md) · [tech](Android/docs/TECHNICAL.md) · [testing](Android/docs/TESTING.md) |
| [Hammerhead-Karoo/](Hammerhead-Karoo/) | Karoo port — not started |
| [iPhone/](iPhone/) | iOS port — not started|

Radar pairing policy (all ports): **native ANT+ when available, otherwise
Bluetooth LE**. See [SPEC §8](docs/SPEC.md#8-radar).

## Release tags

Platforms version **independently**. Git tags are prefixed so they never collide:

| Tag | Matches |
| --- | --- |
| `android/vX.Y.Z` | `versionName` in `Android/app/build.gradle.kts` |
| `garmin/vX.Y.Z` | version in [Garmin/CHANGELOG.md](Garmin/CHANGELOG.md) (entered in the Connect IQ store) |

```sh
git tag -a android/v1.0.2 -m "Android 1.0.2"
git tag -a garmin/v1.0.1 -m "Connect IQ 1.0.1"
git push origin android/v1.0.2 garmin/v1.0.1
```

Legacy unprefixed `v1.0.0` is the first Garmin store release; prefer `garmin/v…`
going forward. The same commit may carry more than one platform tag if both ship
from that snapshot.

## Reference tools

Shared FIT viewer and parser tests at the repo root (used by Garmin, Android, and CI):

- [`tools/fit-viewer.html`](tools/fit-viewer.html)
- [`tools/test-fit-parser.mjs`](tools/test-fit-parser.mjs)
- [`tools/radar-sim.html`](tools/radar-sim.html)
- Android FIT smoke: [`Android/tools/validate-scout-fit.mjs`](Android/tools/validate-scout-fit.mjs)

```sh
node tools/test-fit-parser.mjs tools/fit-viewer.html
```

## License

[MIT](LICENSE) © BikeCoders (see each platform folder for its copy where applicable).
