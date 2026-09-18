# Scout — Google Play Data safety form

Use this when filling in **Play Console → App content → Data safety**.
Scout v1 records **locally only** — no account, no ads, no analytics SDKs, no
required network in the recording path.

| Related doc | Role |
| --- | --- |
| [PERMISSIONS.md](PERMISSIONS.md) | What each Android permission is for |
| [TECHNICAL.md](TECHNICAL.md) §9–10 | FIT payload, declared permissions |
| [LEGAL.md](LEGAL.md) | In-app safety / policy copy (`legal.json`) |
| [SPEC.md](../../docs/SPEC.md) §2.4 | Product privacy principles |

**Privacy policy URL (store listing + form):** `https://cyclingcommons.org/privacy`

---

## Quick answers

| Question | Scout v1 answer |
| --- | --- |
| Does your app collect or share user data? | **Yes — collects** (on device). **Does not share** with the developer or third parties automatically. |
| Is all data encrypted in transit? | **N/A** — Scout does not send ride data to Cycling Commons servers. User-initiated share uses the system share sheet (HTTPS depends on the recipient app). |
| Can users request data deletion? | **Yes** — delete individual rides in Settings, or uninstall the app (all local data removed). No server-side account data. |
| Independent security review? | **No** (unless you opt in separately). |
| Families / Designed for children? | **No** — general cycling app; not directed at children. |
| Account required? | **No** |

---

## Data types to declare

Play groups answers into **Collected**, **Shared**, and **Processed ephemerally**.
For Scout, everything below is **collected** and stored **on the device** until
the user deletes it or uninstalls. Nothing is **shared** with you (the developer)
or ad/analytics partners.

### 1. Location — **Collected**

| Field | Answer |
| --- | --- |
| Collected? | **Yes** |
| Shared? | **No** (not sent to developer; user may share a FIT file manually) |
| Required or optional? | **Required** for recording rides with usable tag coordinates |
| Purpose | **App functionality** |
| Ephemeral? | **No** — written into the FIT file on device |

**What:** GPS latitude/longitude (and speed when available) while a ride is
**RUNNING**. Only while recording; updates stop on Pause/Stop.

**What is NOT collected:** background “all the time” location, coarse-only mode
as a product feature (precise location is needed), geofencing, or location for ads.

---

### 2. Files and docs — **Collected**

| Field | Answer |
| --- | --- |
| Collected? | **Yes** |
| Shared? | **No** (unless user uses Share) |
| Purpose | **App functionality** |

**What:** FIT ride files in app-private storage (`files/rides/…`), containing:

- Timestamps  
- Latitude / longitude  
- Speed (when available)  
- Scout tag codes (POI type/detail, optional radar fields)

Optional photos you add during a ride (plus captions and the GPS stamp at save) live in a sidecar folder next to that FIT — not inside the FIT. Delete a ride to remove both.

---

### 3. Photos / videos — **Collected** (optional, rider-initiated)

| Field | Answer |
| --- | --- |
| Collected? | **Yes** (only if the rider adds a photo) |
| Shared? | **No** (unless the rider uses Share) |
| Required or optional? | **Optional** |
| Purpose | **App functionality** |

**What:** JPEGs the rider takes or picks while a ride is RUNNING, stored in app-private storage with an optional caption and the GPS fix at save. Not uploaded automatically.

**What is NOT collected:** the camera roll as a whole, contacts, or unrelated files. The system photo picker grants one-shot access to the chosen image.

---

### 4. App activity — **Collected** (in-app events only)

| Field | Answer |
| --- | --- |
| Collected? | **Yes** |
| Shared? | **No** |
| Purpose | **App functionality** |

**What:** Tag taps, undo/confirm events, and optional rear-radar sample values
encoded into the FIT file as developer fields. This is ride logging, not
behavioral analytics.

**What is NOT collected:** page views sent to a server, crash reporting SDK
events, advertising IDs, or funnel analytics.

---

### 5. Device or other IDs — **Collected** (optional radar pairing)

| Field | Answer |
| --- | --- |
| Collected? | **Yes** (only if user pairs a BLE radar) |
| Shared? | **No** |
| Required or optional? | **Optional** |
| Purpose | **App functionality** |

**What:** Bluetooth MAC address (and sometimes advertised device name) of the
user’s saved rear radar, stored in app preferences so Scout can reconnect on the
next ride.

**What is NOT collected:** advertising ID, IMEI, or analytics device IDs.

---

## Data types you can skip

Declare **No** for these unless you add them in a future version:

| Category | Why skip |
| --- | --- |
| Personal info (name, email, address) | No account |
| Financial info | No payments in app |
| Health & fitness (Google’s HR/calories categories) | Scout does not read HR or body sensors |
| Messages | None |
| Audio | None |
| Calendar / contacts | None |
| Web browsing | None |
| Search history | None |
| Installed apps | None |
| Diagnostics / crash logs to developer | No crash SDK in v1 |
| Advertising ID | None |

---

## Security practices

| Practice | Scout v1 |
| --- | --- |
| Data encrypted in transit (to your servers) | **Not applicable** — no upload |
| Data encrypted at rest | **Device-level** (Android full-disk encryption). FIT files live in app-private storage. |
| Users can request deletion | **Yes** — per-ride delete + uninstall |
| `android:allowBackup` | **`false`** — ride files are not auto-backed up to Google |

---

## Step-by-step (Play Console UI)

1. **Overview**  
   - “Does your app collect or share any of the required user data types?” → **Yes**  
   - “Is all of your app’s user data encrypted in transit?” → **No** or **N/A**  
     (explain in store listing / policy: data stays on device; share is user-initiated)  
   - “Do you provide a way for users to request that their data is deleted?” → **Yes**

2. **Data types** — add the sections above (Location, Files, Photos, App activity,
   Device IDs). For each:
   - **Collected:** Yes  
   - **Shared:** No  
   - **Processed ephemerally:** No  
   - **Required:** Location + files + app activity = required for core recording;
     photos = optional; device IDs = optional (radar only)  
   - **Purpose:** App functionality only (not advertising, not analytics)

3. **Account deletion**  
   - No online account → state that deletion is via in-app ride delete or
     uninstall; link privacy policy.

4. **Preview** — generated text should mention location and files stored on
   device for ride logging; no third-party sharing.

---

## Store listing alignment

Keep these consistent with the form and [PERMISSIONS.md](PERMISSIONS.md):

- **Short description:** ride logger with optional rear radar; local-only.  
- **Full description:** mention GPS while recording, optional Bluetooth radar,
  FIT export/share is user-initiated, no account.  
- **Privacy policy:** `https://cyclingcommons.org/privacy`  
- **Permissions justification** (if asked): location for ride track and tags;
  nearby devices / Bluetooth for optional radar pairing; notifications for the
  recording foreground service only; camera only when the rider takes an in-ride
  photo (gallery pick uses the system picker).

---

## When to update this doc

Revisit before each Play release if you add:

- Crash reporting, analytics, or ads  
- Cloud sync / accounts  
- Automatic upload of rides  
- New sensors (HR, power, etc.)  
- Backup or export to your servers
