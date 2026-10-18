# Naari Android Companion: Android Studio Master Prompt

## How to use this file

1. Create a new empty project folder (or open your repo) in Android Studio.
2. Copy `ANDROID_COMPANION_PRD.md` into `docs/ANDROID_COMPANION_PRD.md` inside the project. The agent can only read files that are in the project.
3. Open Gemini in Android Studio, switch to **Agent mode**, and paste everything under **THE PROMPT** below.
4. The agent will create `docs/TASKS.md`, do ONE task, stop, and report. Reply `continue` to start the next task. If it drifts, reply: `Re-read docs/TASKS.md and the rules, then continue with the current task.`
5. After each task, run the build yourself and check the gate (see Essential Checklist at the bottom).

Note: Android Studio's agent works on the Android side only. The watch firmware is a separate repo. The agent will write the firmware requirements to `docs/FIRMWARE_CHANGES.md` so you can apply them to the firmware project.

---

# THE PROMPT (copy from here)

You are a senior Android engineer (Kotlin, Jetpack Compose, BLE, Room, coroutines). You will build the **Naari Android Companion** app from the product requirements in `docs/ANDROID_COMPANION_PRD.md`.

## STEP 0: Read before doing anything

1. Read `docs/ANDROID_COMPANION_PRD.md` completely, every section, including sections 3, 5, 6 and 7. Do not skim.
2. Create these files:
   - `docs/TASKS.md`: the task list below, each with a status (`TODO`, `IN PROGRESS`, `DONE`, `BLOCKED`) and notes.
   - `docs/ASSUMPTIONS.md`: every assumption you make, and every open question for the human.
   - `docs/FIRMWARE_CHANGES.md`: every change the watch firmware must make so the app works (profile read-back, control/events service, recording catalog and transfer, UUIDs, payload limits).
3. Summarize the PRD back to me in 10 lines or fewer and list anything ambiguous. Then start Task 1.

## WORKING RULES (apply to every task)

- **One task at a time.** Do only the current task. When it is finished, update `docs/TASKS.md`, run the gate (build, tests, lint), report results, and **stop and wait** for me to say `continue`.
- **Each task report must contain:** what was done, files created or changed, commands run and their results, what was NOT done, and the next task.
- **If a build or test fails,** fix it before reporting. If you cannot, mark the task `BLOCKED` and explain why. Never hide failures.
- **Never fake it.** No placeholder screens that look functional. If a feature depends on firmware that does not exist yet (remote settings, recording sync), show the control as "Not supported by this watch firmware" based on capability discovery. Do not simulate success.
- **Test fakes live only in `src/test` or `src/androidTest`,** never in release code.
- **Truthful states.** Never show "ready" because BLE is connected. Never show "saved" before the watch confirms. `sent` is not `delivered`. `calling` is not connected. Unknown outcomes stay unknown.
- **Safety rules:** Never auto-resend an uncertain SMS or call. Persist the request in the ledger BEFORE any side effect. Never place or test calls to emergency numbers (112, 1091, 911, etc.). Use only example numbers like `+15555550101` in tests and never dial or text them for real.
- **Privacy:** Do not log phone numbers, names, locations or recordings. Exclude sensitive data from backups. No cloud upload, no analytics, no secrets or signing keys in source.
- **Tech stack:** Kotlin, Jetpack Compose (Material 3), coroutines and Flow, ViewModel, Hilt for DI, Room, DataStore, WorkManager (maintenance only), Gradle Kotlin DSL with a version catalog and pinned versions. minSdk 26. targetSdk and compileSdk = current stable required by Google Play (verify and note it in ASSUMPTIONS.md).
- **Architecture:** Bluetooth, location and action work happen outside composables (in a service, repositories and use cases). Use repository interfaces. Serialize all GATT operations through a single queue.
- **Code quality:** Small focused files, meaningful names, KDoc on public protocol classes, no TODO stubs left behind without an entry in TASKS.md.
- **Ask me** (and stop) only when a decision truly blocks you. Otherwise choose a sensible default and record it in `docs/ASSUMPTIONS.md`.

