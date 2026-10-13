#include "display_co5300.h"
#include <esp_log.h>
#include <driver/spi_master.h>
#include <esp_heap_caps.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <cstring>
#include <cmath>
#include <cstdlib>

static const char *TAG = "Display_CO5300";

namespace SheShield {

DisplayCO5300 &DisplayCO5300::instance() {
    static DisplayCO5300 s_instance;
    return s_instance;
}

DisplayCO5300::DisplayCO5300()
    : m_initialized(false), m_brightness(100), m_spi(nullptr), m_framebuffer(nullptr),
      m_dmaChunkBuf(nullptr) {}

esp_err_t DisplayCO5300::init() {
    ESP_LOGI(TAG, "Initializing CO5300 2.06\" AMOLED display (410x502 QSPI)...");

    // Allocate 410x502 Full Framebuffer in PSRAM (411,640 bytes)
    size_t fbSize = LCD_H_RES * LCD_V_RES * sizeof(uint16_t);
    m_framebuffer = (uint16_t *)heap_caps_malloc(fbSize, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!m_framebuffer) {
        ESP_LOGE(TAG, "Failed to allocate 411KB framebuffer in PSRAM!");
        return ESP_ERR_NO_MEM;
    }
    std::memset(m_framebuffer, 0, fbSize);
    ESP_LOGI(TAG, "Allocated 411KB framebuffer in PSRAM at %p", m_framebuffer);

    // Allocate 16KB DMA Buffer in Internal SRAM for chunk streaming
    m_dmaChunkBuf = (uint8_t *)heap_caps_malloc(16384, MALLOC_CAP_DMA | MALLOC_CAP_INTERNAL);
    if (!m_dmaChunkBuf) {
        ESP_LOGE(TAG, "Failed to allocate 16KB DMA transfer buffer in SRAM!");
        return ESP_ERR_NO_MEM;
    }

    // Configure Reset Pin & CS Pin
    gpio_config_t io_conf = {.pin_bit_mask = (1ULL << LCD_PIN_RST) | (1ULL << LCD_PIN_CS),
                             .mode = GPIO_MODE_OUTPUT,
                             .pull_up_en = GPIO_PULLUP_ENABLE,
                             .pull_down_en = GPIO_PULLDOWN_DISABLE,
                             .intr_type = GPIO_INTR_DISABLE};
    gpio_config(&io_conf);
    gpio_set_level(LCD_PIN_CS, 1);

    // Hardware Reset Pulse
    gpio_set_level(LCD_PIN_RST, 1);
    vTaskDelay(pdMS_TO_TICKS(10));
    gpio_set_level(LCD_PIN_RST, 0);
    vTaskDelay(pdMS_TO_TICKS(50));
    gpio_set_level(LCD_PIN_RST, 1);
    vTaskDelay(pdMS_TO_TICKS(150));

    // Initialize SPI Bus for QSPI (D0:4, D1:5, D2:6, D3:7, SCLK:11)
    spi_bus_config_t buscfg = {};
    buscfg.data0_io_num = LCD_PIN_D0;
    buscfg.data1_io_num = LCD_PIN_D1;
    buscfg.sclk_io_num = LCD_PIN_SCK;
    buscfg.data2_io_num = LCD_PIN_D2;
    buscfg.data3_io_num = LCD_PIN_D3;
    buscfg.max_transfer_sz = 16384 + 64;
    buscfg.flags = SPICOMMON_BUSFLAG_MASTER | SPICOMMON_BUSFLAG_GPIO_PINS;

    esp_err_t ret = spi_bus_initialize(LCD_QSPI_HOST, &buscfg, SPI_DMA_CH_AUTO);
    if (ret != ESP_OK && ret != ESP_ERR_INVALID_STATE) {
        ESP_LOGE(TAG, "Failed to initialize SPI bus for QSPI display: %s", esp_err_to_name(ret));
        return ret;
    }

    spi_device_interface_config_t devcfg = {};
    devcfg.command_bits = 8;
    devcfg.address_bits = 24;
    devcfg.dummy_bits = 0;
    devcfg.mode = 0;                          // Mode 0
    devcfg.clock_speed_hz = 20 * 1000 * 1000; // 20 MHz rock solid QSPI clock
    devcfg.spics_io_num = -1;                 // Manual CS control
    devcfg.flags = SPI_DEVICE_HALFDUPLEX;
    devcfg.queue_size = 1;

    ret = spi_bus_add_device(LCD_QSPI_HOST, &devcfg, &m_spi);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to add display device to SPI bus: %s", esp_err_to_name(ret));
        return ret;
    }

