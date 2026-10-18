# Naari Android Companion — Product Requirements and AI Handoff

Version 1.0 · 12 October 2026 · Status: implementation brief, not a shipped app.

## 1. Build objective

Build a real native Android companion and installable APK for Naari OS. The app pairs securely with the watch, executes watch-requested phone calls/SMS/location actions, manages guardians, saves every successfully synchronized watch recording, and controls supported watch settings. It must feel calm, simple, polished, and visually consistent with the premium watch UI. Apple-inspired typography, spacing, hierarchy, motion and craftsmanship are the reference; use original assets and Android-native navigation/accessibility.

Primary user journey: install → pair → grant capabilities → set guardians → verify readiness → wear watch. A watch action reaches the phone through BLE; the phone uses its own SIM and location services. No SIM800L, cloud account, or web dashboard is required for core operation.

Delivery includes Android source, reproducible Gradle builds, debug APK, release build instructions, matching firmware changes, protocol documentation, tests and a physical-device validation report. A mock screen or browser wrapper is not completion.

## 2. Ground truth: what exists now

Inspect source before coding; these paths are authoritative:

| Capability | Current state | Source |
| --- | --- | --- |
| Watch display | Real custom immediate-mode RGB565 renderer, CO5300 QSPI, 410 × 502; not LVGL | `firmware/main/ui/`, `drivers/display_co5300/` |
| UI and faces | Existing app order and paged 2 × 2 drawer; five full-size backgrounds with local NVS selection | `ui_manager.cpp`, `wallpaper_assets.h`, `storage/preferences.*` |
| BLE transport | Real NimBLE authenticated/bonded service, one phone connection | `network/phone_bridge.*` |
| Guardians | Two persisted name/phone slots written from phone | `phone_bridge.cpp`, `state_machine/` |
| Phone actions | Watch queues call/SMS/location/location_sms; Android executor missing | `phone_bridge.*` |
| Recorder | ES7210/ES8311 initialized through esp_codec_dev; 16 kHz mono signed 16-bit PCM memo, up to 10 seconds in RAM; local playback | `audio_manager.*` |
| Fall detection | Low-G → impact → stillness heuristic; off by default; saved switch; local safe demo; real detections enter cancellable SOS | `safety/fall_detector.*`, `drivers/imu_qmi8658/` |
| SOS | SAFE/WATCH/EMERGENCY state machine, approximately 30-second cancellation window and configured guardian phone requests | `state_machine.*` |
| Remote settings / audio sync | Not implemented | Must extend firmware |
| SIM800L | Current legacy adapter delegates to PhoneBridge; no validated functioning direct modem fallback | `drivers/sim800l/` |
| Other systems | Web/backend projects exist; voice wake word and SD logger contain unfinished/mock paths | Audit before integration |
| Android app | No companion project/APK yet | Create `android-companion/` |

Previous hardware self-test captured 51,456 real microphone samples (~3.2 seconds), completed playback without driver errors, rendered/saved all five faces, and started BLE advertising. This is not proof of human-audible quality or end-to-end Android actions. Host tests cover fall/protocol logic and native UI rendering. Recheck the currently flashed image; do not assume the last self-test image is a production build.

Do not describe placeholder heart rate, voice recognition, SD recording, modem/network paths or cloud delivery as functional without implementing and testing them.

## 3. Product scope and behavior

### Pairing and readiness

Pair through Android CompanionDeviceManager where suitable, using the watch service UUID and user selection. Watch exposes a temporary pairing window and six-digit passkey; require the Android system pairing confirmation. Retain bonded identity, reconnect with capped exponential backoff, serialize GATT operations and discover services after every connection. Show separate states: Bluetooth off, disconnected, connecting, paired, connected but service unavailable, ready, and permission-limited.

Run the appropriate companion/background service and user-visible notification. Explain battery restrictions only when they affect readiness. Do not promise reconnection after force-stop; explain recovery. Never claim protected/ready solely because BLE is connected.

### Guardians

First release supports the firmware's two guardian slots. Add/edit/reorder/remove names and international phone numbers with validation and optional system contact picker. Store locally, synchronize the complete two-slot profile, and show pending/saved/failed status. Do not silently seed contacts. Contact permission is optional; manual entry must work. Reject truncated UTF-8 names and payload overflow before writing. Reordering changes the primary guardian explicitly. Support more guardians only after a versioned firmware schema change.