## TASK LIST (execute in order)

### Task 1: Project scaffold
- Create the Gradle project `android-companion` (or use the open project), package `com.naari.companion`, Kotlin DSL, version catalog, Gradle wrapper, documented JDK 17.
- Create the package layout: `designsystem`, `onboarding`, `home`, `guardians`, `recordings`, `watch`, `ble`, `protocol`, `actions`, `location`, `data` (Room), `ledger`, `service`, `di`.
- Set up Hilt, Room, DataStore, Compose, Navigation, and test dependencies.
- Add `.gitignore` (no keystores), and a README stub.
- **Gate:** `./gradlew assembleDebug` succeeds and the app launches to a blank themed screen.

### Task 2: Design system
- Implement the PRD section 4 tokens: colors (light and dark), spacing scale (4, 8, 12, 16, 24, 32 dp), card shapes (20 to 24 dp), typography (system font; Title 28-32sp, Section 20-22, Body 16, Label 13-14), motion durations (180-280 ms) with reduced-motion support.
- Build reusable components: `NaariCard`, `PrimaryButton`, `SecondaryButton`, `DangerButton`, `StatusChip`, `SectionHeader`, `InlineError`, `BottomSheet` wrapper, `ReadinessBadge`.
- All controls at least 48 dp, with labeled content descriptions. Original vector icons only; no SF fonts.
- Add a `@Preview` gallery for light and dark and 200% font scale.
- **Gate:** build passes, previews render in both themes.

### Task 3: App shell and navigation
- Bottom navigation with four destinations: **Home, Guardians, Recordings, Watch**. Edge-to-edge with safe insets and predictive back.
- Labeled entries for Settings, Permissions and Activity History.
- Screens are empty but real (title, empty state), not fake data.
- **Gate:** navigation works, back behavior is correct, layout is fine at 320 dp width.

### Task 4: Permissions and readiness model
- Create a `PermissionsRepository` exposing Flow state for: Bluetooth (Nearby Devices on Android 12+, legacy on older), SEND_SMS, CALL_PHONE, location (precise vs approximate vs denied), notifications.
- Create a `ReadinessState` model with separate states: BluetoothOff, Disconnected, Connecting, Paired, ConnectedServiceUnavailable, Ready, PermissionLimited.
- Request each permission separately with a feature-level explanation. Contacts permission is optional; manual entry must work.
- **Gate:** unit tests for readiness derivation. Revoking a permission updates state without a crash.

### Task 5: Protocol v1 codec
- Implement exactly PRD section 5: service UUID `d973f2e0-b19e-11ee-a506-0242ac120002`, request characteristic `...e1`, reply characteristic `...e2`.
- Parse the 4-byte little-endian request ID notification. Build and parse JSON for `hello`, `profile`, watch requests (`sms`, `location_sms`, `call`, `location`) and replies (`received`, `sent`, `delivered`, `located`, `calling`, `needs_phone`, `failed`).
- Enforce limits: JSON <= 480 UTF-8 bytes, message <= 256 bytes, names <= 32 bytes with no truncated UTF-8, phone digits with optional leading `+` and 3-20 characters, latitude within +/-90, longitude within +/-180.
- Unknown operations fail explicitly.
- **Gate:** protocol conformance unit tests including largest UTF-8 payload, oversize rejection, truncated multibyte names, invalid numbers.

### Task 6: BLE session and operation queue
- Use CompanionDeviceManager for pairing, filtered by the service UUID, with user selection and the Android system pairing confirmation. Verify service and bond identity, not the name alone.
- Implement: connect, MTU negotiation, service discovery after every connection, subscribe to the request characteristic, full long-read of the request characteristic (`{}` means no pending request), reliable writes sized to the negotiated MTU, serialized GATT queue, capped exponential-backoff reconnect.
- Send the hello message at startup, after permission changes and about every 15 seconds. Capabilities must reflect real permissions.
- Expose connection and readiness state as Flow.
- **Gate:** unit tests for the queue and backoff using fakes (in test source set). Document manual hardware test steps in `docs/HARDWARE_TEST_PLAN.md`.