    ESP_LOGI(TAG, "Sending CO5300 AMOLED initialization commands...");

    // 1. Sleep Out (0x11)
    writeCmd(0x11);
    vTaskDelay(pdMS_TO_TICKS(120));

    // 2. Unlock vendor registers & QSPI Mode Control
    writeC8D8(0xFE, 0x00);
    writeC8D8(0xC4, 0x80); // Enable Quad SPI mode

    // 3. Pixel Format (0x3A -> 0x55 = 16-bit RGB565)
    writeC8D8(0x3A, 0x55);

    // 4. Memory Access Control (MADCTL 0x36 -> 0x00 RGB)
    writeC8D8(0x36, 0x00);

    // 5. Display Control 1 (0x53 -> 0x20: Dimming / Backlight Enable)
    writeC8D8(0x53, 0x20);

    // 6. Max HBM Brightness (0x63 -> 0xFF)
    writeC8D8(0x63, 0xFF);

    // 7. Normal Brightness (0x51 -> 0xFF = 100% Brightness)
    writeC8D8(0x51, 0xFF);

    // 8. Color Enhancement Off (0x58 -> 0x00)
    writeC8D8(0x58, 0x00);

    // 9. Display ON (0x29)
    writeCmd(0x29);
    vTaskDelay(pdMS_TO_TICKS(50));

    m_initialized = true;

    // Clear and present blank frame
    clear(Colors::Black);
    present();

    ESP_LOGI(TAG, "CO5300 AMOLED PSRAM Framebuffer Graphics Engine Ready.");
    return ESP_OK;
}

void DisplayCO5300::writeCmd(uint8_t cmd, const uint8_t *params, size_t len) {
    if (!m_spi)
        return;
    gpio_set_level(LCD_PIN_CS, 0);

    spi_transaction_ext_t t = {};
    t.base.flags = SPI_TRANS_MULTILINE_CMD | SPI_TRANS_MULTILINE_ADDR;
    t.base.cmd = 0x02;
    t.base.addr = ((uint32_t)cmd) << 8;
    if (params && len > 0) {
        t.base.tx_buffer = params;
        t.base.length = len * 8;
    } else {
        t.base.tx_buffer = nullptr;
        t.base.length = 0;
    }

    spi_device_polling_transmit(m_spi, (spi_transaction_t *)&t);
    gpio_set_level(LCD_PIN_CS, 1);
}

void DisplayCO5300::writeC8D8(uint8_t cmd, uint8_t data) {
    if (!m_spi)
        return;
    gpio_set_level(LCD_PIN_CS, 0);

    spi_transaction_ext_t t = {};
    t.base.flags = SPI_TRANS_USE_TXDATA | SPI_TRANS_MULTILINE_CMD | SPI_TRANS_MULTILINE_ADDR;
    t.base.cmd = 0x02;
    t.base.addr = ((uint32_t)cmd) << 8;
    t.base.tx_data[0] = data;
    t.base.length = 8;

    spi_device_polling_transmit(m_spi, (spi_transaction_t *)&t);
    gpio_set_level(LCD_PIN_CS, 1);
}

