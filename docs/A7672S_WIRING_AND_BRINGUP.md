# Graylogix A7672S LTE + GNSS modem — wiring and bring-up

Target: **Graylogix SIM A7672S 4G LTE TTL Modem with GNSS + BLE**, SKU **A7672-GNSS-TTL-10AG3**, connected to the Waveshare ESP32-S3 Touch AMOLED 2.06. This guide is for the TTL carrier board, not the bare SIMCom module or Graylogix's separate serial/DB9 products.

Status: firmware implementation and host tests; modem not yet wired or physically verified. Do not claim working carrier SMS, GNSS fix, VoLTE or watch call audio until tested on the actual assembled hardware.

## 1. Before connecting: logic voltage and power

The [exact Graylogix product listing](https://www.graylogix.in/product/sim-a7672s-4g-lte-ttl-modem-with-gnss-ble) says the UART board defaults to **5 V TTL**, with a **0-ohm selector resistor** that can be moved to the **3.3 V** position. It also specifies a **5 V board supply**. These are separate settings: switching the UART to 3.3 V does not change the carrier board's supply requirement.

**Power off both boards, move the selector to 3.3 V as shown by Graylogix, and verify its position before connecting UART. Never connect a 5 V TX output directly to an ESP32 GPIO.** If the modification is uncertain, use a correctly designed UART level translator instead and verify voltages before connecting the watch.

Use a regulated **5 V external supply for the modem carrier**, with common ground to the watch. For initial bench work, a supply rated for at least **2 A** is an engineering starting recommendation for transient headroom, not a measured specification for this board. Graylogix lists 500 mA nominal operation; verify the actual board's peak demand and supply stability during network activity. Use short power leads and appropriate decoupling; do not power this modem from the watch's 3.3 V rail, a GPIO, or an assumed spare PMIC output. Do not join independent 5 V supply outputs together.

For a battery-powered wearable, a suitable separate 5 V boost/power design must be sized and tested. The watch's LiPo connector alone is not a validated modem power supply. This firmware does not alter PMIC rails or enable an unverified modem power circuit.

## 2. Connections

The default firmware uses **UART1**, **115200 baud, 8 data bits, no parity, 1 stop bit**, no hardware flow control.

| Watch / supply | Connect to Graylogix board | Purpose |
| --- | --- | --- |
| Watch **GPIO43 / U0TXD** | **RX** on modem TTL connector | Watch transmits AT commands |
| Watch **GPIO44 / U0RXD** | **TX** on modem TTL connector, after 3.3 V selection | Modem responses to watch |
| Watch **GND** | Modem **GND** | Required signal reference |
| External regulated **+5 V** | Board's labeled **5 V / VCC supply input** | Carrier board power; verify silkscreen/revision |
| External supply **GND** | Modem **GND** and watch **GND** | Common ground |
| Included cellular antenna | Board's designated **LTE antenna connector** | Network operation |
| Included GNSS antenna | Board's designated **GNSS/GPS antenna connector** | Satellite positioning |

```text
Watch ESP32-S3                              Graylogix TTL carrier
GPIO43 / U0TXD  --------------------------> RX
GPIO44 / U0RXD  <-------------------------- TX  [3.3 V TTL selected]
GND             -------------------------- GND ----- external supply GND
                                           5V  ----- regulated external +5 V
Watch power remains supplied separately.
```

**TX connects to RX; RX connects to TX.** GPIO43/44 are ESP32 pin numbers, not physical connector pin positions. The [official Waveshare schematic](https://files.waveshare.com/wiki/ESP32-S3-Touch-AMOLED-2.06/ESP32-S3-Touch-AMOLED-2.06.pdf) exposes the U0TXD/U0RXD nets at test points; locate the corresponding pads on the actual board revision. This may require fine soldering. Do not assume the USB socket or an I2C connector provides these UART pins. Confirm pad/net identity using the schematic and continuity testing before soldering.

The original firmware's UART0 console would contend with these GPIOs. The modem configuration moves logging/monitoring to the watch's **native USB Serial/JTAG console**. Do not attach a second UART adapter driving the modem RX at the same time.

Leave **RING, STATUS, NETLIGHT, RESET and DTR** unconnected for the initial three-signal UART integration unless the board's own documentation requires a bias/enable. Do not wire carrier control pins directly to the watch until their voltage and active level are confirmed. Firmware deliberately does not pulse the former SIM800L GPIO10 power key. Supply/power on the carrier using its documented procedure; if that revision needs a physical power-button press, do that manually.

Insert an activated nano-SIM with SMS and voice service. Keep the LTE/GNSS antennas on their correct connectors; test GNSS outdoors with a clear sky view. The first fix can take time and is not guaranteed indoors.

## 3. Firmware configuration

`firmware/main/Kconfig.projbuild` adds **Naari cellular transport** in menuconfig:

- `CONFIG_NAARI_A7672S_ENABLED=y`: direct modem SMS/calls/location; default for this hardware revision.
- `CONFIG_NAARI_MODEM_TX_GPIO=43`.
- `CONFIG_NAARI_MODEM_RX_GPIO=44`.
- `CONFIG_NAARI_MODEM_BAUD=115200`.

Only change GPIOs after verifying exposure and conflicts. The driver rejects pins reserved by the watch's display, touch, I2C, audio, USB, flash/PSRAM and other configured hardware, and rejects equal TX/RX pins. It also rejects an active UART console. No display pin or framebuffer layout is changed.

Disabling `NAARI_A7672S_ENABLED` selects the existing Android BLE transport. BLE pairing/profile configuration remains active in either mode. In v8.3, the ready phone can receive a request only if the modem rejects it before queue acceptance. Once LTE accepts work, a timed-out or uncertain request is never automatically replayed through the phone, avoiding duplicate SMS/calls. The SIM800L-named class/file is a legacy compatibility facade; the new AT driver is `drivers/a7672s/`, not a renamed SIM800L AT implementation.

Build with the project's ESP-IDF environment:

```sh
cd firmware
idf.py -B build-a7672s -DNAARI_HARDWARE_SELFTEST=OFF menuconfig
idf.py -B build-a7672s -DNAARI_HARDWARE_SELFTEST=OFF build
idf.py -B build-a7672s -p YOUR_WATCH_USB_PORT flash monitor
```

Use the actual detected watch USB port. Flash only after reviewing wiring and supplying the modem correctly. Preserve partitions and NVS; do not erase flash to install this change. Use USB monitoring, not the UART pins now connected to the modem.

## 4. What the driver does

- One dedicated FreeRTOS worker owns UART1; UI and state-machine requests remain nonblocking.
- Uses an AT response barrier and validates `AT+CGMM` identifies **A7672** before configuration. Wrong or missing modules stay unavailable and retry discovery without sending user actions.
- Checks SIM readiness with `AT+CPIN?`; queries LTE registration via `AT+CEREG?`, with `AT+CREG?` fallback for supported networks, and signal via `AT+CSQ`.
- Powers the GNSS engine with `AT+CGNSSPWR=1`, then polls `AT+CGPSINFO`. Converts degree/minute coordinates and hemisphere to decimal degrees. Empty/malformed fixes are rejected. No NMEA stream is enabled on the shared command UART.
- Stores the last real fix for up to 120 seconds. A location SMS labels a reused fix as cached; when no fix exists, it sends explicit location-unavailable text instead of invented coordinates.
- Sends SMS in **PDU mode**, using the SIM's provisioned SMSC, UCS-2 BMP text and concatenated multipart messages. Non-BMP emoji/invalid UTF-8 are rejected rather than silently corrupted. Messages can incur multiple SMS charges.
- Shows successful submission only after modem `+CMGS` and final `OK`; recipient delivery is **unconfirmed**, not “delivered”. Partial/timeout outcomes are exposed without automatic retry. The legacy maxRetries argument is intentionally not used to replay a possible side effect.
- Dials with `ATD<number>;` and supports `ATH` from the watch's End call action. Dial command acceptance is not proof the recipient answered.
- Keeps cellular registration available in SAFE mode and does not power off the modem while safe-check-in SMS might still be queued.

Requests are bounded, checked for valid destinations and accepted for SMS/calls only while the modem is ready and registered. Queue depth is eight; requests waiting more than 30 seconds expire without dispatch. No offline SMS replay backlog is created. Pending submissions are not automatically recovered across watch reboot. These constraints are intentionally visible; they are not a guarantee of alert delivery.

## 5. Guardians and independence from a connected phone

The two existing guardian profiles remain persisted in NVS. Configure them through the Android companion's profile command and verify the watch's Guardians screen before disconnecting the phone. Once configured, direct modem SMS/calls and GNSS do not require the phone to stay connected. Changing guardians still uses the app/BLE profile interface; this change does not add a phone-number editor to the watch.

For developer provisioning, an authenticated bonded BLE client may write the existing complete v1 profile JSON to `d973f2e2-b19e-11ee-a506-0242ac120002` after pairing through Phone Link. Use actual consented guardian numbers, preserve both slots in each write, and verify values on the watch. See `ANDROID_COMPANION_PRD.md` for the profile schema. No real phone numbers are embedded in firmware and no alerts are sent automatically at boot.

The small top-centre Bluetooth icon still reports **companion app connectivity**, not LTE registration. Open **System** for the modem status. The watch may have working cellular communication while the app icon is grey.

## 6. Voice-call limitation

The board advertises voice support, but a UART carries commands and data, not the watch's live microphone/speaker signal. The watch's ES7210/ES8311 I2S path is not automatically connected to the A7672S audio interface. Two-way audio needs a verified modem microphone/speaker/headset circuit or a separately designed and validated compatible digital audio bridge. Do not connect the watch speaker amplifier output to a modem microphone input.

This change implements call dial/hang-up control, not live voice routing through the watch audio hardware, incoming-call UI or audio evidence upload. Operator/SIM/firmware support determines whether voice/VoLTE actually works. The modem's advertised BLE is not enabled or used; the watch's existing ESP32 BLE handles the companion connection.

## 7. Bench validation sequence

1. With power off, verify 3.3 V TTL selection, pad identity, power polarity, common ground and antenna connections.
2. Power the modem separately. Verify a stable 5 V supply during network activity. Confirm SIM service and power-on procedure.
3. Flash normal firmware with self-test OFF. Watch System should move from waiting/check wiring to SIM/network status, then registered. If it does not, inspect power/TX-RX/baud/console configuration; do not trigger SOS as a connection test.
4. Configure and verify guardians. Use only an explicitly approved non-emergency test number for SMS/call tests. Never test by calling emergency services.
5. Send a single check-in SMS; compare modem acceptance, actual received text, Unicode and location link. Verify multipart reassembly where used.
6. Test outdoors until a real GNSS fix appears; verify position against a known location and test no-fix behavior indoors.
7. Test dial and End call with an approved number. Verify separately whether the attached modem audio circuit works; UART dialing alone is insufficient.
8. Test loss of signal, missing/locked SIM, GNSS antenna loss, UART disconnection and modem power cycling. Confirm UI remains responsive and uncertain submissions do not auto-retry.
9. Test a controlled SOS countdown cancellation before dispatch, followed by an approved real dispatch test. Recheck supply stability while siren/audio/display and LTE transmission are active together.

Host tests and build results are recorded separately from these hardware checks. Hardware remains untested until the module is physically connected.

## References

- [Exact Graylogix TTL carrier board](https://www.graylogix.in/product/sim-a7672s-4g-lte-ttl-modem-with-gnss-ble): supply, selector, antennas and carrier features.
- [SIMCom A7672 family](https://www.simcom.com/product/A7672X.html): module family and vendor documents; GNSS/BLE depend on SKU.
- [SIMCom A76XX AT manual, hosted by Waveshare](https://files.waveshare.com/wiki/A7670E-Cat-1-GNSS-HAT/A76XX_Series_AT_Command_Manual_V1.09.pdf): registration, SMS PDU submission, GNSS and call commands.
- [Official Waveshare watch schematic](https://files.waveshare.com/wiki/ESP32-S3-Touch-AMOLED-2.06/ESP32-S3-Touch-AMOLED-2.06.pdf): ESP32 UART nets and onboard peripherals.

## Implementation verification — 17 October 2026

- ESP-IDF 5.2.8 ESP32-S3 production build: passed, self-test OFF, USB console and direct A7672S enabled.
- Image: `firmware/build-a7672s/sheshield_sentinel.bin` (1,491,744 bytes; 53% of the 3 MB app partition remains free). The local production manifest records its checksum and explicitly marks hardware testing/flashing false.
- Existing BLE profile/action protocol host suite: passed.
- Actual C++ UI renderer/navigation preview with ASan/UBSan: passed.
- New A7672S transcript tests with ASan/UBSan: passed, covering model identity, command framing/late OK, LTE/legacy registration, GNSS coordinate parsing, UCS-2/multipart PDU generation, submission ambiguity, no automatic retries, call dial/hang-up and bounded malformed input.

Run the new protocol tests from the repository root:

```sh
clang++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -Ifirmware/main firmware/main/drivers/a7672s/modem_protocol.cpp \
  firmware/tools/tests/a7672s_protocol_test.cpp -o /tmp/naari-a7672s-tests
/tmp/naari-a7672s-tests
```

These tests substitute UART responses. They do not establish actual carrier delivery, antenna performance, GNSS acquisition, modem power integrity or two-way voice audio.
