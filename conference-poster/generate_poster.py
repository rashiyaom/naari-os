#!/usr/bin/env python3
"""Generate the print-ready Naari OS A3 research poster.

The PDF is the canonical, vector-first artifact. Raster firmware screenshots are
embedded at their native resolution and everything else (text, diagrams, QR,
icons and device frames) remains vector artwork.
"""

from __future__ import annotations

from pathlib import Path

import qrcode
from reportlab.lib.colors import Color, HexColor, white
from reportlab.lib.pagesizes import A3
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = Path(__file__).resolve().parent
OUT_PDF = OUT_DIR / "naari-os-a3-poster.pdf"
FONT_PATH = ROOT / "firmware/tools/assets/InterVariable.ttf"
PREVIEW_DIR = ROOT / "firmware/build-ui-preview"

PURPLE = HexColor("#6A1794")
PURPLE_DARK = HexColor("#3B0A57")
PURPLE_TINT = HexColor("#F4ECF8")
MAGENTA = HexColor("#B8005E")
MAGENTA_TINT = HexColor("#FBEAF2")
TEAL = HexColor("#007F73")
TEAL_BRIGHT = HexColor("#00BFA5")
TEAL_TINT = HexColor("#E6F7F4")
INK = HexColor("#15111A")
MUTED = HexColor("#5F5965")
LINE = HexColor("#DED7E2")
PAPER = HexColor("#FBF9FC")
AMBER = HexColor("#B86400")
RED = HexColor("#C72D46")
GREEN = HexColor("#23845A")

PAGE_W, PAGE_H = A3
MARGIN = 10 * mm
GAP = 5 * mm
COL_W = (PAGE_W - 2 * MARGIN - 2 * GAP) / 3


def register_font() -> tuple[str, str]:
    if FONT_PATH.exists():
        pdfmetrics.registerFont(TTFont("Inter", str(FONT_PATH)))
        return "Inter", "Inter"
    return "Helvetica", "Helvetica-Bold"


FONT, FONT_BOLD = register_font()


def rounded_card(c: canvas.Canvas, x: float, y: float, w: float, h: float,
                 fill=white, radius: float = 4.2 * mm, stroke=LINE) -> None:
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(0.55)
    c.roundRect(x, y, w, h, radius, fill=1, stroke=1)


def wrap_text(text: str, width: float, size: float, font: str = FONT) -> list[str]:
    lines: list[str] = []
    for paragraph in text.split("\n"):
        if not paragraph:
            lines.append("")
            continue
        words = paragraph.split()
        current = words[0]
        for word in words[1:]:
            candidate = f"{current} {word}"
            if pdfmetrics.stringWidth(candidate, font, size) <= width:
                current = candidate
            else:
                lines.append(current)
                current = word
        lines.append(current)
    return lines


def paragraph(c: canvas.Canvas, text: str, x: float, y_top: float, width: float,
              size: float = 14.5, leading: float | None = None, color=INK,
              font: str = FONT, max_lines: int | None = None) -> float:
    leading = leading or size * 1.32
    lines = wrap_text(text, width, size, font)
    if max_lines is not None:
        lines = lines[:max_lines]
    c.setFont(font, size)
    c.setFillColor(color)
    y = y_top
    for line in lines:
        c.drawString(x, y, line)
        y -= leading
    return y


def section_heading(c: canvas.Canvas, number: str, title: str, x: float,
                    y_top: float, accent=PURPLE) -> float:
    c.setFillColor(accent)
    c.roundRect(x, y_top - 8.2 * mm, 8.2 * mm, 8.2 * mm, 2.2 * mm, fill=1, stroke=0)
    c.setFillColor(white)
    c.setFont(FONT_BOLD, 16)
    c.drawCentredString(x + 4.1 * mm, y_top - 5.65 * mm, number)
    c.setFillColor(INK)
    c.setFont(FONT_BOLD, 21)
    c.drawString(x + 11 * mm, y_top - 6.2 * mm, title.upper())
    return y_top - 12.5 * mm


def small_label(c: canvas.Canvas, text: str, x: float, y: float, fill=PURPLE_TINT,
                color=PURPLE_DARK, width: float | None = None) -> float:
    size = 8.8
    text_w = pdfmetrics.stringWidth(text, FONT_BOLD, size)
    w = width or text_w + 6 * mm
    c.setFillColor(fill)
    c.roundRect(x, y - 4.4 * mm, w, 6.6 * mm, 3.3 * mm, fill=1, stroke=0)
    c.setFillColor(color)
    c.setFont(FONT_BOLD, size)
    c.drawCentredString(x + w / 2, y - 2.3 * mm, text)
    return w


