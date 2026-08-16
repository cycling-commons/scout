import Toybox.Activity;
import Toybox.AntPlus;
import Toybox.Attention;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;
import Toybox.FitContributor;

// Category codes written into the FIT "poi_type" record field.
// 0 = no tag on this record; parse for records where poi_type != 0.
// Codes 1..4 predate the DANGER rename and the RESUPPLY grouping and are
// unchanged, so old rides still parse against this table.
enum {
    POI_NONE       = 0,
    POI_DANGER     = 1,   // any hazard: bad corner, junction, loose dog
    POI_SCENERY    = 2,   // nice view / photo spot
    POI_WATER      = 3,   // water / refill point
    POI_OTHER      = 4,   // anything else
    POI_CLOSURE    = 5,   // road shut / detour needed
    POI_SURFACE    = 6,   // potholes, broken tarmac, unexpected gravel
    POI_FOOD       = 7,   // cafe, shop, bakery (legacy writer — use POI_RESUPPLY)
    POI_MECHANICAL = 8,   // bike shop, public pump, tools (legacy writer)
    POI_RESUPPLY   = 9    // resupply point; kind in poi_detail (RESUP_*)
}

// How long a closure is expected to last, written into "poi_detail".
// Only meaningful on records where poi_type == POI_CLOSURE; 0 everywhere else.
// Deliberately coarse: exact dates get attached later in the web UI, where the
// rider has a keyboard and the sign photo.
enum {
    DUR_NONE    = 0,
    DUR_TODAY   = 1,
    DUR_DAYS    = 2,
    DUR_WEEKS   = 3,
    DUR_MONTHS  = 4,
    DUR_UNKNOWN = 5
}

// Surface type, written into "poi_detail" too. Only meaningful on records where
// poi_type == POI_SURFACE; shares the byte with DUR_* the same way (the reader
// keys off poi_type, so the ranges may overlap). Ordered smooth -> rough, and
// aligned to OSM surface= values: asphalt, concrete, paving_stones (klinkers),
// sett, cobblestone, gravel, ground, sand. SURF_NONE is an unspecified surface
// point — what a timed-out picker or a bare SURFACE tap records.
//
// A rough stretch is a *segment*, not a point: a type tag marks where it starts
// (or switches), SURF_END marks where it ends and the road is back to normal.
// The device just logs these transition points; the parser joins them into runs.
enum {
    SURF_NONE     = 0,
    SURF_ASPHALT  = 1,
    SURF_CONCRETE = 2,
    SURF_PAVING   = 3,   // klinkers / paving_stones
    SURF_SETT     = 4,
    SURF_COBBLES  = 5,
    SURF_GRAVEL   = 6,
    SURF_DIRT     = 7,
    SURF_SAND     = 8,
    SURF_END      = 9    // stretch ends here; road back to normal (untagged)
}

// Scenery kind, written into "poi_detail" when poi_type == POI_SCENERY. Shares
// the byte with DUR_* / SURF_* the same way (the reader keys off poi_type).
// SCEN_NONE is legacy (pre-picker rides) or a timed-out picker with no pick.
enum {
    SCEN_NONE    = 0,
    SCEN_NATURE  = 1,
    SCEN_HISTORY = 2,
    SCEN_CULTURE = 3,
    SCEN_VIEW    = 4,
    SCEN_ARCH    = 5,   // architecture
    SCEN_UNKNOWN = 6
}

// Hazard kind, written into "poi_detail" when poi_type == POI_DANGER. Shares the
// byte with the other detail enums the same way (the reader keys off poi_type).
// DANG_NONE is legacy (pre-picker rides) or a timed-out picker with no pick.
enum {
    DANG_NONE     = 0,
    DANG_POTHOLES = 1,
    DANG_CROSSING = 2,
    DANG_CORNER   = 3,
    DANG_OTHER    = 4,
    DANG_UNKNOWN  = 5
}

// Resupply kind, written into "poi_detail" when poi_type == POI_RESUPPLY. Shares the
// byte with the other detail enums the same way (the reader keys off poi_type).
enum {
    RESUP_NONE       = 0,
    RESUP_WATER      = 1,
    RESUP_FOOD       = 2,
    RESUP_MECHANICAL = 3
}

// Tiles that only steer the UI. Never written to the FIT, so they sit at the
// top of the byte range, clear of every real code above.
enum {
    UI_RESUPPLY = 254,
    UI_BACK     = 255
}

enum {
    MODE_GRID     = 0,
    MODE_DURATION = 1,
    MODE_RESUPPLY = 2,
    MODE_SURFACE  = 3,
    MODE_SCENERY  = 4,
    MODE_NOTICE   = 5
}

class ScoutView extends WatchUi.DataField {

    hidden const TYPE_FIELD_ID   = 0;   // MUST match fit_contributions.xml
    hidden const DETAIL_FIELD_ID = 1;   // MUST match fit_contributions.xml
    hidden const RADAR_COUNT_ID  = 2;
    hidden const RADAR_NEAR_ID   = 3;
    hidden const RADAR_SPEED_ID  = 4;

    // uint8 FIT invalid. Written when no radar is tracking, so a rider with no
    // Varia yields null rather than 0 — "no data" and "empty road" must never
    // collapse into the same value or the traffic dataset is worthless.
    hidden const RADAR_NA = 255;

