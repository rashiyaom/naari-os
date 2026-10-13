#pragma once

#include <cstdint>
#include <esp_err.h>
#include "../../board_pins.h"

namespace SheShield {

struct TouchPoint {
    uint16_t x;
    uint16_t y;
    bool pressed;
    uint8_t pointCount;
};

class TouchFT3168 {
public:
    static TouchFT3168& instance();

    esp_err_t init();
    bool read(TouchPoint& point);

private:
    TouchFT3168();
    ~TouchFT3168() = default;
    TouchFT3168(const TouchFT3168&) = delete;
    TouchFT3168& operator=(const TouchFT3168&) = delete;

    bool m_initialized;
};

} // namespace SheShield