### SMS and phone calls

Handle `sms` and `location_sms` through the selected phone subscription with explicit SEND_SMS consent. Support dual-SIM selection, missing SIM/no service, multipart messages, per-part callbacks and aggregated sent/delivery outcomes. `sent` means Android reports successful sending, not recipient receipt. Report `delivered` only when delivery reports actually confirm it; unknown delivery remains unknown.

Place ordinary calls using permitted Android telecom APIs with CALL_PHONE consent. `calling` means the call launch was accepted, not connected or answered. If direct execution is unavailable, use a user-facing dialer path and return `needs_phone`; never label a dialer opening as a placed call. Emergency numbers may require phone UI. No automatic test calls to 112/1091 and no watch hang-up or live call audio claim unless a separately implemented Android role/API supports it.

Provide activity history, redacted failures and manual retry with duplicate-risk explanation. Persist request identity before side effects. A crash between dispatch and result is an unknown outcome, not permission to send again. Never automatically replay an uncertain SMS/call.

### Location

Get fresh phone location for `location` and `location_sms`, track timestamp/accuracy/provider, impose a bounded timeout and handle approximate/denied/offline states. Proposed freshness target: ≤30 seconds and accuracy ≤100 m; show actual quality when these are unmet. Cached fallback must be labeled with its age and must not be presented as fresh GPS. Compose a readable guardian alert and map link for location_sms. Location failures must not silently prevent a safety message: send explicit location-unavailable text when SMS is permitted, and return a truthful result.

Continuous location sharing is an opt-in mode with clear start/stop, foreground notification, duration and battery implications; requires protocol extensions. Core one-shot location must work without an account or cloud service. Do not track in SAFE mode by default.

### Watch controls

App controls: wallpaper selection from the five built-in faces, brightness, speaker volume, fall detection enable, watch/night-guard mode, safe check-in, find-watch sound, time synchronization, device name/status and unpair. Expose only controls supported by negotiated firmware capabilities. Read back the applied state and distinguish pending/applying/applied/failed; never show a switch as saved before device confirmation.

Remote SOS cancellation must honor the existing disarm authentication policy, require deliberate user action and be logged. Do not add unauthenticated remote emergency triggers. Firmware update/OTA is a later feature, disabled until signed images, rollback, partition and interrupted-update tests exist.

### Fall detection and incidents

Show monitoring readiness, last sync and enabled state. Watch performs detection locally; app relays actual incident actions. Synchronize incident ID, trigger, countdown/state and action results through a new event/status contract. The watch is authoritative for its countdown; phone must not create a second independent countdown or duplicate guardian alerts. Demo mode stays visibly marked and cannot send messages, place calls or dispatch real SOS. Explain that the wrist heuristic can miss falls or trigger accidentally; do not market it as a medically validated system.

### Recordings

Automatically save completed watch recordings to phone-owned local storage after reconnect, with progress, retry/resume, duration/date, playback, rename, delete, export/share and available-space status. Distinguish captured-on-watch, waiting-for-transfer, transferring, verified-on-phone and failed. Save standard WAV (16 kHz, mono, PCM16) initially; optional phone transcoding after verification. No cloud upload by default. Deleting a phone copy must not implicitly delete the watch original.

**Required firmware work:** the current single RAM memo cannot retain all recordings or survive reboot. Implement a durable memo catalog and bounded local storage, real file writes/mount checks, stable recording IDs and metadata. Validate available SD or flash storage on the real board; the existing mock SD mount is not sufficient. Preserve partitions unless a separately reviewed migration is necessary. If storage is full, warn and stop accepting new recordings rather than silently overwriting unsynchronized recordings. If durable storage is unavailable, explicitly label volatile capture; this fallback does not satisfy the final “save all recordings” requirement.

Transfer completed recordings only. Phone stores to a temporary file, verifies expected length and SHA-256, atomically commits file/catalog, then acknowledges durable save. Only afterward may the watch mark the memo synchronized. Watch may delete originals under an explicit documented retention policy. A disconnected phone must not stop local recording; a reboot must not lose completed durable files. Handle partial files, duplicate IDs, interrupted writes, full storage and duplicate transfer acknowledgments.

