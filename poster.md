# A3 Poster Brief — Naari OS / SheShield Sentinel

This document supplies the copy, images, layout and checks needed to produce the poster. It describes the current repository prototype, not all capabilities promised in older proposals. It is a content/design brief, not the final print PDF.

## 1. Required print format

- A3 portrait: **297 × 420 mm**.
- Export a vector PDF with embedded fonts, or **3508 × 4961 px at 300 dpi**. Do not upscale low-resolution screenshots and call them 300 dpi.
- White background; purple **#6a1794** and magenta **#b8005e** accents.
- Use **Inter** throughout. The project includes Inter assets/license under `firmware/tools/assets/`; use an appropriate desktop font file for poster typesetting.
- Product title: **at least 72 pt**. Section headings: **at least 36 pt**. Body: **at least 20 pt**. Use these sizes in the final printed document, not merely in the editing canvas.
- Leave clear space around sections. If content does not fit, shorten it; do not reduce required font sizes.
- Bring **one printed colour copy to SVNIT Surat on 25 October 2026**.

## 2. Top strip — exact event wording

Full-width purple band, approximately **60 mm tall**, with white text. Keep text inset from the page edges.

**Line 1 — copy exactly:**

> IEEE WIE ILS 2026 · HackX Grand Finale · SVNIT Surat · 25 October 2026

**Line 2 — complete before print:**

> Track 3: SheLeads · P6: [OFFICIAL PROBLEM STATEMENT TITLE]

The existing presentation site identifies **Track 3 SheLeads**, **P6**, and **Team Puraanmaya** in `sheshield-sentinel-guardian-main/src/components/sections/Hero.tsx`. The repository does not establish the full official P6 title. Confirm the registered track spelling and paste the organizer's exact problem-statement title; do not invent it. Line 1 is long: check its legibility and fit at full printed size. Keep the two logical lines required by the organizer; ask the organizer about wrapping if necessary rather than altering event wording.

## 3. Title block — below the strip

**Largest text:**

> NAARI OS

**Supporting identity:**

> SheShield Sentinel — Wearable Safety Prototype

**Team and college:**

> Team Puraanmaya · [FULL COLLEGE NAME]

Naari OS is the current firmware name; SheShield Sentinel is the existing project/product identity. Using both connects the working watch with the original submission. Verify the team spelling against registration. College identity is not available in the inspected project files.

## 4. Poster content — copy in exactly this order

The following is the short, poster-ready copy. Keep implementation notes elsewhere in this document off the printed poster.

### 1. The Problem

> During unsafe journeys or a sudden fall, reaching a phone to ask for help can be difficult.
> Women and vulnerable users need an accessible way to initiate a safety alert from their wrist.

Do not add an invented prevalence statistic. No verified public safety statistic is supplied by the repository. If a number is desired, use the prototype's **30-second cancellation window** in the solution/architecture, not as a population statistic.

### 2. Our Solution

> A touch-screen safety watch with wrist-based SOS, fall detection and voice memos.
> BLE connects it to an Android phone for guardian alerts, calls and location; the companion app is the next integration step.

The second sentence must retain its development status until a real Android app and phone actions have been tested. Do not label the current prototype phone-independent.

### 3. How It Works

Render this as a simple architecture diagram, with solid lines for implemented watch functionality and a dashed line for the planned phone executor.

```text
SOS / motion sensors / microphone
               ↓
        ESP32-S3 watch
   UI • fall detection • voice memo
               ↓
   SOS → 30-second cancel window
               ↓
      Bonded BLE request bridge
               ⇢
  Android companion — IN DEVELOPMENT
               ⇢
 Guardian SMS / phone call / phone location
```

**Diagram legend:**

> Solid: implemented watch path · Dashed: Android integration pending

Use a separate short microphone → local recording branch if the main flow would imply that every memo triggers SOS. Suggested compact diagram: watch box contains “SOS + fall detection” and “Local voice memos” as separate rows, then only the SOS row feeds the cancellation window. The phone is the location source; do not draw GPS hardware inside the watch. Ordinary phone-call requests are separate from SOS; do not imply an automatic emergency-services call.

### 4. Prototype

Use **three real device photos**, arranged in one row or a compact grid:

1. **Watch face** — caption: “Working 410 × 502 AMOLED interface.”
2. **Voice recorder** — caption: “On-watch audio capture and playback.”
3. **Fall detection / Phone Link** — caption: “Local fall monitoring and bonded BLE.”

Prefer newly captured photos of the latest flashed build, with the recorder and Fall Detection/Phone Link screens open. The user-supplied physical watch photos already show real hardware, app drawer, guardians and home face, but they show an earlier UI state. They can be used if accurately captioned; do not present them as the latest firmware without checking.

If only the earlier photos are available, use: **watch face**, **app drawer**, **guardian screen**. Caption the guardian image “Guardian interface”; it does not prove that the phone sent SMS or placed calls. Avoid displaying personal phone numbers in print—crop or redact them.

Project-generated images available as supplementary options:

- `firmware/build-ui-preview/home.png`
- `firmware/build-ui-preview/recorder.png`
- `firmware/build-ui-preview/fall-detection.png`
- `firmware/build-ui-preview/phone-link.png`

These come from the **actual C++ UI renderer with mocked hardware**, not a stock image, but they are **native UI previews**, not physical-device evidence. If used, label them “Firmware UI preview”. Do not use `calling.png`, `message.png`, or simulated companion/map screens to suggest verified phone execution. The existing preview output can become stale after edits; regenerate it before selection.

### 5. Tech Stack