    // Screenshot/demo aid ONLY: the simulator cannot emulate a real
    // Toybox.AntPlus.BikeRadar (long-standing Connect IQ limitation — no ANT+
    // accessory profile support in-sim), so there's no other way to show the
    // "N cars" strip live for a store screenshot. Flip to true locally, capture
    // the shot, flip back to false before committing — MUST be false at release;
    // double-check before running the release build (see docs/PUBLISHING.md).
    // Requires SHOW_RADAR_STRIP = true as well, or the strip has nowhere to draw.
    hidden const DEMO_RADAR = false;

    // Optional on-device strip: ride car tally and last-pass ground speed after a
    // car has already gone by (not a live approaching warning). Radar is always
    // polled and written to FIT; this only controls whether the strip is drawn.
    // Default false; set true for a personal sideload if you want the readout.
    hidden const SHOW_RADAR_STRIP = true;

    hidden const FLASH_MS = 1500;       // confirmation flash duration
    hidden const PICK_MS  = 12000;      // sub-page gives up (no pick) after this
    hidden const CORRECT_MS = 3000;     // after a subitem pick, window to re-pick
    hidden const UNDO_MS  = 3000;       // display only; MUST match the parser rule
    hidden const QUEUE_MAX = 16;        // backstop; a rider cannot outrun this
    hidden const COLS = 2;

    hidden var _typeField as FitContributor.Field?;
    hidden var _detailField as FitContributor.Field?;
    hidden var _radarCountField as FitContributor.Field?;
    hidden var _radarNearField as FitContributor.Field?;
    hidden var _radarSpeedField as FitContributor.Field?;

    // Polled once per compute(); no listener, so nothing async to reason about.
    hidden var _radar as AntPlus.BikeRadar?;
    hidden var _carCount as Number = 0;      // display only, mirrors the parser
    hidden var _prevCount as Number = 0;     // raw target count from the previous second
    hidden var _heldClosing as Number = -1;  // closing kph while targets present
    hidden var _heldRange as Number = -1;    // nearest range (m) previous sample
    hidden var _minRange as Number = 10000;  // closest nearest-range in stretch
    hidden var _stretchLen as Number = 0;    // consecutive occupied seconds

    // FIFO of [type, detail], one drained per compute(). A single slot would
    // collapse two taps in the same second into one record — which would also
    // silently break undo, since the parser cancels a pair of same-type tags
    // and can only see a pair if both reach the FIT.
    hidden var _queue as Array<Array> = [];

    // On-screen tally only, per poi_type (index = POI code 1..8; slot 0 unused).
    // The FIT stays a raw log of what was tapped; this mirrors the parser's undo
    // rule so the per-tile number the rider sees matches what the ride yields.
    hidden var _counts as Array<Number> = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    hidden var _lastTapType as Number = POI_NONE;
    hidden var _lastTapAt as Number = 0;

    // Index into _buttons of the tile to flash, not a code: a leaf chosen on a
    // sub-page (WATER, say) has no tile of its own, so it flashes its parent.
    hidden var _flashIdx as Number = -1;
    hidden var _flashUntil as Number = 0;

    hidden var _mode as Number = MODE_GRID;
    hidden var _parentIdx as Number = -1;   // tile that opened the sub-page
    hidden var _pickUntil as Number = 0;

    // A subitem pick isn't committed at once: it's held here so another pick can
    // replace it, and compute() commits it (type + detail) when CORRECT_MS lapses.
    // _pendingType == POI_NONE means "nothing chosen yet on this page".
    hidden var _pendingType as Number = POI_NONE;
    hidden var _pendingDetail as Number = DUR_NONE;
    hidden var _pendingIdx as Number = -1;     // picker tile to highlight
    hidden var _pendingUntil as Number = 0;

    hidden var _w as Number = 0;
    hidden var _h as Number = 0;
    hidden var _stripH as Number = 0;   // one-line strip height (surface banner or radar)
    hidden var _openSurfaceDetail as Number = SURF_NONE;  // §7.1 open-stretch indicator

    hidden var _radarLive as Boolean = false;  // radar actually TRACKING
    hidden var _radarSearching as Boolean = false;  // channel open, waiting for a Varia
    hidden var _lastCarSpeed as Number = -1;   // kph, car ground speed, most recent car
    hidden var _riderKph as Number = 0;        // rider ground speed this compute, kph
    hidden var _imperial as Boolean = false;   // display mph, following device settings
    hidden var _timerState as Number = Activity.TIMER_STATE_OFF;

    // Each row is [code as Number, label as String, RGB as Number], laid out
    // left-to-right, top-to-bottom over a COLS-wide grid.
    hidden var _buttons as Array<Array> = [
        [UI_RESUPPLY,  "RESUPPLY", 0x1E7FC0],
        [POI_CLOSURE,  "CLOSURE",  0x8E44AD],
        [POI_SURFACE,  "SURFACE",  0x8E5A2B],
        [POI_DANGER,   "NOTICE",   0xD1421F],
        [POI_SCENERY,  "SCENERY",  0x2E8B57],
        [POI_OTHER,    "OTHER",    0xB58900]
    ];

