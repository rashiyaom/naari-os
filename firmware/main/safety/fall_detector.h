#pragma once
#include <cstdint>
namespace SheShield {
// Pure, timestamp-based detection; the sampling task is its only writer.
class FallDetector {
  public:
    bool update(float accelerationG, float angularSpeedDps, int64_t nowMs);
    void reset();
    float impactG() const { return peakG; }

  private:
    enum Phase { Monitoring, Falling, Settling, Cooldown } phase = Monitoring;
    int64_t lowSince = -1, phaseSince = 0, stillSince = -1, lastSample = -1;
    float peakG = 0;
};
} // namespace SheShield
