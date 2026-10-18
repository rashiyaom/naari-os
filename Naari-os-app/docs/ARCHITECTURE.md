# Naari Android Companion: Architecture & Technical Specification

## 1. System Overview

The **Naari Android Companion** is an installable, native Android application engineered for women's personal safety. It connects to the **Naari Watch** via Bluetooth Low Energy (BLE), acting as the hardware executor for critical distress requests while providing independent, on-device preventative safety tools.

```
+-----------------------------------------------------------------------------------+
|                                  NAARI WATCH                                      |
|            Hardware SOS Button | Fall Detection | Mic | Battery Sensor            |
+-----------------------------------------------------------------------------------+
                                          |
                      Encrypted BLE GATT (Service UUID: ...0002)
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                              ANDROID COMPANION APP                                |
|                                                                                   |
|  +---------------------------+  +------------------------+  +------------------+  |
|  |     BLE Transport Queue   |  |   Companion Service    |  |  Durable Ledger  |  |
|  |  - Serialized FIFO Queue  |  |  - Persistent FG Svc   |  |  - Room SQLite   |  |
|  |  - 15s Mutual Handshake   |  |  - Watch Link Listener |  |  - Deduplication |  |
|  |  - Auto Backoff Reconnect |  |  - Battery Monitor     |  |  - Crash Safe    |  |
|  +---------------------------+  +------------------------+  +------------------+  |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                    Preventative Safety & Awareness Engine                   |  |
|  |                                                                             |  |
|  |  [Safe Walk Manager]        [Anti-Loss Manager]       [Safe Check-In Mgr]   |  |
|  |  Dead-Man's Journey Timer   BLE Disconnect Pinning    1-Tap Reassurance SMS |  |
|  |  Auto-dispatches SOS GPS    Sound Chime + GPS Map     Direct to Guardians   |  |
|  +-----------------------------------------------------------------------------+  |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                           Hardware Executors                                |  |
|  |  - Direct Carrier SMS (SmsManager)      - Direct Phone Call (TelecomManager)|  |
|  |  - High-Precision GPS (LocationClient)  - Audio Recording Storage           |  |
|  +-----------------------------------------------------------------------------+  |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                           UI & Instant Access                               |  |
|  |  - Jetpack Compose Minimalist UI       - Quick Settings Tiles (Check-in/Walk)|  |
|  |  - 1-Tap Home Screen Safety Widget     - Durable Activity Ledger Screen      |  |
+-----------------------------------------------------------------------------------+
```

---

## 2. Core Architectural Principles

1. **100% On-Device & Private (Zero Cloud Dependencies):**
   - No servers, no tracking analytics, and no remote databases.
   - All guardian contacts, activity history, and audio memos reside in local, encrypted SQLite storage.
   - SMS and emergency calls originate directly through the phone's native SIM hardware.
2. **Durable Ledger Before Hardware Side-Effects:**
   - Every incoming request identity is durably persisted in SQLite (`RequestLedgerEntry`) *prior* to executing an SMS or phone call.
   - If the application crashes mid-execution, the request remains in `UnknownOutcome` and is **never** automatically replayed to prevent accidental duplicate alerts.
3. **Strict Truthful Readiness:**
   - The UI displays **"Ready"** only when:
     - Watch is bonded and connected over BLE.
     - A mutual `hello` handshake has succeeded within the last 45 seconds.
     - All critical permissions (SMS, Location, Bluetooth) are actively granted.
4. **Emergency Number Safeguard:**
   - The application strictly refuses to automatically background-dial government emergency numbers (`112`, `1091`, `911`, `999`). Requests for emergency numbers are routed directly to the native Android dialer (`needs_phone`).

---

## 3. Component Deep Dive

### 3.1. BLE Transport & GATT Serialization
- **Classes:** `BleSessionManager`, `AndroidGattTransport`, `ProtocolV1Codec`
- **FIFO Queue:** All Android GATT reads, writes, and MTU negotiations run sequentially through a thread-safe channel, preventing BLE race conditions.
- **Handshake Loop:** When connected, the app exchanges mutual 15-second `hello` messages with characteristic `e2`. If no heartbeat is received within 45 seconds, the connection state drops from `Ready` to `Connecting`.
- **MTU Negotiation:** Requests 512 bytes upon connection, falling back gracefully to standard 23 bytes if the peripheral denies the request.

### 3.2. Preventative Safety Modules

#### 🚶‍♀️ Safe Walk Journey Timer (Dead-Man's Switch)
- **Class:** `com.naari.companion.safewalk.SafeWalkManager`
- **Concept:** Designed for solitary walks at night, cab rides in unfamiliar areas, or isolated jogging.
- **Workflow:**
  1. User selects 10, 15, or 30 minutes and starts the journey.
  2. A persistent notification appears with a live countdown.
  3. If the user taps **"I've Arrived Safely"**, the timer stops cleanly.
  4. If the countdown reaches `00:00` without confirmation, the manager automatically:
     - Acquires immediate fresh GPS coordinates.
     - Dispatches emergency SOS SMS alerts to all configured guardians.
     - Logs the incident in the durable ledger.
     - Sounds an emergency alert.
  5. The user can extend the journey (+10 mins) or cancel with confirmation at any time.