    // Second-level pages. A data field may not pushView(), but it owns every
    // pixel of its own area, so these are repaints rather than new views.
    hidden var _durButtons as Array<Array> = [
        [DUR_TODAY,   "TODAY",   0x2E8B57],
        [DUR_DAYS,    "DAYS",    0x1E7FC0],
        [DUR_WEEKS,   "WEEKS",   0xB58900],
        [DUR_MONTHS,  "MONTHS",  0xD1421F],
        [DUR_UNKNOWN, "UNKNOWN", 0x777777],
        [UI_BACK,     "BACK",    0x444444]
    ];

    hidden var _resButtons as Array<Array> = [
        [RESUP_WATER,      "WATER",  0x1E7FC0],
        [RESUP_FOOD,       "FOOD",   0xE67E22],
        [RESUP_MECHANICAL, "REPAIR", 0x7F8C8D],
        [UI_BACK,          "BACK",   0x444444]
    ];

    // Surface picker, smooth -> rough. First element is a SURF_* detail code, not
    // a POI code: every leaf tags POI_SURFACE and carries its type in poi_detail.
    // Greys for the paved surfaces, browns/tan for the unpaved ones. END (green,
    // "back to normal") closes the current stretch; it shares the last row with
    // BACK, keeping a clean 5x2 grid that still fits the smallest screen.
    hidden var _surfButtons as Array<Array> = [
        [SURF_ASPHALT,  "ASPHALT",  0x555555],
        [SURF_CONCRETE, "CONCRETE", 0x8A8A8A],
        [SURF_PAVING,   "PAVING",   0xC0392B],
        [SURF_SETT,     "SETT",     0x9B7653],
        [SURF_COBBLES,  "COBBLES",  0x6E4B3A],
        [SURF_GRAVEL,   "GRAVEL",   0xB58900],
        [SURF_DIRT,     "DIRT",     0x8E5A2B],
        [SURF_SAND,     "SAND",     0xD2B48C],
        [SURF_END,      "END",      0x2E8B57],
        [UI_BACK,       "BACK",     0x444444]
    ];

    hidden var _scenButtons as Array<Array> = [
        [SCEN_NATURE,  "NATURE",  0x2E8B57],
        [SCEN_HISTORY, "HISTORY", 0x8E44AD],
        [SCEN_CULTURE, "CULTURE", 0xE67E22],
        [SCEN_VIEW,    "VIEW",    0x1E7FC0],
        [SCEN_ARCH,    "ARCHITECT", 0xB58900],
        [SCEN_UNKNOWN, "UNKNOWN", 0x777777],
        [UI_BACK,      "BACK",    0x444444]
    ];

    hidden var _noticeButtons as Array<Array> = [
        [DANG_POTHOLES, "POTHOLES",  0xD1421F],
        [DANG_CROSSING, "CROSSING",  0xE67E22],
        [DANG_CORNER,   "CORNER",    0xB58900],
        [DANG_OTHER,    "OTHER",     0x8E44AD],
        [UI_BACK,       "BACK",      0x444444]
    ];

    function initialize() {
        DataField.initialize();
        // createField() must run here (in initialize), never in compute().
        _typeField = createField(
            "poi_type",
            TYPE_FIELD_ID,
            FitContributor.DATA_TYPE_UINT8,
            { :mesgType => FitContributor.MESG_TYPE_RECORD }
        );
        _detailField = createField(
            "poi_detail",
            DETAIL_FIELD_ID,
            FitContributor.DATA_TYPE_UINT8,
            { :mesgType => FitContributor.MESG_TYPE_RECORD }
        );
        _radarCountField = createField(
            "radar_count", RADAR_COUNT_ID, FitContributor.DATA_TYPE_UINT8,
            { :mesgType => FitContributor.MESG_TYPE_RECORD, :units => "vehicles" }
        );
        _radarNearField = createField(
            "radar_near", RADAR_NEAR_ID, FitContributor.DATA_TYPE_UINT8,
            { :mesgType => FitContributor.MESG_TYPE_RECORD, :units => "m" }
        );
        _radarSpeedField = createField(
            "radar_speed", RADAR_SPEED_ID, FitContributor.DATA_TYPE_UINT8,
            { :mesgType => FitContributor.MESG_TYPE_RECORD, :units => "kph" }
        );

        _typeField.setData(POI_NONE);
        _detailField.setData(DUR_NONE);
        _radarCountField.setData(RADAR_NA);
        _radarNearField.setData(RADAR_NA);
        _radarSpeedField.setData(RADAR_NA);

        // First search only. A Connect IQ BikeRadar that misses the Varia goes
        // DEAD and will not hunt again — tap "no radar" to open a new channel
        // rather than reconstructing every second.
        openRadar();
    }

    // Drop any existing channel and start a fresh ANT+ search. Throws if the
    // Ant permission is missing or the device has no radar support; swallow
    // that so the tagger still runs without a Varia.
    hidden function openRadar() as Void {
        _radar = null;
        _radarLive = false;
        _radarSearching = false;
        try {
            _radar = new AntPlus.BikeRadar(null);   // null listener => poll only
            _radarSearching = true;
        } catch (ex instanceof Lang.Exception) {
            _radar = null;
        }
    }

