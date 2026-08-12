# Scout — Hammerhead Karoo

Karoo port of Scout: in-ride tagging and optional rear-radar logging into the
activity FIT file, built as a [karoo-ext](https://github.com/hammerheadnav/karoo-ext)
Android extension.

**Status:** K0–K3 shipped — FIT + ride-page data field, background recording, tagging UI, radar. K4 = real-device FIT validation; K5 = polish.

## Shared contracts

- [Product spec](../docs/SPEC.md)
- [Data format](../docs/DATA-FORMAT.md)

## Karoo docs

| Doc | Contents |
| --- | --- |
| [docs/TECHNICAL.md](docs/TECHNICAL.md) | Extension architecture, FIT via `startFit`, radar, UI plan, phases |
| [docs/SETUP.md](docs/SETUP.md) | karoo-ext dependency, build, sideload, FIT smoke test |
| [docs/HANDOFF.md](docs/HANDOFF.md) | Real-device validation checklist |
| [docs/PUBLISHING.md](docs/PUBLISHING.md) | Releases, Companion sideload, Extensions Library |

## Platform deltas (vs root SPEC)

| Topic | Karoo behaviour |
| --- | --- |
| FIT writer | karoo-ext `WriteToRecordMesg` into Karoo's ride file (not standalone `:fit` module) |
| Ride timer | Follows Karoo `RideState`; Scout does not own start/stop |
| Radar transport | Karoo native ANT+ via `DataType.Type.RADAR` — no in-app BLE pairing |
| `radar_speed` | Often **255** — Karoo stream exposes target ranges, not closing speed ([TECHNICAL §8](docs/TECHNICAL.md#83-delta-vs-spec-8-documented)) |

## Official Hammerhead references

- API: https://hammerheadnav.github.io/karoo-ext/index.html
- Template: https://github.com/hammerheadnav/karoo-ext-template
- Sample: https://github.com/hammerheadnav/karoo-ext/tree/master/app
- Developer forum: https://support.hammerhead.io/hc/en-us/community/topics/31298804001435-Hammerhead-Extensions-Developers
