# Scout — Android permissions

What the system dialogs mean, what Scout uses them for, and what to choose.
Scout records **locally only** — no account, no analytics, no required network.

| Related doc | Role |
| --- | --- |
| [SETUP.md](SETUP.md) | Install / first run |
| [TESTING.md](TESTING.md) | Device smoke checklist |
| [TECHNICAL.md](TECHNICAL.md) | Declared permissions / FGS |

Change later anytime: **Settings → Apps → Scout → Permissions**.

---

## 1. Location (precise vs approximate)

**Dialog wording (typical):** allow Scout to access this device’s location — often
with **Precise** / **Approximate**.

| Choice | What Android gives | For Scout |
| --- | --- | --- |
| **Precise** (`ACCESS_FINE_LOCATION`) | GNSS / fused fix, typically metres outdoors | **Required** for usable tag coordinates and the FIT track |
| **Approximate** (`ACCESS_COARSE_LOCATION`) | Rough cell/Wi‑Fi position, often 100–500+ m | **Not enough** — tags can land on the wrong street |

**Choose Precise** while recording. Approximate alone is not useful for Atlas /
fit-viewer style tagging.

Scout only turns GPS on while a ride is **RUNNING**. On Pause / Stop, location
updates are removed (see TECHNICAL battery policy).

Scout does **not** need “allow all the time” / background location for v1 — the
recording foreground service covers the ride while the session is open.

---

## 2. Nearby devices (Bluetooth)

**Dialog wording (typical):** “Allow Scout to find, connect to, and determine
the relative position of nearby devices?”

That is Android 12+ packaging of **Bluetooth scan / connect**
(`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`). Despite the wording, Scout uses it to:

- **Scan for** a bike radar on the pair screen only  
- **Connect** to your saved radar while a ride is RUNNING  

It is **not** GPS, and Scout does **not** use it to track other phones or people.

| Choice | Effect |
| --- | --- |
| **Allow** | BLE radar pairing and in-ride connection work |
| **Don’t allow** | Tagging + GPS still work; strip stays `no radar` until you allow this later |

ANT+ radar (when the phone / USB stick supports it) goes through ANT Radio
Service and does not replace the need for this permission if you also use BLE.

---

## 3. Notifications

**Dialog wording (typical):** “Allow Scout to send you notifications?”

Android 13+ requires this for the **ongoing recording notification** while a
ride is open (foreground service). Example copy: “Scout ride — Recording” /
“Paused — tap to open”.

Scout does **not** send marketing, promo, or chat notifications. The only
notification in v1 is that recording status line so:

- the system allows the location foreground service to keep recording, and  
- you can tap back into Scout from the shade.

| Choice | Effect |
| --- | --- |
| **Allow** | Normal recording + visible ride notification |
| **Don’t allow** | May break or limit background / FGS recording on newer Android — **allow for testing and real rides** |

---

## 4. Camera

**Dialog wording (typical):** allow Scout to take pictures and record video.

Scout uses **Camera** only when you tap **Take photo** during a running ride. Choosing an existing picture uses the system photo picker and does **not** need this permission (or Photos/Media access).

| Choice | Effect |
| --- | --- |
| **Allow** | You can photograph from the ride screen |
| **Don’t allow** | Tagging still works; you can still **Choose from phone** |

Scout does not record video.

---

## 5. Quick “what should I tap?”

First launch / first Start:

1. **Location → Precise** (and “while using the app” is enough).  
2. **Nearby devices → Allow** if you will use a BLE radar; otherwise optional.  
3. **Notifications → Allow**.

Optional later: Settings in-app for units, keep-screen-on, and radar pairing —
those are not system permission dialogs.

---

## 6. Privacy summary

| Data | Where it goes |
| --- | --- |
| GPS track + tags + optional radar samples | Local `.fit` under app `files/rides/` |
| Photos you add during a ride + optional caption | Local sidecar next to that FIT (`files/rides/<stem>/`) |
| Share | Only if **you** use Share (FIT, or a zip of FIT + photos) |
| Network | Not used in the recording path |
| Account / ads / analytics | None |