    // Called ~once per second. Drains one queued tag onto exactly one record,
    // then falls back to 0 so only tagged records carry a value.
    function compute(info as Activity.Info) as Void {
        watchTimer(info);
        if (_mode != MODE_GRID) {
            var now = System.getTimer();
            if (_pendingType != POI_NONE) {
                // A subitem is chosen. Once the re-pick window lapses, commit it —
                // tag() beeps, tallies, and drops back to the grid.
                if (now > _pendingUntil) {
                    var pt = _pendingType;
                    var pd = _pendingDetail;
                    var pi = _parentIdx;
                    clearPending();
                    tag(pt, pd, pi);
                    WatchUi.requestUpdate();
                }
            } else if (now > _pickUntil) {
                // No pick made in time.
                if (_mode == MODE_DURATION) {
                    // The closure itself is worth recording even with no duration.
                    tag(POI_CLOSURE, DUR_UNKNOWN, _parentIdx);
                } else if (_mode == MODE_SURFACE) {
                    // The surface point is worth recording even without a type, and
                    // this keeps the old muscle memory: tap SURFACE, ignore the
                    // picker, still get a tag.
                    tag(POI_SURFACE, SURF_NONE, _parentIdx);
                } else if (_mode == MODE_SCENERY) {
                    // The spot is worth recording even without a kind.
                    tag(POI_SCENERY, SCEN_UNKNOWN, _parentIdx);
                } else if (_mode == MODE_NOTICE) {
                    // The hazard is worth recording even without a kind.
                    tag(POI_DANGER, DANG_UNKNOWN, _parentIdx);
                } else {
                    // A resupply with no kind says nothing, so drop it.
                    closePage();
                }
                WatchUi.requestUpdate();
            }
        }

        var type = POI_NONE;
        var detail = DUR_NONE;
        if (_queue.size() > 0) {
            var item = _queue[0] as Array;
            _queue = _queue.slice(1, null);
            type = item[0] as Number;
            detail = item[1] as Number;
        }
        if (_typeField != null) {
            _typeField.setData(type);
        }
        if (_detailField != null) {
            _detailField.setData(detail);
        }
        // Rider ground speed, added to the radar's closing reading so the strip
        // shows the car's actual speed rather than closing speed. Display only —
        // the FIT field still logs raw closing speed for the parser to interpret.
        if (info != null && info.currentSpeed != null) {
            _riderKph = (info.currentSpeed * 3.6).toNumber();
        } else {
            _riderKph = 0;
        }
        writeRadar();
    }

    // Reset live tallies when a ride ends or a fresh one starts. The data field
    // outlives individual activities, so initialize() is not enough — mirror the
    // Android resetRide() / controller.stop() behaviour. Pause/resume keeps the
    // running tally (ON after PAUSED must not clear).
    hidden function watchTimer(info as Activity.Info) as Void {
        if (info == null || info.timerState == null) { return; }
        var next = info.timerState as Number;
        var prev = _timerState;
        if (next != prev) {
            if (next == Activity.TIMER_STATE_ON &&
                (prev == Activity.TIMER_STATE_OFF || prev == Activity.TIMER_STATE_STOPPED)) {
                resetRideSession();
            } else if (prev == Activity.TIMER_STATE_ON &&
                       (next == Activity.TIMER_STATE_STOPPED || next == Activity.TIMER_STATE_OFF)) {
                resetRideSession();
            }
            _timerState = next;
        }
    }

    hidden function resetRideSession() as Void {
        _carCount = 0;
        _lastCarSpeed = -1;
        _prevCount = 0;
        _heldClosing = -1;
        _heldRange = -1;
        _minRange = 10000;
        _stretchLen = 0;
        for (var i = 0; i < _counts.size(); i++) {
            _counts[i] = 0;
        }
        _lastTapType = POI_NONE;
        _lastTapAt = 0;
        _openSurfaceDetail = SURF_NONE;
        _queue = [];
        closePage();
    }

    // Bottom strip: open-surface banner while a stretch is active; otherwise the
    // optional radar tally (SHOW_RADAR_STRIP). Surface wins when both apply.
    hidden function stripVisible() as Boolean {
        return (_openSurfaceDetail != SURF_NONE) || SHOW_RADAR_STRIP;
    }

    hidden function effectiveGridH() as Number {
        if (!stripVisible() || _stripH <= 0) { return _h; }
        return _h - _stripH;
    }

    hidden function surfaceLabel(detail as Number) as String {
        for (var i = 0; i < _surfButtons.size(); i++) {
            var b = _surfButtons[i] as Array;
            if ((b[0] as Number) == detail) { return b[1] as String; }
        }
        return "SURFACE";
    }

    // One-tap END for the open stretch (strip banner shortcut, §7.1).
    hidden function endOpenSurface() as Void {
        if (_openSurfaceDetail == SURF_NONE) { return; }
        var idx = -1;
        for (var i = 0; i < _buttons.size(); i++) {
            if ((_buttons[i][0] as Number) == POI_SURFACE) { idx = i; break; }
        }
        if (idx < 0) { idx = 0; }
        tag(POI_SURFACE, SURF_END, idx);
    }

