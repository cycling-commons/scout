<!-- SPDX-License-Identifier: AGPL-3.0-only -->
# Scout ride bundle

The file a Scout **phone** app (Android, iPhone) exports after a ride when the
ride has photos or notes. A bike computer (Garmin Edge, Hammerhead Karoo) writes
a bare `.fit` file; a phone can also take a photo and a short note at a tag, and
a FIT file cannot carry a photo. The bundle is the FIT file plus those photos
and notes, in one zip.

Recording behaviour stays [SPEC](SPEC.md). Tag codes and FIT channels stay
[DATA-FORMAT](DATA-FORMAT.md). This document only covers the extra sidecar that
phones can attach.

## 1. What stays the same

The ride file inside the zip is a normal Scout FIT: ~1 Hz samples, five Scout
channels, undo / surface / radar rules unchanged. A consumer that ignores
`scout.json` and the `photos/` folder still has a valid ride.

Notes and photos are **optional extras**, keyed to FIT timestamps. They never
replace a tag in the FIT. A photo on a tag that the rider never shares never
has to leave the device.

Head units do **not** emit this zip. Phones emit a bare `.fit` when there are
no notes or photos.

## 2. Layout

```
scout-2026-09-12-1325.zip
├── ride.fit
├── scout.json
└── photos/
    ├── 20260912T144633Z-1.jpg
    └── 20260912T144633Z-2.jpg
```

Only the files named in `scout.json` are read. Anything else in the zip is
ignored. The zip file's own name is free; the one above is the suggested form.

## 3. `scout.json`

```json
{
  "format": "scout-bundle",
  "version": 1,
  "fit": "ride.fit",
  "tags": [
    {
      "at": "2026-09-12T14:46:33Z",
      "note": "Cobbles start after the bridge",
      "photos": ["photos/20260912T144633Z-1.jpg", "photos/20260912T144633Z-2.jpg"]
    }
  ]
}
```

| Field | Rule |
|---|---|
| `format` | Exactly `"scout-bundle"`. Anything else: the file is refused. |
| `version` | `1`. A higher version is refused with a message to update the page. |
| `fit` | Path of the ride inside the zip. |
| `tags[].at` | The **FIT timestamp of the record the tag was written on**, UTC, whole seconds, ISO 8601 with `Z`. This is the link: the page reads the same timestamp from the FIT file for every tag. |
| `tags[].n` | Optional. Only when two tags share one second: `0`, `1`, … in the order they appear in the FIT file. Absent means `0`. |
| `tags[].note` | Optional plain text, one line. It fills the card's name field, which the rider can still edit. Longer than 200 characters is cut at 200. |
| `tags[].photos` | Optional list of paths inside the zip. JPEG or WebP. |

A **surface stretch** is keyed on the tap that **starts** it.

## 4. Photos

- JPEG or WebP, at most 15 MB each.
- File names follow `<at in compact form>-<number>.<ext>`, for example
  `20260912T144633Z-1.jpg`. The name is for people; the link is the `photos`
  list, so a name that breaks the pattern still works.
- Photos may keep their EXIF, GPS included. A consumer that uploads them
  elsewhere should strip EXIF as its own policy.

## 5. Phone ports (Android and iPhone)

These rules are the SPEC parts that matter for writing the bundle. iPhone
follows the same contract when that port exists; Android is the reference.

### 5.1 Recording (SPEC §4, §12.1)

- Photos and notes are only taken while the timer is **running**. Idle or paused:
  no camera, no gallery pick, no note, no write.
- Stop requires confirmation before the ride is saved (SPEC §9).
- Nothing expensive while idle: no high-accuracy GPS, no radar, no forced
  screen-on. No network in the recording path (SPEC §2.4).
- Share / export is **after** the ride, rider-initiated. Never while recording.

### 5.2 FIT first (SPEC §4.2, DATA-FORMAT)

- Write original FIT with timestamp, lat/lon, speed when available, and the
  five Scout developer fields. Do not invent extra FIT channels for photos.
- Drain the tag FIFO one tag per sample. `tags[].at` / `tags[].n` must match
  that drain order so a second tag in the same second is not ambiguous.
- A note or photo without a matching FIT tag is not a tag. Do not synthesise
  `poi_type` from a picture.

### 5.3 When to zip

| Ride contents | Export |
|---|---|
| No notes, no photos | Bare `.fit` |
| At least one note or photo | This zip (`ride.fit` + `scout.json` + named JPEGs/WebPs) |

Omit `tags[]` entries that have neither a note nor a photo. Omit `photos/`
when every remaining entry is note-only.

### 5.4 Permissions and storage (phones)

- Camera only when the rider taps take-photo on a running ride. A gallery pick
  uses the system picker (no broad photo-library access).
- Store FIT and sidecar locally, app-private. Delete ride → delete FIT and
  photos together.
- Do not require an account to record or to export the zip.

## 6. What a reader does with a mismatch

- An entry whose `at` (and `n`) matches no tag: counted as unmatched, never
  guessed onto the nearest tag.
- A photo path that is missing from the zip, too large, or not JPEG/WebP:
  skipped and counted the same way.
- A tag the rider drops from a review takes its photos with it; nothing
  uploads.

## 7. Limits

Refuse a bundle whose `scout.json` is over 1 MB, whose ride is over 64 MB, or
whose unpacked total is over 300 MB, before reading any of it. These guard the
reader, not a server; a server’s own limits still apply if photos are uploaded
later.

## 8. Related docs

- [SPEC.md](SPEC.md) — recording, grid, radar, battery, privacy
- [DATA-FORMAT.md](DATA-FORMAT.md) — FIT channels and parser rules
- [SHARING.md](SHARING.md) — optional post-ride upload of the **FIT** (not this
  zip) when a port implements sharing
- Android storage/share today: [`Android/docs/TECHNICAL.md`](../Android/docs/TECHNICAL.md) §9
- iPhone: [`iPhone/`](../iPhone/) (same bundle when the port ships)