void DisplayCO5300::writeC8D16D16(uint8_t cmd, uint16_t d1, uint16_t d2) {
    if (!m_spi)
        return;
    gpio_set_level(LCD_PIN_CS, 0);

    spi_transaction_ext_t t = {};
    t.base.flags = SPI_TRANS_USE_TXDATA | SPI_TRANS_MULTILINE_CMD | SPI_TRANS_MULTILINE_ADDR;
    t.base.cmd = 0x02;
    t.base.addr = ((uint32_t)cmd) << 8;
    t.base.tx_data[0] = (d1 >> 8) & 0xFF;
    t.base.tx_data[1] = d1 & 0xFF;
    t.base.tx_data[2] = (d2 >> 8) & 0xFF;
    t.base.tx_data[3] = d2 & 0xFF;
    t.base.length = 32;

    spi_device_polling_transmit(m_spi, (spi_transaction_t *)&t);
    gpio_set_level(LCD_PIN_CS, 1);
}

void DisplayCO5300::setBrightness(uint8_t percentage) {
    if (percentage > 100)
        percentage = 100;
    m_brightness = percentage;
    uint8_t val = (255 * percentage) / 100;
    writeC8D8(0x51, val);
}

// -------------------------------------------------------------
// 2D Vector & Raster Drawing Implementations (Direct to PSRAM)
// -------------------------------------------------------------
void DisplayCO5300::clear(uint16_t color) {
    if (!m_framebuffer)
        return;
    uint16_t swapped = (color >> 8) | ((color & 0xFF) << 8);
    for (int i = 0; i < LCD_H_RES * LCD_V_RES; i++) {
        m_framebuffer[i] = swapped;
    }
}

void DisplayCO5300::drawPixel(int x, int y, uint16_t color) {
    if (!m_framebuffer || x < 0 || x >= LCD_H_RES || y < 0 || y >= LCD_V_RES)
        return;
    m_framebuffer[y * LCD_H_RES + x] = (color >> 8) | ((color & 0xFF) << 8);
}

void DisplayCO5300::drawHLine(int x, int y, int w, uint16_t color) {
    if (!m_framebuffer || y < 0 || y >= LCD_V_RES || w <= 0)
        return;
    if (x < 0) {
        w += x;
        x = 0;
    }
    if (x + w > LCD_H_RES)
        w = LCD_H_RES - x;
    if (w <= 0)
        return;

    uint16_t swapped = (color >> 8) | ((color & 0xFF) << 8);
    uint16_t *ptr = &m_framebuffer[y * LCD_H_RES + x];
    for (int i = 0; i < w; i++) {
        ptr[i] = swapped;
    }
}

void DisplayCO5300::drawVLine(int x, int y, int h, uint16_t color) {
    if (!m_framebuffer || x < 0 || x >= LCD_H_RES || h <= 0)
        return;
    if (y < 0) {
        h += y;
        y = 0;
    }
    if (y + h > LCD_V_RES)
        h = LCD_V_RES - y;
    if (h <= 0)
        return;

    uint16_t swapped = (color >> 8) | ((color & 0xFF) << 8);
    for (int i = 0; i < h; i++) {
        m_framebuffer[(y + i) * LCD_H_RES + x] = swapped;
    }
}

void DisplayCO5300::drawLine(int x0, int y0, int x1, int y1, uint16_t color) {
    int dx = std::abs(x1 - x0);
    int dy = std::abs(y1 - y0);
    int sx = (x0 < x1) ? 1 : -1;
    int sy = (y0 < y1) ? 1 : -1;
    int err = dx - dy;

    while (true) {
        drawPixel(x0, y0, color);
        if (x0 == x1 && y0 == y1)
            break;
        int e2 = 2 * err;
        if (e2 > -dy) {
            err -= dy;
            x0 += sx;
        }
        if (e2 < dx) {
            err += dx;
            y0 += sy;
        }
    }
}