    // Logs what the radar sees this second and nothing more. Identifying which
    // observations are the same vehicle is the parser's job — the radar gives
    // no target ids, so tracking is inference either way, and inference belongs
    // where it can be fixed without reflashing.
    hidden function writeRadar() as Void {
        if (DEMO_RADAR) {
            // Canned reading — enough to populate the on-screen strip for a
            // screenshot. Deliberately does NOT touch the FIT dev fields below,
            // so a demo build still records honest (invalid) radar data if
            // someone forgets to flip DEMO_RADAR back and records a real ride.
            _radarLive = true;
            _radarSearching = false;
            _carCount = 3;
            _lastCarSpeed = 42;
            return;
        }

        var count = RADAR_NA;
        var near = RADAR_NA;
        var speed = RADAR_NA;

        if (_radar != null) {
            var st = _radar.getDeviceState();
            // Only TRACKING means "radar is live". SEARCHING/CLOSED/DEAD all
            // leave the fields invalid rather than claiming an empty road.
            if (st != null && st.state == AntPlus.DEVICE_STATE_TRACKING) {
                count = 0;
                var speedNow = -1;
                var targets = _radar.getRadarInfo();
                if (targets != null) {
                    var nearRange = 1.0e9;
                    var nearSpeed = 0.0;
                    for (var i = 0; i < targets.size(); i++) {
                        var t = targets[i] as AntPlus.RadarTarget?;
                        // getRadarInfo() hands back a fixed 8-slot array; an
                        // unoccupied slot is a RadarTarget with NO_THREAT and
                        // range/speed 0, not a null. Threat level is the only
                        // occupancy flag, so skip anything at NO_THREAT.
                        if (t == null || t.threat == AntPlus.THREAT_LEVEL_NO_THREAT) {
                            continue;
                        }
                        count++;
                        if (t.range < nearRange) {
                            nearRange = t.range;
                            nearSpeed = t.speed;
                        }
                    }
                    if (count > 0) {
                        near = clampByte(nearRange.toNumber());
                        speed = clampByte((nearSpeed * 3.6).toNumber());
                        speedNow = speed;
                    }
                }

                // Count + speed on leave only when the nearest target got within
                // 10 m during the stretch and the last reading was ≤20 m. A car
                // that turns away farther out must not count — and must not leave
                // its closeness on the next car still behind.
                if (_prevCount > 0 && count < _prevCount && _heldClosing >= 0 &&
                    _stretchLen >= 2 && _minRange <= 10 &&
                    _heldRange >= 0 && _heldRange <= 20) {
                    _carCount += (_prevCount - count);
                    _lastCarSpeed = _heldClosing + _riderKph;
                }

                if (count == 0) {
                    _heldClosing = -1;
                    _heldRange = -1;
                    _minRange = 10000;
                    _stretchLen = 0;
                } else {
                    _stretchLen = (_prevCount == 0) ? 1 : (_stretchLen + 1);
                    if (speedNow >= 0) {
                        _heldClosing = speedNow;
                    }
                    if (near != RADAR_NA) {
                        _heldRange = near;
                        if (count < _prevCount) {
                            _minRange = near; // fresh nearest after departure
                        } else if (near < _minRange) {
                            _minRange = near;
                        }
                    }
                }

                _prevCount = count;
                _radarLive = true;
                _radarSearching = false;
            } else {
                // Dropping out mid-pass discards the pending arrival rather than
                // crediting it on reconnect, where "still a target present" would
                // be meaningless across the gap. Do not reconstruct here — a
                // DEAD channel stays dead until the rider taps "no radar".
                _prevCount = 0;
                _heldClosing = -1;
                _heldRange = -1;
                _minRange = 10000;
                _stretchLen = 0;
                _radarLive = false;
                _radarSearching = (st != null && st.state == AntPlus.DEVICE_STATE_SEARCHING);
            }
        } else {
            _radarLive = false;
            _radarSearching = false;
        }

        if (_radarCountField != null) { _radarCountField.setData(count); }
        if (_radarNearField != null) { _radarNearField.setData(near); }
        if (_radarSpeedField != null) { _radarSpeedField.setData(speed); }
    }

    // 255 is the uint8 invalid marker, so real readings must stop at 254.
    hidden function clampByte(v as Number) as Number {
        if (v < 0) { return 0; }
        if (v > 254) { return 254; }
        return v;
    }

    function onLayout(dc as Dc) as Void {
        _w = dc.getWidth();
        _h = dc.getHeight();
        // Font plus padding so the surface END / no-radar strip is a usable
        // tap target. Drawn only while a surface stretch is open or
        // SHOW_RADAR_STRIP is on.
        _stripH = dc.getFontHeight(Graphics.FONT_MEDIUM) + 24;
        // The strip speed follows the rider's unit setting; the logged FIT field
        // stays kph either way, so only this readout switches. Read once here —
        // the setting doesn't change mid-ride, and onLayout re-runs on wake.
        var ds = System.getDeviceSettings();
        _imperial = (ds != null && ds.distanceUnits == System.UNIT_STATUTE);
    }

    hidden function currentSet() as Array<Array> {
        if (_mode == MODE_DURATION) { return _durButtons; }
        if (_mode == MODE_RESUPPLY) { return _resButtons; }
        if (_mode == MODE_SURFACE)  { return _surfButtons; }
        if (_mode == MODE_SCENERY)  { return _scenButtons; }
        if (_mode == MODE_NOTICE)   { return _noticeButtons; }
        return _buttons;
    }

    hidden function rowsOf(set as Array<Array>) as Number {
        return (set.size() + COLS - 1) / COLS;
    }