## 4. Visual and interaction specification

Build with Kotlin and Jetpack Compose. Use one token system shared across all app screens. System Android font by default; do not bundle Apple's SF fonts or proprietary icons. Original consistent vector icons, subtle elevation and restrained blur; avoid expensive full-screen animated blur.

| Token | Initial value / rule |
| --- | --- |
| Background | Light #F5F5F7; dark #08090C |
| Surface | White / dark #17191F |
| Text | #181A20 / #F4F5F7; secondary text retains readable contrast |
| Accent | Teal #00BFA5; emergency red #E63D4F; amber only for attention |
| Spacing | 4, 8, 12, 16, 24, 32 dp |
| Cards | 20–24 dp corners; 16–20 dp inner padding |
| Controls | Minimum 48 dp touch targets, no icon-only unlabeled safety actions |
| Type | Title 28–32 sp; section 20–22; body 16; label 13–14; supports font scaling |
| Motion | 180–280 ms ordinary transitions; interruptible restrained spring for state changes; reduced-motion support |

Four bottom destinations: **Home, Guardians, Recordings, Watch**. Settings/permissions/history are reachable through clear labeled entries. Home centers a watch illustration, real readiness, battery/last-seen and three concise action tiles; do not flood the screen with diagnostics. Show failures inline near the relevant action. Use bottom sheets for focused editing, full screens for pairing and incident state. Emergency state prioritizes countdown and actions over decorative motion.

Deliver light/dark themes, edge-to-edge safe insets, Android back/predictive-back behavior where supported, TalkBack labels, contrast checks, 200% font-scale layouts and phone widths from 320 dp. UI stays responsive while scanning, transferring and obtaining location. Target smooth 60 Hz interaction on a representative midrange Android phone; profile before claiming performance. Use haptics sparingly with system settings respected.

## 5. Existing BLE v1 contract — implement exactly

Service: `d973f2e0-b19e-11ee-a506-0242ac120002`.

Request characteristic: `d973f2e1-b19e-11ee-a506-0242ac120002`, encrypted/authenticated READ + NOTIFY. Reply characteristic: `d973f2e2-b19e-11ee-a506-0242ac120002`, encrypted/authenticated WRITE. Device advertises `Naari Watch`; verify service/bond identity, not name alone.

A notification contains a **4-byte little-endian request ID**, not JSON. Subscribe, then read the entire characteristic (including long-read handling); `{}` means no pending request. Serialized UTF-8 JSON maximum **480 bytes**, message maximum **256 bytes**, names maximum **32 bytes**, phone digits with optional leading `+`, 3–20 characters. Negotiate MTU and implement correct write sizing/reliable long writes; test the largest UTF-8 payload against real Android and firmware. Never assume a small-MTU single write can carry 480 bytes.

Phone hello at startup, after permission changes and every ~15 seconds:

```json
{"v":1,"op":"hello","sms":true,"calls":true,"location":true}
```

Capabilities must reflect real permissions/service availability. Readiness expires after 45 seconds without hello. Guardian write is a complete replacement:

```json
{"v":1,"op":"profile","name0":"Guardian A","phone0":"+15555550101","name1":"Guardian B","phone1":"+15555550102"}
```

An empty phone clears that slot. These are example numbers, never test destinations. GATT write success confirms handler acceptance; v1 has no profile read-back endpoint, so add one for verified state synchronization.

Watch request:

```json
{"v":1,"id":123,"type":"location_sms","phone":"+15555550101","message":"Safety alert"}
```

Types: `sms`, `location_sms`, `call`, `location`. Acknowledge durable receipt, then actual outcome:

```json
{"v":1,"id":123,"status":"received","detail":"Accepted by phone"}
{"v":1,"id":123,"status":"sent","detail":"SMS sent","lat":19.1,"lon":72.9}
```

