#include "safety/fall_detector.h"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <limits>
using SheShield::FallDetector;
static bool feed(FallDetector &d, int64_t &t, int ms, float g, float rotation = 0) {
    bool found = false;
    for (int i = 0; i < ms; i += 10) {
        found = d.update(g, rotation, t) || found;
        t += 10;
    }
    return found;
}
static void falling(FallDetector &d, int64_t &t) {
    assert(!feed(d, t, 100, .1f));
    assert(!feed(d, t, 20, 4.5f));
}
int main() {
    int64_t t = 0;
    FallDetector d;
    // Rest and ordinary walking must not trigger.
    assert(!feed(d, t, 5000, 1));
    for (int i = 0; i < 4000; ++i) {
        assert(!d.update(1 + .45f * std::sin(i * .2f), 80, t));
        t += 10;
    }
    // A wrist bump alone and a brief acceleration dip alone must not trigger.
    assert(!feed(d, t, 30, 5));
    assert(!feed(d, t, 5000, 1));
    assert(!feed(d, t, 50, .1f));
    assert(!feed(d, t, 5000, 1));
    d.reset();
    t = 0;
    falling(d, t);
    assert(feed(d, t, 2700, 1, 2));
    assert(d.impactG() >= 4.5f);
    // A second sequence in the cooldown cannot retrigger an SOS.
    assert(!feed(d, t, 100, .1f));
    assert(!feed(d, t, 20, 5));
    assert(!feed(d, t, 3000, 1));
    assert(!feed(d, t, 60000, 1));
    falling(d, t);
    assert(feed(d, t, 2700, 1));
    // Movement after impact, stale data, and malformed samples reject detection.
    d.reset();
    t = 0;
    falling(d, t);
    assert(!feed(d, t, 12000, 1, 100));
    assert(!feed(d, t, 3000, 1));
    d.reset();
    t = 0;
    assert(!feed(d, t, 100, .1f));
    t += 2000;
    assert(!feed(d, t, 20, 4));
    assert(!feed(d, t, 3000, 1));
    d.reset();
    t = 0;
    falling(d, t);
    assert(!d.update(std::numeric_limits<float>::quiet_NaN(), 0, t));
    assert(!feed(d, t, 3000, 1));
    // A rotating wrist impact can lack sustained freefall; it must still settle.
    d.reset();
    t = 0;
    assert(!feed(d, t, 150, 1.0f, 180));
    assert(!feed(d, t, 10, 3.5f, 60));
    assert(feed(d, t, 2700, 1.03f, 35));
    d.reset();
    t = 0;
    assert(!feed(d, t, 50, 1, 200));
    assert(!feed(d, t, 500, 1, 0)); // Rotation is too old to corroborate impact.
    assert(!feed(d, t, 20, 5, 0));
    assert(!feed(d, t, 3000, 1, 0));
    d.reset();
    t = 0;
    assert(!feed(d, t, 50, 1, 200));
    assert(!feed(d, t, 20, 4, 200));
    assert(!feed(d, t, 12000, 1, 100)); // Continued movement rejects this path too.
    std::puts("Fall detector: normal motion, valid sequence, cooldown, continued "
              "motion, missing samples, invalid values: PASS");
}
