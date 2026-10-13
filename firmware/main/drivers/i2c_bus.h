#pragma once

#include <esp_err.h>
#include <driver/i2c.h>
#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>
#include "../board_pins.h"

namespace SheShield {

esp_err_t i2c_bus_init();
esp_err_t i2c_bus_write(uint8_t devAddr, const uint8_t* writeBuf, size_t writeLen, TickType_t timeout = pdMS_TO_TICKS(50));
esp_err_t i2c_bus_write_read(uint8_t devAddr, const uint8_t* writeBuf, size_t writeLen, uint8_t* readBuf, size_t readLen, TickType_t timeout = pdMS_TO_TICKS(50));

} // namespace SheShield
