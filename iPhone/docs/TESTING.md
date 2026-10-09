# Scout — iPhone testing

Mirrors [Android/docs/TESTING.md](../../Android/docs/TESTING.md) where the
contract is shared. Domain and FIT tests are the source of truth for undo,
pickers, vehicles, and CRC.

## Unit tests

Xcode: Product → Test. Coverage:

| Suite | What |
| --- | --- |
| `DomainTests` | Undo, pickers, FIFO, surface END, recovery snapshot |
| `VehicleCounterTests` | Leave-gate (pass vs turn-away) |
| `RadarDecoderTests` | Varia V1 + Magene L508 |
| `FitTests` | Header, CRC, streamed flush, resume-append |
| `RidePhotoStoreTests` | Sidecar, zip bundle, caption cap |

## Device smoke

On a physical iPhone:

1. Get started → grant location.
2. Start ride → GPS line changes from “Waiting for GPS”.
3. Tag OTHER twice inside 3 s → tally returns to 0 (undo).
4. Pause → tiles do not open pickers. Resume → tagging works.
5. Stop → confirm dialog → FIT appears under Rides.
6. Optional: pair a Varia or Magene, Start, confirm the radar strip goes live.

## Field

A 1–2 hour ride, keep-screen-on off, with and without radar. Drop the FIT on
`tools/fit-viewer.html`. Idle / paused drain should stay close to the phone at
rest.