> **Hardware:** ESP32-S3 · 2.06″ AMOLED · QMI8658 IMU · ES7210/ES8311 audio
> **Firmware:** C/C++ · ESP-IDF · FreeRTOS · NimBLE
> **Next:** Native Android companion · Kotlin / Jetpack Compose

“Kotlin / Jetpack Compose” is the planned stack, not an already-built application. Keep it on the explicitly labeled Next line. The display is rendered using custom RGB565 drawing, **not LVGL**. Web dashboard/backend technology is optional context, not necessary in this concise primary-system stack.

### 6. Impact & What's Next

> **Goal:** Make help requests accessible from the wrist.
> **Next:** Complete Android SMS/call/location integration, guardian setup and recording sync; validate fall detection and reliability on real devices.

Expected impact is a product goal, not a measured reduction in harm. Do not claim guaranteed protection, medically validated fall detection, automatic evidence backup, or a demonstrated response-time improvement.

### 7. Team

> **Team Puraanmaya**
> [MEMBER 1 FULL NAME] · [MEMBER 2 FULL NAME]
> [MEMBER 3 FULL NAME, IF APPLICABLE] · [MEMBER 4 FULL NAME, IF APPLICABLE]
> [FULL COLLEGE NAME]

Use actual registered team members. Do not infer names from filesystem usernames. Team photos are optional; omit them if they crowd the required content.

## 5. Footer

**Bottom-right:** QR code pointing to a public demo video or the actual GitHub repository.

**Suggested label:**

> Scan to see the prototype

**URL to supply:** `[PUBLIC DEMO VIDEO OR GITHUB REPOSITORY URL]`

No verified public project/demo URL was found in the inspected project identity files. Do not use the localhost preview URL, a filesystem path, a private inaccessible repository or a generic GitHub homepage. Generate the QR only after selecting the final URL. Print it roughly **25–30 mm square**, dark modules on white with an intact quiet zone; test on the printed proof with more than one phone.

**Bottom-left:** Official college logo, optional. Obtain the correct high-resolution/vector asset and college spelling. **No other logos:** no IEEE/WIE, Apple, Android, sponsor, hardware-vendor or team logo additions. The event name remains text in the strip.

## 6. Suggested A3 arrangement

- Top 60 mm: event strip.
- Below: title and team/college identity.
- Then sections 1 and 2 as two short full-width blocks.
- Section 3: compact diagram with short labels.
- Section 4: three images with concise captions.
- Sections 5 and 6: two columns, reading left-to-right.
- Section 7 and footer: names, optional college logo, QR bottom-right.

Use approximately 10–12 mm content margins and 5–8 mm gaps as starting values. The required 72/36/20 pt typography is large for seven sections on A3: make a full-scale proof and tighten copy or image count to two if needed. All numbered sections must remain visible in order. Do not paste this entire brief onto the poster.

## 7. Evidence and claim checks

| Poster statement | Project evidence | Limit to preserve |
| --- | --- | --- |
| Working watch interface | `firmware/main/ui/ui_manager.cpp`; actual device photos | UI previews alone are not hardware tests |
| 410 × 502 AMOLED | `firmware/main/drivers/display_co5300/`; UI framebuffer | Custom RGB565 renderer |
| Audio capture/playback | `firmware/main/drivers/audio_es7210_es8311/audio_manager.cpp` | Current single memo is RAM-only, up to 10 seconds; no phone archival yet |
| Fall detection | `firmware/main/safety/fall_detector.cpp`; IMU driver | Wrist heuristic; not medically validated |
| SOS cancellation | `firmware/main/state_machine/` | Approximately 30-second grace window; phone execution still requires app |
| Secure BLE phone bridge | `firmware/main/network/phone_bridge.cpp` | BLE transport exists; Android end-to-end execution not yet demonstrated |
| Two guardian slots | Phone bridge profile persistence | App-based editing planned; do not imply unlimited guardians |
| Android APK and recording sync | `docs/ANDROID_COMPANION_PRD.md` | Requirements only, not delivered functionality |
| SIM800L future backup | `firmware/main/drivers/sim800l/` | Current adapter routes via phone; direct modem backup not verified |

Avoid historical claims in the original proposal/site: standalone GSM protection, guaranteed alert delivery, live phone-independent GPS, completed offline wake words, continuous SD evidence recording, proven 105/110 dB output, measured real heart rate, or cloud evidence delivery. Those are not established by the current implementation.

## 8. Assets and details still needed

Before final artwork, collect:

- Exact official P6 problem-statement title and confirmation of “Track 3: SheLeads”.
- Registered team spelling, member full names and college name.
- Two to four current physical prototype photos, preferably three listed above.
- Public demo/repository URL and generated, tested QR code.
- Optional official college logo.
- Final firmware/app status as of printing. If Android integration is completed and physically tested before the event, update the solution/diagram/stack accordingly; only change claims supported by that test.

## 9. Final print checklist

- [ ] A3 portrait dimensions and vector PDF / correct 300 dpi raster dimensions.
- [ ] Purple #6a1794 top band approximately 6 cm tall; exact event text and confirmed track/problem line.
- [ ] Product title, registered team name and college present.
- [ ] Sections 1–7 in the requested order.
- [ ] One sans-serif family; minimum 72 pt title, 36 pt section headings, 20 pt body.
- [ ] Two to four real prototype visuals; previews clearly labeled where used.
- [ ] Planned app features clearly identified; no fake completion claims or personal phone numbers exposed.
- [ ] QR bottom-right; optional college logo bottom-left; no other logos.
- [ ] QR and text checked at actual print size; colour proof inspected.
- [ ] One colour copy ready to bring to SVNIT Surat on 25 October 2026.
