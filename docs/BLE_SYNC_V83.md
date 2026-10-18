# Watch v8.3 + Android companion 1.1

This update changes the actual ESP-IDF firmware and `Naari-os-app/` Android project. The website is unaffected. Install the companion APK and use watch v8.3 together; older watch firmware does not provide the control/audio service required by this app.

## Install and pair

1. Install `Naari-os-app/app/build/outputs/apk/debug/app-debug.apk` on Android 8.0 or later. Package: `com.naari.companion`, version 1.1 (code 2). It is a debug-signed testing APK, not a Play Store release.
2. Allow Nearby devices/Bluetooth, SMS, Phone and Location when requested. Enable phone Bluetooth and location services. Grant notifications for the visible companion service. These permissions are separate: Bluetooth pairing alone does not authorize SMS/calls/location.
3. Open **Phone Link → Start pairing** on the watch. Its pairing window lasts 60 seconds; enter the six-digit watch passkey in Android's system pairing dialog.
4. Select **Naari Watch** in the app. The app waits for bonding, encrypted GATT discovery, notification subscription and an acknowledged initial hello before storing the paired device. A system association or an accepted `createBond()` call alone is not considered a completed app connection.
5. Keep the app open for the first sync. Add your primary and secondary guardian. Wait for **Synced to watch**, then open Guardians on the watch and verify the name and number. That state requires a persisted watch profile read-back.
6. Change a watch face on the watch and confirm the app updates. Change brightness or volume in the app and check the watch. Watch-local state is reconciled approximately every two seconds while connected.
7. Record a short memo, then stop recording. Wait for the watch's save to finish. Leave the devices near each other; the app discovers and downloads memos automatically and verifies them before creating a playable WAV. BLE transfers can take time; no fixed throughput is promised.
8. Test guardian SMS/calls only with a consenting contact. Phone calls are controlled and heard on the phone; BLE does not carry call audio to the watch. Android may require opening the phone dialer, especially for emergency numbers.

After an app/phone restart, reopen the app once to restore location-enabled foreground operation. The service can recover BLE in the background, but Android restricts starting location services from the background. The app upgrades its foreground service while its activity is visible and location permission is granted. It does not request unrestricted background location or stream GPS continuously. Battery restrictions and force-stop can still interrupt Android background operation.

If a stale bond prevents pairing, first retry with the watch pairing window open. Only remove that specific watch bond in Android settings if necessary, then pair again. Do not erase watch NVS, format storage, or clear the app's data as a routine troubleshooting step. If APK installation reports an incompatible signature, an earlier APK used a different signing key; use that original key for an in-place upgrade rather than uninstalling and losing app data.

## Implemented synchronization

| Area | Behavior |
| --- | --- |
| Pairing | Waits for the real Android bond, ignores callbacks from closed GATT sessions, serializes operations, reconnects with backoff, closes timed-out sessions. |
| App readiness | Immediate acknowledged v1 hello, then periodic hello with actual permission grants. Watch icon is blue for app readiness, amber for BLE without readiness and grey/slashed when disconnected. |
| Guardians | Two real hardware slots. Local edits persist before transfer; a pending flag survives restart. BLE profile writes use responses and read back both phone numbers and normalized names. Reconnect reconciles the watch profile; serialized edits cannot be overwritten by concurrent reconciliation. |
| Watch settings | Wallpapers 0–4, brightness, volume, fall toggle, SAFE/WATCH mode, find-watch chime and time sync. Commands report Applied only after a matching firmware response. Emergency mode is reported; remote mode changes cannot bypass it. |
| Watch state | Battery, mode, settings, screen, recording activity/count and SOS/fall countdown with a boot-scoped incident ID. App state comes from watch responses. |
| Recordings | Persistent mono 16 kHz PCM16 capture, stable random recording IDs, bounded reads, raw PCM SHA-256 verification, app-private WAV storage and Room catalog, contiguous-offset resume after disconnect. |
| Phone actions | Existing v1 SMS, call, location and location-with-SMS requests use Android executors and the request ledger. Repeated delivery of the same watch request does not authorize another send. |
| LTE | A7672S remains preferred when it can accept a request. If it returns zero before queuing work, the facade can queue through the ready phone. Once accepted by LTE, uncertain/failed submissions are not automatically replayed on the phone. Route-tagged IDs keep replies and call controls attached to their original transport. |

> Watch v8.4 also provides an on-watch Saved Memos library and persisted playback. Companion 1.1 remains compatible; see [v8.4 safety and recording notes](SAFETY_V84_AND_PRODUCT_DIRECTION.md).

## Recording storage and retention

The watch uses the existing `storage` FAT/wear-levelling partition when it mounts. It only allows automatic formatting if the entire partition was erased beforehand. A nonblank unrecognized volume is preserved. If flash cannot mount, it can use a dedicated `naari-memos` directory on an already mounted microSD card without formatting the card. This connected watch selected `/sdcard/naari-memos` during boot verification.