void DisplayCO5300::fillRect(int x, int y, int w, int h, uint16_t color) {
    if (!m_framebuffer || w <= 0 || h <= 0)
        return;
    if (x < 0) {
        w += x;
        x = 0;
    }
    if (y < 0) {
        h += y;
        y = 0;
    }
    if (x + w > LCD_H_RES)
        w = LCD_H_RES - x;
    if (y + h > LCD_V_RES)
        h = LCD_V_RES - y;
    if (w <= 0 || h <= 0)
        return;

    uint16_t swapped = (color >> 8) | ((color & 0xFF) << 8);
    for (int row = 0; row < h; row++) {
        uint16_t *ptr = &m_framebuffer[(y + row) * LCD_H_RES + x];
        for (int col = 0; col < w; col++) {
            ptr[col] = swapped;
        }
    }
}

void DisplayCO5300::drawRect(int x, int y, int w, int h, uint16_t color) {
    drawHLine(x, y, w, color);
    drawHLine(x, y + h - 1, w, color);
    drawVLine(x, y, h, color);
    drawVLine(x + w - 1, y, h, color);
}

void DisplayCO5300::fillRoundedRect(int x, int y, int w, int h, int r, uint16_t color) {
    fillSmoothRoundRect(x, y, w, h, r, color);
}

void DisplayCO5300::drawRoundedRect(int x, int y, int w, int h, int r, uint16_t color) {
    if (r <= 0) {
        drawRect(x, y, w, h, color);
        return;
    }
    if (r > w / 2)
        r = w / 2;
    if (r > h / 2)
        r = h / 2;

    drawHLine(x + r, y, w - 2 * r, color);
    drawHLine(x + r, y + h - 1, w - 2 * r, color);
    drawVLine(x, y + r, h - 2 * r, color);
    drawVLine(x + w - 1, y + r, h - 2 * r, color);

    int f = 1 - r;
    int ddF_x = 1;
    int ddF_y = -2 * r;
    int cx = 0;
    int cy = r;

    while (cx < cy) {
        if (f >= 0) {
            cy--;
            ddF_y += 2;
            f += ddF_y;
        }
        cx++;
        ddF_x += 2;
        f += ddF_x;

        drawPixel(x + r - cx, y + r - cy, color);
        drawPixel(x + w - r + cx - 1, y + r - cy, color);
        drawPixel(x + r - cx, y + h - r + cy - 1, color);
        drawPixel(x + w - r + cx - 1, y + h - r + cy - 1, color);

        drawPixel(x + r - cy, y + r - cx, color);
        drawPixel(x + w - r + cy - 1, y + r - cx, color);
        drawPixel(x + r - cy, y + h - r + cx - 1, color);
        drawPixel(x + w - r + cy - 1, y + h - r + cx - 1, color);
    }
}

void DisplayCO5300::drawCircle(int cx, int cy, int r, uint16_t color) {
    drawSmoothArc(cx, cy, r, 1, 0, 6.2831853f, color);
}

void DisplayCO5300::fillCircle(int cx, int cy, int r, uint16_t color) {
    fillSmoothCircle(cx, cy, r, color);
}

void DisplayCO5300::drawRing(int cx, int cy, int rInner, int rOuter, uint16_t color) {
    drawSmoothArc(cx, cy, (rInner + rOuter) / 2, rOuter - rInner, 0, 6.2831853f, color);
}

void DisplayCO5300::drawRingArc(int cx, int cy, int rInner, int rOuter, float start, float end,
                                uint16_t color) {
    drawSmoothArc(cx, cy, (rInner + rOuter) / 2, rOuter - rInner, start, end, color);
}

// -------------------------------------------------------------
// Modern Typography Engine
// -------------------------------------------------------------
void DisplayCO5300::drawChar(int x, int y, char c, uint16_t color, int scale) {
    char text[2] = {c, 0};
    drawString(x, y, text, color, scale, false);
}

void DisplayCO5300::drawString(int x, int y, const char *str, uint16_t color, int scale,
                               bool center) {
    UiAssets::Font font = scale >= 4   ? UiAssets::Font::Timer
                          : scale == 3 ? UiAssets::Font::Display
                          : scale == 2 ? UiAssets::Font::Title
                                       : UiAssets::Font::Body;
    drawText(x, y, str, color, font, center);
}