Statuses: `received` nonterminal; `sent`/`delivered` for SMS; `located` with finite valid `lat`/`lon` for location; `calling` for calls; `needs_phone` and `failed` for terminal intervention/failure. Latitude ±90, longitude ±180. Cached watch coordinates expire after 120 seconds. Late delivery reports are accepted only while a matching sent result remains in the 32-slot result cache. Duplicate known terminal acknowledgments are accepted; contradictory or unrelated replies are rejected.

Queue capacity 16. Notifications retry ~1.5 seconds. Calls expire after 30 seconds, other actions after 90 seconds. Disconnect clears queued work with unknown outcomes; no offline request queue. Re-notification is not a new action. Persist and deduplicate by bonded device + request ID + payload identity. IDs are random-seeded counters, not globally unique incident IDs; introduce a boot/session ID in the extension and avoid assuming deduplication is perfect across firmware restarts.

## 6. Required additive protocol extensions

Do not send invented v2 JSON into the existing v1 reply parser. Maintain v1 action compatibility and add a separately negotiated versioned control/events service. Allocate/document UUIDs and payload limits before implementing both sides. Unknown operations fail explicitly. Include capability discovery, firmware/protocol version, device identity and boot/session identity.

Control request fields: version, commandId, operation, arguments. Response: commandId, accepted/applied/rejected, error code and applied state. Operations: get_status, get_profile, set_wallpaper, set_brightness, set_volume, set_fall_enabled, set_mode, find_watch, sync_time and authenticated safe-check-in. Validate ranges and serialize UI mutations through existing locks. Persist only settings intended to survive reboot; limit flash writes.

Device events include monotonic sequence/session, state revision, battery, link/feature readiness, incident lifecycle, countdown deadline and recording catalog changes. Phone can fetch a full snapshot after missed events. Prefer snapshots over trying to reconstruct device truth from notifications alone.

Recording protocol: list metadata, request/resume transfer by recordingId/byte offset, flow-controlled binary chunks on a dedicated data characteristic, complete/hash, phone durable-save acknowledgment. Chunk size derives from negotiated ATT payload minus header; include recording/transfer identity, offset/sequence, length and integrity checking. Serialize GATT work; prioritize SOS/control over bulk audio. Target no lost/duplicated samples after resume and measure actual transfer rate. Do not base64 entire recordings into 480-byte JSON or add unrestricted access to the live PCM buffer.

## 7. Android architecture and permissions

Create `android-companion/`, Kotlin, Compose, coroutines/Flow, ViewModels, Room for guardians/requests/incidents/recordings, DataStore for preferences, explicit repository interfaces and test fakes isolated from release code. Minimum Android 8/API 26 proposed; target the current required Android API at implementation time and verify requirements. Use Gradle wrapper, pinned dependencies and documented JDK. No secret signing keys in source.

Modules/packages: design system, onboarding, home, guardians, recordings, watch/settings; BLE session and operation queue; action executor; location provider; recording transfer/store; request ledger; device capability/state repository. Keep Bluetooth/action work outside composables. Use WorkManager for deferrable file maintenance, not as the sole live BLE listener.

Bluetooth permissions vary by API; request Nearby Devices permissions on Android 12+ as appropriate. Pairing/background strategy follows [Android BLE background guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background) and [CompanionDeviceManager guidance](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing). Declare appropriate foreground-service types and obey background-start and while-in-use location restrictions; pairing alone does not grant background location.

Request SEND_SMS, CALL_PHONE and location separately with feature-level explanations. Ask notifications permission where required. Use a contact picker instead of demanding address-book access. Recording playback needs no phone microphone permission. Store files in app-owned storage; export through FileProvider/system document APIs without broad storage permission.

