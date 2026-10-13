# Naari OS and Android Companion

Naari is a personal safety watch running on the Waveshare ESP32-S3 Touch AMOLED 2.06. The Android phone is the primary transport for SMS, calls, and location through a bonded BLE connection. SIM800L is reserved for a future optional backup; it is not required or active today.

**Start here:** [Android Companion PRD](docs/ANDROID_COMPANION_PRD.md). This is the implementation brief for the next developer or AI. It distinguishes implemented firmware from features that still require work. No Android APK has been built in this repository yet.

## Current implementation

- Actual watch UI: custom C++ RGB565 renderer, 410 × 502 pixels, premium typography/icons, paged 2 × 2 app drawer and five full-screen faces. It does not use LVGL.
- Real ES7210 microphone capture and ES8311 speaker playback. Current voice memo is one volatile RAM recording, up to 10 seconds. BLE recording export is not implemented.
- Bonded, authenticated BLE phone bridge: call, SMS, location, location-with-SMS requests and two persisted guardian slots. Phone execution requires the companion app.
- QMI8658 fall heuristic, persisted enable switch, cancellable SOS countdown and a local demo. This is not a medically validated detector.
- Phone-to-watch general controls, status synchronization, and durable recording synchronization require protocol extensions.

## Repository map

| Path | Purpose |
| --- | --- |
| `firmware/main/` | Actual ESP-IDF watch firmware |
| `firmware/main/network/phone_bridge.cpp` | Existing BLE contract and validation |
| `firmware/main/drivers/audio_es7210_es8311/` | Real recording and playback |
| `firmware/main/drivers/sim800l/` | Legacy compatibility adapter, currently routes through the phone bridge |
| `firmware/main/safety/` | Fall detection state machine |
| `firmware/tools/` | Asset generation, actual C++ browser preview, protocol/fall tests |
| `docs/ANDROID_COMPANION_PRD.md` | Authoritative app requirements and firmware gaps |
| `dashboard/`, `backend/`, `supabase/` | Existing web/backend work; not the Android companion |
| `sheshield-sentinel-guardian-main/` | Existing separate presentation site |

Older proposals and audits describe historical aspirations. They are not evidence that wake-word recognition, SD evidence recording, cloud delivery, or direct GSM are working.

## Build and preview

Use ESP-IDF 5.2.8, target ESP32-S3, and the checked-in dependency lock/configuration. Preserve existing partitions and NVS.

```sh
cd firmware
idf.py -B build-premium -DNAARI_HARDWARE_SELFTEST=OFF build
idf.py -B build-premium -p /dev/cu.usbmodem1101 flash
```

The port above is an example; discover the actual port first. Hardware self-test must remain OFF in normal builds.

From the repository root:

```sh
python3 firmware/tools/preview_ui.py --serve
python3 firmware/tools/tests/test_phone_protocol.py
```

The preview uses the actual C++ UI renderer with mocked hardware at http://localhost:5174. Preview interactions cannot send real SMS or calls. The protocol test requires the local ESP-IDF checkout and a C/C++ compiler. See [watch UI design](docs/WATCH_UI_V7_DESIGN.md) for rendering constraints.

## Cleanup policy

Removed macOS `.DS_Store` metadata. Keep firmware, dependencies.lock, asset sources/licenses, tests, existing web/backend source, historical docs, SDK configuration and the SIM800L compatibility path. Build output and dependencies are reproducible, but retain the current flash/preview output while hardware work is ongoing. See the PRD for a safe cleanup process; do not delete a project merely because it is not needed by the companion APK.