#### 📍 Anti-Loss & Separation Alert ("Watch Left Behind")
- **Class:** `com.naari.companion.antiloss.AntiLossManager`
- **Concept:** Prevents leaving the watch behind at cafes/gyms or losing it if snatched.
- **Workflow:**
  1. Actively observes `BleConnectionState`.
  2. When the link drops unexpectedly from `Ready`:
     - Phone immediately queries the last-known GPS location and pins it.
     - Plays a distinct alert chime and vibrates.
     - Pushes a high-priority heads-up notification: *"Naari Watch Disconnected: Did you leave your watch behind?"*
     - Renders an amber card on the Home Screen with a **"Find on Maps"** button that directly launches Google Maps to the pinned coordinates.

#### 💬 One-Tap "Reached Safely" Quick Check-in
- **Class:** `com.naari.companion.checkin.SafeCheckInManager`
- **Concept:** Provides daily reassurance without triggering emergency distress alarms.
- **Workflow:**
  1. Single tap on the Home screen card, Quick Settings tile, or Home screen widget.
  2. Acquires one-shot GPS fix.
  3. Sends direct SMS to both guardians:
     `"Hi [Guardian Name], I have reached safely at [Google Maps Link] - Sent via Naari OS"`
  4. Records carrier delivery confirmation in the ledger.

#### 🔋 Low Watch Battery Guardian Warning
- **Class:** `com.naari.companion.service.CompanionService`
- **Workflow:**
  1. Reads battery percentage during periodic BLE handshakes.
  2. When watch battery drops $\le 20\%$, pushes an alert notification reminding the user to recharge before heading out to ensure SOS availability.
  3. Displays a red warning badge on the Home dashboard battery card.

---

### 3.3. Instant Access: Quick Settings & App Widget

#### Android Quick Settings Tiles (`TileService`)
- **`SafeCheckInTileService`**: Pull down notification shade from anywhere to trigger a Safe Check-in SMS in 1 tap.
- **`SafeWalkTileService`**: Pull down notification shade to monitor remaining Safe Walk time or start/finish a walk.

#### 1-Tap Home Screen Safety Widget
- **Class:** `com.naari.companion.widget.NaariSafetyWidgetProvider`
- **Layout:** `res/layout/widget_naari_safety.xml`
- **Features:**
  - Dark OLED minimalist squircle container (`#101014`, rounded corners 22dp).
  - Status header dynamically reflecting standby or live countdown (`Walk Active: 14:20`).
  - Three 1-tap action buttons:
    1. **Check-in**: Instant safe check-in SMS with live GPS.
    2. **Safe Walk**: 1-tap start 15m timer / 1-tap confirm arrival.
    3. **SOS**: Red accent button for immediate emergency distress dispatch.

---

## 4. Database Schema (Room SQLite)

The local SQLite database (`AppDatabase`, version 1) manages three durable tables:

1. **`guardians`**:
   - `slot` (0 = Primary, 1 = Secondary)
   - `name`, `phone`, `relationship`
   - `isActive`
2. **`request_ledger`**:
   - `watchRequestId` (unique deduplication key)
   - `deviceAddress`, `requestType`
   - `status` (`Received`, `Executing`, `Sent`, `Delivered`, `Located`, `Calling`, `Failed`, `UnknownOutcome`)
   - `createdAtEpochMs`, `completedAtEpochMs`
   - `payloadSnapshot`, `detail`
3. **`safety_incidents`**:
   - `id`, `timestampEpochMs`, `incidentType`, `summary`, `status`

---

## 5. UI & Design System

The application adheres to an honest, calm, high-contrast aesthetic:
- **Theme Palette:**
  - `MinimalistBackground`: Pitch Black (`#0A0A0C`)
  - `MinimalistSurface`: Dark Charcoal (`#121216`)
  - `MinimalistSurfaceSecondary`: Elevated Charcoal (`#1A1A22`)
  - `MinimalistBorder`: Subtle Border (`#24242A`)
  - `MinimalistTextPrimary`: Pure White (`#FFFFFF`)
  - `MinimalistTextSecondary`: Muted Gray (`#9CA3AF`)
  - `ModernEmerald`: Safe / Confirmed (`#10B981`)
  - `ModernAmber`: Attention / Disconnected (`#F59E0B`)
  - `ModernRed`: Distress / SOS / Low Battery (`#EF4444`)
- **Typography:** Modern clean sans-serif typography with generous line-height and letter-spacing for rapid readability under stress.
- **Iconography:** Fully custom vector icons (`NaariIcons`) avoiding generic or copyrighted fonts.
