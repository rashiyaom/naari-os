#include "rtc_pcf85063.h"
#include <esp_log.h>
#include <esp_timer.h>
#include <driver/i2c.h>
#include <sys/time.h>
#include <cstdio>
#include <cstring>

static const char* TAG = "RTC_PCF85063";

namespace SheShield {

static inline uint8_t bcd2dec(uint8_t val) {
    return (val >> 4) * 10 + (val & 0x0F);
}

static inline uint8_t dec2bcd(uint8_t val) {
    return ((val / 10) << 4) | (val % 10);
}

RtcPCF85063& RtcPCF85063::instance() {
    static RtcPCF85063 s_instance;
    return s_instance;
}

RtcPCF85063::RtcPCF85063()
    : m_initialized(false)
    , m_hasHwRtc(false)
    , m_bootEpochSec(1790851260) // Baseline: 2026-10-01 10:41:00
    , m_bootEspTimerUs(0)
{
}

esp_err_t RtcPCF85063::init() {
    ESP_LOGI(TAG, "Initializing PCF85063 Real-Time Clock (I2C addr 0x51)...");
    m_bootEspTimerUs = esp_timer_get_time();

    // Check if PCF85063 responds
    uint8_t reg = 0x00; // Control_1
    uint8_t ctrl1 = 0;
    esp_err_t ret = i2c_master_write_read_device(
        I2C_MASTER_NUM,
        I2C_ADDR_PCF85063,
        &reg, 1,
        &ctrl1, 1,
        pdMS_TO_TICKS(50)
    );

    if (ret == ESP_OK) {
        m_hasHwRtc = true;
        ESP_LOGI(TAG, "PCF85063 RTC detected on I2C bus! Control_1: 0x%02X", ctrl1);

        // Check Oscillator Stop flag in register 0x04 (Seconds)
        reg = 0x04;
        uint8_t secReg = 0;
        i2c_master_write_read_device(I2C_MASTER_NUM, I2C_ADDR_PCF85063, &reg, 1, &secReg, 1, pdMS_TO_TICKS(50));

        RtcDateTime baseline = {
            .year = 2026,
            .month = 10,
            .day = 1,
            .hour = 10,
            .minute = 41,
            .second = 0,
            .weekday = 4 // Thursday
        };

        if (secReg & 0x80) { // OS bit set -> RTC oscillator was stopped / lost power
            ESP_LOGW(TAG, "PCF85063 oscillator was stopped. Calibrating with baseline time (2026-10-01)...");
            setDateTime(baseline);
        } else {
            RtcDateTime current;
            getDateTime(current);
            // If RTC still has old test date (Sep 2026) or invalid date, re-calibrate to Oct 1 2026
            if (current.year < 2026 || (current.year == 2026 && current.month < 10) || current.day == 0) {
                ESP_LOGW(TAG, "Calibrating PCF85063 to Oct 1 2026 10:41...");
                setDateTime(baseline);
            } else {
                ESP_LOGI(TAG, "PCF85063 RTC time validated: %04d-%02d-%02d %02d:%02d:%02d",
                         current.year, current.month, current.day, current.hour, current.minute, current.second);
            }
        }
    } else {
        m_hasHwRtc = false;
        ESP_LOGW(TAG, "PCF85063 RTC not responding at 0x51; utilizing ESP32 internal precision clock.");
    }

    m_initialized = true;
    return ESP_OK;
}

esp_err_t RtcPCF85063::getDateTime(RtcDateTime& dt) {
    if (m_hasHwRtc) {
        uint8_t startReg = 0x04; // Seconds register
        uint8_t buf[7] = {0};
        esp_err_t ret = i2c_master_write_read_device(
            I2C_MASTER_NUM,
            I2C_ADDR_PCF85063,
            &startReg, 1,
            buf, 7,
            pdMS_TO_TICKS(50)
        );

        if (ret == ESP_OK) {
            dt.second  = bcd2dec(buf[0] & 0x7F);
            dt.minute  = bcd2dec(buf[1] & 0x7F);
            dt.hour    = bcd2dec(buf[2] & 0x3F);
            dt.day     = bcd2dec(buf[3] & 0x3F);
            dt.weekday = bcd2dec(buf[4] & 0x07);
            dt.month   = bcd2dec(buf[5] & 0x1F);
            dt.year    = 2000 + bcd2dec(buf[6]);

            // Sync with system time
            struct tm t;
            t.tm_year = dt.year - 1900;
            t.tm_mon  = dt.month - 1;
            t.tm_mday = dt.day;
            t.tm_hour = dt.hour;
            t.tm_min  = dt.minute;
            t.tm_sec  = dt.second;
            t.tm_isdst = -1;
            time_t epoch = mktime(&t);
            struct timeval tv = { .tv_sec = epoch, .tv_usec = 0 };
            settimeofday(&tv, nullptr);
            return ESP_OK;
        }
    }

    // Fallback to elapsed time from system boot
    int64_t elapsedSec = (esp_timer_get_time() - m_bootEspTimerUs) / 1000000;
    time_t curEpoch = m_bootEpochSec + elapsedSec;
    struct tm* tmInfo = gmtime(&curEpoch);

    dt.year    = tmInfo->tm_year + 1900;
    dt.month   = tmInfo->tm_mon + 1;
    dt.day     = tmInfo->tm_mday;
    dt.hour    = tmInfo->tm_hour;
    dt.minute  = tmInfo->tm_min;
    dt.second  = tmInfo->tm_sec;
    dt.weekday = tmInfo->tm_wday;
    return ESP_OK;
}

esp_err_t RtcPCF85063::setDateTime(const RtcDateTime& dt) {
    if (m_hasHwRtc) {
        // Stop clock during write
        uint8_t ctrl[2] = {0x00, 0x20}; // Set STOP bit in Control_1
        i2c_master_write_to_device(I2C_MASTER_NUM, I2C_ADDR_PCF85063, ctrl, 2, pdMS_TO_TICKS(50));

        uint8_t writeBuf[8];
        writeBuf[0] = 0x04; // Start register (Seconds)
        writeBuf[1] = dec2bcd(dt.second) & 0x7F; // Clear OS bit
        writeBuf[2] = dec2bcd(dt.minute) & 0x7F;
        writeBuf[3] = dec2bcd(dt.hour) & 0x3F;
        writeBuf[4] = dec2bcd(dt.day) & 0x3F;
        writeBuf[5] = dec2bcd(dt.weekday) & 0x07;
        writeBuf[6] = dec2bcd(dt.month) & 0x1F;
        writeBuf[7] = dec2bcd(dt.year >= 2000 ? (dt.year - 2000) : dt.year);

        i2c_master_write_to_device(I2C_MASTER_NUM, I2C_ADDR_PCF85063, writeBuf, 8, pdMS_TO_TICKS(50));

        // Restart clock
        ctrl[1] = 0x00; // Clear STOP bit
        i2c_master_write_to_device(I2C_MASTER_NUM, I2C_ADDR_PCF85063, ctrl, 2, pdMS_TO_TICKS(50));
    }

    struct tm t;
    t.tm_year = dt.year - 1900;
    t.tm_mon  = dt.month - 1;
    t.tm_mday = dt.day;
    t.tm_hour = dt.hour;
    t.tm_min  = dt.minute;
    t.tm_sec  = dt.second;
    t.tm_isdst = -1;
    m_bootEpochSec = mktime(&t);
    m_bootEspTimerUs = esp_timer_get_time();

    struct timeval tv = { .tv_sec = m_bootEpochSec, .tv_usec = 0 };
    settimeofday(&tv, nullptr);

    ESP_LOGI(TAG, "RTC Date/Time set to %04d-%02d-%02d %02d:%02d:%02d",
             dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second);
    return ESP_OK;
}

void RtcPCF85063::getTimeString(char* buf, size_t maxLen) {
    RtcDateTime dt;
    getDateTime(dt);
    std::snprintf(buf, maxLen, "%02d:%02d", dt.hour, dt.minute);
}

void RtcPCF85063::getDateString(char* buf, size_t maxLen) {
    static const char* kDays[]   = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    static const char* kMonths[] = {"", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};

    RtcDateTime dt;
    getDateTime(dt);

    const char* dayStr = (dt.weekday < 7) ? kDays[dt.weekday] : "DAY";
    const char* monStr = (dt.month >= 1 && dt.month <= 12) ? kMonths[dt.month] : "MON";

    std::snprintf(buf, maxLen, "%s, %02d %s", dayStr, dt.day, monStr);
}

} // namespace SheShield
