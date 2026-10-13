# Naari OS Watch UI — native AMOLED design, v7

Physical-watch photographs revealed the shortcomings of v6: the 8×16 font had
a blocky appearance, improvised icons were difficult to recognize, bright edges
dominated the screen, and contact details collided with the call/SMS buttons.
The browser CSS simulator concealed these limitations.

## Rendering contract

- Keep CO5300 QSPI initialization, panel offsets, DMA chunks and RGB565 wire byte
  order intact. The canvas remains 410×502.
- Retain the existing 411,640-byte PSRAM framebuffer and 16 KB DMA buffer.
- Draw directly into that buffer. No LVGL, runtime SVG/PNG decoder, second
  framebuffer, or heap allocations inside drawing routines.
- Text and symbols use flash-resident 4-bit coverage masks. Undo wire byte order
  before blending with the existing pixel; store through `drawPixel` afterward.
- Inter glyphs are baked at their actual display sizes. Do not magnify 8×16
  glyphs for headings or clocks. The SIL OFL font license is included in
  `firmware/tools/assets/Inter-LICENSE.txt`.
- Symbols come from original normalized vector paths in the asset generator,
  precomputed into coverage masks. Firmware does not parse those paths.
- Rounded shapes and ring edges use antialiasing. Icon gradients use ordered
  dithering to reduce visible RGB565 banding.
- Maintain three drawer pages, four apps per page, app order, card positions and
  hitboxes. Home complications now have individual matching actions.
- Static screens still render on navigation; animated screens retain the
  existing tick cadence. Screen changes log render/transfer time.

## Visual system

OLED black surrounds neutral charcoal cards. White titles and muted secondary
text establish hierarchy without bright outlines or decorative rails. Mint
denotes safety, coral SOS, blue communication, amber Guard, and purple sound and
face customization.

The drawer uses consistent 76-pixel icon tiles and shorter captions. Contact
names, actions and phone details occupy separate rows. Headers use a simple
back chevron; primary actions use clear rounded buttons. The clock, keypad,
controls, sound picker, motion view, diagnostics, Guard, SOS, cancel flow,
call/message overlays, recorder states, flashlight and face picker share the
same typography, spacing and symbol treatment.

Diagnostic labels use available battery, modem and IMU data. The home face no
longer presents hard-coded `72 BPM` or `3D FIX` as live readings. Existing modem
and audio behavior is outside this visual change; decorative rings and recorder
waves remain illustrative.

## Exact firmware preview

```sh
python3 firmware/tools/preview_ui.py
python3 firmware/tools/preview_ui.py --serve
```

The host executable compiles the **actual** `ui_manager.cpp`, `ui_graphics.cpp`,
and verbatim drawing methods extracted from the display driver. Only device
I/O, sensor readings and time are mocked. Calls, messages, SOS and audio in the
preview cannot reach the watch or a network.

Frames export from the byte-swapped RGB565 buffer into `firmware/build-ui-preview/`.
The contact sheet is `overview.png`. The browser preview at
`http://127.0.0.1:5174/` sends taps, swipes and crown presses through firmware
touch dispatch and displays exported pixels. Hardware sensors, audio, radio,
brightness and frame timing still require the physical watch.

Host checks cover RGB565 blending, bounds with AddressSanitizer and
UndefinedBehaviorSanitizer, glyph widths, drawer pagination, keypad input,
contact actions and SOS cancellation. Every screen plus recorder, torch and
wallpaper states is rendered. Build the production target using
`idf.py -B build-premium build` before flashing.

To regenerate masks, install Pillow and CairoSVG and run
`firmware/tools/generate_ui_assets.py`. On macOS, CairoSVG may need
`DYLD_FALLBACK_LIBRARY_PATH=/opt/homebrew/lib` to find Cairo. The generated
header lives in the source workspace, so firmware builds require none of these
asset-generation dependencies.

References: [Waveshare board documentation](https://www.waveshare.com/wiki/ESP32-S3-Touch-AMOLED-2.06),
[Inter source and license](https://github.com/rsms/inter).
