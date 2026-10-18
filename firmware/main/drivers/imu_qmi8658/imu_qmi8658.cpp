#include "imu_qmi8658.h"
#include "../../safety/fall_detector.h"
#include "../../storage/preferences.h"
#include <cmath>
#include <driver/i2c.h>
#include <esp_log.h>
#include <esp_timer.h>

static SheShield::FallDetector s_fallDetector;
static const char *TAG = "IMU_QMI8658";

namespace SheShield {

ImuQMI8658 &ImuQMI8658::instance() {
    static ImuQMI8658 s_instance;
    return s_instance;
}

ImuQMI8658::ImuQMI8658() : m_initialized(false) {}

esp_err_t ImuQMI8658::init() {
    ESP_LOGI(TAG, "Initializing QMI8658 6-Axis IMU (I2C addr 0x6B)...");

    uint8_t whoAmIReg = 0x00;
    uint8_t chipId = 0;
    esp_err_t ret = i2c_master_write_read_device(I2C_MASTER_NUM, I2C_ADDR_QMI8658, &whoAmIReg, 1,
                                                 &chipId, 1, pdMS_TO_TICKS(50));

    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to communicate with QMI8658!");
        return ret;
    }

    ESP_LOGI(TAG, "QMI8658 detected! Chip ID: 0x%02X", chipId);

    if (chipId != 0x05)
        return ESP_ERR_NOT_FOUND;
    m_fallEnabled = readPreference("fall_enabled", 0) != 0;

    uint8_t ctrl1Cmd[2] = {0x02, 0x40}; // enable address auto-increment for coherent axis bursts
    if (i2c_master_write_to_device(I2C_MASTER_NUM, I2C_ADDR_QMI8658, ctrl1Cmd, 2,
                                   pdMS_TO_TICKS(50)) != ESP_OK)
        return ESP_FAIL;

    // Change ranges while both sensors are disabled; enable them only after setup.
    uint8_t ctrl7Cmd[2] = {0x08, 0x00};
    if (i2c_master_write_to_device(I2C_MASTER_NUM, I2C_ADDR_QMI8658, ctrl7Cmd, 2,
                                   pdMS_TO_TICKS(50)) != ESP_OK)
        return ESP_FAIL;

    // Configure Accel Scale (+/- 16G) & ODR in CTRL2 (0x03)
    uint8_t ctrl2Cmd[2] = {0x03, 0x35}; // 16G, 250Hz ODR
    if (i2c_master_write_to_device(I2C_MASTER_NUM, I2C_ADDR_QMI8658, ctrl2Cmd, 2,
                                   pdMS_TO_TICKS(50)) != ESP_OK)
        return ESP_FAIL;

    // Configure Gyro Scale (+/- 1024 dps) & ODR in CTRL3 (0x04)
    uint8_t ctrl3Cmd[2] = {0x04, 0x65}; // 1024dps, 250Hz ODR
    if (i2c_master_write_to_device(I2C_MASTER_NUM, I2C_ADDR_QMI8658, ctrl3Cmd, 2,
                                   pdMS_TO_TICKS(50)) != ESP_OK)
        return ESP_FAIL;

    ctrl7Cmd[1] = 0x03;
    if (i2c_master_write_to_device(I2C_MASTER_NUM, I2C_ADDR_QMI8658, ctrl7Cmd, 2,
                                   pdMS_TO_TICKS(50)) != ESP_OK)
        return ESP_FAIL;
    uint8_t reg = 0x02, config[3] = {};
    if (i2c_master_write_read_device(I2C_MASTER_NUM, I2C_ADDR_QMI8658, &reg, 1, config, 3,
                                     pdMS_TO_TICKS(50)) != ESP_OK ||
        config[0] != 0x40 || config[1] != 0x35 || config[2] != 0x65) {
        ESP_LOGE(TAG, "IMU configuration read-back failed: %02x %02x %02x", config[0], config[1],
                 config[2]);
        return ESP_FAIL;
    }
    ESP_LOGI(TAG, "Verified IMU: CTRL1=%02x accel=%02x gyro=%02x; monitoring=%d", config[0],
             config[1], config[2], bool(m_fallEnabled));
    m_initialized = true;
    ESP_LOGI(TAG, "QMI8658 IMU configured for Fall & Distress detection.");
    return ESP_OK;
}

