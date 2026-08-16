# Changelog — Scout (Android)

Semantic versioning; `versionName` in `app/build.gradle.kts` is the source of
truth. Tag releases **`android/vX.Y.Z`** (see root [README](../README.md#release-tags)).

## Unreleased
- Tap **No radar** (or **Connecting…**) while recording to start a new 45 s seek
  for the saved Varia — same as Garmin. Start still seeks once; a drop after
  TRACKING still auto-retries with backoff.

## 1.3.0 — 2026-08-08
- **Resupply FIT encoding** unified: `poi_type` 9 + detail (WATER / FOOD /
  REPAIR), matching SCENERY and CLOSURE. Legacy types 3 / 7 / 8 still parse.
- Ride recovery persists resupply detail tallies.

## 1.2.0 — 2026-08-08
- Scenery picker label **ARCHITECT** (was ARCH); FIT code unchanged.
- Undo window unified to **3 s** for all tiles (was 6 s for two-tap tiles).

## 1.1.0 — 2026-08-08
- Tile label **NOTICE** (was BEWARE); FIT type code 1 stays `DANGER`.
- **NOTICE** and **SCENERY** detail pickers (two-tap, same flow as closure and
  surface); timeout commits `UNKNOWN`.
- Grid layout: **RESUPPLY** top-left, **NOTICE** middle-right (swapped with
  **RESUPPLY**).
- Ride recovery persists notice and scenery detail tallies.

## 1.0.2
- Analytics wording, splash/intro polish, and related fixes.

## 1.0.0
- First Android release: ride session, tag grid/pickers, GPS, FIT writer, BLE/ANT+
  radar, settings, export.