The current memo limit is **10 seconds each**, with **six retained recordings**. When all six are unsynced, capture is blocked until the phone verifies a copy. Only acknowledged recordings are eligible for retention cleanup on a later save. Acknowledgement follows durable WAV storage and Room verification on the phone. Watch power loss during a write may lose that in-progress memo; previous complete memos are scanned again at boot. Partial `.tmp` files are discarded on restart. Ordinary firmware app-flashing preserves the partition table, NVS and recording volumes.

The recording catalog covers Recorder app memos. Continuous emergency SD evidence logging is a separate subsystem and is not exposed as an unlimited BLE recording library by this update. Previously volatile RAM-only recordings cannot be recovered after a reboot.

## Wire contract

All UUIDs have suffix `-b19e-11ee-a506-0242ac120002`:

| UUID prefix | Use | Protection |
| --- | --- | --- |
| `d973f2e0` | v1 phone bridge service | Bonded authenticated link |
| `d973f2e1` | Full pending phone request READ + tiny NOTIFY | Encrypted/authenticated read |
| `d973f2e2` | Hello, profile and action reply WRITE | Encrypted/authenticated write with response |
| `d973f2f0` | v2 watch control/audio service | Bonded authenticated link |
| `d973f2f1` | Control command WRITE | Encrypted/authenticated write with response |
| `d973f2f2` | Last control response READ + tiny NOTIFY | Encrypted/authenticated read |
| `d973f2f3` | Selected audio chunk READ | Encrypted/authenticated read |

Control command: `{"v":2,"cmd":123,"op":"get_status","args":{}}`.
Response: `{"v":2,"cmd":123,"status":"applied","applied":{...}}`, or `status:"rejected"` with `err`. The Android client serializes transactions, reads the full response and requires the matching command ID. A GATT write callback alone does not prove a setting was applied. Callbacks enqueue commands for a dedicated firmware worker; rendering changes use the UI mutex.

Implemented operations: `get_status`, `get_profile`, `set_wallpaper` (`index`), `set_brightness`/`set_volume` (`percent`), `set_fall_enabled` (`enabled`), `set_mode` (`mode`), `find_watch` (`duration_s`), `sync_time` (`epoch_s`, `tz_offset_min`), `get_recording` (`index`), `recording_chunk` (`id`, `offset`), `recording_ack` (`id`, `sha256`). The firmware does not advertise `safe_check_in`; unsupported PIN operations must remain unavailable in the app.

JSON messages are limited to 480 UTF-8 bytes. When a write exceeds MTU−3, the app uses sequential frames: `[0x7e, transferId, offsetLow, offsetHigh, finalFlag, payload...]`. Firmware requires contiguous offsets and rejects malformed transfers. Raw JSON remains supported. Notifications are hints; full JSON is read from the characteristic. Reads use stable values so ATT long reads also work at smaller MTUs.

Audio transfer uses `get_recording` metadata, then `recording_chunk` to select a stable offset, then reads `f3`. The binary packet is 10 bytes of little-endian header (`u32 sequence`, `u32 byteOffset`, `u16 payloadLength`) followed by at most 230 bytes PCM. The client rejects holes, overflow, inconsistent lengths and conflicting duplicate data. SHA-256 covers raw PCM, not the WAV header. `recording_ack` requires the exact catalog hash and is idempotent.

## Rebuild

Android: JDK 17, Android SDK platform 36, Gradle wrapper 8.14.3, AGP 8.13.1, Kotlin 2.2.10, Hilt 2.57.2. Set `ANDROID_HOME` or local `sdk.dir`; no developer-specific Java path is checked in.

```sh
cd Naari-os-app
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Firmware: ESP-IDF 5.2.8 with the checked-in board configuration and partitions.

```sh
cd firmware
idf.py -B build-sync -DNAARI_HARDWARE_SELFTEST=OFF build
idf.py -B build-sync -p /dev/cu.usbmodem101 app-flash
```

Discover the actual serial port first. Never flash the hardware self-test build as the normal OS. A named copy of the testing APK is also available at `Naari-os-app/build/Naari-Companion-1.1-debug.apk`.

The flash log is `/tmp/naari-watch-flash.log`; the verified v8.3 boot log is `/tmp/naari-watch-boot-fixed.log` on this development machine.

## Verification limits

The connected watch was flashed and booted into the production UI with BLE advertising, initialized microphone/speaker and persistent memo storage. The C++ UI preview and v1 protocol/frame tests pass with AddressSanitizer/UndefinedBehaviorSanitizer. Android debug APK assembly and lint completed successfully (lint warnings remain); all 224 local unit tests passed with no failures or skips. The APK signature and package/version metadata were checked using Android build tools. No Android phone was connected over ADB during this work, so real phone bonding, carrier delivery, background behavior and an acoustic recording round trip still need device validation. The A7672S is not wired and remains physically untested.

Platform references: [Android foreground service requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required), [CompanionDeviceManager association API](https://developer.android.com/reference/android/companion/CompanionDeviceManager), [Dagger 2.57 Kotlin metadata support](https://github.com/google/dagger/releases/tag/dagger-2.57).

APK SHA-256 for this build: `7a59e276926d2c27a398076a4e96ba27cbf1cba30f73903ee5a2d304ca1c24d2`.
