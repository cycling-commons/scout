# Contributing to Scout (Garmin)

Thanks for helping out. This folder is the Garmin Connect IQ data field. The shared
reference FIT parser (browser viewer and Node test suite) lives at the repo root in
`tools/`. Shared product and data contracts:
[SPEC](../docs/SPEC.md) · [DATA-FORMAT](../docs/DATA-FORMAT.md).

## Ground rules

- **Keep the device dumb.** The head unit only *logs raw taps* — one `poi_type`
  (and `poi_detail`) per record, plus the raw radar channels. All interpretation
  (undo, surface segments, vehicle counting) lives in the parser, so it can be
  fixed and re-applied to rides already recorded without reflashing anyone's
  device. If you're adding "smarts", they almost certainly belong in the parser.
- **The device and the parser must agree.** Anything the device mirrors for live
  display (the undo rule, the tallies) has a reference implementation in
  `../tools/fit-viewer.html`. Change both, and add a test.
- **The FIT is a contract.** `poi_type` / `poi_detail` / `SURF_*` / `DUR_*` codes
  are append-only — old rides must keep parsing. Add new codes; don't renumber.
  Spec changes belong in the root docs, not only here.

## Parser changes (no Garmin SDK required)

The parser lives between the `// ===PARSER-START===` / `===PARSER-END===` markers
in [`../tools/fit-viewer.html`](../tools/fit-viewer.html). The test suite extracts that
exact block and exercises it, so what's tested is what ships.

```sh
# Self-contained — builds a full binary FIT in-memory and parses it. No SDK needed.
node ../tools/test-fit-parser.mjs ../tools/fit-viewer.html

# Optionally also smoke-test against a real SDK sample (3rd arg):
node ../tools/test-fit-parser.mjs ../tools/fit-viewer.html path/to/sample.FIT
```

CI runs the first form on every push (see `.github/workflows/ci.yml`).

- Add a test for any parser change. The end-to-end fixture is
  [`../tools/make-test-fit.mjs`](../tools/make-test-fit.mjs) — it writes a real binary
  `.fit` covering every option (all POI types, closure durations, a surface
  segment sequence, an undo pair, two radar passes). Extend its `SCENARIO` and
  assert in section `[2]` of the test.
- Want a real file to open in the viewer? `node ../tools/make-test-fit.mjs out.fit`,
  then drop `out.fit` on `../tools/fit-viewer.html`. (`.fit` files are git-ignored —
  never commit a real ride; it contains a GPS track.)

## Device (Connect IQ) changes

Needs the [Connect IQ SDK](https://developer.garmin.com/connect-iq/sdk/) and a
**developer key** — generate one (Monkey C extension: *Generate a Developer Key*,
or `openssl genrsa`/`pkcs8` per Garmin's docs) and keep it **outside** the repo
(it's git-ignored). The key signs your local builds; you don't need Garmin's app
`id` to build a fork.

Build and typecheck at **level 2** (level 3 has never been clean on this project —
the tile `Array<Array>` literals trip it; that's expected, not a regression):

```sh
monkeyc -f monkey.jungle -d edge1030plus -o bin/Scout.prg -y <your-key> -l 2
```

Test on the **simulator** (`connectiq` / `monkeydo`) and, ideally, a real touch
Edge. To sideload: copy `Scout.prg` to `GARMIN/Apps/` over USB, then on the unit
pick a ride profile → Data Screens → add a page → **single field** → Connect IQ
Fields → **Scout**, kept as the *only* field on that page (the tap hit-test assumes
the field fills the screen).

### Connect IQ gotchas
- **A data field may not `pushView()`.** A probe build calling `WatchUi.pushView()`
  from `onScreenTap()` throws on the Edge 1030 Plus (SDK 4.2.0b2) *and* the Edge
  1050 / CIQ 6.0.0 (SDK 9.2.0, re-tested 2026-07): the pushed view never renders,
  while the tap itself is delivered and the tag still records. Garmin relaxed the
  *input* half of this restriction since 2023, but not this half. So: no menus, no
  pickers, no second screen — the follow-up pages are this field repainting its own
  area in a different mode, the only way to get a multi-step flow mid-ride.
- **`onTap` works on touch data-field Edge units** — the whole basis of Scout —
  but there's no runtime check for it: on a button-only unit the tiles silently do
  nothing. Keep `<iq:products>` **touch-only** and only add devices you've actually
  tested (button units like the 540 won't receive `onTap`).
- `AppBase.getSettingsView()` gives a real menu flow (API 3.2+), but it's only
  reachable by stopping and entering the data field's settings — no good for
  tagging while riding.

## Pull requests

- One focused change per PR; describe the rider-facing effect.
- Green CI (parser tests) is required.
- For device changes, note which device(s)/SDK you tested on.
- By contributing you agree your work is licensed under the project's
  [MIT License](LICENSE).
