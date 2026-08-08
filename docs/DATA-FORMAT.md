# Scout — Data format & parser reference

How Scout records tags into the activity file, and how to read them back. This is
the reference for anyone ingesting Scout rides (the Cycling Commons Atlas, or your
own tooling). Behavioural product rules (UI, timings, radar transport) are in
**[SPEC.md](SPEC.md)**; this document is the **on-disk / parser contract**.

The reference parser currently ships in
[`Garmin/tools/fit-viewer.html`](../Garmin/tools/fit-viewer.html); port its logic,
don't reinvent it.

**Canonical encoding:** FIT developer fields on each `record` message — no
separate file, no separate coordinates (the record already carries lat/lon).
Platforms should write original FIT with these fields when feasible. Platform
deltas (if any) live under that platform’s folder, with a short pointer here.

**Android:** no format delta — [`Android/fit`](../Android/fit/) writes the same
five Scout channels plus timestamp / lat / lon / speed (SPEC §4.2); see
[`Android/docs/TECHNICAL.md`](../Android/docs/TECHNICAL.md) §9.

Everything below is stored as those developer fields on the `record` message.

## The tags: `poi_type` / `poi_detail`

The watch grid — six tiles, one tap each. Five (★) open a second page
before they commit; the other one tags on the spot.

    ┌──────────┬───────────┐
    │RESUPPLY ★│  CLOSURE ★│
    ├──────────┼───────────┤
    │ SURFACE ★│ DANGER ★  │
    ├──────────┼───────────┤
    │ SCENERY ★│  OTHER    │
    └──────────┴───────────┘

    ★ second page:
      DANGER   →  NOTICE?     POTHOLES · CROSSING · CORNER · OTHER
      CLOSURE  →  CLOSED FOR?   TODAY · DAYS · WEEKS · MONTHS · UNKNOWN
      SURFACE  →  WHICH?        ASPHALT · CONCRETE · PAVING · SETT · COBBLES · GRAVEL · DIRT · SAND · END
      RESUPPLY →  WHAT KIND?    WATER · FOOD · REPAIR
      SCENERY  →  SCENERY?      NATURE · HISTORY · CULTURE · VIEW · ARCHITECT · UNKNOWN

The pickers look alike but encode differently. DANGER, CLOSURE, SCENERY, and SURFACE each
write **one** `poi_type` with a qualifier in `poi_detail`. RESUPPLY is a menu
folder, not a code — it writes nothing itself; its leaves are **three distinct
`poi_type`s** (WATER=3, FOOD=7, REPAIR=`MECHANICAL`=8) with `poi_detail` 0, because
each resupply kind is its own OSM-style POI category (drinking water / cafe-shop /
bike shop), not a qualifier on one feature the way a duration or a surface is.

`poi_type`: 1=DANGER · 2=SCENERY · 3=WATER · 4=OTHER · 5=CLOSURE · 6=SURFACE ·
7=FOOD · 8=MECHANICAL. Codes are **append-only** — add new ones, never renumber.

`poi_detail` carries a qualifier for the type on the same record; it is 0 wherever
no qualifier applies. The reader keys off `poi_type`, so the two code sets below
may overlap numerically.

- `poi_type == 1` (DANGER) — hazard: 1=POTHOLES · 2=CROSSING · 3=CORNER ·
  4=OTHER · 5=UNKNOWN. 0 = legacy bare tap or unspecified.
- `poi_type == 5` (CLOSURE) — duration: 1=TODAY · 2=DAYS · 3=WEEKS · 4=MONTHS ·
  5=UNKNOWN.
- `poi_type == 2` (SCENERY) — kind: 1=NATURE · 2=HISTORY · 3=CULTURE · 4=VIEW ·
  5=ARCHITECT (architecture) · 6=UNKNOWN. 0 = legacy bare tap or unspecified.
- `poi_type == 6` (SURFACE) — surface type, smooth→rough, aligned to OSM
  `surface=`: 1=asphalt · 2=concrete · 3=paving_stones · 4=sett ·
  5=cobblestone · 6=gravel · 7=ground (dirt) · 8=sand · **9=END** (stretch ends,
  road back to normal). 0 = unspecified (a bare SURFACE tap or a timed-out picker).
  Surface is recorded as **stretches, not points** (see below).

