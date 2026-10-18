#include "fall_detector.h"
#include <algorithm>
#include <cmath>
namespace SheShield {
void FallDetector::reset() {
    phase = Monitoring;
    lowSince = stillSince = lastSample = -1;
    phaseSince = 0;
    lastRotation = -1;
    peakG = 0;
}
bool FallDetector::update(float g, float rotation, int64_t now) {
    if (!std::isfinite(g) || !std::isfinite(rotation) || g < 0 || rotation < 0) {
        reset();
        return false;
    }
    // Missing samples cannot count as freefall or stillness.
    if (lastSample >= 0 && (now < lastSample || now - lastSample > 100))
        reset();
    lastSample = now;
    if (phase == Cooldown) {
        if (now - phaseSince >= 60000) {
            phase = Monitoring;
            lowSince = -1;
        }
        return false;
    }
    if (rotation >= 120.0f)
        lastRotation = now;
    if (phase == Monitoring) {
        // A wrist may rotate through a fall without a sustained near-zero-g interval.
        // Require a hard impact plus recent rapid rotation for this alternative path.
        if (g >= 3.0f && lastRotation >= 0 && now - lastRotation <= 300) {
            peakG = g;
            phase = Settling;
            phaseSince = now;
            stillSince = -1;
            lowSince = -1;
        } else if (g < 0.45f) {
            if (lowSince < 0)
                lowSince = now;
            if (now - lowSince >= 80) {
                phase = Falling;
                phaseSince = now;
            }
        } else
            lowSince = -1;
    } else if (phase == Falling) {
        if (now - phaseSince > 1200) {
            phase = Monitoring;
            lowSince = -1;
        } else if (g >= 3.0f) {
            peakG = g;
            phase = Settling;
            phaseSince = now;
            stillSince = -1;
        }
    } else if (phase == Settling) {
        peakG = std::max(peakG, g);
        if (now - phaseSince > 10000) {
            phase = Monitoring;
            lowSince = -1;
        } else if (g > 0.75f && g < 1.25f && rotation < 45.0f) {
            if (stillSince < 0)
                stillSince = now;
            if (now - stillSince >= 2500) {
                phase = Cooldown;
                phaseSince = now;
                return true;
            }
        } else
            stillSince = -1;
    }
    return false;
}
} // namespace SheShield