Use [SmsManager](https://developer.android.com/reference/android/telephony/SmsManager) sent/delivery callbacks and explicit subscription selection. Follow [TelecomManager](https://developer.android.com/reference/android/telecom/TelecomManager) permission and emergency-number restrictions. An installable sideload APK is the initial delivery; Play Store distribution must separately satisfy [SMS/call-log policy](https://support.google.com/googleplay/android-developer/answer/10208820). Do not promise Play approval or add unrelated SMS inbox/call-log access.

Keep local sensitive data private, exclude recordings/phone numbers from logs and unintended backups, use OS-backed protections for sensitive secrets, and provide explicit delete/export controls. Do not silently forward recordings/location to an existing backend. Only the authenticated bonded phone may control the watch or retrieve recordings.

## 8. SIM800L backup policy

Phone transport is default and active. Preserve the existing driver path and board definitions as a future integration point. Current code is a phone adapter, not a validated UART modem backup. Introduce a transport interface and disabled-by-default modem implementation only when real SIM800L hardware is available. Future failover requires explicit user opt-in, health checks and a shared action ledger to prevent double sends. Never claim modem fallback worked because the phone bridge accepted a request.

## 9. Acceptance tests and release gates

- Fresh install pairs with physical watch, displays correct passkey flow, reconnects after Bluetooth toggle/device reboot, and rejects unauthorized access.
- Revoking each permission updates readiness and watch capabilities; denied SMS/call/location does not crash or claim success.
- Two guardians save, read back and survive phone/watch restart. Unicode names respect byte limits; invalid numbers and oversized JSON fail cleanly.
- Controlled test SMS uses an explicitly approved test recipient; sent/delivery statuses match OS callbacks. Duplicate notification, reconnect and crash do not produce an automatic second send.
- Ordinary approved test call opens/places correctly; emergency-number actions and denied permissions return needs_phone/failed truthfully. Do not test emergency services.
- One-shot location handles approximate/fresh/stale/denied/timeout paths; no fabricated coordinates. Dual-SIM selection and no-SIM/no-network are tested.
- Watch controls report actual applied state after restart; disconnect mid-command reconciles via snapshot without fake success.
- Multiple completed recordings survive watch reboot, synchronize automatically, match sample count/hash, play audibly on phone, resume after disconnect and remain intact under duplicate chunks/low storage. No unsynced file is silently overwritten.
- Physical microphone/speaker listening test in addition to DMA/log checks. UI demo/fall demo never sends alerts; real controlled detector test uses approved recipients.
- Background screen-off, OEM battery restrictions, app crash, phone reboot and force-stop are documented/tested on representative Android devices; unsupported recovery is reported honestly.
- Compose UI/accessibility tests, Room/request-ledger tests, protocol conformance tests and instrumented BLE tests pass. Profile release-build scrolling/navigation during transfer.
- `./gradlew test lint assembleDebug` succeeds; APK and checksum available. Release signing instructions and reproducible firmware build/flash instructions included. Final watch image has hardware self-test OFF.

## 10. Implementation sequence

1. Audit firmware/build/flash state and existing tests. Establish Android project, design tokens, navigation and real BLE pairing/status; no fake ready state.
2. Implement v1 guardian sync, durable request ledger and actual SMS/call/location execution. Test on hardware with approved non-emergency recipients.
3. Implement additive status/control/events contract on both watch and phone, then complete watch settings and incident views.
4. Implement durable watch recording storage and resumable transfer; phone library/playback/export and integrity tests.
5. Test background reliability/accessibility/performance, finalize APK and documentation, flash production firmware.

Do not start cloud/OTA, incoming SMS mirroring or live phone-call audio until the above is complete. These require additional product/API decisions and are not implied by the existing v1 protocol.

## 11. Cleanup instructions

Remove only demonstrated disposable artifacts: macOS `.DS_Store`, Python bytecode, obsolete generated preview outputs and superseded build caches after a working rebuild and flash. Retain current artifacts during active hardware verification. Keep lockfiles, SDK configs, partitions, source assets and font licenses, tests, firmware tools, SIM800L interface, web/backend source and historical architecture documents. Inventory ambiguous directories and dependencies before deleting them; the companion's lack of dependency on a site is not evidence that the site is unwanted. Document each deletion. Never remove source, credentials/config needed by existing deployments, or the only known working firmware image in a broad cleanup.

## 12. Instructions for the next AI

Read this document, root README, actual phone_bridge source, audio manager, state machine, storage and UI before editing. Preserve the real hardware renderer, display byte order, PSRAM/DMA constraints and existing drawer layout. Run source-derived protocol tests and native UI preview after firmware changes. Implement both sides of missing protocols; do not make app screens pretend unsupported controls/audio sync exist. Keep SIM800L as a future backup path. Deliver source and a real tested Android APK, describe what was physically verified, and list remaining blockers honestly.