    // Commits a tag: queues it, tallies it, confirms, and returns to the grid.
    // Point tags are still written raw — a cancelling double-tap writes both, and
    // the parser drops the pair (see countTap / applyUndoRule). Keeping the undo
    // rule in the parser means it can change without reflashing the device.
    //
    // Type and detail go onto the same record, so a closure is one row to
    // parse; the cost is that the fix is the one from when the rider finished
    // choosing, a few seconds past the sign. That beats splitting a closure
    // across two records.
    hidden function tag(type as Number, detail as Number, flashIdx as Number) as Void {
        if (_queue.size() < QUEUE_MAX) {
            _queue.add([type, detail]);
        }
        var undone = countTap(type, detail);
        confirmTap(undone);
        _flashIdx = flashIdx;
        // A new point tag stays lit for the whole undo window, so the colour is a
        // live "tap again to cancel" cue — not gone while the option lingers. A
        // cancel, or a surface tag (no double-tap undo), is a brief confirmation.
        var lit = (undone || type == POI_SURFACE) ? FLASH_MS : undoMsFor(type);
        _flashUntil = System.getTimer() + lit;
        closePage();
    }

    // Eyes-on-road confirmation that a tap landed: a short buzz, or a tone on a
    // device with no vibrator (like the Edge), whichever it has and the rider
    // hasn't muted. One signal is enough. A cancelling double-tap gets a distinct
    // sound so an undo is audible as an undo, not a new tag. Best-effort — wrapped
    // so a device that refuses either in a data-field context still tags cleanly.
    hidden function confirmTap(undone as Boolean) as Void {
        var ds = System.getDeviceSettings();
        if ((Attention has :vibrate) && (ds has :vibrateOn) && ds.vibrateOn) {
            try {
                var vibe = undone
                    ? [new Attention.VibeProfile(75, 90), new Attention.VibeProfile(0, 60),
                       new Attention.VibeProfile(75, 90)]   // double pulse = undo
                    : [new Attention.VibeProfile(60, 120)];
                Attention.vibrate(vibe);
            } catch (ex) { }
        }
        else if ((Attention has :playTone) && (ds has :tonesOn) && ds.tonesOn) {
            try {
                Attention.playTone(undone ? Attention.TONE_RESET : Attention.TONE_KEY);
            } catch (ex) { }
        }
    }

    // Mirrors the parser: two taps of one type inside UNDO_MS annihilate. Display
    // only — both taps still go to the FIT. Counts are per poi_type so each tile
    // can show its own tally. Returns true when this tap cancelled a pair (an
    // undo), so the caller can sound it differently.
    // SURFACE is exempt — it's a segment channel, not double-tap undo (see countTap).
    // MUST stay in step with undoWindowFor() in the parser.
    hidden function undoMsFor(type as Number) as Number {
        return UNDO_MS;
    }

    // Whether this tag counts toward its tile's tally. Every point type counts;
    // for SURFACE only a segment *start* (a real surface type) does, so the tile
    // shows stretches marked, not the ENDs that close them.
    hidden function tileCounts(type as Number, detail as Number) as Boolean {
        return (type != POI_SURFACE) || (detail >= SURF_ASPHALT && detail <= SURF_SAND);
    }

    // Mirrors the parser: two taps of one type inside the window annihilate.
    // Display only — both taps still go to the FIT. SURFACE is exempt: a second
    // surface tag is a segment *transition*, not a retraction, so it never cancels
    // (mistakes are fixed in the pick window, or by ENDing the stretch). Returns
    // true when this tap cancelled a pair, so the caller can sound it differently.
    hidden function countTap(type as Number, detail as Number) as Boolean {
        var now = System.getTimer();
        var undone = false;
        if (type != POI_SURFACE && type == _lastTapType && (now - _lastTapAt) < undoMsFor(type)) {
            _counts[type]--;
            if (_counts[type] < 0) { _counts[type] = 0; }
            _lastTapType = POI_NONE;    // pair consumed; a third tap is new
            undone = true;
        } else {
            if (tileCounts(type, detail)) { _counts[type]++; }
            if (type == POI_SURFACE) {
                if (detail >= SURF_ASPHALT && detail <= SURF_SAND) {
                    _openSurfaceDetail = detail;
                } else if (detail == SURF_END || detail == SURF_NONE) {
                    _openSurfaceDetail = SURF_NONE;
                }
            }
            _lastTapType = type;
        }
        _lastTapAt = now;
        return undone;
    }

    // The tally to print on a grid tile. Most tiles map straight to their poi_type;
    // RESUPPLY is a group, so it sums its leaves (water + food + repair).
    hidden function tileCount(code as Number) as Number {
        if (code == UI_RESUPPLY) {
            return _counts[POI_RESUPPLY];
        }
        if (code >= 1 && code < _counts.size()) {
            return _counts[code];
        }
        return 0;
    }

    hidden function openPage(mode as Number, parentIdx as Number) as Void {
        _mode = mode;
        _parentIdx = parentIdx;
        _pickUntil = System.getTimer() + PICK_MS;
    }

    hidden function closePage() as Void {
        _mode = MODE_GRID;
        _parentIdx = -1;
        _pickUntil = 0;
        clearPending();
    }

    hidden function clearPending() as Void {
        _pendingType = POI_NONE;
        _pendingIdx = -1;
    }