void DisplayCO5300::drawLargeClockDigits(int x, int y, const char *timeStr, uint16_t color) {
    drawText(x, y, timeStr, color, UiAssets::Font::Clock, false);
}

// -------------------------------------------------------------
// Vector System Icons
// -------------------------------------------------------------
void DisplayCO5300::drawIconShield(int cx, int cy, int size, uint16_t color, bool filled) {
    int w = size;
    int h = (size * 5) / 4;
    int top = cy - h / 2;

    // Outer shield polygon
    for (int dy = 0; dy < h; dy++) {
        int py = top + dy;
        int halfW;
        if (dy < h / 3) {
            halfW = w / 2;
        } else {
            int progress = dy - h / 3;
            int rem = h - h / 3;
            halfW = (w / 2) * (rem - progress) / rem;
        }
        if (filled) {
            drawHLine(cx - halfW, py, 2 * halfW + 1, color);
        } else {
            drawPixel(cx - halfW, py, color);
            drawPixel(cx + halfW, py, color);
        }
    }
    if (!filled) {
        drawHLine(cx - w / 2, top, w + 1, color);
    }
}

void DisplayCO5300::drawIconBattery(int x, int y, int pct, uint16_t color) {
    if (pct < 0)
        pct = 0;
    if (pct > 100)
        pct = 100;

    int bw = 32;
    int bh = 16;
    drawRoundedRect(x, y, bw, bh, 3, Colors::CardBorder);
    drawRect(x + bw, y + 4, 3, 8, Colors::CardBorder);

    int fillW = ((bw - 6) * pct) / 100;
    uint16_t fillColor = (pct > 20) ? Colors::Mint : Colors::Coral;
    if (fillW > 0) {
        fillRoundedRect(x + 3, y + 3, fillW, bh - 6, 2, fillColor);
    }
}

void DisplayCO5300::drawIconSignal(int x, int y, int csq, uint16_t color) {
    // 4 Cellular Bars
    int bars = (csq >= 20) ? 4 : (csq >= 14) ? 3 : (csq >= 7) ? 2 : (csq > 0) ? 1 : 0;
    for (int i = 0; i < 4; i++) {
        int bh = 4 + i * 4;
        int by = y + 16 - bh;
        int bx = x + i * 6;
        uint16_t barColor = (i < bars) ? color : Colors::TextDim;
        fillRect(bx, by, 4, bh, barColor);
    }
}

void DisplayCO5300::drawIconCheck(int cx, int cy, int size, uint16_t color) {
    int r = size / 2;
    // Draw 3-pixel thick checkmark
    for (int thick = -1; thick <= 1; thick++) {
        drawLine(cx - r / 2, cy + thick, cx - r / 6, cy + r / 2 + thick, color);
        drawLine(cx - r / 6, cy + r / 2 + thick, cx + r / 2, cy - r / 2 + thick, color);
    }
}

void DisplayCO5300::drawIconCross(int cx, int cy, int size, uint16_t color) {
    int r = size / 2;
    for (int thick = -1; thick <= 1; thick++) {
        drawLine(cx - r + thick, cy - r, cx + r + thick, cy + r, color);
        drawLine(cx - r + thick, cy + r, cx + r + thick, cy - r, color);
    }
}

void DisplayCO5300::drawIconAlert(int cx, int cy, int size, uint16_t color) {
    int h = size;
    int top = cy - h / 2;
    int bot = cy + h / 2;
    int w = size;

    // Draw triangle
    drawLine(cx, top, cx - w / 2, bot, color);
    drawLine(cx, top, cx + w / 2, bot, color);
    drawHLine(cx - w / 2, bot, w, color);

    // Exclamation mark
    drawVLine(cx, top + 6, h / 2 - 4, color);
    drawPixel(cx, bot - 4, color);
}