def bullet(c: canvas.Canvas, text: str, x: float, y_top: float, width: float,
           color=INK, dot=TEAL, size: float = 13.0) -> float:
    c.setFillColor(dot)
    c.circle(x + 1.5 * mm, y_top - 1.5 * mm, 1.2 * mm, fill=1, stroke=0)
    return paragraph(c, text, x + 5 * mm, y_top, width - 5 * mm, size=size,
                     leading=size * 1.28, color=color)


def arrow(c: canvas.Canvas, x1: float, y1: float, x2: float, y2: float,
          color=PURPLE, dashed: bool = False) -> None:
    c.saveState()
    c.setStrokeColor(color)
    c.setFillColor(color)
    c.setLineWidth(1.8)
    if dashed:
        c.setDash(4, 3)
    c.line(x1, y1, x2, y2)
    import math
    angle = math.atan2(y2 - y1, x2 - x1)
    length = 6
    p1 = (x2 - length * math.cos(angle - 0.55), y2 - length * math.sin(angle - 0.55))
    p2 = (x2 - length * math.cos(angle + 0.55), y2 - length * math.sin(angle + 0.55))
    path = c.beginPath()
    path.moveTo(x2, y2)
    path.lineTo(*p1)
    path.lineTo(*p2)
    path.close()
    c.drawPath(path, fill=1, stroke=0)
    c.restoreState()


def architecture_box(c: canvas.Canvas, x: float, y: float, w: float, h: float,
                     title: str, subtitle: str, fill, accent, dashed=False) -> None:
    c.saveState()
    c.setFillColor(fill)
    c.setStrokeColor(accent)
    c.setLineWidth(1.2)
    if dashed:
        c.setDash(4, 3)
    c.roundRect(x, y, w, h, 3 * mm, fill=1, stroke=1)
    c.restoreState()
    c.setFillColor(accent)
    c.circle(x + 6 * mm, y + h / 2, 2.5 * mm, fill=1, stroke=0)
    c.setFillColor(INK)
    c.setFont(FONT_BOLD, 12.2)
    c.drawString(x + 11 * mm, y + h - 6.5 * mm, title)
    paragraph(c, subtitle, x + 11 * mm, y + h - 12.5 * mm, w - 15 * mm,
              size=8.6, leading=10.8, color=MUTED, max_lines=2)


def draw_watch(c: canvas.Canvas, image: Path, x: float, y: float, w: float,
               label: str, hero: bool = False) -> None:
    screen_ratio = 410 / 502
    frame_w = w
    frame_h = frame_w / screen_ratio
    strap_w = frame_w * 0.45
    strap_h = frame_h * (0.22 if hero else 0.16)
    c.setFillColor(HexColor("#19171D"))
    c.roundRect(x + (frame_w - strap_w) / 2, y + frame_h - 1 * mm,
                strap_w, strap_h, 2.2 * mm, fill=1, stroke=0)
    c.roundRect(x + (frame_w - strap_w) / 2, y - strap_h + 1 * mm,
                strap_w, strap_h, 2.2 * mm, fill=1, stroke=0)
    c.setFillColor(HexColor("#111014"))
    c.setStrokeColor(HexColor("#343038"))
    c.setLineWidth(1.1)
    c.roundRect(x, y, frame_w, frame_h, 5 * mm, fill=1, stroke=1)
    bezel = 2.2 * mm
    if image.exists():
        c.drawImage(str(image), x + bezel, y + bezel,
                    width=frame_w - 2 * bezel, height=frame_h - 2 * bezel,
                    preserveAspectRatio=False, mask="auto")
    else:
        c.setFillColor(HexColor("#2B2630"))
        c.roundRect(x + bezel, y + bezel, frame_w - 2 * bezel,
                    frame_h - 2 * bezel, 3 * mm, fill=1, stroke=0)
        c.setFillColor(white)
        c.setFont(FONT, 8)
        c.drawCentredString(x + frame_w / 2, y + frame_h / 2, "PREVIEW MISSING")
    c.setFillColor(MUTED)
    c.setFont(FONT, 8.3 if hero else 7.7)
    c.drawCentredString(x + frame_w / 2, y - strap_h - 2.8 * mm, label)


