#include "display_co5300.h"
#include <algorithm>
#include <cmath>
#include <cstring>

namespace SheShield {
namespace {
uint32_t nextCodepoint(const char *&p) {
    const auto *b = reinterpret_cast<const uint8_t *>(p);
    uint32_t c = *b++;
    if (c >= 0xc2 && c <= 0xdf && (b[0] & 0xc0) == 0x80) {
        c = ((c & 31) << 6) | (b[0] & 63);
        ++b;
    } else if (c >= 0xe0 && c <= 0xef && b[0] && b[1] && (b[0] & 0xc0) == 0x80 &&
               (b[1] & 0xc0) == 0x80) {
        c = ((c & 15) << 12) | ((b[0] & 63) << 6) | (b[1] & 63);
        b += 2;
    }
    p = reinterpret_cast<const char *>(b);
    return c;
}
unsigned glyphIndex(uint32_t c) {
    if (c >= 32 && c <= 126)
        return c - 32;
    for (unsigned i = 95; i < sizeof(UiAssets::codepoints) / sizeof(uint32_t); ++i)
        if (UiAssets::codepoints[i] == c)
            return i;
    return '?' - 32;
}
} // namespace

void DisplayCO5300::blendPixel(int x, int y, uint16_t color, uint8_t a) {
    if (!a || !m_framebuffer || x < 0 || y < 0 || x >= LCD_H_RES || y >= LCD_V_RES)
        return;
    if (a >= 15) {
        drawPixel(x, y, color);
        return;
    }
    // The PSRAM buffer is stored in wire byte order. Undo exactly once before
    // blending, then use drawPixel to put the result back in that same order.
    uint16_t raw = m_framebuffer[y * LCD_H_RES + x];
    uint16_t bg = (raw >> 8) | (raw << 8);
    unsigned r = (((color >> 11) & 31) * a + ((bg >> 11) & 31) * (15 - a) + 7) / 15;
    unsigned g = (((color >> 5) & 63) * a + ((bg >> 5) & 63) * (15 - a) + 7) / 15;
    unsigned b = ((color & 31) * a + (bg & 31) * (15 - a) + 7) / 15;
    drawPixel(x, y, (r << 11) | (g << 5) | b);
}

int DisplayCO5300::measureText(const char *text, UiAssets::Font font) const {
    if (!text)
        return 0;
    const auto &f = UiAssets::fonts[static_cast<unsigned>(font)];
    int w = 0;
    while (*text && *text != '\n')
        w += f.glyphs[glyphIndex(nextCodepoint(text))].advance;
    return w;
}

void DisplayCO5300::drawText(int x, int y, const char *text, uint16_t color, UiAssets::Font font,
                             bool center, int maxWidth) {
    if (!text || !m_framebuffer)
        return;
    const auto &f = UiAssets::fonts[static_cast<unsigned>(font)];
    int width = measureText(text, font);
    if (!maxWidth)
        maxWidth = center ? std::min(x, LCD_H_RES - x) * 2 : LCD_H_RES - x;
    bool truncated = width > maxWidth;
    int dotsWidth = 3 * f.glyphs['.' - 32].advance;
    int drawn = truncated ? maxWidth : width;
    int left = center ? x - drawn / 2 : x;
    int cursor = left;
    const auto paint = [&](const UiAssets::Glyph &g, int gx) {
        for (int row = 0; row < g.height; ++row)
            for (int col = 0; col < g.width; ++col) {
                unsigned px = row * g.width + col;
                uint8_t packed = f.pixels[g.offset + px / 2];
                blendPixel(gx + g.x + col, y + g.y + row, color,
                           px & 1 ? packed & 15 : packed >> 4);
            }
    };
    while (*text && *text != '\n') {
        const auto &g = f.glyphs[glyphIndex(nextCodepoint(text))];
        if (cursor + g.advance > left + maxWidth - (truncated ? dotsWidth : 0))
            break;
        paint(g, cursor);
        cursor += g.advance;
    }
    if (truncated)
        for (int n = 0; n < 3; ++n) {
            const auto &g = f.glyphs['.' - 32];
            paint(g, cursor);
            cursor += g.advance;
        }
}

void DisplayCO5300::drawSymbol(int cx, int cy, int size, UiAssets::Symbol symbol, uint16_t color) {
    unsigned idx = 0;
    int delta = 999;
    for (unsigned i = 0; i < 7; ++i) {
        int d = std::abs(size - UiAssets::symbolSizes[i]);
        if (d < delta) {
            idx = i;
            delta = d;
        }
    }
    int sz = UiAssets::symbolSizes[idx];
    const uint8_t *pixels = UiAssets::symbols[static_cast<unsigned>(symbol)][idx];
    for (int row = 0; row < sz; ++row)
        for (int col = 0; col < sz; ++col) {
            int p = row * sz + col;
            uint8_t v = pixels[p / 2];
            blendPixel(cx - sz / 2 + col, cy - sz / 2 + row, color, p & 1 ? v & 15 : v >> 4);
        }
}

void DisplayCO5300::fillSmoothRoundRect(int x, int y, int w, int h, int r, uint16_t color) {
    if (w <= 0 || h <= 0)
        return;
    r = std::min(r, std::min(w, h) / 2);
    if (r <= 0) {
        fillRect(x, y, w, h, color);
        return;
    }
    for (int row = 0; row < h; ++row) {
        float dy = row < r ? r - row - 0.5f : row >= h - r ? row - (h - r) + 0.5f : 0;
        float inset = dy > 0 ? r - std::sqrt(r * r - dy * dy) : 0;
        int edge = static_cast<int>(std::ceil(inset));
        drawHLine(x + edge, y + row, w - 2 * edge, color);
        if (edge) {
            uint8_t a = static_cast<uint8_t>(std::clamp((edge - inset) * 15.0f, 0.0f, 15.0f));
            blendPixel(x + edge - 1, y + row, color, a);
            blendPixel(x + w - edge, y + row, color, a);
        }
    }
}

void DisplayCO5300::fillSmoothCircle(int cx, int cy, int r, uint16_t color) {
    fillSmoothRoundRect(cx - r, cy - r, 2 * r, 2 * r, r, color);
}

void DisplayCO5300::drawSmoothArc(int cx, int cy, int radius, int thickness, float start, float end,
                                  uint16_t color) {
    if (thickness <= 0 || end <= start)
        return;
    constexpr float tau = 6.28318530718f;
    float half = thickness * 0.5f, outer = radius + half, inner = radius - half;
    const bool full = end - start >= tau - 0.001f;
    int limit = static_cast<int>(std::ceil(outer));
    for (int dy = -limit; dy <= limit; ++dy) {
        if (std::abs(dy) > outer)
            continue;
        int maxX = static_cast<int>(std::ceil(std::sqrt(outer * outer - dy * dy)));
        int minX = std::abs(dy) < inner - 1
                       ? static_cast<int>(std::sqrt((inner - 1) * (inner - 1) - dy * dy))
                       : 0;
        for (int dx = -maxX; dx <= maxX; ++dx) {
            if (dx > -minX && dx < minX)
                continue;
            float dist = std::sqrt(float(dx * dx + dy * dy));
            float coverage = std::min(outer - dist, dist - inner) + 0.5f;
            if (coverage <= 0)
                continue;
            if (!full) {
                float angle = std::atan2(float(dy), float(dx));
                while (angle < start)
                    angle += tau;
                if (angle > end)
                    continue;
            }
            blendPixel(cx + dx, cy + dy, color,
                       static_cast<uint8_t>(std::min(1.0f, coverage) * 15));
        }
    }
    if (!full) {
        fillSmoothCircle(cx + int(radius * std::cos(start)), cy + int(radius * std::sin(start)),
                         thickness / 2, color);
        fillSmoothCircle(cx + int(radius * std::cos(end)), cy + int(radius * std::sin(end)),
                         thickness / 2, color);
    }
}
} // namespace SheShield