### Task 7: Database and request ledger
- Room entities: Guardian, ActionRequest (ledger), Incident, Recording (metadata only for now).
- The ledger persists bonded-device + request ID + payload identity BEFORE any side effect, with states: Received, Dispatching, Sent, Delivered, Located, Calling, NeedsPhone, Failed, UnknownOutcome.
- A crash between dispatch and result becomes `UnknownOutcome` on next start and is NEVER auto-replayed. Re-notification of the same request is deduplicated.
- **Gate:** Room and ledger tests, including the simulated crash and duplicate notification cases.

### Task 8: Guardians
- UI: add, edit, reorder (explicitly sets the primary), remove. Two slots only for v1. Validation for international numbers, optional system contact picker, no silent seeding.
- Sync the complete two-slot `profile` message. Show pending, saved and failed per PRD. Note in the UI and code that v1 has no read-back, so "saved" means "accepted by the watch handler" until firmware adds read-back (record this in FIRMWARE_CHANGES.md).
- Reject payload overflow before writing.
- **Gate:** tests for Unicode byte limits, invalid numbers, reorder behavior; UI works with TalkBack labels.

### Task 9: SMS executor
- Handle `sms` and `location_sms` using explicit SEND_SMS consent, dual-SIM subscription selection, multipart messages, per-part sent and delivery PendingIntent callbacks, and aggregated outcomes.
- Reply `received` first, then `sent` when Android reports success, and `delivered` only if delivery reports confirm. Missing SIM or no service returns `failed` with a truthful detail.
- **Gate:** tests of the aggregation logic with fakes. Manual test only with an explicitly approved recipient.

### Task 10: Call executor
- Use TelecomManager with CALL_PHONE consent. `calling` means launch accepted, not answered.
- If direct placement is unavailable or the number is an emergency number, open the dialer and return `needs_phone`. Never label a dialer opening as a placed call. No auto test calls.
- **Gate:** tests for emergency-number detection and status mapping.

### Task 11: Location provider
- One-shot fresh location with a bounded timeout. Track timestamp, accuracy and provider. Target freshness <= 30 seconds and accuracy <= 100 m; show actual quality when unmet. Handle approximate, denied and offline states.
- Cached fallback is labeled with its age and never presented as fresh GPS. `location_sms` composes a readable alert with a map link; if location fails, send an explicit "location unavailable" text (when SMS is permitted) and report truthfully.
- **Gate:** tests for fresh, stale, denied, timeout and approximate paths. No fabricated coordinates anywhere.

### Task 12: Action dispatcher and history
- Wire BLE request notifications to the ledger, then to the SMS, call and location executors, then reply on the reply characteristic.
- Build an Activity History screen with redacted failures and a manual retry that explains duplicate risk. No automatic replay of uncertain actions.
- **Gate:** integration tests with fake BLE and fake executors; duplicate notification and reconnect produce no second send.

### Task 13: Foreground service and background reliability
- A companion foreground service with the appropriate service types and a user-visible notification. Obey background-start and while-in-use location rules.
- Explain battery restrictions only when they affect readiness. Never promise reconnection after force-stop; show recovery guidance instead.
- WorkManager only for deferrable maintenance, not for the live BLE listener.
- **Gate:** service starts, survives screen-off in a manual test, and states are reported honestly.

### Task 14: Onboarding and Home
- Onboarding flow: install, pair, grant capabilities (one at a time with explanations), set guardians, verify readiness, wear the watch.
- Home screen: centered original watch illustration, real readiness, battery and last-seen, three concise action tiles. Failures shown inline near the action. Do not flood with diagnostics.
- **Gate:** the flow works end to end with real state; readiness never shows Ready without a fresh hello acknowledgment and permissions.

