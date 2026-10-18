# Naari OS v8.4: safety fixes and product direction

## What was wrong

- Crown, side-button, long-press and emergency-screen tap handlers called the final cancellation function directly. The displayed hold flow was bypassable. Its nominal duration was also only two seconds.
- Sound Studio changed the sound currently playing, while `playSirenAlarm(true)` always chose `POLICE_WAIL`. There was no independent persisted SOS tone.
- Torch mode three alternated red/black on a 250 ms timer, with additional UI scheduling delay. It was not a fast red/blue attention pattern.
- Fall detection was a strict free-fall → impact → stillness heuristic. It could miss wrist falls without sustained low acceleration, short impacts between samples, and sequences with continuing wrist movement. Monitoring was already enabled in the user's test. Throwing an unworn watch is not a validation of detecting falls by a wearer.

## Current fall logic

The QMI8658 accelerometer is configured for ±16 g and the gyroscope for ±1024 degrees/second, both at 250 Hz. Registers are configured with the sensors disabled, then enabled and read back. The sampling task targets one read every 4 ms, rather than delaying another 10 ms after each read. Actual timing depends on bus/task scheduling. Read-back values are CTRL1=0x40, CTRL2=0x35, CTRL3=0x65. Conversion remains 2048 LSB/g and 32 LSB/(degree/second), matching the configured ranges in the [QMI8658 datasheet](https://files.waveshare.com/wiki/common/QMI8658C.pdf).

1. Compute acceleration magnitude `sqrt(ax²+ay²+az²)` and angular-speed magnitude `sqrt(gx²+gy²+gz²)`.
2. Accept either candidate path:
   - Free-fall path: acceleration below **0.45 g for at least 80 ms**, followed by an impact of **at least 3 g within 1.2 seconds** of entering the impact-wait phase.
   - Rotating-wrist path: impact of **at least 3 g**, corroborated by rotation of **at least 120 degrees/second within the previous 300 ms**, without requiring the free-fall phase.
3. After impact, require **2.5 continuous seconds** of acceleration between **0.75 and 1.25 g** and rotation below **45 degrees/second**. The candidate expires after 10 seconds without that stillness.
4. A detected pattern starts the existing **30-second cancellable SOS countdown**. It is not proof of injury. The detector then has a 60-second cooldown; malformed samples, backward time and sample gaps over 100 ms reset an incomplete sequence.
5. If monitoring is disabled, ordinary motion never generates a fall SOS. The enabled preference persists.

This is a deterministic prototype heuristic. It can miss slow slides, soft falls, falls with continued movement, or impacts outside the sampling windows. It can mistake a dropped unworn watch or some abrupt wrist motions for a fall. It has no validated wear-detection signal and no clinical accuracy claim. Adding the second candidate path requires measuring false positives before relying on it outside a prototype demonstration.

## Sensor test and field diagnostics

Open **Fall Detection → Test sensor · no SOS**. For 30 seconds the app runs the actual physical IMU/detector, shows live acceleration and rotation, and reports settling/detected state. A detected pattern during this explicit test is logged and counted but cannot emit the fall SOS event. Stopping, timing out or leaving the screen restores the previous enabled setting; the sampling task resets the test sequence on the mode transition so a partial test cannot spill into live detection.

This is different from the previous visual-only alert demo. The watch logs live acceleration, rotation, phase, sample count and enabled/test flags every five seconds. Around 1 g while stationary, plausible changes during gentle movement, and an increasing sample count are useful checks that the sensor path is alive. Sensor test is not a reason to throw the watch or stage a person's fall. Use instrumented, padded bench equipment and labelled recordings for impact validation; ordinary hand movement only checks live telemetry.

## Cancellation, torch and tone

- Every emergency disarm tap or physical-button action opens the confirmation screen. It cannot turn SOS off directly.
- Cancellation requires a continuous **three-second touch** inside the visible hold target. Release, movement outside it, backward time or a gap over 500 ms in the input stream resets the hold. Passing a progress value directly cannot authorize cancellation.
- Crown/side-button short, long, double or triple actions cannot bypass this confirmation. The emergency countdown continues during confirmation.
- Third torch mode alternates full-screen red and blue on 100 ms phases, using elapsed time rather than accumulating timer drift. Actual refresh speed is bounded by the AMOLED transfer and UI task. No visibility distance or emergency-vehicle approval is claimed.
- Selecting any of the five tones saves the SOS selection to NVS and previews it. Later speaker tests/find-watch sounds do not overwrite it. SOS startup and post-countdown dispatch both read the saved selection. A failed preference write leaves the previous selection and shows an error. Volume remains independently adjustable; a quieter chosen tone is not relabelled as a loud siren.

## Tests and hardware verification

The pure detector tests cover rest/walking, isolated bumps and low-g dips, complete free-fall sequences, rotating impacts without free-fall, stale rotation, continuing movement, cooldown, missing/invalid data. Hold tests cover early/exact deadlines, release, lost input and clock reversal. The actual native C++ UI tests exercise tap/crown/side/long-press bypass attempts, leaving the hold target, three-second completion, every selected tone and alternating torch frames. Native tests use AddressSanitizer and UndefinedBehaviorSanitizer.

A normal firmware build keeps `NAARI_HARDWARE_SELFTEST=OFF`. No test sends a real SMS or call. Hardware boot/read-back and live telemetry findings are recorded at the end of this file after flashing. Physical fall sensitivity, false alarms, acoustic output and long-range visibility still require measured validation; passing synthetic sequences does not establish those results.

## On-watch recording library

Voice Memos now opens **Saved Memos** when persisted recordings exist. Browse three recordings per page, select one and use **Play recording** / **Stop playback**. The list shows duration, a known UTC date/time (or “Time unavailable”) and verified phone-sync status. **New recording** starts a new memo; the capture screen also has a Saved memos shortcut. The archive remains after restarting the watch. Watch v8.5 adds a confirmed Delete recording action; see [watch-only deletion](RECORDING_DELETE_V85.md).

Playback reads the selected recording's ID from persistent storage, checks its size and SHA-256, then copies PCM to the existing audio buffer. It does not assume the latest RAM recording is the selected memo. File IO runs on a background worker; stopping, leaving the screen or starting an alarm invalidates its playback generation so an old load cannot restart sound afterward. The existing six-recording retention policy and phone verification requirement remain in effect. Previously volatile recordings that were lost before persistent storage existed cannot be recovered.

The real PCM reader is tested with existing files, capacity limits, invalid IDs, extra/truncated data and checksum mismatch under ASan/UBSan. Native UI tests cover two archive pages, selecting an older recording, starting/stopping playback and recording a new memo while keeping the archive intact.

Hardware verification on 18 October 2026: the connected watch recovered three existing memos after reboot; monitoring was enabled, CTRL1/2/3 read back 0x40/0x35/0x65, and samples increased by about 1,250 every five seconds with acceleration around 1 g. Startup minimum free stack was 3,840 bytes after increasing the main task stack to 8 KiB; the previous 3.5 KiB startup stack overflowed while scanning existing recordings. Sensor-task stack was also increased to 4 KiB for diagnostics. The final library build recovered six existing memos. Its hardware log shows two different stored recordings loaded successfully (68,096 and 19,968 samples) and playback completion through the audio task. The saved SOS tone also reloaded as Hi-Lo Horn after the user changed it, confirming preference persistence. These logs establish the storage-to-audio path, not measured acoustic loudness. Real fall sensitivity and user-confirmed audible output still require validation; healthy sensor telemetry is not proof of fall recall.

## Market context: what is already common

Research checked 17 October 2026. These are vendor descriptions, not a comparative performance test:

| Existing product | Advertised capability | Implication for Naari |
| --- | --- | --- |
| [Apple Watch](https://support.apple.com/en-ie/108896) | Hard-fall detection, wearer prompt, escalation when immobile and emergency-contact notification. | Fall detection and SOS are already established features. |
| [Samsung Galaxy Watch](https://www.samsung.com/us/support/answer/ANS10002904/) | Hard-fall detection, emergency countdown/calls and information sharing with emergency contacts. | A polished smartwatch with those functions alone is not a distinctive position. |
| [invisaWear](https://invisawear.com/pages/faqs) | Discreet safety accessories using Bluetooth and the phone to send alerts; active safety plan required for help features. | Discreet activation and a phone relay are also established. |
| [Garmin](https://www.garmin.com/en-CA/connectivity/smartwatches/forerunner-945-lte/feature-requirements/) | Activity incident detection, location sharing, phone or supported LTE transport depending on device/subscription. | LTE and location sharing are not individually unique either. |

## Recommended differentiation: verified response for everyday local journeys

Proposed positioning: **“A safety watch that shows who is responding, not just that an alert was sent.”** This is a product direction to validate, not a claim that no competitor offers acknowledgement or escalation.

Prioritize one clear scenario: a student or shift worker travelling between college/work, transit and home. Pilot with a college safety team and consenting guardians. Build the complete response journey around that scenario instead of competing with Apple on general smartwatch features.

### 1. Guardian acknowledgement and escalation — highest priority, not implemented yet

A guardian explicitly accepts an incident: “I am responding.” Show that person on the watch and wearer app. Escalate to the next opted-in guardian after a bounded period without acknowledgement. Keep transport submission, carrier delivery (when actually reported), guardian acknowledgement and resolution as separate states. An SMS success callback never means a person has seen it.

For an MVP, use an authenticated expiring incident link or guardian-app acceptance, with an incident ID and audit trail. A backend/guardian receiving surface is required; the current BLE Applied response only confirms watch commands, not human response. No automatic police/campus integration should be advertised until that partner actually participates.

Demo evidence: guardian A ignores the alert; guardian B accepts; the watch names B and avoids duplicate escalation.

### 2. Honest readiness and independent connectivity — partially implemented

Before a journey, show which route can currently accept a request: phone, LTE, or neither; guardian profile verified; location age; battery; recording storage. Re-check when conditions change. Current BLE handshake/profile read-back, action correlation, local memo storage and guarded pre-queue LTE/phone routing are a foundation. The new external modem still needs power, antenna, network, battery and wearable-size validation. It does not provide satellite or no-network coverage. Never promise automatic failover after uncertain submission.

Demo evidence: disconnect the phone and prove an actual modem SMS with a real carrier; show a clear failure state when neither route is available.

### 3. Proactive journey check-in with local context — requires further work

An opted-in expected-arrival time, “extend journey” control, and missed-check-in escalation can help before a user is able to press SOS. Localize prompts and guardian alerts for the pilot's languages; do not claim the placeholder voice-trigger code recognizes distress speech. If campus security participates, include a consented campus responder alongside family, with an actual response workflow and service hours.

Demo evidence: an overdue arrival is acknowledged by the wearer, or escalated to the configured guardian when there is no response. Track false escalations and comprehension across languages.

### 4. Privacy-preserving incident evidence — memos implemented; incident workflow not complete

Build on local recordings and verified phone copies. Make recording visible and incident-scoped, use explicit retention/export controls, and protect any future remote storage. Do not imply that a raw PCM checksum proves legal authenticity: it checks transfer integrity, not who recorded it or whether it was altered before hashing. Encryption, signed incident metadata and consent/retention design need further implementation.

Demo evidence: disconnect mid-transfer, reconnect and recover a verified playable file without duplicate recordings. Show an explicit deletion/export workflow.

### 5. Measured reliability and practical ownership cost — validation work

Publish sensor recall/false-alarm measurements on a labelled test set, alert-to-human-acknowledgement time, background reconnect success, independently measured runtime, and the full device + SIM + service cost. Avoid “100% accurate,” “105 dB,” “always connected,” “cheapest” or subscription-free claims without evidence. Prefer a small successful supervised pilot over a long list of unvalidated emergency features.

## Suggested next build order

1. Finish controlled phone/watch and carrier tests; collect labelled sensor traces.
2. Add guardian acceptance and timeout escalation, with one real responder.
3. Complete journey check-in and transparent readiness states.
4. Validate with a small campus pilot; measure response times, missed alerts and false alarms.
5. Refine modem integration, battery life and enclosure from that evidence.

A silent/duress mode can be researched later with users and responders. It must not be bolted onto cancellation as a surprise; sound, vibration, escalation and recovery behavior need explicit user understanding and careful testing.

Final library firmware SHA-256: `fda7d88ce7d2069f8b0d5f5c4d63e541edb934c15594ecd312df7d154f299601`. Normal build, hardware self-test OFF. Flash log: `/tmp/naari-watch-flash.log`; final hardware log: `/tmp/naari-watch-library-boot.log`.