def draw_qr(c: canvas.Canvas, url: str, x: float, y: float, size: float) -> None:
    qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_M,
                       box_size=1, border=4)
    qr.add_data(url)
    qr.make(fit=True)
    matrix = qr.get_matrix()
    n = len(matrix)
    cell = size / n
    c.setFillColor(white)
    c.rect(x, y, size, size, fill=1, stroke=0)
    c.setFillColor(PURPLE_DARK)
    for row, values in enumerate(matrix):
        for col, enabled in enumerate(values):
            if enabled:
                c.rect(x + col * cell, y + (n - row - 1) * cell,
                       cell + 0.08, cell + 0.08, fill=1, stroke=0)


def draw_top_band(c: canvas.Canvas) -> None:
    band_h = 60 * mm
    band_y = PAGE_H - band_h
    c.setFillColor(PURPLE)
    c.rect(0, band_y, PAGE_W, band_h, fill=1, stroke=0)
    c.setFillColor(MAGENTA)
    path = c.beginPath()
    path.moveTo(PAGE_W * 0.73, band_y)
    path.curveTo(PAGE_W * 0.82, band_y + 18 * mm, PAGE_W * 0.89,
                 PAGE_H - 3 * mm, PAGE_W, PAGE_H)
    path.lineTo(PAGE_W, band_y)
    path.close()
    c.drawPath(path, fill=1, stroke=0)
    c.setStrokeColor(Color(1, 1, 1, alpha=0.11))
    c.setLineWidth(0.5)
    for offset in range(-80, 500, 28):
        c.line(offset * mm / 10, band_y, (offset + 180) * mm / 10, PAGE_H)

    c.setFillColor(white)
    c.setFont(FONT_BOLD, 16.5)
    c.drawString(MARGIN, PAGE_H - 18 * mm,
                 "IEEE WIE ILS 2026 · HackX Grand Finale · SVNIT Surat · 25 October 2026")
    c.setFont(FONT_BOLD, 19)
    c.drawString(MARGIN, PAGE_H - 31.5 * mm,
                 "Track 3: SheLeads · P6: Women’s Safety Wearable System")
    c.setFont(FONT, 10.5)
    c.setFillColor(Color(1, 1, 1, alpha=0.82))
    c.drawString(MARGIN, PAGE_H - 43 * mm,
                 "EDGE SAFETY  ·  WEARABLE COMPUTING  ·  HUMAN-CENTRED DESIGN")
    c.setFillColor(white)
    c.setFont(FONT_BOLD, 9.2)
    c.drawRightString(PAGE_W - MARGIN, PAGE_H - 51 * mm, "RESEARCH PROTOTYPE  •  2026")


def draw_title(c: canvas.Canvas) -> None:
    y0 = PAGE_H - 60 * mm
    c.setFillColor(PAPER)
    c.rect(0, y0 - 45 * mm, PAGE_W, 45 * mm, fill=1, stroke=0)
    c.setFillColor(INK)
    c.setFont(FONT_BOLD, 73)
    c.drawString(MARGIN, y0 - 24 * mm, "NAARI")
    title_w = pdfmetrics.stringWidth("NAARI", FONT_BOLD, 73)
    c.setFillColor(MAGENTA)
    c.drawString(MARGIN + title_w + 4 * mm, y0 - 24 * mm, "OS")
    c.setFillColor(PURPLE_DARK)
    c.setFont(FONT_BOLD, 18)
    c.drawString(MARGIN, y0 - 35.5 * mm, "A wrist-first safety system for faster, deliberate help requests")
    c.setFillColor(MUTED)
    c.setFont(FONT, 11.8)
    c.drawRightString(PAGE_W - MARGIN, y0 - 19 * mm, "TEAM PURAANMAYA")
    c.drawRightString(PAGE_W - MARGIN, y0 - 27 * mm, "COLLEGE: __________________________")
    c.setStrokeColor(MAGENTA)
    c.setLineWidth(2.5)
    c.line(MARGIN, y0 - 42 * mm, PAGE_W - MARGIN, y0 - 42 * mm)


