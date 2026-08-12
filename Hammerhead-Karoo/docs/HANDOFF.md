# Scout Karoo — handoff

Real-device validation checklist for the Karoo port. Debug APK path:

`Hammerhead-Karoo/app/build/outputs/apk/debug/app-debug.apk`

A **physical Karoo** is required (there is no Karoo simulator). Scout behaves like the Garmin
**data field**: Karoo owns start/stop; Scout records FIT and radar in the background
while you are on **any** ride page, as long as Scout is on the ride profile.

---

## Garmin vs Karoo (what to expect)

| Topic | Garmin Scout | Karoo Scout |
| --- | --- | --- |
| Start/stop | Karoo/Garmin ride controls it | Same — Karoo `RideState` |
| FIT + radar while on another screen | Yes (~1 Hz in data field service) | Yes — `ScoutExtension` + `fitFile="true"` |
| Tile tagging | Only when Scout data field page is visible | Full-screen **Tag ride** UI (BonusAction or app) |
| Open surface reminder | Bottom strip `rec. surface tap to END` on Scout field only | Same strip text on Scout field; Tag ride screen uses full banner |
| Ride-page widget | Data field on a profile page | **Scout** graphical data field (`scout-status`) |
| Radar pairing | Garmin sensor menu | Karoo **Settings → Sensors** (ANT+ radar) |

**Important:** Add the **Scout** data field to at least one page on your ride profile.
That enrolls the extension for the ride. You can swipe to other pages — FIT and radar
should still log. Open **Tag ride** only when you want to tap tiles.

---

## One-time setup on Karoo

1. **Install APK** — Companion sideload or `adb install -r app-debug.apk` ([SETUP.md](SETUP.md)).
2. **Extensions** — confirm Scout appears under Karoo extensions / installed apps.
3. **Ride profile** — edit a data page → add field **Scout** (graphical).
4. **Controller** — assign bonus action **Tag ride** to a hardware button (recommended).
5. **Radar (optional)** — pair Varia or compatible ANT+ radar in Karoo sensor settings.

---

## Test ride script (~15 min)

### A. Recording without tagging UI

1. Start a ride on Karoo (outdoor or indoor).
2. Stay on a page **without** Scout visible for 2–3 minutes.
3. If radar is paired, ride where cars pass (or simulate with radar on a stand).
4. Swipe to the **Scout** field page — check:
   - State shows **recording**
   - Tag count (likely 0)
   - Radar line updates if traffic present
5. End ride and sync/export FIT from Karoo as you normally would.

### B. Tagging

1. Start a new ride.
2. Press **Tag ride** (bonus action) or open Scout app → **Open tagging**.
3. Tap several tiles (e.g. surface, POI). Confirm haptic/tone on tag.
4. Test undo: tap same tile within undo window.
5. Return to map/data pages — tagging UI can close; ride keeps recording.
6. End ride.

### C. Multi-page (Garmin parity)

1. Ride with Scout field on page 1, speed/cadence on pages 2–4.
2. Tag only while **Tag ride** screen is open.
3. Spend most time on other pages.
4. After ride, verify FIT still has Scout samples on seconds when you were not on Scout page.

---

## FIT validation (on a PC)

1. Copy the activity `.fit` from Karoo (USB, Hammerhead dashboard, or third-party sync).
2. Open `tools/fit-viewer.html` from this repo in a browser.
3. Load the FIT file.
4. Confirm developer fields present each second while recording:
   - `poi_type`, `poi_detail`, `radar_count`, `radar_near`, `radar_speed`
5. Tagged seconds should show non-zero `poi_type` / `poi_detail` where expected.
6. `radar_speed` may be **255** on Karoo (no closing speed in stream) — that is expected.

---

## Pass / fail notes

| Check | Pass | Fail (report back) |
| --- | --- | --- |
| Scout field on ride page | Shows status/tags/radar | Blank, crash, or missing from field list |
| Ride start | Scout state → recording, no manual start | Stays idle |
| FIT fields | Present in fit-viewer while recording | Missing entirely |
| Background pages | FIT rows while on non-Scout pages | Gaps only when on other pages |
| Tag ride UI | Tiles respond, tallies increment | Crashes, no haptic, idle message while riding |
| Bonus action | Opens tagging screen | No action |
| Radar (if paired) | `radar_count` / `radar_near` vary | Always 0 / disconnected |

---

## Feedback to collect

- Karoo model (2 / 3) and OS version if visible in settings
- Whether Scout field was added to profile
- Short ride description (radar yes/no, tags placed)
- The `.fit` file (or screenshot from fit-viewer showing Scout columns)
- Any crash or “stuck idle” notes

---

## Known limitations (v0.1 beta)

- No in-app radar pairing — use Karoo sensors.
- Car **speed** on the strip may be hidden (`radar_speed` 255).
- Tagging is not on the small data field — use **Tag ride** full screen.
- Not validated on hardware yet (K4 in [TECHNICAL.md](TECHNICAL.md)).
