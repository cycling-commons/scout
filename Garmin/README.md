# Scout — a Connect IQ data field for touch Edge units

_Tag hazards, closures, surfaces, resupply and scenery from the saddle (and count
passing traffic), straight into your ride's FIT file. Built for the Cycling Commons
Atlas ([cyclingcommons.org](https://cyclingcommons.org)) — but the app and its FIT
format stand alone and are MIT-licensed, so any project can ingest the data._

Tap an on-screen zone during a ride to stamp a category onto the current GPS
record in the FIT file.

## Using it
- Swipe to the POI page, tap a tile → it flashes **and buzzes/beeps** to confirm
  (whichever your device supports and you haven't muted), and the code is written
  to the next record. The tap feedback means you don't have to look down to know
  it landed.
- Each tile shows its own running tally for the ride ("NOTICE 3"), net of
  retractions; an untouched tile shows no number. RESUPPLY sums its leaves;
  CLOSURE sums all duration commits. Duration and resupply submenu leaves
  keep their own counts too (e.g. MONTHS `2` on `CLOSED FOR?`; COBBLES on the
  surface picker) — see [SPEC §6.7](../docs/SPEC.md#67-per-tile-tallies).
- Tapped the wrong tile? Tap the same one again within the **3 s** cancel window and the tag is retracted — the tile stays lit for that
  whole window and its count ticks back down.
- CLOSURE / SURFACE / RESUPPLY open a follow-up page instead. Picked the wrong
  subitem? Just tap the right one — for **3 s** after a pick the page stays open
  with your choice lit, and a new tap replaces it. When the 3 s pass it commits
  with a beep and returns to the grid. BACK aborts with no tag. If you ride on
  without picking at all, after 12 s a closure (duration UNKNOWN) and a surface
  point (type unspecified) are still recorded; a resupply is dropped.
- SURFACE marks a **stretch**: pick a type to start it, pick another to switch, or
  pick **END** to close it (road back to normal). See
  [Surface stretches](../docs/DATA-FORMAT.md#surface-stretches).
- The dot top-right is **red while the timer is running** (taps are recorded),
  grey when paused/stopped (nothing is being written).

Setup: for the biggest tap target, give Scout a data page to itself — it uses
whatever area it's given for tapping, so sharing a page shrinks the hit zone.
Build & sideload steps are in [CONTRIBUTING.md](CONTRIBUTING.md).

## Getting the tags off your ride
Scout only *records* into your FIT. It has **no network access** — no account, no
analytics, no upload. What happens after the ride is entirely your choice:

1. **Finish the ride.** Your tags and surface stretches are in the activity's FIT
   file, alongside your normal ride data.
2. **Read it yourself** — drop the file on
   [`tools/fit-viewer.html`](tools/fit-viewer.html) to see every tagged point and
   surface stretch drawn on the track, or hand it to any tool that reads FIT
   developer fields ([DATA-FORMAT](../docs/DATA-FORMAT.md) is the shared spec).
3. **Or contribute it** to a project that ingests Scout data.

**Contributing to the Cycling Commons Atlas** — the project Scout was built for —
is a separate, opt-in step: upload the file manually, or let it auto-sync from
wherever you already record your rides (e.g. Intervals.icu or Garmin Connect);
CC draws your tags on a map for you to review, enrich and prune; then you
submit them for moderator approval before they go live.
**Only the tagged points and surface stretches are read** — no route track, times,
power or heart rate. A free account is required (no real name), contributions are
anonymous unless you make your profile public, and the CC parser's source is
published, so what is read — and what isn't — can be verified rather than trusted.

Only the original `.FIT` carries the tags — a TCX/GPX export or the Strava copy
drops them. See [FIT gotchas for integrators](../docs/DATA-FORMAT.md#fit-gotchas-for-integrators).

## Verify the parser
The reference parser is self-contained and needs no Garmin SDK:

    node tools/test-fit-parser.mjs tools/fit-viewer.html

It builds a full binary FIT in memory covering every option and asserts the whole
pipeline. To eyeball a real ride, open [`tools/fit-viewer.html`](tools/fit-viewer.html)
in a browser and drop a `.fit` on it.

## Radar tally simulator
Open [`tools/radar-sim.html`](tools/radar-sim.html) in a browser to queue virtual
cars, watch them pass, and see the live car count / speed strip update with the
same 1&nbsp;Hz `VehicleCounter` rule as the head unit and Android app.

## Docs
- **Shared (repo root):** [SPEC](../docs/SPEC.md) · [DATA-FORMAT](../docs/DATA-FORMAT.md)
- **[CONTRIBUTING.md](CONTRIBUTING.md)** — build, test, and Connect IQ device
  notes; the "keep the device dumb" principle.
- **[docs/PUBLISHING.md](docs/PUBLISHING.md)** — publishing to the Connect IQ
  store (account, app `id`, signing key, versioning).
- **[CHANGELOG.md](CHANGELOG.md)** · **[ROADMAP.md](ROADMAP.md)**

## License
[MIT](LICENSE) © BikeCoders, current steward of the Cycling Commons project.
Permissive on purpose: the FIT format and the reference parser (`applyUndoRule` /
`buildSurfaceSegments` in [`tools/fit-viewer.html`](tools/fit-viewer.html)) are
meant to be ported freely into whatever ingests these rides.
