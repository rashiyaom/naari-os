#include "touch_ft3168.h"
#include "../i2c_bus.h"
#include <esp_log.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

static const char* TAG = "Touch_FT3168";

namespace SheShield {

TouchFT3168& TouchFT3168::instance() {
    static TouchFT3168 s_instance;
    return s_instance;
}

TouchFT3168::TouchFT3168()
    : m_initialized(false)
{
}

esp_err_t TouchFT3168::init() {
    ESP_LOGI(TAG, "Initializing FT3168 Capacitive Touch controller (I2C addr 0x38)...");

    // Configure Reset Pin
    gpio_config_t rst_conf = {
        .pin_bit_mask = (1ULL << TOUCH_PIN_RST),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE
    };
    gpio_config(&rst_conf);

    // Configure Touch Interrupt Pin
    gpio_config_t int_conf = {
        .pin_bit_mask = (1ULL << TOUCH_PIN_INT),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE
    };
    gpio_config(&int_conf);

    // Reset cycle
    gpio_set_level(TOUCH_PIN_RST, 0);
    vTaskDelay(pdMS_TO_TICKS(20));
    gpio_set_level(TOUCH_PIN_RST, 1);
    vTaskDelay(pdMS_TO_TICKS(250)); // Allow 250ms for FT3168 MCU boot & calibration

    // Set normal operating mode and active power
    uint8_t cfgMode[2] = {0x00, 0x00}; // REG 0x00 = Normal Mode
    i2c_bus_write(I2C_ADDR_FT3168, cfgMode, 2, pdMS_TO_TICKS(50));

    uint8_t cfgPwr[2] = {0xA5, 0x00};  // REG 0xA5 = Active Power Mode
    i2c_bus_write(I2C_ADDR_FT3168, cfgPwr, 2, pdMS_TO_TICKS(50));

    m_initialized = true;
    ESP_LOGI(TAG, "FT3168 Capacitive Touch controller initialized (active mode).");
    return ESP_OK;
}

bool TouchFT3168::read(TouchPoint& point) {
    if (!m_initialized) return false;

    uint8_t reg = 0x02; // TD_STATUS register
    uint8_t data[5] = {0};

    esp_err_t ret = i2c_bus_write_read(
        I2C_ADDR_FT3168,
        &reg, 1,
        data, 5,
        pdMS_TO_TICKS(15)
    );

    if (ret != ESP_OK) {
        point.pressed = false;
        return false;
    }

    uint8_t touchPoints = data[0] & 0x0F;
    if (touchPoints > 0 && touchPoints <= 2) {
        point.pressed = true;
        point.pointCount = touchPoints;
        int rawX = ((data[1] & 0x0F) << 8) | data[2];
        int rawY = ((data[3] & 0x0F) << 8) | data[4];

        // Clamp to AMOLED 410x502 boundaries
        if (rawX < 0) rawX = 0;
        if (rawX >= LCD_H_RES) rawX = LCD_H_RES - 1;
        if (rawY < 0) rawY = 0;
        if (rawY >= LCD_V_RES) rawY = LCD_V_RES - 1;

        point.x = rawX;
        point.y = rawY;
        return true;
    }

    point.pressed = false;
    point.pointCount = 0;
    return false;
}

} // namespace SheShield
