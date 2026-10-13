#pragma once

#include <cstdint>
#include <ctime>
#include <esp_err.h>
#include "../../board_pins.h"

namespace SheShield {

struct RtcDateTime {
    uint16_t year;   // e.g. 2026
    uint8_t  month;  // 1-12
    uint8_t  day;    // 1-31
    uint8_t  hour;   // 0-23
    uint8_t  minute; // 0-59
    uint8_t  second; // 0-59
    uint8_t  weekday;// 0=Sunday, 1=Monday, ..., 6=Saturday
};

class RtcPCF85063 {
public:
    static RtcPCF85063& instance();

    esp_err_t init();
    esp_err_t getDateTime(RtcDateTime& dt);
    esp_err_t setDateTime(const RtcDateTime& dt);

    // Formatted helpers
    void getTimeString(char* buf, size_t maxLen);  // "HH:MM"
    void getDateString(char* buf, size_t maxLen);  // "MON, 28 SEP"

private:
    RtcPCF85063();
    ~RtcPCF85063() = default;
    RtcPCF85063(const RtcPCF85063&) = delete;
    RtcPCF85063& operator=(const RtcPCF85063&) = delete;

    bool m_initialized;
    bool m_hasHwRtc;
    int64_t m_bootEpochSec;
    int64_t m_bootEspTimerUs;
};

} // namespace SheShield