def draw_problem_solution(c: canvas.Canvas, x: float, y: float, w: float, h: float) -> None:
    rounded_card(c, x, y, w, h)
    pad = 6 * mm
    top = y + h - pad
    cur = section_heading(c, "1", "The Problem", x + pad, top, MAGENTA)
    cur = paragraph(c,
        "In a fall or unsafe journey, unlocking and operating a phone can delay a request for help. A wearable must make the action immediate while limiting accidental escalation.",
        x + pad, cur, w - 2 * pad, size=13.6, leading=17.7, color=INK)
    cur -= 4 * mm
    c.setFillColor(MAGENTA_TINT)
    c.roundRect(x + pad, cur - 18 * mm, w - 2 * pad, 20 * mm, 3 * mm, fill=1, stroke=0)
    c.setFillColor(MAGENTA)
    c.setFont(FONT_BOLD, 24)
    c.drawString(x + pad + 4 * mm, cur - 7 * mm, "1 action")
    c.setFillColor(MUTED)
    c.setFont(FONT, 10.5)
    c.drawString(x + pad + 4 * mm, cur - 13 * mm, "from the wrist to start an SOS workflow")
    cur -= 25 * mm

    cur = section_heading(c, "2", "Our Solution", x + pad, cur, PURPLE)
    cur = paragraph(c,
        "Naari OS combines a touch-screen ESP32-S3 watch and an Android companion. The watch keeps critical sensing and interaction local; the phone provides guardian communication and location services over authenticated BLE.",
        x + pad, cur, w - 2 * pad, size=13.3, leading=17.2)
    cur -= 4 * mm
    small_label(c, "DESIGN HYPOTHESIS", x + pad, cur, TEAL_TINT, TEAL)
    cur -= 9 * mm
    for text in [
        "Wrist-first action reduces interaction effort under stress.",
        "A 30 s review window and 3 s hold reduce accidental cancellation.",
        "Local sensing continues even when the phone link is unavailable.",
    ]:
        cur = bullet(c, text, x + pad, cur, w - 2 * pad, size=11.7)
        cur -= 2.5 * mm


def draw_architecture(c: canvas.Canvas, x: float, y: float, w: float, h: float) -> None:
    rounded_card(c, x, y, w, h, fill=HexColor("#FFFEFF"))
    pad = 6 * mm
    top = y + h - pad
    section_heading(c, "3", "How It Works", x + pad, top, TEAL)
    box_x = x + 9 * mm
    box_w = w - 18 * mm
    box_h = 22 * mm
    first_y = y + h - 50 * mm
    architecture_box(c, box_x, first_y, box_w, box_h,
                     "1  SIGNALS + USER INPUT",
                     "Touch SOS · QMI8658 motion · microphone", TEAL_TINT, TEAL)
    arrow(c, x + w / 2, first_y, x + w / 2, first_y - 8 * mm, TEAL)
    second_y = first_y - 31 * mm
    architecture_box(c, box_x, second_y, box_w, box_h,
                     "2  EDGE DECISION ON WATCH",
                     "Fall candidate · manual SOS · 30 s cancel", PURPLE_TINT, PURPLE)
    arrow(c, x + w / 2, second_y, x + w / 2, second_y - 8 * mm, PURPLE)
    third_y = second_y - 31 * mm
    architecture_box(c, box_x, third_y, box_w, box_h,
                     "3  AUTHENTICATED BLE BRIDGE",
                     "Bonded Android link · request identity · status", HexColor("#EEF3FF"), HexColor("#335EA8"))
    arrow(c, x + w / 2, third_y, x + w / 2, third_y - 8 * mm, HexColor("#335EA8"))
    fourth_y = third_y - 31 * mm
    architecture_box(c, box_x, fourth_y, box_w, box_h,
                     "4  PHONE ACTION EXECUTOR",
                     "Guardian SMS · call request · phone location", MAGENTA_TINT, MAGENTA)
    arrow(c, x + w / 2, fourth_y, x + w / 2, fourth_y - 8 * mm, MAGENTA)
    fifth_y = fourth_y - 31 * mm
    architecture_box(c, box_x, fifth_y, box_w, box_h,
                     "5  GUARDIAN RESPONSE",
                     "Action result returns to watch and incident log", HexColor("#EDF8F1"), GREEN)

    branch_y = y + 10 * mm
    c.setFillColor(HexColor("#F7F3F9"))
    c.roundRect(x + pad, branch_y, w - 2 * pad, 16 * mm, 3 * mm, fill=1, stroke=0)
    c.setFillColor(PURPLE_DARK)
    c.setFont(FONT_BOLD, 10.4)
    c.drawString(x + pad + 4 * mm, branch_y + 10 * mm, "PARALLEL EVIDENCE PATH")
    c.setFont(FONT, 9.1)
    c.setFillColor(MUTED)
    c.drawString(x + pad + 4 * mm, branch_y + 4.7 * mm,
                 "Voice memo → local storage → verified BLE transfer to phone")


