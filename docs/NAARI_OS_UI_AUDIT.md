# Naari OS UI and feature audit

Reviewed 12 October 2026. This is a source-and-logic audit, not a browser visual inspection or a physical-device certification. No live SMS/calls, cloud writes, microphone recordings, or device commands were performed. Application source was not changed.

## Scope and architecture

- `dashboard/`: React guardian portal, invite-code entry, watch showcase, watch simulator, wearer/device cards, Leaflet map, alert history, incident modal, event simulator and database setup instructions.
- `sheshield-sentinel-guardian-main/`: SheShield presentation site, linked watch/companion prototype and Watch OS Studio. Its local AGENTS.md explicitly describes a static prototype. Its watch implementation has diverged from the dashboard implementation.
- `backend/`: Express alert/device APIs, SMS parser/webhooks, in-memory incident store, optional Supabase writes and WebSocket broadcasts.
- `firmware/`: LVGL watch screens and ESP32 peripheral/state-machine code. Communication and speech features were traced to check whether the web UI's claims correspond to implemented behavior.
- `supabase/schema.sql`: tables, seeded demo data and access policies.

The main issue is inconsistent meaning: a screen can say “safe,” “live,” “delivered” or “recording” because a local variable changed, without confirmation from the corresponding device, network or storage system.

## Screen and feature inventory

| Area | Implemented behavior | Dummy, incomplete or problematic behavior |
|---|---|---|
| Watch face | Clock/date, wallpaper selection and navigation are implemented. | Battery, carrier, activity and other watch readings are largely preset; simulator telemetry is independent of guardian device state. |
| App drawer | Dashboard uses a scrollable list; presentation simulator uses paginated apps. | Dashboard Compass/Telemetry and Diagnostics navigate to IDs with no rendered screen. Duplicate Phone/Keypad entries add clutter. |
| SOS | Local countdown, sound and call-overlay transitions exist. Dashboard also inserts a demo/cloud event. | No real browser call or SMS dispatch. Different components have different emergency timelines and cancellation behavior. |
| Calling and keypad | Digits, ringing synthesis, elapsed-time display and exit controls. | Ringing and elapsed time do not establish a telephone connection. No real call status or modem transport. |
| Contact SMS | Selecting SMS opens a success overlay in the dashboard simulator. | It does not send a message or confirm delivery. |
| Night Walk Guard | Session-looking screen and fixed guardian/location/ping labels. External demo controls can change companion state. | Opening/ending the watch app itself does not consistently start/end the shared session. No periodic pings from this screen. |
| Contacts | Lists and call/SMS navigation. Guardian portal can display fetched contacts. | Watch contacts are hardcoded independently; no user flow for adding, editing, removing or verifying guardians or changing notification preferences. |
| Sound Studio | Web Audio oscillator sirens and sound selection. | The displayed 105/110 dB claims are not measurements. Volume changes are not consistently applied to an already-playing studio tone. |
| Flashlight | Screen-color and strobe modes. | A display effect, not control of a separate torch. Motion/flashing preferences need attention. |
| Control Center | Toggles and visual states. | Wi-Fi/cellular toggles do not control transport. Silent mode does not gate SOS sound. Other screens continue showing connection claims. |
| Compass and Diagnostics | Presentation simulator renders both screens. | Its heading, altitude, battery lifetime, satellite count and health claims are static. Dashboard versions have no render branch. |
| Voice memo | Presentation simulator includes genuine getUserMedia/MediaRecorder capture and local playback code. | On unavailable/denied microphone it can still show recording/saved states and play a bell. Recordings are temporary blob URLs, not durable stored evidence. |
| Guardian login | Form, basic validation, demo session and optional invite lookup. | Not an authenticated guardian identity. Demo fallback accepts any code starting with SENTINEL and can run after a real lookup fails. |
| Guardian status | State-dependent banners and data-driven fields. | No reliable stale/offline state; several sensor-health claims are hardcoded. Zero battery/signal values are replaced by healthy defaults. |
| Guardian map | Real Leaflet map, markers, trail visibility, recenter and Maps links. | Map data is seeded or supplied by events, not proof of live GPS. Missing and zero coordinates can become New Delhi. |
| History | Filtering and opening event detail. | Local initial history is not sorted newest-first. No pagination beyond the remote 50-row query; local events accumulate without a cap. |
| Incident evidence | Detail panel, waveform animation, resolution notes. | Play does not play audio; Download opens alert(). Delivery/voice-recognition claims are fabricated display text. |
| Resolve incident | Inserts a resolution event and changes device state. | Original incident remains active-looking when reopened; it does not join resolution events by incident_ref. No device acknowledgement. |
| Portal mute | Button changes icon/text. | It does not control audio. |
| Event simulator | Inserts fall, voice, watch, SOS and location demo events. | Shake dispatches an event with no watch listener. Simulations can write to configured cloud data rather than an isolated demo store. |
| Database configuration | Instructions and copy buttons. | It is not a connection/setup workflow. “Connected” is inferred from environment-variable presence, not a successful health/subscription check. |
| Presentation companion | Shared React state, SVG route marker, event log and in-memory offline buffer. | No external guardian communication, GNSS feed, presence detection or durable offline delivery queue. |
| Presentation sections | Navigation, educational content and interactive hero states. | Product/capability statements need to distinguish implemented, simulated and planned features. |