    // Hold a subitem pick instead of committing it: a later pick on the same page
    // replaces it, BACK drops it, and compute() commits it once CORRECT_MS passes.
    hidden function holdPick(type as Number, detail as Number, idx as Number) as Void {
        _pendingType = type;
        _pendingDetail = detail;
        _pendingIdx = idx;
        _pendingUntil = System.getTimer() + CORRECT_MS;
    }

    // Hit-tests against the grid, not the full field. The live radar tally is a
    // readout, so a tap there still hits the tile above; surface END and a
    // "no radar" retry are intercepted in onScreenTap before this runs.
    hidden function cellAt(x as Number, y as Number, rows as Number) as Number {
        var col = (x < _w / 2) ? 0 : 1;
        var gridH = effectiveGridH();
        var h = (gridH > 0) ? gridH : _h;
        var row = y * rows / h;
        if (row < 0) { row = 0; }
        if (row > rows - 1) { row = rows - 1; }
        return row * COLS + col;
    }

    // Called by the delegate on a tap. Full-screen field is assumed, so screen
    // coords == field coords.
    function onScreenTap(x as Number, y as Number) as Void {
        if (_w <= 0 || _h <= 0) { return; }
        if (y >= effectiveGridH()) {
            if (_openSurfaceDetail != SURF_NONE) {
                endOpenSurface();
                WatchUi.requestUpdate();
                return;
            }
            // Dead/searching strip: open a new ANT+ channel. A BikeRadar that
            // missed the Varia will not resume searching on its own.
            if (SHOW_RADAR_STRIP && !_radarLive) {
                openRadar();
                confirmTap(false);
                WatchUi.requestUpdate();
                return;
            }
        }
        var set = currentSet();
        var i = cellAt(x, y, rowsOf(set));
        if (i >= set.size()) { return; }    // odd-sized page: trailing gap
        var code = set[i][0] as Number;

        if (_mode == MODE_GRID) {
            if (code == POI_DANGER) {
                openPage(MODE_NOTICE, i);       // ask which hazard before tagging
            } else if (code == POI_CLOSURE) {
                openPage(MODE_DURATION, i);     // ask how long before tagging
            } else if (code == POI_SURFACE) {
                openPage(MODE_SURFACE, i);      // ask which surface before tagging
            } else if (code == UI_RESUPPLY) {
                openPage(MODE_RESUPPLY, i);     // ask which kind; tags nothing
            } else if (code == POI_SCENERY) {
                openPage(MODE_SCENERY, i);      // ask which kind before tagging
            } else {
                tag(code, DUR_NONE, i);
            }
        } else if (code == UI_BACK) {
            closePage();
        } else if (_mode == MODE_DURATION) {
            holdPick(POI_CLOSURE, code, i);   // a wrong duration is re-pickable for CORRECT_MS
        } else if (_mode == MODE_SURFACE) {
            holdPick(POI_SURFACE, code, i);   // a wrong surface is re-pickable for CORRECT_MS
        } else if (_mode == MODE_SCENERY) {
            holdPick(POI_SCENERY, code, i);   // a wrong kind is re-pickable for CORRECT_MS
        } else if (_mode == MODE_NOTICE) {
            holdPick(POI_DANGER, code, i);    // a wrong kind is re-pickable for CORRECT_MS
        } else if (_mode == MODE_RESUPPLY) {
            holdPick(POI_RESUPPLY, code, i);  // a wrong kind is re-pickable for CORRECT_MS
        } else {
            holdPick(code, DUR_NONE, i);
        }
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Dc) as Void {
        var bg = getBackgroundColor();
        var fg = (bg == Graphics.COLOR_BLACK) ? Graphics.COLOR_WHITE : Graphics.COLOR_BLACK;
        dc.setColor(Graphics.COLOR_TRANSPARENT, bg);
        dc.clear();

        var set = currentSet();
        var gridH = effectiveGridH();
        var cw = _w / COLS;
        var ch = ((gridH > 0) ? gridH : _h) / rowsOf(set);
        var flashing = (_mode == MODE_GRID) && (System.getTimer() < _flashUntil);

        for (var i = 0; i < set.size(); i++) {
            var b     = set[i] as Array;
            var label = b[1] as String;
            var rgb   = b[2] as Number;
            var bx = (i % COLS) * cw;
            var by = (i / COLS) * ch;

            // Fill a tile when it's the grid tag just placed, the subitem held
            // for correction in a picker, or the SURFACE tile while a stretch is
            // open (§7.1 — mirrors the Android open-stretch indicator).
            var code = b[0] as Number;
            var openHere = (_mode == MODE_GRID) && (code == POI_SURFACE) &&
                           (_openSurfaceDetail != SURF_NONE);
            var pendingHere = (_mode != MODE_GRID) && (_pendingType != POI_NONE) && (_pendingIdx == i);
            if ((flashing && _flashIdx == i) || pendingHere || openHere) {
                dc.setColor(rgb, Graphics.COLOR_TRANSPARENT);
                dc.fillRectangle(bx + 2, by + 2, cw - 4, ch - 4);
                dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            } else {
                dc.setColor(rgb, Graphics.COLOR_TRANSPARENT);
                dc.setPenWidth(3);
                dc.drawRectangle(bx + 2, by + 2, cw - 4, ch - 4);
                dc.setColor(fg, Graphics.COLOR_TRANSPARENT);
            }
            // On the grid, each tile shows its running tally ("NOTICE 3"); the
            // count is dropped once it hits 0 so an untouched tile stays clean.
            var text = label;
            if (_mode == MODE_GRID) {
                if (code == POI_SURFACE && _openSurfaceDetail != SURF_NONE) {
                    text = surfaceLabel(_openSurfaceDetail);
                } else {
                    var n = tileCount(code);
                    if (n > 0) { text = label + " " + n.toString(); }
                }
            }
            dc.drawText(bx + cw / 2, by + ch / 2, Graphics.FONT_SMALL, text,
                        Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }

        // Surface tiles (ASPHALT, COBBLES, …) name themselves, so that page needs
        // no header; the other pickers keep a prompt.
        if (_mode == MODE_DURATION || _mode == MODE_RESUPPLY || _mode == MODE_SCENERY ||
            _mode == MODE_NOTICE) {
            var title = (_mode == MODE_DURATION) ? "CLOSED FOR?" :
                        (_mode == MODE_SCENERY) ? "SCENERY?" :
                        (_mode == MODE_NOTICE) ? "NOTICE?" : "WHAT KIND?";
            dc.setColor(fg, Graphics.COLOR_TRANSPARENT);
            dc.drawText(_w / 2, 2, Graphics.FONT_TINY, title,
                        Graphics.TEXT_JUSTIFY_CENTER);
        }

        drawBottomStrip(dc, fg, bg);

        // Recording indicator: red = timer running (taps will land in the FIT),
        // grey = paused/stopped (taps are shown but nothing is being recorded).
        var rec = false;
        var info = Activity.getActivityInfo();
        if (info != null && info.timerState != null) {
            rec = (info.timerState == Activity.TIMER_STATE_ON);
        }
        dc.setColor(rec ? Graphics.COLOR_RED : Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(_w - 12, 12, 5);
    }

    // Bottom strip: open-surface banner (tappable → END) or optional radar tally.
    hidden function drawBottomStrip(dc as Dc, fg as Number, bg as Number) as Void {
        if (!stripVisible() || _stripH <= 0) { return; }
        if (_openSurfaceDetail != SURF_NONE) {
            drawSurfaceStrip(dc, fg, bg);
        } else if (SHOW_RADAR_STRIP) {
            drawRadarStrip(dc, fg, bg);
        }
    }

    hidden function drawSurfaceStrip(dc as Dc, fg as Number, bg as Number) as Void {
        var gridH = effectiveGridH();
        var y = gridH + (_stripH / 2);
        var txt = "rec. surface tap to END";

        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(1);
        dc.drawLine(2, gridH, _w - 2, gridH);

        dc.setColor(0x8E5A2B, bg);   // surface tile brown — stands out from radar grey
        dc.drawText(_w / 2, y, stripFont(dc, txt), txt,
                    Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // Optional radar strip (SHOW_RADAR_STRIP): ride tally and last-pass ground speed —
    // shown after a car has already gone by, for checking the count against the
    // FIT later. Counting and FIT writes always run in writeRadar(). "no radar"
    // is shown deliberately rather than a zero — the same distinction the FIT
    // makes, since a disconnected Varia and an empty road must never look alike.
    // Tap it (or "searching...") to start a new ANT+ search.
    hidden function drawRadarStrip(dc as Dc, fg as Number, bg as Number) as Void {
        if (!SHOW_RADAR_STRIP || _stripH <= 0) { return; }
        var gridH = effectiveGridH();
        var y = gridH + (_stripH / 2);

        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(1);
        dc.drawLine(2, gridH, _w - 2, gridH);

        var txt;
        if (!_radarLive) {
            dc.setColor(Graphics.COLOR_DK_GRAY, bg);
            txt = _radarSearching ? "searching..." : "no radar";
        } else {
            dc.setColor(fg, bg);
            txt = _carCount.toString() + " cars";
            if (_lastCarSpeed >= 0) {
                // ±tolerance flags the coarse radar reading — closing speed
                // arrives in ~11 kph (~7 mph) buckets, so the ground-speed figure
                // is a ballpark. _lastCarSpeed is kph; convert only for display.
                if (_imperial) {
                    var mph = (_lastCarSpeed * 0.621371).toNumber();
                    txt += "   " + mph.toString() + " ±3 mph";
                } else {
                    txt += "   " + _lastCarSpeed.toString() + " ±5 kph";
                }
            }
        }
        dc.drawText(_w / 2, y, stripFont(dc, txt), txt,
                    Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // Biggest font this string still fits in, largest first. The strip has to be
    // readable at arm's length on a bar-mounted head unit, but the text grows
    // with the tally ("142 cars   88 kph"), so a single hard-coded size either
    // clips on a narrow screen or wastes a wide one.
    hidden function stripFont(dc as Dc, txt as String) as Graphics.FontDefinition {
        var fonts = [Graphics.FONT_MEDIUM, Graphics.FONT_SMALL, Graphics.FONT_TINY];
        for (var i = 0; i < fonts.size(); i++) {
            var f = fonts[i] as Graphics.FontDefinition;
            if (dc.getTextWidthInPixels(txt, f) <= _w - 8) { return f; }
        }
        return Graphics.FONT_XTINY;
    }
}
