#pragma once

#include "../../board_pins.h"
#include <atomic>
#include <cstdint>
#include <esp_err.h>

namespace SheShield {

struct ImuData {
    float ax, ay, az;  // in G's
    float gx, gy, gz;  // in dps (degrees per second)
    float temperature; // in Celsius
    bool fallSuspected;
    bool highImpact;
    bool violentShake;
};

class ImuQMI8658 {
  public:
    static ImuQMI8658 &instance();

    esp_err_t init();
    esp_err_t read(ImuData &data);
    bool isReady() const { return m_initialized; }
    bool isFallDetectionEnabled() const { return m_fallEnabled; }
    float lastFallImpactG() const;
    struct FallTelemetry {
        float accelerationG, angularSpeedDps, peakG;
        unsigned phase;
        uint32_t samples, detections;
        bool testing;
    };
    FallTelemetry fallTelemetry() const;
    void setFallTestMode(bool enabled) { m_testMode = enabled; }
    bool setFallDetectionEnabled(bool enabled);
    bool checkFallCondition(const ImuData &data);
    bool checkViolentShakeCondition(const ImuData &data);

  private:
    ImuQMI8658();
    ~ImuQMI8658() = default;
    ImuQMI8658(const ImuQMI8658 &) = delete;
    ImuQMI8658 &operator=(const ImuQMI8658 &) = delete;

    bool m_initialized;
    std::atomic<bool> m_fallEnabled{false};
    std::atomic<bool> m_testMode{false};
    bool m_detectorTestMode = false; // Sampling task is the only writer.
    std::atomic<float> m_liveG{0}, m_liveRotation{0}, m_peakG{0};
    std::atomic<unsigned> m_phase{0};
    std::atomic<uint32_t> m_samples{0}, m_detections{0};
};

} // namespace SheShield
