# Naari Watch & Companion: Physical Hardware Acceptance Test Plan

**Document Version:** 1.0
**Target Specification:** `docs/ANDROID_COMPANION_PRD.md` Section 9
**Application Package:** `com.naari.companion`
**Companion App Version:** 1.0.0 (Debug APK: `app/build/outputs/apk/debug/app-debug.apk`)

> **CRITICAL SAFETY DIRECTIVE:**
> - NEVER place automated or manual test calls/texts to real emergency dispatch numbers (112, 1091, 911, 999).
> - All SMS and calling validation tests MUST use an explicitly pre-consenting personal test recipient.
> - Safe Demo mode MUST be visibly active whenever simulating emergency falls or countdowns.

---

## Hardware Test Environment Record

| Field | Value / Recorded Value |
|---|---|
| **Test Date** | `____________________` |
| **Lead Tester Name** | `____________________` |
| **Phone Model / Manufacturer** | `____________________` (e.g. Pixel 8, Samsung S24) |
| **Android OS Version** | `____________________` (API level / Android 14) |
| **Watch Hardware Revision** | `Naari Watch Ultra v1.0` (CO5300 QSPI 410x502 display) |
| **Watch Firmware Git SHA** | `____________________` |
| **APK SHA-256 Checksum** | `CF9ECB2A258D37AC5858A13AC9826DAA68287DB35F8122F8C862736FF6E2C7AD` |

---

## Acceptance Test Matrix

### 1. Pairing & Bond Lifecycle (PRD Section 3 & 5)

#### TEST 1.1: Fresh Out-of-Box Pairing via CompanionDeviceManager
- **Precondition:** Watch advertising BLE `Naari Watch` (`d973f2e0-b19e-11ee-a506-0242ac120002`). Companion app clean install.
- **Action:** Open companion app -> Setup Guide -> Pair Watch -> Select discovered device.
- **Expected Outcome:**
  - System pairing modal appears with 6-digit passkey.
  - Encryption and bond are negotiated successfully.
  - App displays bond confirmation and transitions to Readiness state.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 1.2: Bluetooth Toggle Reconnect & Backoff
- **Precondition:** Watch paired and in `Ready` state.
- **Action:** Disable Bluetooth in phone Quick Settings for 10 seconds, then re-enable.
- **Expected Outcome:**
  - Status immediately changes to `Bluetooth off on your phone`.
  - Upon enabling Bluetooth, serialized GATT queue connects automatically with capped backoff.
  - Mutual `hello` exchange (<15s) brings readiness back to `Protected & Ready`.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 1.3: Reconnect After Phone Reboot
- **Precondition:** Paired watch in range.
- **Action:** Restart the Android phone.
- **Expected Outcome:**
  - `BootReceiver` receives `ACTION_BOOT_COMPLETED` and starts `CompanionService`.
  - Ongoing foreground notification appears (`Naari Companion Active`).
  - BLE reconnects silently in the background without user intervention.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 2. Permissions & Truthful Readiness Derivation (PRD Section 4 & 7)

#### TEST 2.1: Individual Permission Revocation
- **Precondition:** App in `Protected & Ready` state with all permissions granted.
- **Action:** Go to Android Settings -> Revoke `SMS` permission, return to app.
- **Expected Outcome:**
  - Status immediately switches from `Ready` to `PermissionLimited`.
  - Capability sync payload sends `{"v":1,"op":"hello","sms":false,"calls":true,"location":true}` to watch within 15 seconds.
  - Watch updates internal routing and does not assume phone can dispatch SMS.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 2.2: Freshness Timeout (< 45 Seconds)
- **Precondition:** Connected watch.
- **Action:** Move watch out of radio range (or turn watch off).
- **Expected Outcome:**
  - If no `hello` acknowledgment arrives for >45 seconds, phone strictly refuses to show `Ready`.
  - Status updates truthfully to `Disconnected`. Zero false security promises.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 3. Guardians Synchronization (PRD Section 3 & 5)

#### TEST 3.1: Two-Slot Guardian Sync & E.164 Validation
- **Precondition:** Connected watch.
- **Action:** Add Guardian 1 (`+15555550101`, "Mom") and Guardian 2 (`+15555550102`, "Sister"). Tap "Sync to Watch".
- **Expected Outcome:**
  - Profile JSON serialized and written to reply characteristic:
    `{"v":1,"op":"profile","name0":"Mom","phone0":"+15555550101","name1":"Sister","phone1":"+15555550102"}`
  - Status chip transitions `Pending` -> `Saved to Watch`.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 3.2: Guardian Reordering (Primary Slot Allocation)
- **Precondition:** Guardians 1 and 2 saved.
- **Action:** Tap "Set as Primary" on Guardian 2.
- **Expected Outcome:**
  - Guardian 2 swaps to slot 0 (`phone0`), Guardian 1 shifts to slot 1 (`phone1`).
  - Full profile message re-synced to watch immediately.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 3.3: Persistence Across Reboot
