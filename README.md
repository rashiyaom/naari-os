# Naari OS and Android Companion

Naari is a personal safety watch running on the Waveshare ESP32-S3 Touch AMOLED 2.06. The current hardware revision targets a directly connected Graylogix A7672S LTE/GNSS TTL modem for SMS, call commands and location. The Android BLE connection remains available for guardian configuration and optional phone-only operation. See the [A7672S wiring and bring-up guide](docs/A7672S_WIRING_AND_BRINGUP.md) before connecting hardware. The new modem path is implemented but has not yet been physically tested.

**Start here:** [Android Companion PRD](docs/ANDROID_COMPANION_PRD.md). This is the implementation brief for the next developer or AI. It distinguishes implemented firmware from features that still require work. The Android app lives in `Naari-os-app/`. See [v8.3 BLE sync, APK installation and verification](docs/BLE_SYNC_V83.md) for the implemented handshake, controls, guardians and memo transfer.

See [v8.4 safety fixes, fall logic and product differentiation](docs/SAFETY_V84_AND_PRODUCT_DIRECTION.md) for the current detector, three-second SOS confirmation, attention light and saved alarm tones.

The print-ready national-conference poster is available as an [A3 vector PDF](conference-poster/naari-os-a3-poster.pdf), with a [300 dpi PNG](conference-poster/naari-os-a3-poster.png), compact preview and reproducible generator in `conference-poster/`.

## Current implementation

- Actual watch UI: custom C++ RGB565 renderer, 410 × 502 pixels, premium typography/icons, paged 2 × 2 app drawer and five full-screen faces. It does not use LVGL.
- Real ES7210 microphone capture and ES8311 speaker playback. Memos up to 10 seconds persist to watch storage (six retained); an on-watch library browses/plays saved memos with [confirmed watch-only deletion](docs/RECORDING_DELETE_V85.md), and automatic BLE transfer verifies raw PCM SHA-256 and saves playable WAV files on Android.
- Bonded, authenticated BLE phone bridge: call, SMS, location, location-with-SMS requests and two persisted guardian slots. Phone execution requires the companion app.
- QMI8658 fall heuristic, persisted enable switch, cancellable SOS countdown and a local demo. This is not a medically validated detector.
- Direct A7672S UART driver: bounded asynchronous requests, real registration/GNSS queries, UCS-2 multipart SMS and dial/hang-up commands. Physical modem/antenna/SIM validation and live call audio routing are still required.
- Authenticated v2 BLE controls and two-second state reconciliation: faces, brightness, volume, fall detection, mode, time sync, find-watch and recording transfer. Companion 1.1 requires watch v8.3 for these features. Physical Android end-to-end validation remains outstanding.

## Repository map

| Path | Purpose |
| --- | --- |
| `firmware/main/` | Actual ESP-IDF watch firmware |
| `Naari-os-app/` | Android companion source, vendored here for one reproducible checkout |
| `firmware/main/network/phone_bridge.cpp` | Existing BLE contract and validation |
| `firmware/main/drivers/audio_es7210_es8311/` | Real recording and playback |
| `firmware/main/drivers/a7672s/` | Real A76XX AT engine, UART worker and modem protocol |
| `firmware/main/drivers/sim800l/` | Legacy API facade preferring A7672S with phone fallback before modem queue acceptance |
| `firmware/main/safety/` | Fall detection state machine |
| `firmware/tools/` | Asset generation, actual C++ browser preview, protocol/fall tests |
| `docs/ANDROID_COMPANION_PRD.md` | Authoritative app requirements and firmware gaps |
| `conference-poster/` | Print-ready A3 research poster, 300 dpi proof and editable generator |
| `sheshield-sentinel-guardian-main/` | Sole website project: [narios.vercel.app](https://narios.vercel.app) |

Older proposals and audits describe historical aspirations. They are not evidence that wake-word recognition, SD evidence recording, cloud delivery, or direct GSM are working.

## Build and preview

Use ESP-IDF 5.2.8, target ESP32-S3, and the checked-in dependency lock/configuration. Preserve existing partitions and NVS.

```sh
cd firmware
idf.py -B build-sync -DNAARI_HARDWARE_SELFTEST=OFF build
idf.py -B build-sync -p /dev/cu.usbmodem101 app-flash
```

The port above is an example; discover the actual port first. Modem wiring defaults to watch TX GPIO43 → modem RX, watch RX GPIO44 ← modem TX, common GND, and a separately powered 5 V modem carrier with its UART selector set to 3.3 V. The build uses USB console instead of UART0. Do not flash until wiring and supply are reviewed. Hardware self-test must remain OFF in normal builds. The watch face has a small top-centre Bluetooth icon: blue means the companion is responding, amber means BLE connected without app readiness, and grey/slashed means disconnected. It refreshes when link status changes, including heartbeat expiry.

From the repository root:

```sh
python3 firmware/tools/preview_ui.py --serve
python3 firmware/tools/tests/test_phone_protocol.py
```

The preview uses the actual C++ UI renderer with mocked hardware at http://localhost:5174. Preview interactions cannot send real SMS or calls. The protocol test requires the local ESP-IDF checkout and a C/C++ compiler. See [watch UI design](docs/WATCH_UI_V7_DESIGN.md) for rendering constraints.

## Android APK

```sh
cd Naari-os-app
# Use JDK 17 and Android SDK platform 36
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Install `Naari-os-app/app/build/outputs/apk/debug/app-debug.apk` on Android. Open Phone Link → Start pairing on the watch, then select Naari Watch in the app and enter the watch passkey in Android. The app confirms guardian storage by reading it back from the watch. See [pairing and recording instructions](docs/BLE_SYNC_V83.md). This is a debug APK; no Android phone was connected for a physical integration test.

## Website development

```sh
cd sheshield-sentinel-guardian-main
npm ci
npm run dev
npm run build
```

This is the only website retained. Its interactive watch/guardian views are browser simulations; real firmware lives in `firmware/`. Keep the existing website directory name so the Vercel project root remains valid. The local `.vercel/` link is preserved and ignored by Git.

## Repository hygiene

The obsolete dashboard, Express backend and Supabase demo were removed on 17 October 2026. Firmware source, tests, asset generators/licenses, dependency lock, hardware configuration, native preview tools, companion PRD and poster brief remain. Historical audit/proposal documents describe earlier revisions and removed components.

Dependencies and generated firmware builds/previews are ignored and no longer tracked. The latest flashed production build and preview remain available locally; the stale `firmware/build/` output was removed. A fresh clone regenerates dependencies/build output using the commands above. No published Git history was rewritten. See [cleanup record](docs/REPOSITORY_CLEANUP.md).