esp_err_t ImuQMI8658::read(ImuData &data) {
    if (!m_initialized)
        return ESP_ERR_INVALID_STATE;

    uint8_t startReg = 0x35; // Accel X LSB register
    uint8_t rawBuf[12] = {0};

    esp_err_t ret = i2c_master_write_read_device(I2C_MASTER_NUM, I2C_ADDR_QMI8658, &startReg, 1,
                                                 rawBuf, 12, pdMS_TO_TICKS(20));

    if (ret != ESP_OK)
        return ret;

    int16_t axRaw = static_cast<int16_t>((rawBuf[1] << 8) | rawBuf[0]);
    int16_t ayRaw = static_cast<int16_t>((rawBuf[3] << 8) | rawBuf[2]);
    int16_t azRaw = static_cast<int16_t>((rawBuf[5] << 8) | rawBuf[4]);

    int16_t gxRaw = static_cast<int16_t>((rawBuf[7] << 8) | rawBuf[6]);
    int16_t gyRaw = static_cast<int16_t>((rawBuf[9] << 8) | rawBuf[8]);
    int16_t gzRaw = static_cast<int16_t>((rawBuf[11] << 8) | rawBuf[10]);

    // Convert raw +/-16G scale (2048 LSB/g)
    data.ax = static_cast<float>(axRaw) / 2048.0f;
    data.ay = static_cast<float>(ayRaw) / 2048.0f;
    data.az = static_cast<float>(azRaw) / 2048.0f;

    // Convert raw 1024dps scale (32 LSB/dps)
    data.gx = static_cast<float>(gxRaw) / 32.0f;
    data.gy = static_cast<float>(gyRaw) / 32.0f;
    data.gz = static_cast<float>(gzRaw) / 32.0f;

    data.temperature = 25.0f;
    // Detection runs only in the dedicated sampling task; telemetry reads cannot
    // advance it.
    data.fallSuspected = false;
    data.highImpact = false;
    data.violentShake = false;

    return ESP_OK;
}

bool ImuQMI8658::checkViolentShakeCondition(const ImuData &data) {
    return false; // Feature removed per user request
}

bool ImuQMI8658::checkFallCondition(const ImuData &data) {
    float g = std::sqrt(data.ax * data.ax + data.ay * data.ay + data.az * data.az);
    float rotation = std::sqrt(data.gx * data.gx + data.gy * data.gy + data.gz * data.gz);
    m_liveG = g;
    m_liveRotation = rotation;
    ++m_samples;
    bool test = m_testMode;
    if (m_detectorTestMode != test || (!m_fallEnabled && !test))
        s_fallDetector.reset();
    m_detectorTestMode = test;
    bool detected =
        (m_fallEnabled || test) && s_fallDetector.update(g, rotation, esp_timer_get_time() / 1000);
    m_peakG = s_fallDetector.impactG();
    m_phase = s_fallDetector.phaseCode();
    if (detected) {
        ++m_detections;
        ESP_LOGW(TAG, "Fall pattern: impact=%.2fg sensorTest=%d", float(m_peakG), test);
    }
    static int64_t lastLog = 0;
    auto now = esp_timer_get_time();
    if (now - lastLog >= 5000000) {
        lastLog = now;
        ESP_LOGI(TAG, "Motion: g=%.2f rotation=%.1f phase=%u samples=%lu enabled=%d test=%d", g,
                 rotation, unsigned(m_phase), (unsigned long)m_samples.load(), bool(m_fallEnabled),
                 test);
    }
    return detected && !test; // Sensor test uses the real detector but cannot dispatch an SOS.
}
ImuQMI8658::FallTelemetry ImuQMI8658::fallTelemetry() const {
    return {m_liveG, m_liveRotation, m_peakG, m_phase, m_samples, m_detections, m_testMode};
}

float ImuQMI8658::lastFallImpactG() const { return s_fallDetector.impactG(); }
bool ImuQMI8658::setFallDetectionEnabled(bool enabled) {
    if (enabled && !m_initialized)
        return false;
    if (!writePreference("fall_enabled", enabled ? 1 : 0))
        return false;
    m_fallEnabled = enabled;
    return true;
}

} // namespace SheShield
