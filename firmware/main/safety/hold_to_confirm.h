#pragma once
#include <algorithm>
#include <cstdint>
namespace SheShield {
// Release, moving outside the target, or a lost input stream resets the whole hold.
class HoldToConfirm {
  public:
    static constexpr int64_t durationUs = 3000000;
    void reset() { since = last = -1; }
    void update(bool pressedInside, int64_t now) {
        if (!pressedInside) {
            reset();
            return;
        }
        if (since < 0 || now < last || now - last > 500000)
            since = now;
        last = now;
    }
    bool active() const { return since >= 0; }
    float progress(int64_t now) const {
        if (!active() || now < last || now - last > 500000)
            return 0;
        return std::min(100.0f, float(now - since) * 100 / durationUs);
    }
    bool complete(int64_t now) const { return progress(now) >= 100; }

  private:
    int64_t since = -1, last = -1;
};
} // namespace SheShield
