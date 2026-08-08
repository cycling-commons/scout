# Changelog — Scout

Semantic versioning; this file is the source of truth for the version string
entered in the Connect IQ store at upload time.

## Unreleased

## 1.2.0 — 2026-08-08
- **Open-surface strip** while a stretch is active: banner `rec. surface tap to END`;
  tap the strip to END; SURFACE tile shows the active type.
- Undo window unified to **3 s** for all tiles (was 6 s for two-tap tiles).
- Scenery picker label **ARCHITECT** (was ARCH); FIT code unchanged.

## 1.1.0 — 2026-08-08
- Tile label **NOTICE** (was BEWARE); FIT type code 1 stays `DANGER`.
- **NOTICE** and **SCENERY** detail pickers (two-tap, same flow as closure and
  surface); timeout commits `UNKNOWN`.
- Grid layout: **RESUPPLY** top-left, **NOTICE** middle-right (swapped with
  **RESUPPLY**).
- Reset live radar car count when a ride starts or ends (not on pause/resume).
- Optional radar strip via `SHOW_RADAR_STRIP` (default off). Counting and FIT
  `radar_*` fields still run.

## 1.0.0 — 2026-07-22 (first store release)
- One-tap tagging on a full-screen touch data field: **notice, closure** (with
  duration), **surface** (8 OSM-aligned types), **resupply** (water/food/repair),
  **scenery, other**.
- **Surface stretches**: a type starts a stretch, another type switches it, END
  closes it; the parser joins the transitions into segments and the viewer draws
  them as coloured lines along the track with measured lengths.
- Two-tap pickers with a 3 s correction window (re-pick to replace before it
  commits; only the final choice is written), plus double-tap undo — 3 s for
  direct tiles, 3 s for all tiles).
- Per-tile ride tally and a tap-tone confirmation (distinct tone on undo).
- Optional Varia-compatible radar: per-second vehicle count, nearest range, and
  per-pass speed (closing speed plus the rider's own speed, unit-aware, ±5 kph).
- Everything written to the FIT as developer fields, in an open, documented format.
- Compatible with touch Edge units (1030/1030 Plus/1040/1050, 830/840/850, 820,
  Explore/Explore 2).
- Companion FIT inspector (`../tools/fit-viewer.html`) with a ride map of all tags.