## Highest-priority defects

1. **A location update clears an emergency.** `dashboard/src/services/supabase.js:222` initializes every event's next state as safe. Trigger SOS, then send Cell Ping: the device changes from emergency to safe. `backend/src/db/supabase_client.js` repeats the same transition logic. Telemetry must preserve the current safety state; resolving must be an explicit, validated transition.

2. **Data access is not isolated by guardian/wearer.** `supabase/schema.sql` enables RLS but uses unrestricted true policies for reading all four tables, inserting events/contacts and updating devices. Invite-code entry is only an application lookup; it does not restrict those database policies. Backend routes and WebSocket connections lack authentication and wearer-specific authorization, and webhook sender/signature verification is absent. Replace this before using real personal/location data. This finding is based on the supplied schema, not inspection of a deployed project.

3. **Cloud failures masquerade as success.** `insertEvent` does not inspect Supabase's returned insert/update errors, then updates local state and returns success. A configured project with no events also falls back to demo history. Demo fallback should be explicit; real-mode errors and empty results must remain visible. Local IDs and server-assigned UUIDs differ, so the local event followed by its Realtime echo can also produce duplicate history entries.

4. **False delivery/evidence confirmation.** `dashboard/src/components/EmergencyDetailModal.jsx:130` only animates playback. Download uses alert(); each contact gets a static SMS DELIVERED label. The SMS parser invents an evidence URL from message text; no corresponding evidence storage/serving pipeline is present. Show unavailable/pending/failed/confirmed states from actual records.

5. **The UI's emergency state is not authoritative.** Dashboard SOS inserts emergency immediately at the start of a nominal 30-second grace period. Its Crown can return to the normal watch face while its countdown continues. The presentation companion uses a default three-second confirmation, while the simulator uses 30 seconds. External screen jumps do not set isEmergencyActive, so the emergency screen can appear without a running countdown. Use one emergency controller with an absolute deadline, independent of the visible screen.

6. **Cancellation and trigger controls disagree.** Presentation Crown/Side clicks immediately disarm, bypassing the separate hold-to-cancel UI. Its advertised side-button long click is implemented as onContextMenu, rather than a timed press. Dashboard side-button handling is mouse-only and lacks mouse-leave cancellation. Define one intended interaction and implement pointer, keyboard and cancellation handling consistently.

7. **Dashboard watch has blank destinations.** `dashboard/src/components/WatchSimulator.jsx` declares SYSTEM and TELEMETRY and links to them, but renders neither. Add the screens or remove the unavailable entries.

8. **Backend fallback is disconnected from the portal.** Backend broadcasts WebSocket updates and stores incidents in memory; dashboard subscribes to Supabase or same-window CustomEvents and never opens that WebSocket. Without Supabase, an external webhook does not reach the current portal. Backend REST alert routes also operate on a separate incident model from portal events. Choose and connect one canonical event transport/store.

9. **Watch and guardian actions do not stay synchronized.** In-watch Night Walk navigation only changes screens; its end-session button does not emit the corresponding shared-state transition. The presentation simulator never emits onStateChange('WATCH'). The dashboard simulator never consumes device.current_state to synchronize its screen. Screen dots update demo.screen, but the current Watch wrapper only reacts to demo.state, making those screen jumps ineffective.

10. **Missing data is displayed as healthy data.** `battery_level || 88` and `gsm_signal || 26` turn valid zero readings into healthy values. Map/parser defaults replace unknown location with Delhi; the parser even classifies an unknown message as a location update. Preserve zero with nullish checks, reject unknown input, and represent unavailable readings explicitly.

## Firmware implications

