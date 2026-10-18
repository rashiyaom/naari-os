#pragma once

#include <driver/gpio.h>

/**
 * ============================================================================
 * Waveshare ESP32-S3-Touch-AMOLED-2.06 Pin Configuration & Hardware Mapping
 * ============================================================================
 * SoC: ESP32-S3R8 (8MB Octal PSRAM, 16MB/32MB Flash)
 * Display: 2.06" AMOLED 410x502 via CO5300 (QSPI)
 * Touch: FT3168 Capacitive Touch (I2C)
 * IMU: QMI8658 6-Axis (I2C)
 * Audio Codec: ES7210 (Mic ADC) + ES8311 (Audio DAC)
 * PMIC: AXP2101 (I2C)
 * RTC: PCF85063 (I2C)
 * External: A7672S LTE/GNSS TTL modem on UART1; pins set in menuconfig
 */

// --- Primary System I2C Bus (AXP2101, PCF85063, FT3168, QMI8658, ES7210, ES8311) ---
#define I2C_MASTER_SDA_IO           GPIO_NUM_15
#define I2C_MASTER_SCL_IO           GPIO_NUM_14
#define I2C_MASTER_NUM              I2C_NUM_0
#define I2C_MASTER_FREQ_HZ          400000

// I2C Device Addresses (7-bit)
#define I2C_ADDR_AXP2101            0x34
#define I2C_ADDR_PCF85063           0x51
#define I2C_ADDR_FT3168             0x38
#define I2C_ADDR_QMI8658            0x6B
#define I2C_ADDR_ES7210             0x40
#define I2C_ADDR_ES8311             0x18

// --- Display: CO5300 AMOLED (QSPI Interface, 410 x 502) ---
#define LCD_QSPI_HOST               SPI2_HOST
#define LCD_PIN_CS                  GPIO_NUM_12
#define LCD_PIN_SCK                 GPIO_NUM_11
#define LCD_PIN_D0                  GPIO_NUM_4
#define LCD_PIN_D1                  GPIO_NUM_5
#define LCD_PIN_D2                  GPIO_NUM_6
#define LCD_PIN_D3                  GPIO_NUM_7
#define LCD_PIN_RST                 GPIO_NUM_8
#define LCD_PIN_TE                  GPIO_NUM_13   // Tearing Effect (VSYNC Sync)

#define LCD_H_RES                   410
#define LCD_V_RES                   502
#define LCD_COL_OFFSET              22
#define LCD_ROW_OFFSET              0

// --- Touchscreen: FT3168 Capacitive Touch ---
#define TOUCH_PIN_INT               GPIO_NUM_38
#define TOUCH_PIN_RST               GPIO_NUM_9

// --- 6-Axis IMU: QMI8658 ---
#define IMU_PIN_INT1                GPIO_NUM_3
#define IMU_PIN_INT2                GPIO_NUM_21

// --- Audio: ES7210 (Dual Mic ADC) & ES8311 (Codec) over I2S ---
#define I2S_NUM                     I2S_NUM_0
#define I2S_PIN_MCLK                GPIO_NUM_16
#define I2S_PIN_BCLK                GPIO_NUM_41
#define I2S_PIN_WS                  GPIO_NUM_45
#define I2S_PIN_DOUT                GPIO_NUM_40   // DAC to ES8311 speaker/buzzer
#define I2S_PIN_DIN                 GPIO_NUM_42   // ADC from ES7210 dual mics
#define PA_PIN_ENABLE               GPIO_NUM_46   // Speaker Power Amp Enable (Active HIGH)

// --- MicroSD Slot (SDMMC / SPI mode) ---
#define SD_PIN_CLK                  GPIO_NUM_2
#define SD_PIN_CMD                  GPIO_NUM_1
#define SD_PIN_D0                   GPIO_NUM_3
#define SD_PIN_CS                   GPIO_NUM_17
#define SD_PIN_CD                   GPIO_NUM_NC   // Card Detect

// --- Legacy SIM800L pin names retained for source compatibility only. ---
// Active A7672S UART pins/baud are CONFIG_NAARI_MODEM_* in menuconfig.
// GPIO10 PWRKEY is deliberately not driven: this carrier board powers separately.
#define SIM800L_UART_NUM            UART_NUM_1
#define SIM800L_PIN_TX              GPIO_NUM_43
#define SIM800L_PIN_RX              GPIO_NUM_44
#define SIM800L_PIN_RST             GPIO_NUM_NC
#define SIM800L_PIN_PWRKEY          GPIO_NUM_NC
#define SIM800L_BAUD_RATE           115200

// --- Physical SOS Hardware Button ---
#define BUTTON_PIN_SOS              GPIO_NUM_0    // BOOT / SOS Button