CLOSURE, DANGER, SCENERY, SURFACE and RESUPPLY are two-tap: they repaint the field with a follow-up
page rather than tagging immediately. Both codes land on the *same* record. Picking
a subitem doesn't commit at once — it's **held for 3 s** and the chosen tile stays
lit; picking a different subitem in that window replaces it (only the last choice
is ever written, so a corrected mistake leaves no trace in the FIT). When the 3 s
lapse the tag is committed, the device beeps, and it drops back to the grid — so
the fix lands a few seconds past the sign. BACK during the window aborts with no
tag. With no pick at all, DANGER, CLOSURE, SCENERY and SURFACE still tag on the 12 s picker timeout
(as UNKNOWN / unspecified), since the point is worth recording even without the
qualifier; RESUPPLY drops, because a resupply with no kind says nothing.

## Surface stretches
A rough surface is a *length* of road, not a point, so surface is recorded as
**segments**. The picker has the 8 types plus **END**:

- Pick a type (e.g. COBBLES) → the stretch **starts** here.
- Pick a different type (GRAVEL) → the previous stretch **ends** here and the new
  one starts. It can be switched again or ended.
- Pick **END** → the stretch ends and the road is back to normal (untagged). You
  only ever mark the road that deviates.

The device stays dumb: it just logs these transition points (`poi_type=6` with the
type or `9`=END in `poi_detail`), exactly like any other tag — no new field, no
per-second writing. `buildSurfaceSegments()` in the parser joins consecutive
surface tags into runs: a type opens, a switch closes-and-opens, END closes, and
an unterminated stretch is closed at ride end and flagged. The FIT viewer draws
each stretch as a coloured line along the ridden track with its length. An
accidental start is undone by ENDing it immediately (a near-zero stretch) — surface
has no double-tap undo, because a second surface tag is a transition, not a retract.

## Radar: automatic vehicle counting
If a Varia-compatible radar is paired, every record also carries what the radar
saw that second. The device logs observations only — counting distinct vehicles is
the parser's job, for the same reason undo is (see [Undo](#undo)).

    radar_count   uint8  targets tracked this second (0..8)
    radar_near    uint8  nearest target, metres
    radar_speed   uint8  closing speed of nearest target, kph (~11 kph steps)

`getRadarInfo()` always returns 8 slots; an unoccupied one is a `RadarTarget`
with `threat == THREAT_LEVEL_NO_THREAT` and `range`/`speed` of 0, **not** a
`null`. Only slots above `NO_THREAT` are counted.

**`null` (FIT invalid, 255) means the radar was not tracking — NOT an empty
road.** This distinction is the whole ballgame for a crowd-sourced dataset: a
rider with no radar must not contribute fake empty roads. The device only writes
real values when `getDeviceState() == DEVICE_STATE_TRACKING`; everything else
stays invalid. `countVehicles()` reports `coverage` so you can weight a ride by
how much of it the radar was actually awake for.

**Counting rule:** sum every *increase* in simultaneous targets, then confirm it
a second later (below). A falling count is the same car finishing its pass, not a
new one. The radar exposes no target ids, so this is inference, and it is a
**floor**: two cars swapping inside one second read as one, and a radar dropout
mid-pass reads as a second arrival.

### How a car gets counted

The head unit and the parser use the **same rule**, so the number on your screen
at the end of a ride matches what the parser reports for that ride. This section
explains it in full.

**The problem.** The radar tells you *how many* cars it can see right now. It does
not tell you *which* ones. So if the number goes from 1 to 2, you have to guess
that a new car arrived — and sometimes the radar reports a car for a single second
that was never there at all. Count those and every ride is inflated with cars that
do not exist.

**The rule: count and speed when the car finishes a pass.**

When targets appear, we do not count yet. When the count **falls**:

- If it was in view ≥2 seconds, had a valid closing speed, got within **10 m**
  at some point in the stretch, and the previous nearest range was **≤20 m**,
  credit those cars and show that ground speed — same tick (last second before
  overtake).
