#include "axp2101.h"
#include "../i2c_bus.h"
#include <esp_log.h>
#include <esp_timer.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

static const char* TAG = "AXP2101_PMIC";

namespace SheShield {

static esp_err_t axpWriteReg(uint8_t reg, uint8_t val) {
    uint8_t buf[2] = {reg, val};
    return i2c_bus_write(I2C_ADDR_AXP2101, buf, 2, pdMS_TO_TICKS(50));
}

static esp_err_t axpReadReg(uint8_t reg, uint8_t* val) {
    return i2c_bus_write_read(I2C_ADDR_AXP2101, &reg, 1, val, 1, pdMS_TO_TICKS(50));
}

AXP2101& AXP2101::instance() {
    static AXP2101 s_instance;
    return s_instance;
}

AXP2101::AXP2101()
    : m_initialized(false)
{
}

esp_err_t AXP2101::init() {
    ESP_LOGI(TAG, "Initializing AXP2101 Power Management IC (I2C addr 0x34)...");

    uint8_t chipId = 0;
    esp_err_t ret = axpReadReg(0x03, &chipId);

    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to communicate with AXP2101 PMIC (ret: 0x%x)!", ret);
        return ret;
    }

    ESP_LOGI(TAG, "AXP2101 detected (Chip ID: 0x%02X). Configuring power rails for AMOLED...", chipId);

    // Set voltage outputs
    axpWriteReg(0x92, 0x1C); // ALDO1: 3.3V (28 * 100mV + 500mV)
    axpWriteReg(0x93, 0x17); // ALDO2: 2.8V
    axpWriteReg(0x94, 0x1C); // ALDO3: 3.3V
    axpWriteReg(0x95, 0x19); // ALDO4: 3.0V
    axpWriteReg(0x96, 0x1C); // BLDO1: 3.3V (OLED VDD)
    axpWriteReg(0x97, 0x1C); // BLDO2: 3.3V (MIC VDD)
    axpWriteReg(0x99, 0x1C); // DLDO1: 3.3V

    // Enable power channels: ALDO1, ALDO2, ALDO3, ALDO4, BLDO1, BLDO2, DLDO1
    axpWriteReg(0x90, 0xBF); 
    axpWriteReg(0x91, 0x01); // DLDO2

    // Enable ADC voltage & battery measurements
    axpWriteReg(0x30, 0xFF);

    // Configure Power Key (PEK) and enable PEK interrupts in INTEN2 (reg 0x41)
    axpWriteReg(0x27, 0x40); // Long press ~1.5s, turn-on ~512ms
    axpWriteReg(0x41, 0xFF); // Enable all PEK interrupts (short, long, press, release)
    axpWriteReg(0x48, 0xFF); // Clear any pending interrupt flags in reg 0x48
    axpWriteReg(0x49, 0xFF); // Clear any pending interrupt flags in reg 0x49
    axpWriteReg(0x4A, 0xFF); // Clear any pending interrupt flags in reg 0x4A

    vTaskDelay(pdMS_TO_TICKS(50));
    setAmoledPower(true);

    m_initialized = true;
    ESP_LOGI(TAG, "AXP2101 power rails & PEK side power button active (AMOLED VDD 3.3V ON).");
    return ESP_OK;
}

bool AXP2101::pollPowerKey(bool& shortPress, bool& longPress) {
    shortPress = false;
    longPress = false;
    if (!m_initialized) return false;

    // Rate limit I2C polling of INTSTS2 to every 40ms to avoid bus saturation
    static int64_t s_lastPollUs = 0;
    int64_t now = esp_timer_get_time();
    if (now - s_lastPollUs < 40000LL) return false;
    s_lastPollUs = now;

    uint8_t intsts2 = 0;
    // If I2C reads 0xFF, it usually indicates NACK / floating pullups, not a real button event
    if (axpReadReg(0x49, &intsts2) == ESP_OK && intsts2 != 0 && intsts2 != 0xFF) {
        if (intsts2 & 0x08) {
            shortPress = true; // Bit 3: PEK short press
        }
        if (intsts2 & 0x04) {
            longPress = true;  // Bit 2: PEK long press
        }
        // Write 0xFF to unconditionally clear all pending status bits in 0x49
        axpWriteReg(0x49, 0xFF);
        return (shortPress || longPress);
    }
    return false;
}

esp_err_t AXP2101::getBatteryStatus(BatteryStatus& status) {
    if (!m_initialized) {
        status.voltage = 4.10f;
        status.percentage = 95;
        status.isCharging = false;
        status.isBatteryPresent = true;
        return ESP_OK;
    }

    // Read Battery Voltage registers (0x34 high, 0x35 low)
    uint8_t vHigh = 0, vLow = 0;
    if (axpReadReg(0x34, &vHigh) == ESP_OK && axpReadReg(0x35, &vLow) == ESP_OK) {
        uint16_t rawV = ((vHigh & 0x1F) << 8) | vLow;
        status.voltage = (rawV * 0.5f) / 1000.0f; // mV to V
    } else {
        status.voltage = 3.95f;
    }

    if (status.voltage < 3.0f || status.voltage > 4.5f) status.voltage = 3.95f;

    // Read Battery Percentage register (0xA4)
    uint8_t capVal = 0;
    if (axpReadReg(0xA4, &capVal) == ESP_OK && capVal > 0 && capVal <= 100) {
        status.percentage = capVal;
    } else {
        status.percentage = 85;
    }

    // Read Charging Status (0x01)
    uint8_t status2 = 0;
    if (axpReadReg(0x01, &status2) == ESP_OK) {
        status.isCharging = ((status2 >> 5) & 0x07) != 0;
    } else {
        status.isCharging = false;
    }

    status.isBatteryPresent = true;
    return ESP_OK;
}

void AXP2101::setAmoledPower(bool enable) {
    ESP_LOGI(TAG, "AXP2101 AMOLED power rail (BLDO1) set to: %s", enable ? "ON" : "OFF");
    if (enable) {
        axpWriteReg(0x96, 0x1C); // BLDO1 = 3.3V
        axpWriteReg(0x90, 0xBF); // Turn on BLDO1 (bit 4)
    } else {
        axpWriteReg(0x90, 0xAF); // Turn off BLDO1
    }
}

void AXP2101::powerOff() {
    ESP_LOGW(TAG, "Shutting down system power rails via AXP2101...");
    axpWriteReg(0x22, 0x01); // Power OFF command
}

} // namespace SheShield
