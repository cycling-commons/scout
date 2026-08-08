# Scout — Product Spec

Version: **1.0** (parity with Garmin Connect IQ Scout v1.0.0)  
Status: **normative for all ports** (Garmin, Android, Karoo, iPhone, …)

This document defines *what* Scout does and *how its data must behave*. It is
deliberately free of Connect IQ, Android, or iOS APIs. Platform ports implement
this contract; they do not invent parallel semantics.

The on-disk recording contract (FIT developer fields, codes, undo / surface /
vehicle rules) is **[DATA-FORMAT.md](DATA-FORMAT.md)**. Ports that cannot write
Garmin FIT must still emit an **equivalent record stream** that a conforming
parser can map 1:1 onto those fields.

**Where docs live:** shared specs stay in this `docs/` folder. Platform-only
deltas go under that platform’s folder, with a short mention here (or in
DATA-FORMAT) pointing at them.

---

## 1. Product summary

Scout is an in-ride companion for tagging road conditions and (optionally)
logging rear radar observations into the ride file.

- Tap coloured tiles while riding to stamp hazards, closures, surfaces,
  resupply, scenery, and other points onto the current GPS sample.
- With a compatible bike radar paired, every sample also carries what the radar
  saw that second. Distinct-vehicle counting is done by interpretation rules,
  not by inventing smarter on-device tracking.
