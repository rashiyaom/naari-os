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
    float m_fallThresholdG;
    float m_impactThresholdG;
    int m_freefallCounter;
    int m_impactWaitCounter;
    int m_stillnessCounter;
    bool m_potentialFallActive;

    // Violent shake detection
    int m_shakePeakCount;
    int m_shakeWindowSamples;
    int64_t m_lastShakeTriggerMs;
    float m_prevAccelMag;
    bool m_prevInPeak;
};

} // namespace SheShield
