# Naari OS — Android Safety Companion

[![Android Platform](https://img.shields.io/badge/Android-API%2026%E2%80%9335-brightgreen)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-purple)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.01-blue)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20Hilt%20%2B%20Room-orange)](docs/ARCHITECTURE.md)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20On--Device%20(Zero%20Cloud)-teal)](#privacy--safety-principles)

Native Android companion application and installable APK for **Naari OS** — an open-source personal safety ecosystem connecting an ESP32-based wearable smartwatch with your smartphone.

The application pairs securely over encrypted Bluetooth Low Energy (BLE), executing critical emergency calls and location SMS alerts via native phone hardware, managing dual guardians, synchronizing audio memos, and providing preventative safety tools like the **Safe Walk Dead-Man's Switch**, **Anti-Loss Separation Alerts**, **Quick Settings Tiles**, and **1-Tap Home Screen Widgets**.

---

## 🌟 Key Features

### 1. 🚨 Crisis Response: Watch Hardware SOS Dispatcher
* **Dual-Guardian Alerts:** Simultaneously transmits direct carrier SMS with live Google Maps coordinates to both configured guardians.
* **Direct Emergency Call:** Automatically dials the primary guardian over the phone's native SIM connection.
* **Durable SQLite Ledger:** Every request ID is persisted in Room SQLite *prior* to executing hardware side-effects. Interrupted dispatches persist as `UnknownOutcome` and are never automatically replayed.
* **Audio Memo & Siren:** Activates emergency siren and captures 30 seconds of high-fidelity ambient audio stored in private local storage.

### 2. 🚶‍♀️ Preventative Safety: "Safe Walk" Journey Timer (Dead-Man's Switch)
* **The Problem Solved:** Walking home alone at night, taking a cab in an unfamiliar neighborhood, or running in an isolated park where an attack may happen before you can press a button.
* **How It Works:**
  * Select 10, 15, or 30 minutes to start a background journey countdown.
  * A persistent foreground notification tracks remaining time.
  * If the user taps **"I've Arrived Safely"**, the timer clears cleanly.
  * **Dead-Man's Trigger:** If the countdown reaches zero without arrival confirmation, the phone automatically acquires fresh GPS coordinates and dispatches emergency SOS texts to both guardians.
  * Extensible (+10 mins) and cancellable with confirmation at any time.

### 3. 📍 Anti-Loss & Separation Alert ("Watch Left Behind")
* **The Problem Solved:** Accidentally leaving your watch behind at a cafe/gym or having the watch forcibly snatched from your wrist.
* **How It Works:**
  * Foreground service continuously monitors BLE link stability.
  * When connection drops unexpectedly from `Ready`:
    * Phone sounds a distinct chime and vibrates.
    * Pushes a high-priority notification: *"Naari Watch Disconnected: Did you leave your watch behind?"*
    * Automatically captures the GPS coordinates of the disconnection spot.
    * Renders an amber card on the Home Screen with a **"Find on Maps"** button linking directly to Google Maps.

### 4. 💬 Peace of Mind: One-Tap "Reached Safely" Check-In
* **The Problem Solved:** Reassuring loved ones after a commute without raising panic or triggering sirens.
* **How It Works:**
  * Single tap on Home screen, Quick Settings tile, or Home Screen widget.
  * Instantly sends a reassuring text with a live Google Maps link to configured guardians:
    `"Hi [Guardian Name], I have reached safely at [Maps Link] - Sent via Naari OS"`
  * Logs delivery confirmation in the durable ledger.

### 5. 🔋 Low Watch Battery Guardian Warning
* **How It Works:** Reads battery percentage during periodic handshakes. When watch battery drops $\le 20\%$, prompts user with a prominent notification and Home screen warning badge to recharge before heading out.

### 6. ⚡ Instant Access: Quick Settings Tiles & Home Screen Widget
* **Android Quick Settings Tiles:**
  * **Safe Check-in Tile:** Pull down notification shade from any screen or lock screen $\to$ tap once to dispatch reassurance SMS.
  * **Safe Walk Tile:** Pull down notification shade to monitor live countdown or toggle journey timer.
* **1-Tap Home Screen Safety Widget:**
  * Minimalist dark OLED container on the phone launcher.
  * Real-time status badge (`Standby • Ready` / `Walk Active: 14:20`).
  * Dedicated buttons: **Check-in**, **Safe Walk (15m)**, and **SOS**.

---

## 🔒 Privacy & Safety Principles

* **100% On-Device Execution:** No cloud accounts, no API tokens, no telemetry, and zero remote analytics. All data stays strictly on your physical phone and watch.
* **Emergency Number Safeguard:** Government emergency numbers (`112`, `1091`, `911`, `999`) are strictly intercepted and redirected to the native dialer (`needs_phone`). The app never places automated background calls to emergency services.
* **Truthful Readiness Reporting:** The companion dashboard strictly refuses to show `Protected & Ready` unless:
  1. Watch is connected and bonded.
  2. A mutual `hello` handshake succeeded within the last 45 seconds.
  3. SMS, Location, and Bluetooth permissions are active.
* **Private Audio Storage:** Voice memos reside in private app storage (`context.filesDir/recordings`), exported securely via `FileProvider`, and are excluded from system cloud backups (`backup_rules.xml`).

---

## 🎨 Design System: Minimalist OLED Aesthetic

Naari OS companion follows a calm, honest, high-contrast visual design built with Jetpack Compose:

| Token | Hex Value | Usage |
|---|---|---|
| `MinimalistBackground` | `#0A0A0C` | Pure black AMOLED canvas |
| `MinimalistSurface` | `#121216` | Container and card surfaces |
| `MinimalistSurfaceSecondary`| `#1A1A22` | Elevated buttons and chips |
| `MinimalistBorder` | `#24242A` | Subtle 1dp structural borders |
| `MinimalistTextPrimary` | `#FFFFFF` | High-contrast readable typography |
| `MinimalistTextSecondary` | `#9CA3AF` | Supporting subtitles |
| `ModernEmerald` | `#10B981` | Safe / Ready / Confirmed |
| `ModernAmber` | `#F59E0B` | Attention / Disconnected |
| `ModernRed` | `#EF4444` | SOS / Emergency / Low Battery |

---

## 🛠️ Tech Stack & Prerequisites

* **Operating System:** Android 8.0 Oreo (API 26) through Android 15 (API 35)
* **Language & Runtime:** Kotlin 2.2.0, OpenJDK 17
* **UI Framework:** Jetpack Compose (BOM 2024.10.01) with Material 3
* **Dependency Injection:** Dagger Hilt 2.51+
* **Local Persistence:** Room SQLite 2.6+, DataStore Preferences
* **Concurrency:** Kotlin Coroutines & Reactive `StateFlow`
* **Hardware Requirements:** Bluetooth 4.2+ Low Energy (BLE), Cellular SIM (SMS & Call), GPS

---

## 🚀 Build, Test & Install Commands

All commands use the included Gradle wrapper (`gradlew` or `gradlew.bat`):

### 1. Run Unit Test Suite
```bash
./gradlew test
```
*Executes all unit tests across Room DAOs, BLE GATT queues, SmsExecutor, SafeWalkManager, AntiLossManager, and ViewModel state machines.*

### 2. Build Debug APK
```bash
./gradlew assembleDebug
./package_debug_apk.sh
```
*Installable v1.2 artifact:* `build/Naari-Companion-1.2-debug.apk` with a matching SHA-256 file. The packaging script also copies the same verified APK into the website download directory.

### 3. Install on Connected Device (via ADB)
```bash
# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch MainActivity
adb shell am start -n com.naari.companion/.MainActivity
```

### 4. Full Verification Gate (Tests + Lint + Assemble)
```bash
./gradlew test lintDebug assembleDebug
```

---

## 📱 Hardware Pairing Guide

1. **Watch Side:**
   * Power on the Naari Watch. Ensure BLE advertising is active (device advertises `Naari Watch` with Service UUID `d973f2e0-b19e-11ee-a506-0242ac120002`).
2. **Phone Side:**
   * Open **Naari OS** on your Android phone.
   * Go to **Watch Controls -> Pair Watch** (or follow the initial Setup Guide).
   * Select `Naari Watch` from the system device picker.
   * Accept the Android Bluetooth bonding dialog and confirm the passkey.
   * The phone service completes the mutual `hello` handshake and transitions to `Protected & Ready`.

---

## 📂 Project Structure

```
app/src/main/
├── AndroidManifest.xml                  # Registered services, receivers, tiles & widget
├── java/com/naari/companion/
│   ├── actions/                         # ActionDispatcher, SmsExecutor, CallExecutor
│   ├── antiloss/                        # AntiLossManager & Separation Alert card
│   ├── ble/                             # Serialized GATT Queue, BleSessionManager, Codec
│   ├── checkin/                         # SafeCheckInManager (One-Tap Reassurance)
│   ├── data/                            # Room AppDatabase, DAOs, Entity models
│   ├── designsystem/                    # Minimalist Black/OLED Theme, NaariIcons, Buttons
│   ├── guardians/                       # Dual-guardian management & SQLite repository
│   ├── history/                         # Durable Activity History ledger screen
│   ├── home/                            # Main dashboard, Watch battery, Safe Walk cards
│   ├── location/                        # High-accuracy GPS provider & Maps utilities
│   ├── navigation/                      # NaariAppShell & Compose NavHost destinations
│   ├── quicksettings/                   # SafeCheckInTileService, SafeWalkTileService
│   ├── safewalk/                        # SafeWalkManager & Journey countdown engine
│   ├── service/                         # Foreground CompanionService & BootReceiver
│   └── widget/                          # NaariSafetyWidgetProvider (1-Tap Home Widget)
└── res/
    ├── drawable/                        # Vector icons & widget shapes
    ├── layout/                          # widget_naari_safety.xml (RemoteViews)
    └── xml/                             # AppWidget metadata, file paths, backup rules
```

---

## 📚 Documentation Index

All technical and operational specifications are organized in the [`docs/`](docs/) directory:

* [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — Comprehensive technical architecture, BLE GATT queue, and data flow.
* [`docs/ANDROID_COMPANION_PRD.md`](docs/ANDROID_COMPANION_PRD.md) — Authoritative product requirements document.
* [`docs/HARDWARE_TEST_PLAN.md`](docs/HARDWARE_TEST_PLAN.md) — Physical hardware acceptance test matrix for phone and watch.
* [`docs/RELEASE_SIGNING_GUIDE.md`](docs/RELEASE_SIGNING_GUIDE.md) — Keystore generation and CI/CD release signing guide.
* [`docs/PERFORMANCE_PROFILE.md`](docs/PERFORMANCE_PROFILE.md) — 60 Hz frame budget, memory footprint, and battery optimization audit.

---

## 📄 License & Safety Notice

This application is provided for personal safety assistance. In life-threatening emergencies, always dial local law enforcement or government emergency services (`112` / `1091` / `911`) directly when possible.