- If it vanished farther out (turn-away, mid-range dropout), throw it away —
  rear radar has no target ids, so closeness is how we tell a pass from a
  disappear. After any leave, remaining cars start a fresh closeness history
  so a departed car’s approach is not reused.

A real overtake closes to a few metres before the target drops. A click-away in
the simulator at 50–80 m must not move the tally.

**Why not just require the same number twice?** That was the first attempt, and it
loses cars in a queue. Picture three cars overtaking in a line, where the third
one arrives at the same moment the first one disappears ahead of you. The count
goes 1, 2, 3, 2, 1 — it touches 3 for only one second, so a "see it twice" rule
never confirms the third car and you are shown 2. It is always the last car of the
group that goes missing. Checking that *something* is still there instead of
checking for the same number fixes this, because the drop from 3 to 2 is another
car, not an empty road.

**Speed is recorded at the end of the pass — with the count.** While a car
approaches, the strip does not change. When the target leaves, ground speed is
taken from the previous second and the tally increments on that same update.

The radar reports *closing* speed — the car's speed minus yours — so the strip
adds your own ground speed back to show the car's actual speed, and follows the
device's unit setting (kph or mph). The reading is coarse either way: the ANT+
radar quantises closing speed to ~3 m/s steps (≈11 kph / ~7 mph), so the strip
carries a **±5 kph** (±3 mph) notice and the figure should be read as a ballpark,
not a clocked speed. The FIT field is unaffected.

**What it costs.**

- The tally is one second behind. A car that just arrived is not on the display
  yet.
- A car seen for only one single second is never counted. A radar normally tracks
  a car for eight seconds or more, so this is rare, but a car appearing and
  vanishing in one second is impossible to tell apart from a false reading, and
  guessing wrong in that direction is worse.
- A false blip that happens *while* a real car is being tracked will be counted,
  because a car genuinely is still there on the next reading. This is the trade
  made to keep queues of cars accurate.
- If the radar disconnects mid-pass, a waiting arrival is dropped rather than
  counted when it reconnects — "is anything still there" means nothing across a
  gap of unknown length.
- An arrival in the very last second of a ride is dropped by the parser, because
  nothing follows it to confirm.

**None of this touches the FIT.** The file stays a plain second-by-second log of
what the radar reported. The counting happens afterwards — on the screen while
you ride, and again in the parser when the file is read. That means the rule can
be changed later and applied to rides already recorded, and it can be fixed
without reflashing the device. If you change it, change it in **both** places
(the platform’s live radar mirror — e.g. `writeRadar()` in
`Garmin/source/ScoutView.mc` — and `countVehicles()` in
[`Garmin/tools/fit-viewer.html`](../Garmin/tools/fit-viewer.html)) or the screen
and the parser will disagree.

**It only sees behind you.** The count is *vehicles that overtook you* — not
oncoming, not crossing. It is not road traffic volume, and it's roughly one
direction only.

`radar_speed` in the **last second before the car leaves** is the interesting
figure for a pass: being overtaken at +15 kph is a different event from +60 kph.
Arrival-time speed is noisier and often incomplete.

## Undo
Tap a tile twice within the cancel window and the tag is retracted. The device
does nothing about this — it queues both taps and writes both to the FIT. The
*parser* cancels the pair. Tags of any other type never interact, at any
spacing, so burst-tagging one spot with two categories is unaffected.

The window is **3 s** for every tile (direct and two-tap). `undoMsFor()` on the device and
`undoWindowFor()` in the parser must stay in step.