def draw_prototype(c: canvas.Canvas, x: float, y: float, w: float, h: float) -> None:
    rounded_card(c, x, y, w, h)
    pad = 6 * mm
    top = y + h - pad
    section_heading(c, "4", "Prototype", x + pad, top, MAGENTA)
    hero_w = 47 * mm
    hero_h = hero_w / (410 / 502)
    hero_x = x + (w - hero_w) / 2
    hero_y = y + h - 128 * mm
    draw_watch(c, PREVIEW_DIR / "home-bluetooth-paired.png", hero_x, hero_y,
               hero_w, "AMOLED home + BLE state", hero=True)
    small_w = 29 * mm
    small_y = y + 27 * mm
    draw_watch(c, PREVIEW_DIR / "fall-detection.png", x + 9 * mm, small_y,
               small_w, "Motion monitoring")
    draw_watch(c, PREVIEW_DIR / "memo-library.png", x + w - 9 * mm - small_w,
               small_y, small_w, "Saved voice memos")
    c.setFillColor(MUTED)
    c.setFont(FONT, 8.5)
    c.drawCentredString(x + w / 2, y + 7 * mm,
                       "Screens rendered by the production C++ watch UI")


def draw_tech(c: canvas.Canvas, x: float, y: float, w: float, h: float) -> None:
    rounded_card(c, x, y, w, h)
    pad = 6 * mm
    cur = section_heading(c, "5", "Tech Stack", x + pad, y + h - pad, PURPLE)
    rows = [
        ("WATCH", "ESP32-S3 · 410×502 AMOLED"),
        ("SENSING", "QMI8658 IMU · touch input"),
        ("AUDIO", "ES7210 ADC · ES8311 codec"),
        ("FIRMWARE", "C/C++ · ESP-IDF · FreeRTOS"),
        ("CONNECT", "NimBLE · authenticated GATT"),
        ("ANDROID", "Kotlin · Jetpack Compose · Room"),
    ]
    for tag, value in rows:
        c.setFillColor(PURPLE_TINT if tag != "ANDROID" else TEAL_TINT)
        c.roundRect(x + pad, cur - 6.5 * mm, 20 * mm, 6.8 * mm, 2.5 * mm, fill=1, stroke=0)
        c.setFillColor(PURPLE_DARK if tag != "ANDROID" else TEAL)
        c.setFont(FONT_BOLD, 7.8)
        c.drawCentredString(x + pad + 10 * mm, cur - 4.4 * mm, tag)
        c.setFillColor(INK)
        c.setFont(FONT, 10.7)
        c.drawString(x + pad + 23 * mm, cur - 4.5 * mm, value)
        cur -= 9.7 * mm


def draw_impact(c: canvas.Canvas, x: float, y: float, w: float, h: float) -> None:
    rounded_card(c, x, y, w, h)
    pad = 6 * mm
    cur = section_heading(c, "6", "Impact & Next", x + pad, y + h - pad, MAGENTA)
    c.setFont(FONT_BOLD, 11.6)
    c.setFillColor(INK)
    c.drawString(x + pad, cur, "Expected impact")
    cur -= 6 * mm
    cur = bullet(c, "Quicker access to a deliberate safety workflow from the wrist.",
                 x + pad, cur, w - 2 * pad, size=10.8)
    cur -= 3 * mm
    c.setFont(FONT_BOLD, 11.6)
    c.setFillColor(INK)
    c.drawString(x + pad, cur, "Research validation")
    cur -= 7 * mm
    status = [
        (GREEN, "DEMONSTRATED", "Watch UI, IMU, audio + persistent memos"),
        (AMBER, "BUILT", "Android APK + BLE sync protocol"),
        (RED, "ONGOING", "Fall accuracy + LTE field tests"),
    ]
    for color, label, detail in status:
        c.setFillColor(color)
        c.circle(x + pad + 1.5 * mm, cur - 1.3 * mm, 1.3 * mm, fill=1, stroke=0)
        c.setFont(FONT_BOLD, 8.6)
        c.drawString(x + pad + 5 * mm, cur, label)
        c.setFont(FONT, 7.4)
        c.setFillColor(MUTED)
        c.drawString(x + pad + 35 * mm, cur, detail)
        cur -= 6.2 * mm
    cur -= 1 * mm
    c.setFillColor(MAGENTA_TINT)
    c.roundRect(x + pad, cur - 12 * mm, w - 2 * pad, 14 * mm, 3 * mm, fill=1, stroke=0)
    paragraph(c, "NEXT: collect wrist-motion datasets, measure false alarms/hour, and complete supervised field trials.",
              x + pad + 3 * mm, cur - 3 * mm, w - 2 * pad - 6 * mm,
              size=9.2, leading=11.5, color=MAGENTA, font=FONT_BOLD)


