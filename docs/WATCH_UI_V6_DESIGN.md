# Naari OS Watch UI v6 — Obsidian Titanium

Historical design notes. Superseded by [v7 native AMOLED design](WATCH_UI_V7_DESIGN.md),
which replaces the bitmap font, icon primitives, accent rails and outlined cards.

This visual system is designed around the real Waveshare ESP32-S3 Touch AMOLED 2.06 rendering path. The firmware UI remains the source of truth; browser simulators should mirror it rather than introduce controls or layouts that cannot be reproduced on the watch.

## Hardware-safe rules

- Canvas: 410 × 502 portrait, RGB565.
- Rendering: immediate-mode drawing into the existing 411 KB PSRAM framebuffer.
- Typography: the built-in 8 × 16 ASCII bitmap font at integer scale only.
- Assets: use geometry and the existing icon functions on firmware. Do not add PNG/JPEG decoding to the watch UI for decorative effects.
- Memory: drawing helpers must not allocate heap memory during a frame.
- Navigation: preserve `WatchScreenId`, the central `showScreen` dispatcher and the current screen touch zones.
- App drawer: preserve the three pages, four apps per page, item order and tap geometry.
- Refresh: static screens render once; animated screens continue using the existing tick schedule. Do not add per-frame full-screen animation to static screens.
- Color: declare firmware colors as RGB565 constants. Browser colors may use CSS equivalents but should retain the same hierarchy.

## Visual language

- Pure OLED black remains the base. A restrained blue-black inner shell and titanium edge add depth without alpha blending.
- Cards use a black shadow offset, graphite fill, quiet outline and a single top highlight.
- Mint communicates safety, coral communicates emergency, amber communicates active Guard mode, blue represents communication and cyan represents motion/location.
- The home face prioritizes time, protection state, safety rings and five quick actions.
- App icons sit in layered squircle tiles. Existing web artwork is preserved and receives consistent framing, contrast and shadow treatment.
- Emergency screens remain visually dominant and use coral only where urgency benefits comprehension.

## Reusable firmware helpers

- `drawPremiumBackdrop(accent)` creates the allocation-free OLED shell and accent rails.
- `drawPremiumHeader(title, accent)` provides consistent app navigation.
- `drawPremiumCard(...)` supplies the shadow, fill, outline and highlight shared by cards.
- `drawIconTile(...)` creates a premium squircle and dispatches to the existing pixel icon set.

When adding a screen, start with these helpers. Keep interactive content inside x=22..388 and avoid placing critical controls under the side accent rails. Verify C++ syntax, build with the installed ESP-IDF toolchain, then flash and inspect the physical AMOLED before changing touch geometry.