**SURFACE is exempt** — a second surface tag is a segment *transition*, not a
retraction, so it never cancels. Surface mistakes are fixed in the pick window
(before commit) or by ENDing the stretch. See [Surface stretches](#surface-stretches).

This is deliberate:

- the first tap keeps its **exact** position (nothing is held back waiting to
  see if an undo arrives),
- the rule can change later without reflashing the device,
- the FIT stays a faithful log of what the rider actually did.

The cost: a consumer that doesn't implement the rule sees both tags instead of
neither. `applyUndoRule()` in [`Garmin/tools/fit-viewer.html`](../Garmin/tools/fit-viewer.html)
is the reference implementation — port it wherever the FIT is ingested.

### Live UI tallies (all ports)

The on-bike / in-app counters are a **display mirror** of the undo rule above —
they do not change the FIT. Every Scout port must show them the same way
([SPEC §6.9](SPEC.md#69-per-tile-tallies)):

- **Main grid:** per-`poi_type` counts; RESUPPLY folder = sum of WATER+FOOD+MECHANICAL;
  DANGER = sum of all hazard commits; CLOSURE = sum of all duration commits;
  SCENERY = sum of all kind commits; SURFACE = stretch starts only.
- **Notice submenu:** per-`poi_detail` counts (`POTHOLES`…`UNKNOWN`). Same
  code-overlap caveat — separate danger detail buckets.
- **Duration submenu:** per-`poi_detail` counts (`TODAY`…`UNKNOWN`). Duration codes
  numerically overlap `poi_type` — keep a **separate** detail bucket array.
- **Scenery submenu:** per-`poi_detail` counts (`NATURE`…`UNKNOWN`). Same
  code-overlap caveat — separate scenery detail buckets.
- **Resupply submenu:** per-leaf `poi_type` counts on WATER / FOOD / REPAIR.
- **Surface submenu:** per-`poi_detail` counts (`ASPHALT`…`SAND` and `END`). Same
  code-overlap caveat — separate surface detail buckets. Grid SURFACE total still
  counts stretch **starts** only (not END). No double-tap undo → leaf counts only
  rise until stop.
- **Open stretch:** while last surface commit was `ASPHALT`…`SAND`, show that type
  as active until `END` / unspecified / ride stop ([SPEC §7.1](SPEC.md#71-open-stretch-indicator-all-ports)).
- Undo of a DANGER, CLOSURE or SCENERY decrements the detail bucket of the **first** tap in the pair.
- Reset tallies when the ride stops; do not tally while idle/paused.

It also means the **FIFO queue is load-bearing**. One tap per `compute()` is
drained onto its own record; a single pending slot would collapse a fast
double-tap into one record and the undo would silently do nothing.

Consequence worth knowing: two *genuine* same-type tags inside the window (3 s is
~20 m at 25 km/h; 6 s ~40 m) cancel each other. They'd be near-duplicates anyway.

Exact closure dates are deliberately **not** entered on the bike. The rider
usually can't know the end date anyway, and two date pickers at speed is a
bad trade; the coarse duration plus the record timestamp is enough to hydrate a
real date range later in the web UI.

## Reading it back
Parse the FIT with the Garmin FIT SDK (or `FitCSVTool.jar`) and keep every
`record` where the developer field `poi_type` is non-zero:

    record.timestamp, record.position_lat, record.position_long,
    poi_type, poi_detail

Then map the codes to your own categories. For `poi_type == 5`, the
closure start is `record.timestamp` and `poi_detail` gives the coarse end; the
rider confirms real dates in the web UI afterwards.

### What it looks like decoded
GPX you can just read; a FIT is binary, so here is the same thing decoded. The
five developer fields are **declared once** near the top of the file, then ride
on **every** `record` as five extra bytes after the standard position/speed:

    field_description  dev_index=0  field=0  base=uint8  name="poi_type"
    field_description  dev_index=0  field=1  base=uint8  name="poi_detail"
    field_description  dev_index=0  field=2  base=uint8  name="radar_count"
    field_description  dev_index=0  field=3  base=uint8  name="radar_near"
    field_description  dev_index=0  field=4  base=uint8  name="radar_speed"

Then one `record` per second. `position_lat`/`long` are int32 **semicircles** on
disk (shown as degrees here); a uint8 dev field of **255** is FIT-invalid — no
tag / radar not tracking. A handful of seconds from a real ride:

    timestamp   lat       lon      poi_type poi_detail  count near speed  meaning
    1000000004  52.0016   4.0024      0        0         255  255  255    untagged second — still written, poi_type 0
    1000000005  52.0020   4.0030      1        1         255  255  255    DANGER + detail 1 = POTHOLES
    1000000006  52.0024   4.0036      0        0          1    40   28    no tag; radar picks up a car (1 target, 40 m, +28 kph)
    1000000007  52.0028   4.0042      0        0          1    25   30    same car still tracked → parser counts it
    1000000008  52.0032   4.0048      0        0          0   255  255    count back to 0; near/speed invalid again
    1000000013  52.0052   4.0078      3        0         255  255  255    WATER — a RESUPPLY leaf: its own poi_type, detail 0
    1000000022  52.0088   4.0132      5        1         255  255  255    CLOSURE + poi_detail 1 = TODAY (type & qualifier, one record)
    1000000024  52.0096   4.0144      6        5         255  255  255    SURFACE + detail 5 = cobbles — stretch STARTS here
    1000000027  52.0108   4.0162      6        6         255  255  255    SURFACE + detail 6 = gravel — cobbles ends, gravel starts
    1000000033  52.0132   4.0198      6        9         255  255  255    SURFACE + detail 9 = END — road back to normal
    1000000057  52.0228   4.0342      1        0         255  255  255    DANGER …
    1000000058  52.0232   4.0348      1        0         255  255  255    … again 1 s later → parser cancels the pair (undo)

Note how the three picker patterns look on disk: CLOSURE and SURFACE are one
`poi_type` carrying a `poi_detail` qualifier, while the RESUPPLY leaf (WATER) is
just its own `poi_type` with detail 0. This is the exact output of
`Garmin/tools/make-test-fit.mjs`, so `node Garmin/tools/make-test-fit.mjs out.fit`
writes the file these rows came from — drop it on the viewer to see them plotted.

## Checking a FIT file (the viewer + tests)
Open [`Garmin/tools/fit-viewer.html`](../Garmin/tools/fit-viewer.html) in any
browser (just double-click it) and drop a `.fit` file on it. No install, no
server, and the file never leaves your machine — it's parsed in the page. It shows:

- file header + **CRC check** (catches truncated/corrupt files),
- every developer field the recording device actually declared,
- a **map** of the ride: the GPS track with every tag plotted on it in one view,
  colour-coded by type (surface stretches drawn as coloured lines along the track;
  no basemap and no network — a route-shape plot, hover for detail),
- a row per tagged record: time, decoded type/detail names, lat/lon, map link.

The two failure modes it's built to tell apart: *no developer fields at all*
(the field wasn't on an active data screen / writer never declared them) versus
*fields declared but poi_type is 0 everywhere* (nothing tapped, or tapped while
the timer was paused).

`Garmin/tools/test-fit-parser.mjs` exercises the parser (extracted from the
marker block in the HTML, so it's the shipping code). It's **self-contained** —
it builds a full binary FIT in memory covering every option and asserts the whole
pipeline (parse → tags → surface segments → vehicle count → CRC/bad input), so it
needs no Garmin SDK:

    node Garmin/tools/test-fit-parser.mjs Garmin/tools/fit-viewer.html

Pass a real FIT as an optional third argument to also smoke-test against it (the
SDK sample, or your own ride). To get a real `.fit` covering every option to open
in the viewer, generate one with `Garmin/tools/make-test-fit.mjs`:

    node Garmin/tools/make-test-fit.mjs out.fit    # then drop out.fit on the viewer

## Platform deltas

None yet. If a port cannot emit FIT developer fields byte-for-byte, document the
mapping under that platform and link it from this section.

## FIT gotchas for integrators
- **The tags travel inside the raw `.FIT`.** Any path that hands you the
  *original* `.FIT` has them: a USB copy, Garmin Connect's **Export Original**,
  Intervals.icu's original-file download, or an equivalent export from another
  Scout port. Those bytes are all an ingester needs. (On Garmin Connect IQ,
  publishing only changes whether Connect *draws* the fields as labelled charts
  — cosmetic, not the data.) A useful consequence: a full ingest pipeline can be
  tested against a **sideloaded** Garmin build, before publishing.
- **Only the `.FIT` carries developer fields — re-encoded exports drop them.**
  Garmin Connect's **TCX/GPX** exports and the **Strava** copy (Strava's API
  doesn't expose CIQ developer fields) all lose the tags. Ingest the original
  `.FIT`, never a converted copy.
- Granularity is ~1 s (smart recording); fine for POIs. Two taps in the same
  second collapse to the last one.