- `firmware/main/drivers/sim800l/sim800l.cpp` implements mock SMS, location and call requests; isRegistered/isReady return true and signal strength is fixed. A working call overlay does not imply a working cellular connection.
- `firmware/main/network/network_manager.cpp` logs Wi-Fi/HTTP/BLE operations and changes flags, without implementing the network operations represented by those functions.
- `firmware/main/voice/voice_trigger.cpp` reads microphone samples but contains no speech-model inference or invocation of the registered distress callback. “Help Me armed” is not evidence of working recognition.
- `firmware/main/storage/sd_logger.cpp:61` marks storage mounted after mount failure. File-open failure still sets recording true, and append counts bytes without a file. That can produce a recording indicator with no recording. Starting evidence while already recording also attempts to reacquire the same non-recursive mutex through closeAudioEvidenceRecording, a potential deadlock path.
- There is real peripheral, I2S audio, LVGL screen and WAV-writing implementation. Its physical behavior was not tested; the whole firmware should not be called dummy. The missing/faked communication and acknowledgement paths are specific blockers.
- UI, documentation and driver labels disagree between GSM/GPRS, Jio 4G and LTE-M/GNSS. Reconcile the supported hardware and expose capabilities supplied by that hardware rather than fixed marketing labels.

## Further functional and UX improvements

- Make demo versus connected-device mode persistent and obvious. Every simulated success should be identifiable without opening developer settings.
- Separate navigation from protection state: leaving SOS or changing wallpaper must not silently resolve or pause an incident. Use a persistent active-incident indicator.
- Display last device contact, location timestamp/source/accuracy, last confirmed recipient delivery and pending operations. “No alert received” is not equivalent to a confirmed safe check-in.
- Add onboarding for device pairing, guardian verification, a test alert, permissions and a readiness check. Include editable contacts and preferences with confirmation that the watch received changes.
- Provide a real Night Walk flow: guardian choice, destination or duration, check-in deadline, overdue handling and a clear session-end acknowledgement.
- Simplify wearer-facing copy: hide MCU/chip names, schema setup, sliced-icon descriptions and developer test controls in a diagnostics/developer view.
- Increase small text and touch targets. Both stylesheets contain many 7–9px labels. Replace clickable divs/spans with accessible controls, support keyboard holds/actions, and add proper modal focus/Escape behavior.
- Review mobile layout, text scaling, contrast and screen-reader reading order in a browser. The fixed-size watch and seven-column simulation row warrant testing; overflow was not visually verified in this audit.
- Keep strobe and animated alert effects controllable; reduced-motion support currently does not cover the complete simulator experience.
- Persist appropriate preferences and recorded memos. Add cleanup for oscillators, microphone streams, blob URLs and pending timers when views unmount.
- Correct session restore: AuthContext saves a device snapshot at login and restores that snapshot on reload rather than fetching current device state. Refresh currently reloads events only.
- Correlate resolution, delivery and evidence to an incident ID, not merely the current device state. Maintain a clear active/resolved incident record.
- Consolidate the two watch simulators or share their state machine and reusable components. Their current feature and behavior drift is already user-visible.
- Split the dashboard bundle so the initial watch screen does not load all portal/map functionality. Its production JS bundle is approximately 623 kB before gzip; Vite reported the size warning.

## Verification completed

- `dashboard`: npm run build passed.
- `sheshield-sentinel-guardian-main`: npm run build passed.
- Presentation app: tsc --noEmit failed with 17 diagnostics, including optional prop typing, dataset access, possible undefined wallpaper access, error-component typing and Vite configuration typing. A successful Vite build is not a successful typecheck.
- An isolated execution of the dashboard service with an in-memory browser-store substitute reproduced emergency → location_update → safe, acceptance of an arbitrary SENTINEL-prefixed invite, and demo history returned for an unrelated wearer ID. Supabase creation was stubbed; no network writes occurred.
- Source checks confirmed missing dashboard SYSTEM/TELEMETRY render branches and the missing naari_demo_shake listener.
- Direct execution of the SMS parser confirmed an unknown message becomes location_update with Delhi coordinates.
- No browser automation capability was available in this session. Layout, microphone behavior, real cloud configuration, runtime delivery and physical hardware remain unverified. No app behavior was modified.

## Recommended order

1. Correct safety-state transitions, unknown/stale telemetry, false success and access isolation.
2. Connect one real device → backend → guardian event path with transport acknowledgement, errors and persistence.
3. Repair blank screens, countdown synchronization, trigger/cancel controls, incident resolution and session restore.
4. Implement real contacts, delivery receipts, evidence playback/download and a complete Night Walk session.
5. Consolidate simulator implementations, improve accessibility/mobile behavior and simplify visual hierarchy/copy.