### Task 15: Watch settings (capability-gated)
- Design the additive control/events contract (PRD section 6) in `docs/PROTOCOL_V2.md` with proposed UUIDs, payload limits, versioning and operations: get_status, get_profile, set_wallpaper, set_brightness, set_volume, set_fall_enabled, set_mode, find_watch, sync_time, safe check-in. Mirror every requirement into `docs/FIRMWARE_CHANGES.md`.
- Implement the Watch screen. Show only controls the negotiated capabilities support; otherwise show "Not supported by this firmware". States: pending, applying, applied, failed. Never show a switch as saved before device confirmation.
- Do NOT send invented v2 JSON to the v1 reply parser.
- **Gate:** tests for capability gating and state transitions.

### Task 16: Incidents view
- Show monitoring readiness, last sync and enabled state; relay incident ID, trigger, countdown and action results from watch events when the capability exists. The watch is the authority for the countdown; the phone never starts its own.
- Demo mode is visibly marked and never sends messages or calls. Include the honest fall-detection disclaimer.
- Remote SOS cancel only with deliberate user action and logging.
- **Gate:** tests that the phone never creates a duplicate countdown or alert.

### Task 17: Recordings
- Room catalog, app-owned storage, WAV (16 kHz, mono, PCM16) writer, available-space check, states: captured-on-watch, waiting, transferring, verified-on-phone, failed.
- Transfer client per PRD: temp file, verify length and SHA-256, atomic commit, then durable-save acknowledgment; resume by offset; handle duplicate chunks, partial files and low storage.
- Playback, rename, delete (phone copy only), export via FileProvider. No cloud upload.
- Gated on firmware capability; show "Recording sync not supported by this firmware" until then.
- **Gate:** unit tests with a fake transfer source (test source set only) covering resume, duplicate chunks, hash mismatch, low storage.

### Task 18: Accessibility, performance and quality
- TalkBack labels, contrast checks, 200% font scale, 320 dp width, reduced motion, haptics respecting system settings.
- Compose UI tests, instrumented BLE test scaffolding, lint clean.
- Profile release-build scrolling and navigation; do not claim performance without measurement.
- **Gate:** `./gradlew test lint assembleDebug` passes.

### Task 19: Documentation and release
- Update the README: build, install via `adb install`, pairing guide, permissions, known limits.
- Write `docs/HARDWARE_TEST_PLAN.md` covering every acceptance test in PRD section 9, with blanks for results. Write release signing instructions (no keys in source). Produce the debug APK and a SHA-256 checksum.
- Final report: what is implemented, what was verified (unit/emulator only vs physical device), and an honest list of remaining blockers, especially the firmware work.
- **Gate:** the APK is built and the checksum is recorded.

## FINAL REMINDER

Start now with STEP 0, then Task 1. Stop after each task and wait for `continue`.

# END OF PROMPT

---

# Essential Checklist (check these yourself after each stage)

## Before you start
- [ ] PRD copied to `docs/ANDROID_COMPANION_PRD.md`
- [ ] Android Studio is up to date, JDK 17, SDK installed
- [ ] Android phone with developer options and USB debugging on
- [ ] Watch charged, with BLE firmware flashed
- [ ] Firmware repo available separately for the firmware changes

## After every task
- [ ] Project builds with no errors
- [ ] Tests pass for that task
- [ ] `docs/TASKS.md` status updated
- [ ] No fake or placeholder success states added
- [ ] No phone numbers, names or locations in logs

## Safety (never skip)
- [ ] No automatic resend of uncertain SMS or calls
- [ ] Request saved in the ledger before any SMS or call
- [ ] No test calls or texts to emergency numbers
- [ ] SMS tests only to a recipient who agreed
- [ ] "Ready" only when permissions, bond, and a fresh hello all agree

## Before calling it done
- [ ] Pairing works on the real watch, including reconnect after Bluetooth toggle
- [ ] Two guardians save and survive restarts
- [ ] SMS, call and location behave truthfully with permissions granted and denied
- [ ] Largest 480-byte payload tested on real hardware
- [ ] Screen-off, battery restriction and reboot behavior tested
- [ ] `./gradlew test lint assembleDebug` passes
- [ ] APK and checksum produced
- [ ] `docs/FIRMWARE_CHANGES.md` handed to whoever updates the watch firmware