- **Precondition:** Guardians saved.
- **Action:** Force stop app and restart phone.
- **Expected Outcome:**
  - Room database loads both guardians with primary designation intact.
  - Zero silent seeding or lost contacts.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 4. SMS Action Execution & Ledger Resilience (PRD Section 3 & 5)

#### TEST 4.1: End-to-End SMS with Real Callbacks
- **Precondition:** Approved personal test phone number set as guardian. SIM card inserted.
- **Action:** Watch triggers test SMS notification request ID.
- **Expected Outcome:**
  - Ledger creates `RECEIVED` entry before executing.
  - `SmsManager` sends multipart SMS.
  - `sent` callback marks ledger `SENT`.
  - `delivered` callback confirms delivery and marks ledger `DELIVERED`.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 4.2: Duplicate Notification & Reconnect Gate
- **Precondition:** Action in progress.
- **Action:** Watch sends duplicate BLE notification with identical `requestId`, or disconnects mid-flight.
- **Expected Outcome:**
  - `ActionDispatcher` detects active ledger entry.
  - Strictly ZERO duplicate second SMS is dispatched.
  - Reply status returned upon reconnection.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 5. Call Placement & Emergency Redirection (PRD Section 3 & 7)

#### TEST 5.1: Approved Contact Direct Call Launch
- **Precondition:** Approved test number. `CALL_PHONE` permission granted.
- **Action:** Watch triggers call action.
- **Expected Outcome:**
  - TelecomManager launches outgoing call to test number.
  - Reply characteristic receives `calling`.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 5.2: Emergency Number Interception (No Auto Calls)
- **Precondition:** Target number matches emergency string (`112`, `911`, `1091`, `999`).
- **Action:** Trigger call action.
- **Expected Outcome:**
  - App strictly REFUSES automated background call placement.
  - Opens phone system dialer with number pre-filled.
  - Reply returned as `needs_phone`. Zero automated calls to emergency services.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 6. One-Shot Location Provider (PRD Section 3 & 7)

#### TEST 6.1: Fresh GPS Location Fix
- **Precondition:** Location permission granted. Clear sky view / GPS lock.
- **Action:** Trigger `location` request.
- **Expected Outcome:**
  - Fresh coordinate acquired within 30-second target window with accuracy <= 100m.
  - Exact coordinates, accuracy, and provider returned to watch.
  - Zero fabricated coordinates.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 6.2: Cached Fallback Age Labeling
- **Precondition:** Device indoors without immediate GPS lock.
- **Action:** Trigger location request.
- **Expected Outcome:**
  - If fresh GPS times out, fallback location is explicitly labeled with its age in seconds/minutes.
  - Never masquerades as fresh GPS fix.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 7. Watch Controls & Setting Transitions (PRD Section 3 & 6)

#### TEST 7.1: Wallpaper Switching (5 Built-in Faces)
- **Precondition:** Protocol v2 firmware negotiated.
- **Action:** Select Wallpaper 3 on Watch Controls screen.
- **Expected Outcome:**
  - UI shows `Applying...`.
  - Watch displays new wallpaper face.
  - Watch sends acknowledgment response; UI switches to `Applied`.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 7.2: Truthful Gating on V1 Firmware
- **Precondition:** Watch running legacy Protocol v1 firmware (no v2 control service).
- **Action:** Open Watch Controls tab.
- **Expected Outcome:**
  - Controls are visibly disabled with notice: `Not supported by this firmware`.
  - Zero phantom control messages sent to v1 reply characteristic.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 8. Voice Memo Recordings (PRD Section 3 & 6)

#### TEST 8.1: Chunked Transfer & SHA-256 Verification
- **Precondition:** Watch has 1 recorded memo in storage.
- **Action:** Tap "Sync Voice Memos" on Recordings tab.
- **Expected Outcome:**
  - Chunks stream across binary characteristic with sequence and offset.
  - Temp PCM file assembled.
  - SHA-256 hash verified against watch header hash.
  - Final standard 44-byte WAV committed atomically.
  - Audible on phone built-in speaker.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 8.2: Resumable Transfer on Interruption
- **Precondition:** Active audio transfer in progress.
- **Action:** Disconnect BLE midway (e.g. walk away), then reconnect.
- **Expected Outcome:**
  - Phone requests transfer resuming at recorded byte offset.
  - Transfer finishes without lost or duplicate samples.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 8.3: Local-Only Deletion Policy
- **Precondition:** Transferred WAV file stored on phone.
- **Action:** Tap Delete Memo on phone app.
- **Expected Outcome:**
  - Local WAV file and Room database record deleted from phone.
  - Watch copy is strictly PRESERVED and untouched.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 9. Safety Incidents & Demo Mode (PRD Section 3 & 6)

#### TEST 9.1: Safe Demo Mode Isolation
- **Precondition:** Open Incidents screen.
- **Action:** Tap "Simulate Fall Incident (Demo Mode)".
- **Expected Outcome:**
  - Card visibly marked with amber badge: `DEMO MODE — ZERO DISPATCH`.
  - 30-second countdown runs.
  - Strictly ZERO SMS messages or phone calls are placed upon timer completion.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 9.2: Deliberate Remote Disarm with PIN
