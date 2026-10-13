#include "i2c_bus.h"
#include <esp_log.h>

static const char* TAG = "I2C_Bus_Safe";
static SemaphoreHandle_t s_i2cMutex = nullptr;

namespace SheShield {

esp_err_t i2c_bus_init() {
    if (!s_i2cMutex) {
        s_i2cMutex = xSemaphoreCreateMutex();
    }

    i2c_config_t conf = {
        .mode = I2C_MODE_MASTER,
        .sda_io_num = I2C_MASTER_SDA_IO,
        .scl_io_num = I2C_MASTER_SCL_IO,
        .sda_pullup_en = GPIO_PULLUP_ENABLE,
        .scl_pullup_en = GPIO_PULLUP_ENABLE,
        .master = { .clk_speed = 200000 }, // 200 kHz rock-solid frequency across 6 I2C chips
        .clk_flags = 0
    };
    esp_err_t err = i2c_param_config(I2C_MASTER_NUM, &conf);
    if (err != ESP_OK) return err;
    return i2c_driver_install(I2C_MASTER_NUM, conf.mode, 0, 0, 0);
}

esp_err_t i2c_bus_write(uint8_t devAddr, const uint8_t* writeBuf, size_t writeLen, TickType_t timeout) {
    if (!s_i2cMutex) return ESP_ERR_INVALID_STATE;
    if (xSemaphoreTake(s_i2cMutex, timeout) != pdTRUE) {
        return ESP_ERR_TIMEOUT;
    }
    esp_err_t ret = i2c_master_write_to_device(I2C_MASTER_NUM, devAddr, writeBuf, writeLen, timeout);
    xSemaphoreGive(s_i2cMutex);
    return ret;
}

esp_err_t i2c_bus_write_read(uint8_t devAddr, const uint8_t* writeBuf, size_t writeLen, uint8_t* readBuf, size_t readLen, TickType_t timeout) {
    if (!s_i2cMutex) return ESP_ERR_INVALID_STATE;
    if (xSemaphoreTake(s_i2cMutex, timeout) != pdTRUE) {
        return ESP_ERR_TIMEOUT;
    }
    esp_err_t ret = i2c_master_write_read_device(I2C_MASTER_NUM, devAddr, writeBuf, writeLen, readBuf, readLen, timeout);
    xSemaphoreGive(s_i2cMutex);
    return ret;
}

} // namespace SheShield