void DisplayCO5300::drawIconHeart(int cx, int cy, int size, uint16_t color) {
    int r = size / 4;
    fillCircle(cx - r, cy - r / 2, r, color);
    fillCircle(cx + r, cy - r / 2, r, color);
    for (int y = 0; y < size / 2; y++) {
        int halfW = (size / 2) - y;
        drawHLine(cx - halfW, cy + y, 2 * halfW + 1, color);
    }
}

void DisplayCO5300::drawIconMicrophone(int cx, int cy, int size, uint16_t color) {
    int w = size / 2;
    int h = size;
    fillRoundedRect(cx - w / 2, cy - h / 2, w, (h * 2) / 3, w / 2, color);
    drawCircle(cx, cy + h / 6, w / 2 + 2, color);
    drawVLine(cx, cy + h / 6 + w / 2 + 2, 4, color);
    drawHLine(cx - 4, cy + h / 6 + w / 2 + 6, 9, color);
}

void DisplayCO5300::drawIconPhone(int cx, int cy, int size, uint16_t color) {
    drawRoundedRect(cx - size / 4, cy - size / 2, size / 2, size, 3, color);
    drawHLine(cx - 2, cy + size / 2 - 4, 5, color);
}

void DisplayCO5300::drawIconFlashlight(int cx, int cy, int size, uint16_t color) {
    int w = size / 2;
    int h = size;
    // Handle
    fillRoundedRect(cx - w / 4, cy - h / 6, w / 2, (h * 2) / 3, 2, color);
    // Head
    fillRoundedRect(cx - w / 2, cy - h / 2, w, h / 3 + 2, 3, color);
    // Light rays radiating from top
    drawVLine(cx, cy - h / 2 - 5, 3, color);
    drawLine(cx - 6, cy - h / 2 - 4, cx - 10, cy - h / 2 - 7, color);
    drawLine(cx + 6, cy - h / 2 - 4, cx + 10, cy - h / 2 - 7, color);
}

void DisplayCO5300::drawIconSpeaker(int cx, int cy, int size, uint16_t color) {
    int w = size / 2;
    int h = size;
    // Speaker box
    fillRect(cx - w / 2, cy - h / 4, w / 3, h / 2, color);
    // Speaker Cone
    for (int y = -h / 2; y <= h / 2; y++) {
        int xProgress = (std::abs(y) <= h / 4) ? 0 : (std::abs(y) - h / 4);
        drawHLine(cx - w / 6, cy + y, (w / 3) + xProgress, color);
    }
    // Sound wave arcs
    drawCircle(cx + 2, cy, (h * 2) / 3, color);
    drawCircle(cx + 4, cy, h, color);
}

void DisplayCO5300::drawIconGrid(int cx, int cy, int size, uint16_t color) {
    int b = (size - 6) / 3;
    if (b < 3)
        b = 3;
    for (int r = -1; r <= 1; r++) {
        for (int c = -1; c <= 1; c++) {
            int px = cx + c * (b + 3) - b / 2;
            int py = cy + r * (b + 3) - b / 2;
            fillRoundedRect(px, py, b, b, 2, color);
        }
    }
}

void DisplayCO5300::drawIconMusic(int cx, int cy, int size, uint16_t color) {
    int r = size / 5;
    // Two note heads
    fillCircle(cx - size / 4, cy + size / 4, r, color);
    fillCircle(cx + size / 4, cy + size / 8, r, color);
    // Vertical stems
    fillRect(cx - size / 4 + r - 2, cy - size / 3, 2, (size * 7) / 12, color);
    fillRect(cx + size / 4 + r - 2, cy - size / 2, 2, (size * 5) / 8, color);
    // Connecting top beam
    for (int i = 0; i < 3; i++) {
        drawLine(cx - size / 4 + r - 2, cy - size / 3 + i, cx + size / 4 + r - 2, cy - size / 2 + i,
                 color);
    }
}