- Built for the [Cycling Commons Atlas](https://cyclingcommons.org), but the app
  and its data format stand alone (MIT). No account, no analytics, no forced
  upload.

**Non-goals (v1 parity):** accounts, cloud sync inside the app, maps while
riding, editing past tags mid-ride, exact closure end-dates on the bike,
oncoming/crossing traffic, button-only / non-touch primary tagging UI.

---

## 2. Design principles

### 2.1 Keep the recorder dumb

The device (phone, head unit, …) **logs raw observations**:

| Channel | What is written |
| --- | --- |
| Tag | At most one `(poi_type, poi_detail)` per sample |
| Radar | Raw `(radar_count, radar_near, radar_speed)` per sample, or “not tracking” |

All interpretation lives in the **parser / ingest layer** (and may be mirrored
on-device for live display only):

- Double-tap undo → `applyUndoRule`
- Surface begin/switch/END → `buildSurfaceSegments`
- Vehicle arrivals → `countVehicles`

Rules can change later and be re-applied to already-recorded rides without
updating every client.

### 2.2 Device and parser must agree

Anything the UI shows as a live tally (grid undo counts, **picker/submenu
leaf counts**, car count, open-surface hint later) **must** use the same rule
as the reference parser in
[`Garmin/tools/fit-viewer.html`](../Garmin/tools/fit-viewer.html). Change both;
add a test.

### 2.3 Codes are append-only

`poi_type`, closure durations, surface types, and radar invalid markers are a
**stable contract**. Add new codes; never renumber or reuse.

### 2.4 Privacy

- Writes only to the rider’s own activity file (and local preferences such as
  paired radar id).
- No network required for core function.
- Upload / contribution to Cycling Commons (or anything else) is **opt-in**; the
  recorder never phones home while recording. Optional in-app sharing of a
  *finished* ride is specified in **[SHARING.md](SHARING.md)** (not yet required
  for v1 parity on every port).
- Radar is optional: tagging works with no radar.

---

## 3. Roles & system context

```
┌──────────────────────────────┐
│  Scout client (any platform) │
│  - GPS + timer               │
│  - Tag UI                    │
│  - Radar adapter             │
│  - Activity writer           │
└──────────────┬───────────────┘
               │ record stream (~1 Hz)
               ▼
┌──────────────────────────────┐
│  Activity file (FIT or equiv)│
│  poi_* + radar_* per sample  │
└──────────────┬───────────────┘
               │ after ride
               ▼
┌──────────────────────────────┐
│  Parser / viewer / Atlas     │
│  undo · surfaces · vehicles  │
└──────────────────────────────┘
```

Optional accessories:

- **Bike radar** (Garmin Varia–compatible or equivalent) via the radar transport
  (see §8).

---

## 4. Activity & sampling model

### 4.1 Recording states

| Timer state | Behaviour |
| --- | --- |
| Running | Samples are written; taps enqueue tags that drain onto samples |
| Paused / stopped (idle) | **No tagging simulation** — tiles must not flash, pickers must not open, nothing is written. UI shows a brief prompt above the controls (with **Start ride** / **Resume** action) if the rider taps a tile |

A **recording indicator** (e.g. green vs red dot) must reflect this so the rider
knows whether taps will land in the file.

### 4.2 Sample cadence

- Target **~1 sample per second** while recording (same granularity as the
  Garmin data field’s `compute()`).
- Each sample carries: timestamp, position (lat/lon when available), speed
  (when available), and the five Scout channels below.
- **FIFO tag queue** (capacity ≥ 16): at most **one** queued tag is drained onto
  each sample. A single pending slot is forbidden — a fast double-tap would
  collapse into one record and silently break undo.

### 4.3 Scout channels on every sample

| Field | Type | Meaning |
| --- | --- | --- |
| `poi_type` | uint8 | 0 = no tag; else category (§5) |
| `poi_detail` | uint8 | Qualifier keyed by `poi_type`; else 0 |
| `radar_count` | uint8 | Simultaneous targets this second, or invalid |
| `radar_near` | uint8 | Nearest target range in metres, or invalid |
| `radar_speed` | uint8 | Closing speed of nearest target in kph, or invalid |

**Invalid marker:** `255` (uint8 FIT invalid). Real radar readings clamp to
`0..254`. **`255` means “radar not tracking” — never invent `0` for that case.**
Empty road while tracking = `radar_count = 0` with near/speed invalid as
appropriate.

Canonical on-disk encoding for Garmin ecosystems: Connect IQ / FIT developer
fields on the `record` message (ids 0–4, names as above). Other platforms must
preserve the same semantics even if the container differs, and should prefer
writing original FIT when feasible so existing ingest tools keep working.

---

## 5. Tag taxonomy

### 5.1 `poi_type` (append-only)

| Code | Name | UI label | Grid behaviour |
| --- | --- | --- | --- |
| 0 | NONE | — | — |
| 1 | DANGER | NOTICE¹ | Opens notice picker |
| 2 | SCENERY | SCENERY | Opens scenery picker |
| 3 | WATER | WATER | Resupply leaf |
| 4 | OTHER | OTHER | Direct tag |
| 5 | CLOSURE | CLOSURE | Opens duration picker |
| 6 | SURFACE | SURFACE | Opens surface picker (segment channel) |
| 7 | FOOD | FOOD | Resupply leaf |
| 8 | MECHANICAL | REPAIR | Resupply leaf |

UI-only codes (never written): `254` = RESUPPLY folder, `255` = BACK.

¹ Connect IQ and Android both label type 1 **NOTICE**. FIT `poi_type` name stays `DANGER`
  (code 1).

### 5.2 Hazard kind (`poi_type == 1`) → `poi_detail`

| Code | Label |
| --- | --- |
| 0 | NONE / legacy (pre-picker rides) |
| 1 | POTHOLES |
| 2 | CROSSING |
| 3 | CORNER |
| 4 | OTHER (hazard; not `poi_type` OTHER) |
| 5 | UNKNOWN |

### 5.3 Closure duration (`poi_type == 5`) → `poi_detail`

| Code | Label |
| --- | --- |
| 0 | NONE (unused on a committed closure) |
| 1 | TODAY |
| 2 | DAYS |
| 3 | WEEKS |
| 4 | MONTHS |
| 5 | UNKNOWN |

Exact end dates are **not** entered on the bike; coarse duration + sample
timestamp hydrates a real range later in a web UI.

### 5.4 Surface type (`poi_type == 6`) → `poi_detail`

Aligned to OSM `surface=` values, smooth → rough:

| Code | Label | OSM alignment |
| --- | --- | --- |
| 0 | NONE / unspecified | — |
| 1 | ASPHALT | asphalt |
| 2 | CONCRETE | concrete |
| 3 | PAVING | paving_stones |
| 4 | SETT | sett |
| 5 | COBBLES | cobblestone |
| 6 | GRAVEL | gravel |
| 7 | DIRT | ground |
| 8 | SAND | sand |
| 9 | END | stretch ends; road back to normal |

Surface is a **segment channel**, not a point channel (see §7).

### 5.5 Scenery kind (`poi_type == 2`) → `poi_detail`

| Code | Label |
| --- | --- |
| 0 | NONE / legacy (pre-picker rides) |
| 1 | NATURE |
| 2 | HISTORY |
| 3 | CULTURE |
| 4 | VIEW |
| 5 | ARCHITECT (architecture) |
| 6 | UNKNOWN |

### 5.6 Resupply encoding

RESUPPLY is a **menu folder**, not a written code. Leaves are distinct
`poi_type`s with `poi_detail = 0`:

- WATER = 3  
- FOOD = 7  
- MECHANICAL = 8  

---

## 6. Interaction model (normative timings)

Primary UI is a **full-screen (or largest practical) touch grid**. Hit testing uses
the grid area only. On Connect IQ, an optional bottom strip can show the ride’s
car tally and last-pass speed (`SHOW_RADAR_STRIP`); default off. Radar is always
counted and written to FIT either way. When the strip is shown, taps on it
resolve to the tile above (not a dead zone).

### 6.1 Main grid (2 columns)

```
┌───────────┬───────────┐
│ RESUPPLY │  CLOSURE  │
├───────────┼───────────┤
│ SURFACE  │  NOTICE   │
├───────────┼───────────┤
│  SCENERY │  OTHER    │
└───────────┴───────────┘
```

Tile colours (RGB, for visual parity):

| Tile | Colour |
| --- | --- |
| NOTICE | `#D1421F` |
| CLOSURE | `#8E44AD` |
| SURFACE | `#8E5A2B` |
| RESUPPLY | `#1E7FC0` |
| SCENERY | `#2E8B57` |
| OTHER | `#B58900` |
| BACK (pickers) | `#444444` |

### 6.2 Direct tags (OTHER)

1. Tap → enqueue `(type, detail=0)`, update tallies, haptic/tone confirm.
2. Tile stays lit for the **undo window** (3 s) as a “tap again to cancel” cue.
   Phone UIs may show the remaining seconds inside the lit tile.
3. Same type again inside the window → both taps are written; live tally
   decrements (parser will cancel the pair). Distinct undo feedback (double
   pulse / reset tone).

### 6.3 Two-tap flows (NOTICE, CLOSURE, SCENERY, SURFACE, RESUPPLY)

Opening a picker does **not** write a tag yet.

| Constant | Value | Role |
| --- | --- | --- |
| `PICK_MS` | 12 000 | Auto-timeout with no pick |
| `CORRECT_MS` | 3 000 | After a pick, window to re-pick |
| `FLASH_MS` | 1 500 | Brief flash for undo or surface commit |
| `UNDO_MS` | 3 000 | Base undo window (display + parser) |
| `QUEUE_MAX` | 16 | Tag FIFO backstop |

**Pick held, not committed:** choosing a subitem lights that tile and starts
`CORRECT_MS`. Another subitem replaces the pending choice (only the last is
ever written). When `CORRECT_MS` elapses → commit type+detail, beep, return to
grid. BACK during the window → abort, no tag. Phone UIs may show the remaining
seconds inside the lit subitem tile.

**Timeout with no pick:**

| Mode | On timeout |
| --- | --- |
| NOTICE | Commit `DANGER` + `UNKNOWN` |
| CLOSURE | Commit `CLOSURE` + `UNKNOWN` |
| SCENERY | Commit `SCENERY` + `UNKNOWN` |
| SURFACE | Commit `SURFACE` + `NONE` (unspecified point) |
| RESUPPLY | Drop; return to grid with no tag |

Picker titles: NOTICE → `NOTICE?`; CLOSURE →
`CLOSED FOR?`; SCENERY → `SCENERY?`; RESUPPLY → `WHAT KIND?`; SURFACE needs
no header (tiles name themselves).

### 6.4 Notice picker

`POTHOLES · CROSSING · CORNER · OTHER · BACK`

### 6.5 Duration picker

`TODAY · DAYS · WEEKS · MONTHS · UNKNOWN · BACK`

### 6.6 Scenery picker

`NATURE · HISTORY · CULTURE · VIEW · ARCHITECT · UNKNOWN · BACK`

### 6.7 Resupply picker

`WATER · FOOD · REPAIR · BACK`

### 6.8 Surface picker (5×2)

`ASPHALT · CONCRETE · PAVING · SETT · COBBLES · GRAVEL · DIRT · SAND · END · BACK`

### 6.9 Per-tile tallies

**Normative for every port** (Garmin, Android, Karoo, iOS, …). Live tallies are
display-only mirrors of the parser undo rule — they never change what is written
to the file. See also [DATA-FORMAT.md](DATA-FORMAT.md) (Undo).

**Main grid** (when count > 0; untouched tiles show no number):

- Shown as label + count (e.g. `NOTICE` with `3` beneath / beside on Garmin).
- Counts mirror parser undo: same type within undo window annihilates for
  display; both taps still go to the file.
- RESUPPLY folder tile shows the **sum** of WATER + FOOD + MECHANICAL.
- CLOSURE tile shows the **sum** of all committed closure durations.
- NOTICE tile shows the **sum** of all committed hazard kinds.
- SCENERY tile shows the **sum** of all committed scenery kinds.
- SURFACE tally counts only stretch **starts** (detail in `ASPHALT..SAND`), not
  END.

**Picker / submenu tallies** (same ride, same undo rules; shown when > 0):

| Picker | What each leaf shows |
| --- | --- |
| Notice (`NOTICE?`) | Per-**detail** count: how many times that kind (`POTHOLES` … `UNKNOWN`) was committed this ride. BACK never tallies. |
| Duration (`CLOSED FOR?`) | Per-**detail** count: how many times that duration (`TODAY` … `UNKNOWN`) was committed this ride. BACK never tallies. |
| Scenery (`SCENERY?`) | Per-**detail** count: how many times that kind (`NATURE` … `UNKNOWN`) was committed this ride. BACK never tallies. |
| Resupply (`WHAT KIND?`) | Per-leaf `poi_type` count (WATER / FOOD / MECHANICAL). BACK never tallies. |
| Surface | Per-**detail** count: how many times that surface leaf (`ASPHALT` … `SAND`, and `END`) was committed this ride. BACK never tallies. (Grid SURFACE total still counts **starts only**, not END.) |

Closure, notice, scenery, and surface detail buckets are independent of `poi_type` indices
(duration / hazard / scenery / surface codes overlap numeric `poi_type` values — implementations
**must not** index the `poi_type` count array by those detail codes to paint
picker leaves). On undo of a CLOSURE, NOTICE, or SCENERY, decrement both the grid total
and the **detail bucket of the first tap** in the undone pair (not necessarily the
second tap’s detail). SURFACE has no double-tap undo, so surface leaf counts
only ever increase until the ride stops.

Tallies reset when the ride stops (timer → idle). Idle/paused taps must not
change tallies.

### 6.10 Undo windows (must match parser)

| Tag class | Window |
| --- | --- |
| Direct (OTHER) | 3 s |
| Two-tap leaves (NOTICE, SCENERY, CLOSURE, WATER, FOOD, MECHANICAL) | 3 s |
| SURFACE | **Exempt** — second surface tag is a transition, never an undo |

### 6.11 Confirmation feedback

Eyes-on-road: short vibration if available and enabled; else tone if available
and enabled. Undo uses a distinct pattern. Failure to buzz/beep must not block
tagging.

**While not recording** (idle or paused): no flash, haptic, tone, or picker
navigation — only the idle prompt in §4.1. This avoids training the rider that
taps “work” when nothing is being written.

---

## 7. Surface stretches

Surface is recorded as **transition points**; the parser joins them into runs.

| Event | Written |
| --- | --- |
| Pick a type | `poi_type=6`, `poi_detail=type` → stretch starts (or previous ends and new starts) |
| Pick END | `poi_type=6`, `poi_detail=9` → stretch ends; road untagged |
| Timeout / unspecified | `poi_type=6`, `poi_detail=0` |

Parser (`buildSurfaceSegments`): type opens; switch closes-and-opens; END
closes; unterminated stretch closes at ride end and is flagged. Accidental
start → END immediately (near-zero stretch). No double-tap undo on surface.

### 7.1 Open-stretch indicator (all ports)

While a stretch is open (last committed surface detail in `ASPHALT..SAND`, not
yet `END`), the UI **must** remind the rider so the stretch is not left open by
mistake:

- Keep device state: current open detail (or “none”).
- Show it on the main tagging surface (e.g. strip / banner: `surface open:
  COBBLES`, and/or keep the SURFACE tile lit with the type name).
- The indicator **may be tappable** to commit `SURFACE` + `END` immediately
  (same as picking END in the picker) — recommended on phone UIs.
- Clear the indicator on `END`, on unspecified (`detail=0`) commit, and when the
  ride stops.
- Switching type updates the indicator to the new type (does not clear).

Recording indicator (dot): **green while the timer is RUNNING**, **red when
idle or paused** (not writing samples).

---

## 8. Radar

### 8.1 Purpose

Optional rear radar logging for crowd-sourced road-feel (overtaking volume and
closing speed). **Only vehicles behind / overtaking** — not oncoming or
crossing, not total road traffic volume.

### 8.2 Transport selection (normative for multi-platform)

Ports **must** obtain the same logical observations regardless of radio:

```
                    ┌─────────────────────┐
                    │  RadarSession API   │
                    │  state + targets[]  │
                    └──────────┬──────────┘
           ┌───────────────────┼───────────────────┐
           ▼                   ▼                   ▼
    Native ANT+          BLE bike radar      (future adapters)
    (if hardware         (if no native ANT+
     available)           or user prefers)
```

**Policy:**

1. If the platform/device has **native ANT+** (or a usable ANT+ adapter the
   product supports) **and** a compatible radar is available that way, use ANT+.
2. Otherwise use **Bluetooth LE** pairing/connection to a compatible radar
   (e.g. Varia models that expose BLE radar data).
3. Tagging must work if neither path is available (`radar_* = 255`).
4. The recorder never cares which transport was used — only the normalized
   `RadarSample` below.

Platforms may expose a settings UI to pick transport, forget a device, or
re-pair. Pairing UX is platform-specific; observation semantics are not.

### 8.3 Normalized radar model

**Device state** (map from transport-specific status):

| Logical state | Write radar fields? |
| --- | --- |
| TRACKING | Yes — real values (count may be 0) |
| Searching / connecting / closed / dead / absent | No — write invalid `255` |

**Targets** (up to 8 logical slots, matching ANT+ bike radar):

| Field | Meaning |
| --- | --- |
| occupied | Threat / presence above “no threat”; empty slots are not targets |
| range_m | Distance to target (metres) |
| speed_mps | **Closing** speed (target toward rider), not ground speed |

Occupied target count = number of occupied slots. Nearest = min `range_m` among
occupied; its closing speed becomes `radar_speed` (kph, clamped).

### 8.4 What is written vs what is displayed

**FIT / file (raw):**

- `radar_count` = occupied targets this second  
- `radar_near` = nearest range (m)  
- `radar_speed` = nearest **closing** speed (kph)  
- All `255` when not TRACKING  

**On-screen strip (derived, live):**

- Show `"no radar"` when not TRACKING (never show `0 cars` for that).
- When TRACKING: show corroborated vehicle tally + last car **ground** speed
  (closing + rider ground speed), unit-aware (kph / mph), with ±5 kph / ±3 mph
  tolerance note (radar quantisation ~3 m/s).
- Count and strip **speed** both commit when the target leaves, from the previous
  second’s closing reading (last second before pass; stretch ≥2 s). Not on arrival.

### 8.5 Vehicle counting rule (device mirror + parser)

Shared rule — reference: `countVehicles` / `writeRadar` in the Garmin tree.

1. Rising counts are ignored for the tally (cars still approaching).
2. When count **falls**, credit only if stretch ≥2 s, previous closing speed was
   valid, nearest got within **10 m** during the stretch, and previous nearest
   range was **≤20 m** (finishing a pass). Add `(prevCount - count)` and set
   ground speed from that previous closing reading. After any leave, reset
   min-range for remaining targets so closeness does not transfer.
3. A leave that never confirmed within 10 m (turn-away / dropout) or a
   one-second blip is discarded.
4. On dropout from TRACKING, clear stretch / held state — do not credit across a gap.
5. Parser drops cars still present at end of file (never left) and reports
   `coverage` = fraction of samples with valid radar.

**None of this mutates the file.** The file stays a second-by-second log; counting
is re-runnable interpretation.

### 8.6 Pairing requirements (BLE path)

When using Bluetooth:

- Discover / pair / reconnect to a user-selected radar peripheral.
- Persist identity of the preferred device across sessions.
- Request only the permissions needed for BLE scan/connect on that OS.
- Surface clear states: scanning, connecting, tracking, disconnected, no device.
- Losing BLE mid-ride → leave TRACKING → write `255`s; do not fabricate empty-road
  zeros.

When using native ANT+:

- Prefer the OS / stack’s standard bike-radar association if one exists.
- Same TRACKING vs not-TRACKING invalidation rules.

---

## 9. Live UI chrome (parity)

| Element | Spec |
| --- | --- |
| Recording dot | Top-right (or equivalent): **green** = timer running; **red** = idle/paused |
| Idle tile tap | Brief message above controls with **Start ride** / **Resume** action (§4.1); no tile flash or picker |
| Stop ride | **Confirm** before ending a recording session and saving the file |
| Radar strip | Bottom of tagging surface; separator line; auto-shrink font to fit |
| Strip copy | `"no radar"` \| `"{n} cars"` optional `"{speed} ±5 kph"` / mph |
| Open surface | While a stretch is open: visible reminder with type name (§7.1) |
| Grid layout | 2 columns; rows = ceil(nTiles / 2); tiles fill grid height above strip |
| Help | In-app rider help; instance-specific copy via a committed template + local override (Android: `help/help.example.json` → `help.json`) |
| Appearance | Light and dark themes; rider-selectable (direct sun vs dusk/OLED) on platforms that support it |
| Brand header | App mark + name grouped tightly; name in brand colour on ride screen |

Optional **share to Atlas** UI (when implemented) uses the instance URL from a
build-time env template (Android: `.env.example` → `.env.dev.local`); see
[SHARING.md](SHARING.md), [CUSTOMIZATION.md](CUSTOMIZATION.md), and
[ATLAS-SERVER.md](ATLAS-SERVER.md) for fork setup and backend requirements.

---

## 10. Data lifecycle & interoperability

1. During ride: append samples to the activity container.
2. After ride: rider owns the file on the device. Scout does not upload during
   or after recording unless the rider explicitly shares a finished file (see
   [SHARING.md](SHARING.md) when that feature is enabled on a port).
3. Inspect: reference viewer / parser (`fit-viewer.html` logic) or any tool that
   understands the Scout channels.
4. Contribute (optional): upload original file to a project that ingests Scout
   data (e.g. Cycling Commons). Prefer paths that preserve developer fields /
  Scout channels — re-encoded GPX/TCX/Strava copies typically **drop** them.

**Integrator rules** (unchanged from DATA-FORMAT):

- Consume original FIT (or documented equivalent), not stripped exports.
- Implement `applyUndoRule`, `buildSurfaceSegments`, `countVehicles`.
- Treat `radar_* == 255` as no coverage, not empty road.

---

## 11. Functional requirements checklist

### Must

- [ ] Full-screen (or dedicated) touch tagging UI with the six-tile grid and three
      pickers, timings, and colours in §6.
- [ ] ~1 Hz samples while recording; pause/stop writes nothing.
- [ ] FIFO tag queue; one tag per sample; queue capacity ≥ 16.
- [ ] Stable `poi_type` / `poi_detail` codes (§5).
- [ ] Surface as transitions + END; no surface double-tap undo.
- [ ] Double-tap undo semantics for point types; live tallies match parser
      (main grid **and** notice / duration / scenery / resupply / surface submenu leaves — §6.9).
- [ ] Open surface-stretch indicator while a type is active (§7.1).
- [ ] Haptic or tone confirmation; distinct undo feedback when possible.
- [ ] Recording indicator.
- [ ] Idle/paused: tile taps do not simulate tagging; rider sees start-first prompt (§4.1).
- [ ] Stop recording requires confirmation before saving.
- [ ] Optional radar via **ANT+ if available, else BLE** (§8); normalized samples;
      invalid `255` when not tracking.
- [ ] Live car tally + ground speed using the shared corroboration rule.
- [ ] Activity export readable by the reference Scout parser (FIT preferred).
- [ ] Works fully without radar.
- [ ] No mandatory network / account for recording.

### Should

- [ ] Persist preferred radar device and transport preference.
- [ ] Unit-aware speed display from system locale / settings.
- [ ] Foreground-friendly ride mode (screen reachable without deep menus).
- [ ] Prefer the lowest location / radio duty cycle that still meets §4 and §8
      while recording (see §12.1).

### Must not

- [ ] Write `0` radar count when radar is absent/disconnected.
- [ ] Collapse multiple taps into one sample.
- [ ] Renumber existing type/detail codes.
- [ ] Put undo / vehicle identity / surface joining solely on-device without a
      matching parser rule (display mirrors are OK; file stays raw).
- [ ] Keep high-rate GPS, radar scan, or screen wake active when not recording
      (paused/stopped/idle), except briefly for user-driven pairing.

---

## 12. Non-functional

| Area | Expectation |
| --- | --- |
| Eyes on road | Large hit targets; confirmation without looking |
| Reliability | Tagging survives radar failure; radar failure never corrupts tags |
| **Battery** | **As low as possible for a multi-hour ride** — see §12.1 |
| Localization | English labels acceptable for v1; strings should be externalizable |
| License | MIT, consistent with Garmin Scout |

### 12.1 Battery (normative)

Phone ports burn far more power than a Garmin data field. **Battery life is a
first-class requirement**, not a polish item. A typical tour/bikepacking ride
must remain practical on one charge alongside the rider’s normal phone use.

**Principles**

1. **Nothing expensive while idle.** Outside an active recording session: no
   high-accuracy GPS stream, no radar connection/scan loop, no sticky wake lock,
   no forced bright screen.
2. **Recording = pay only for what Scout needs.** While the timer is running:
   ~1 Hz samples (§4), optional radar if the user enabled it, screen behaviour
   the rider chose. No network, no analytics, no spare sensors.
3. **Paused / stopped = drop duty cycle immediately.** Stop or sharply throttle
   GPS; disconnect or stop scanning radar; release wake locks; allow normal
   display timeout unless the rider is actively in a pairing flow.
4. **Prefer connected sensors over scanning.** After pairing, maintain a
   connection and consume notifications. Do not continuous-scan for radars
   during a ride. Prefer **native ANT+ when available** (typically cheaper than
   BLE for this class of accessory); otherwise BLE (§8).
5. **Location: accurate enough, not maximum always.** Use the coarsest location
   mode that still yields usable tag positions and rider speed for the strip.
   Do not combine redundant providers “just in case.”
6. **I/O frugally.** Buffer FIT/activity writes; avoid flushing every sample to
   flash if the platform allows safe periodic flush + flush-on-pause/stop.
7. **Screen is optional burn.** Default to normal system brightness / timeout
   policies where safe; offer an explicit “keep screen on while recording”
   toggle (off by default or rider-controlled) rather than forcing it always.
8. **Radar is opt-in.** If no radar is paired/enabled, pay zero radio cost for
   it. Tagging alone must stay light.

**Acceptable trade**

Slightly coarser GPS when the OS batches fixes is fine; inventing positions or
skipping the ~1 Hz **Scout channel** cadence while recording is not. Prefer
dropping display frills before dropping recording correctness.

**Measure**

Phone ports should sanity-check multi-hour recording (with and without radar) on
a real device and treat regressions in idle drain or ride drain as bugs.

## 13. Platform notes

Shared contract only here. Platform implementation notes and any **deltas** live
in that platform’s folder; keep a one-line pointer below when a delta exists.

| Platform | Folder | Notes / deltas |
| --- | --- | --- |
| Garmin Connect IQ | [`Garmin/`](../Garmin/) | Reference implementation. Data field + FitContributor; ANT+ via `Toybox.AntPlus.BikeRadar`; touch Edge only; picker pages are field repaints (no `pushView`). Publishing: [`Garmin/docs/PUBLISHING.md`](../Garmin/docs/PUBLISHING.md). |
| Android phone | [`Android/`](../Android/) | Standalone ride app (**P0–P6**). Tech: [`Android/docs/TECHNICAL.md`](../Android/docs/TECHNICAL.md) · setup: [`Android/docs/SETUP.md`](../Android/docs/SETUP.md) · sharing contract: [SHARING.md](SHARING.md). Radar: ANT+ if present, else BLE (§8). Original FIT (SPEC §4.2 fields only). Battery: §12.1. UI: light/dark, help template, instance `.env` template, stop confirm, idle-tap guard (§9). Sharing to Atlas: specified, not yet shipped. **No on-disk delta** vs this SPEC / DATA-FORMAT for recording. |
| Hammerhead Karoo | [`Hammerhead-Karoo/`](../Hammerhead-Karoo/) | *(not started — no deltas yet)* |
| iPhone | [`iPhone/`](../iPhone/) | *(not started — no deltas yet)* |

### Shared test assets

Reuse / port:

- Code tables and timings from this spec
- Parser tests from [`Garmin/tools/test-fit-parser.mjs`](../Garmin/tools/test-fit-parser.mjs)
- Scenario FIT from [`Garmin/tools/make-test-fit.mjs`](../Garmin/tools/make-test-fit.mjs)

A port is “done” for v1 when its files pass the reference parser assertions for
tags, undo, surfaces, and radar coverage/counting.

---

## 14. Glossary

| Term | Meaning |
| --- | --- |
| Sample / record | One timed GPS (+ Scout channels) row ≈ 1 s |
| Direct tag | Commits on first tap |
| Two-tap / picker | Intermediate UI before commit |
| TRACKING | Radar session actively delivering target data |
| Closing speed | Relative speed of approach (car − bike), as reported by radar |
| Ground speed (display) | Closing + rider speed |
| Invalid / NA | uint8 `255` — no data, not zero |

---

## 15. Document control

| Item | Value |
| --- | --- |
| Spec version | 1.0 |
| Parity baseline | Garmin Scout 1.0.0 (2026-07-22) |
| Normative data format | [DATA-FORMAT.md](DATA-FORMAT.md) |
| Reference parser | [`Garmin/tools/fit-viewer.html`](../Garmin/tools/fit-viewer.html) (`===PARSER-*===` block) |

Changes that affect codes, timings, or interpretation rules require a spec
bump and coordinated parser/device updates.