- **Precondition:** Active incident countdown.
- **Action:** Tap Disarm -> Enter incorrect PIN -> Enter correct PIN (`1234`).
- **Expected Outcome:**
  - Incorrect PIN rejected with tactile warning and error message.
  - Correct PIN disarms incident, cancels watch alarm, and logs event in Room audit log.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 10. Background Reliability & Power Management (PRD Section 3 & 7)

#### TEST 10.1: Screen-Off Deep Sleep Reliability
- **Precondition:** Phone locked / screen off for 30 minutes with watch paired.
- **Action:** Trigger SOS on watch hardware.
- **Expected Outcome:**
  - Phone wakes within 2 seconds.
  - `CompanionService` receives notification and dispatches guardian action.
- **Result:** `[ ] PASS   [ ] FAIL`
---

### 11. Preventative Safety & Awareness Suite

#### TEST 11.1: Safe Walk Journey Timer (Safe Arrival Flow)
- **Precondition:** Guardians configured.
- **Action:** Select 10m/15m/30m on Home -> Tap "Start Safe Walk" -> Tap "I've Arrived Safely".
- **Expected Outcome:**
  - Active countdown runs in real-time.
  - Ongoing notification displayed in notification tray.
  - Tapping "I've Arrived Safely" cancels timer immediately and displays green "Arrived Safely" confirmation.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 11.2: Safe Walk Journey Expiration (Dead-Man's Switch SOS)
- **Precondition:** Safe Walk timer active with pre-consenting guardian recipient.
- **Action:** Allow timer to reach 00:00 without tapping arrival confirmation.
- **Expected Outcome:**
  - Phone acquires fresh GPS coordinates.
  - Automatically sends emergency SOS SMS to guardians with live Google Maps link.
  - Phone sounds high-priority emergency alarm.
  - Incident logged in durable Activity History ledger.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 11.3: Anti-Loss Separation Alert ("Watch Left Behind")
- **Precondition:** Watch connected in Ready state.
- **Action:** Turn off watch or walk out of Bluetooth range.
- **Expected Outcome:**
  - Phone sounds distinct chime and vibrates.
  - High-priority heads-up notification: *"Naari Watch Disconnected: Did you leave your watch behind?"*.
  - Amber alert card appears on Home Screen with "Find on Maps" button linking to last connected GPS coordinates.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 11.4: One-Tap "Reached Safely" Check-In
- **Precondition:** Guardians configured with valid numbers.
- **Action:** Tap "Send Safe Check-in" on Home screen.
- **Expected Outcome:**
  - Acquires current GPS coordinates.
  - Sends reassuring SMS to all active guardians: *"Hi [Name], I have reached safely at [Maps Link] - Sent via Naari OS"*.
  - Displays green confirmation chip on card and records dispatch in Activity History ledger.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 11.5: Low Watch Battery Warning
- **Precondition:** Watch connected.
- **Action:** Watch battery level drops $\le 20\%$.
- **Expected Outcome:**
  - Phone posts alert notification reminding user to recharge watch for SOS safety.
  - Home screen watch battery card displays red warning badge.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

### 12. Instant Access: Quick Settings & Home Screen Widget

#### TEST 12.1: Quick Settings "Safe Check-in" Tile
- **Action:** Pull down notification shade from any screen -> Tap "Safe Check-in" tile.
- **Expected Outcome:**
  - Tile reflects active state with subtitle "Acquiring GPS fix".
  - Reassuring check-in SMS dispatched with live Google Maps link.
  - Shows toast confirmation and updates tile to "Check-in Sent ✓".
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 12.2: Quick Settings "Safe Walk" Tile
- **Action:** Pull down notification shade -> Tap "Safe Walk" tile.
- **Expected Outcome:**
  - When Idle: Starts 15-minute Safe Walk timer and switches tile to Active with remaining minutes.
  - When Active: Single tap confirms arrival safely and resets tile.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

#### TEST 12.3: 1-Tap Home Screen Safety Widget
- **Action:** Add Naari Safety widget to Home screen launcher.
- **Expected Outcome:**
  - Displays dark OLED minimalist container with live status.
  - Tapping "Check-in" button sends reassurance SMS with live GPS in 1 tap.
  - Tapping "Safe Walk" starts 15m timer; button highlights green and switches to "Arrived".
  - Tapping "SOS" triggers immediate emergency distress dispatch.
- **Result:** `[ ] PASS   [ ] FAIL`
- **Notes:** `__________________________________________________`

---

## Acceptance Sign-Off

| Role | Name | Signature | Date |
|---|---|---|---|
| **Android Lead** | `____________________` | `____________________` | `__________` |
| **Firmware Lead** | `____________________` | `____________________` | `__________` |
| **QA / Safety Officer** | `____________________` | `____________________` | `__________` |
