#pragma once

#include <cstdint>
#include <cstddef>
#include <esp_err.h>
#include <driver/spi_master.h>
#include "../../board_pins.h"
#include "../../ui/ui_assets.h"

namespace SheShield {

// Color Palette Constants (RGB565) matching SheShield Dashboard
namespace Colors {
constexpr uint16_t Black = 0x0000;       // Pure OLED Obsidian Black (#000000)
constexpr uint16_t DeepBg = 0x0843;      // Deep Night Surface (#070A11)
constexpr uint16_t Surface = 0x10C4;     // Dark Glass Surface (#121826)
constexpr uint16_t SurfaceCard = 0x1948; // Elevated Card (#1A2338)
constexpr uint16_t CardBorder = 0x21E9;  // Glass Border (#222D42)
constexpr uint16_t TextMain = 0xFFFF;    // Pure Crisp White (#FFFFFF)
constexpr uint16_t TextSoft = 0xCE79;    // Soft Slate (#CBD5E1)
constexpr uint16_t TextMuted = 0x7C53;   // Muted Grey (#7B8B9F)
constexpr uint16_t TextDim = 0x4A69;     // Dim (#475569)
constexpr uint16_t Mint = 0x2EB7;        // SheShield Mint Glow (#2DD4BF)
constexpr uint16_t MintDark = 0x0B8E;    // Dark Teal Mint (#0F766E)
constexpr uint16_t Coral = 0xF9C7;       // Vivid Emergency Coral/Red (#EF4444)
constexpr uint16_t CoralDark = 0x7800;   // Deep Emergency Crimson (#7F1D1D)
constexpr uint16_t Amber = 0xFCE0;       // Warning Amber (#F59E0B)
constexpr uint16_t BlueGlow = 0x1B3F;    // Blue Accent (#3B82F6)

// Authentic Apple Watch watchOS Palette
constexpr uint16_t AppleRed = 0xF9E6;        // Activity Move ring vibrant coral red
constexpr uint16_t AppleRedTrack = 0x3800;   // Deep dark red track
constexpr uint16_t AppleGreen = 0x3FE0;      // Activity Exercise ring vibrant green
constexpr uint16_t AppleGreenTrack = 0x0280; // Deep dark green track
constexpr uint16_t AppleCyan = 0x067F;       // Activity Stand ring electric cyan
constexpr uint16_t AppleCyanTrack = 0x0146;  // Deep dark cyan track
constexpr uint16_t AppleYellow = 0xFFE0;     // Warning / Flashlight yellow
constexpr uint16_t ControlCard = 0x18E3;     // Apple Watch Control Center dark glass card (#1C1C1E)
constexpr uint16_t ControlActive = 0x02DF;   // Apple Watch active blue tile
} // namespace Colors

class DisplayCO5300 {
  public:
    static DisplayCO5300 &instance();

    esp_err_t init();
    void setBrightness(uint8_t percentage);
    uint8_t getBrightness() const { return m_brightness; }
    void sleep();
    void wakeup();

    uint16_t getWidth() const { return LCD_H_RES; }
    uint16_t getHeight() const { return LCD_V_RES; }

    // 2D Vector & Raster Drawing API (Full PSRAM Framebuffer)
    void clear(uint16_t color = Colors::Black);
    void drawPixel(int x, int y, uint16_t color);
    void drawHLine(int x, int y, int w, uint16_t color);
    void drawVLine(int x, int y, int h, uint16_t color);
    void drawLine(int x0, int y0, int x1, int y1, uint16_t color);
    void fillRect(int x, int y, int w, int h, uint16_t color);
    void drawRect(int x, int y, int w, int h, uint16_t color);
    void fillRoundedRect(int x, int y, int w, int h, int r, uint16_t color);
    void drawRoundedRect(int x, int y, int w, int h, int r, uint16_t color);
    void drawCircle(int cx, int cy, int r, uint16_t color);
    void fillCircle(int cx, int cy, int r, uint16_t color);
    void drawRing(int cx, int cy, int rInner, int rOuter, uint16_t color);
    void drawRingArc(int cx, int cy, int rInner, int rOuter, float startAngleRad, float endAngleRad,
                     uint16_t color);

    // Modern Typography & Icons
    void drawChar(int x, int y, char c, uint16_t color, int scale = 1);
    void drawString(int x, int y, const char *str, uint16_t color, int scale = 1,
                    bool center = false);
    void drawLargeClockDigits(int x, int y, const char *timeStr, uint16_t color);
    // Flash-resident 4-bit coverage assets; no runtime font/image decoder.
    void blendPixel(int x, int y, uint16_t color, uint8_t coverage);
    void drawText(int x, int y, const char *text, uint16_t color,
                  UiAssets::Font font = UiAssets::Font::Body, bool center = false,
                  int maxWidth = 0);
    int measureText(const char *text, UiAssets::Font font = UiAssets::Font::Body) const;
    void drawSymbol(int cx, int cy, int size, UiAssets::Symbol symbol, uint16_t color);
    void fillSmoothRoundRect(int x, int y, int w, int h, int radius, uint16_t color);
    void fillSmoothCircle(int cx, int cy, int radius, uint16_t color);
    void drawSmoothArc(int cx, int cy, int radius, int thickness, float start, float end,
                       uint16_t color);

#ifdef NAARI_UI_PREVIEW
    const uint16_t *previewFramebuffer() const { return m_framebuffer; }
#endif

    // Vector System Icons
    void drawIconShield(int cx, int cy, int size, uint16_t color, bool filled = false);
    void drawIconBattery(int x, int y, int pct, uint16_t color);
    void drawIconSignal(int x, int y, int csq, uint16_t color);
    void drawIconCheck(int cx, int cy, int size, uint16_t color);
    void drawIconCross(int cx, int cy, int size, uint16_t color);
    void drawIconAlert(int cx, int cy, int size, uint16_t color);
    void drawIconHeart(int cx, int cy, int size, uint16_t color);
    void drawIconMicrophone(int cx, int cy, int size, uint16_t color);
    void drawIconPhone(int cx, int cy, int size, uint16_t color);
    void drawIconFlashlight(int cx, int cy, int size, uint16_t color);
    void drawIconSpeaker(int cx, int cy, int size, uint16_t color);
    void drawIconGrid(int cx, int cy, int size, uint16_t color);
    void drawIconMusic(int cx, int cy, int size, uint16_t color);
    void drawIconCompass(int cx, int cy, int size, uint16_t color);
    void drawIconGear(int cx, int cy, int size, uint16_t color);

    // Push complete PSRAM Framebuffer to AMOLED Panel via DMA
    void present();

  private:
    DisplayCO5300();
    ~DisplayCO5300() = default;
    DisplayCO5300(const DisplayCO5300 &) = delete;
    DisplayCO5300 &operator=(const DisplayCO5300 &) = delete;

    void writeCmd(uint8_t cmd, const uint8_t *params = nullptr, size_t len = 0);
    void writeC8D8(uint8_t cmd, uint8_t data);
    void writeC8D16D16(uint8_t cmd, uint16_t d1, uint16_t d2);

    bool m_initialized;
    uint8_t m_brightness;
    spi_device_handle_t m_spi;

    uint16_t *m_framebuffer; // 410x502 in PSRAM (411,640 bytes)
    uint8_t *m_dmaChunkBuf;  // 16KB in Internal SRAM for DMA QSPI streaming
};

} // namespace SheShield