def draw_team(c: canvas.Canvas, x: float, y: float, w: float, h: float) -> None:
    rounded_card(c, x, y, w, h)
    pad = 6 * mm
    section_heading(c, "7", "Team", x + pad, y + h - pad, TEAL)
    qr_size = 31 * mm
    qr_x = x + w - pad - qr_size
    qr_y = y + 15 * mm
    draw_qr(c, "https://narios.vercel.app", qr_x, qr_y, qr_size)
    c.setFillColor(INK)
    c.setFont(FONT_BOLD, 12.2)
    c.drawString(x + pad, y + h - 27 * mm, "Team Puraanmaya")
    c.setFont(FONT, 9.8)
    c.setFillColor(MUTED)
    names_x = x + pad
    names_w = w - 2 * pad - qr_size - 5 * mm
    line_y = y + h - 38 * mm
    for index in range(1, 5):
        c.drawString(names_x, line_y + 1.5 * mm, f"{index}.")
        c.setStrokeColor(LINE)
        c.setLineWidth(0.6)
        c.line(names_x + 5 * mm, line_y, names_x + names_w, line_y)
        line_y -= 10 * mm
    c.setFont(FONT, 8.4)
    c.setFillColor(MUTED)
    c.drawCentredString(qr_x + qr_size / 2, qr_y - 4 * mm, "SCAN THE LIVE DEMO")


def draw_footer(c: canvas.Canvas) -> None:
    y = 5.5 * mm
    c.setStrokeColor(LINE)
    c.setLineWidth(0.6)
    c.line(MARGIN, y + 6 * mm, PAGE_W - MARGIN, y + 6 * mm)
    c.setFillColor(MUTED)
    c.setFont(FONT, 8.5)
    c.drawString(MARGIN, y, "Naari OS · Research prototype · Fall detection is under validation; not a medical device")
    c.drawRightString(PAGE_W - MARGIN, y, "narios.vercel.app")


def build() -> None:
    missing = [name for name in ["home-bluetooth-paired.png", "fall-detection.png", "memo-library.png"]
               if not (PREVIEW_DIR / name).exists()]
    if missing:
        raise SystemExit(f"Missing UI preview assets: {', '.join(missing)}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(OUT_PDF), pagesize=A3, pageCompression=1)
    c.setTitle("Naari OS — A3 Research Poster")
    c.setAuthor("Team Puraanmaya")
    c.setSubject("IEEE WIE ILS 2026 HackX Grand Finale research prototype poster")
    c.setCreator("Naari OS poster generator")
    c.setFillColor(PAPER)
    c.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)
    draw_top_band(c)
    draw_title(c)

    row1_y = 111 * mm
    row1_h = 199 * mm
    row2_y = 20 * mm
    row2_h = 86 * mm
    x1 = MARGIN
    x2 = MARGIN + COL_W + GAP
    x3 = MARGIN + 2 * (COL_W + GAP)
    draw_problem_solution(c, x1, row1_y, COL_W, row1_h)
    draw_architecture(c, x2, row1_y, COL_W, row1_h)
    draw_prototype(c, x3, row1_y, COL_W, row1_h)
    draw_tech(c, x1, row2_y, COL_W, row2_h)
    draw_impact(c, x2, row2_y, COL_W, row2_h)
    draw_team(c, x3, row2_y, COL_W, row2_h)
    draw_footer(c)
    c.showPage()
    c.save()
    print(f"Wrote {OUT_PDF}")


if __name__ == "__main__":
    build()
