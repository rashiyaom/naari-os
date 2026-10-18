# Naari OS A3 conference poster

This folder contains the finished A3 portrait research poster for Naari OS.

## Deliverables

- `naari-os-a3-poster.pdf` — canonical print file, A3 portrait with vector text, diagrams and QR code.
- `naari-os-a3-poster.png` — 3508 × 4961 px proof at 300 dpi.
- `naari-os-a3-poster-preview.png` — compact preview for quick review.
- `generate_poster.py` — editable source and reproducible generator.

The QR code opens <https://narios.vercel.app>. The prototype images are rendered by the production C++ watch UI and are sourced from `firmware/build-ui-preview/`.

## Required personalisation before printing

The poster intentionally provides four blank lines for registered team-member names and a blank college field. Enter those in `generate_poster.py`, regenerate the PDF, and inspect the proof before printing. Confirm the organizer's exact official P6 problem-statement wording; the artwork currently uses the concise label “Women’s Safety Wearable System.”

Do not remove the validation-status wording. It distinguishes hardware-demonstrated work, the built Android/BLE implementation, and ongoing fall/LTE testing.

## Regenerate

From the repository root:

```bash
python3 conference-poster/generate_poster.py
```

Dependencies: Python 3, ReportLab and `qrcode`. The included Inter variable font is embedded in the PDF. On macOS, the committed PNGs can be refreshed from the PDF using the repository's `render_poster.sh` helper.

## Print specification

- Page: A3 portrait, 297 × 420 mm.
- Print the PDF at 100% / actual size; disable “fit to page.”
- Use colour output on matte or satin stock.
- Inspect team/college details and test the QR code on the physical proof.