void DisplayCO5300::drawIconCompass(int cx, int cy, int size, uint16_t color) {
    int r = size / 2;
    drawCircle(cx, cy, r, color);
    // Center needle (diamond)
    drawLine(cx, cy - r + 3, cx - 3, cy, color);
    drawLine(cx, cy - r + 3, cx + 3, cy, color);
    drawLine(cx, cy + r - 3, cx - 3, cy, color);
    drawLine(cx, cy + r - 3, cx + 3, cy, color);
    // Top North pointer filled
    for (int y = cy - r + 4; y <= cy; y++) {
        int halfW = (y - (cy - r + 4)) * 3 / (r - 4);
        drawHLine(cx - halfW, y, 2 * halfW + 1, color);
    }
    fillCircle(cx, cy, 2, Colors::Black);
}

void DisplayCO5300::drawIconGear(int cx, int cy, int size, uint16_t color) {
    int r = size / 2;
    drawCircle(cx, cy, r - 3, color);
    drawCircle(cx, cy, r - 4, color);
    fillCircle(cx, cy, 3, color);
    // Teeth
    for (int a = 0; a < 8; a++) {
        float rad = a * (M_PI / 4.0f);
        int tx = cx + static_cast<int>(r * std::cos(rad));
        int ty = cy + static_cast<int>(r * std::sin(rad));
        fillCircle(tx, ty, 2, color);
    }
}

// -------------------------------------------------------------
// Present Full Framebuffer to AMOLED Panel (Continuous DMA QSPI)
// -------------------------------------------------------------
void DisplayCO5300::present() {
    if (!m_initialized || !m_spi || !m_framebuffer || !m_dmaChunkBuf)
        return;

    // Full 410x502 screen address window (Col: 22 to 431, Row: 0 to 501)
    writeC8D16D16(0x2A, LCD_COL_OFFSET, LCD_COL_OFFSET + LCD_H_RES - 1);
    writeC8D16D16(0x2B, LCD_ROW_OFFSET, LCD_ROW_OFFSET + LCD_V_RES - 1);

    // Keep CS LOW for the continuous GRAM auto-increment streaming
    gpio_set_level(LCD_PIN_CS, 0);

    // Stream the 411,640 bytes in 10-line DMA chunks (8,200 bytes per chunk)
    const int linesPerChunk = 10;

    bool firstChunk = true;
    for (int y = 0; y < LCD_V_RES; y += linesPerChunk) {
        int lines = (y + linesPerChunk <= LCD_V_RES) ? linesPerChunk : (LCD_V_RES - y);
        size_t bytes = LCD_H_RES * lines * sizeof(uint16_t);

        // Copy from PSRAM framebuffer to Internal SRAM DMA buffer
        std::memcpy(m_dmaChunkBuf, &m_framebuffer[y * LCD_H_RES], bytes);

        spi_transaction_ext_t t = {};
        if (firstChunk) {
            t.base.flags = SPI_TRANS_MODE_QIO;
            t.base.cmd = 0x32;
            t.base.addr = 0x002C00; // Memory Write Start
            firstChunk = false;
        } else {
            t.base.flags = SPI_TRANS_MODE_QIO | SPI_TRANS_VARIABLE_CMD | SPI_TRANS_VARIABLE_ADDR |
                           SPI_TRANS_VARIABLE_DUMMY;
            t.base.cmd = 0;
            t.base.addr = 0;
        }
        t.base.tx_buffer = m_dmaChunkBuf;
        t.base.length = bytes * 8;

        spi_device_polling_transmit(m_spi, (spi_transaction_t *)&t);
    }

    gpio_set_level(LCD_PIN_CS, 1);
}

void DisplayCO5300::sleep() {
    writeCmd(0x28); // Display OFF
    vTaskDelay(pdMS_TO_TICKS(20));
    writeCmd(0x10); // Sleep IN
}

void DisplayCO5300::wakeup() {
    writeCmd(0x11); // Sleep OUT
    vTaskDelay(pdMS_TO_TICKS(120));
    writeCmd(0x29); // Display ON
}

} // namespace SheShield
