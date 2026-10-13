#pragma once

#include <cstdint>
#include <esp_err.h>
#include "../../board_pins.h"

namespace SheShield {

struct BatteryStatus {
    float voltage;       // In Volts (e.g. 4.12V)
    uint8_t percentage;  // 0 - 100%
    bool isCharging;
    bool isBatteryPresent;
};

class AXP2101 {
public:
    static AXP2101& instance();

    esp_err_t init();
    esp_err_t getBatteryStatus(BatteryStatus& status);
    bool pollPowerKey(bool& shortPress, bool& longPress);
    void setAmoledPower(bool enable);
    void powerOff();

private:
    AXP2101();
    ~AXP2101() = default;
    AXP2101(const AXP2101&) = delete;
    AXP2101& operator=(const AXP2101&) = delete;

    bool m_initialized;
};

} // namespace SheShield
