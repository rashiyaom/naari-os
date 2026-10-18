#include "safety/hold_to_confirm.h"
#include <cassert>
#include <cstdio>
using SheShield::HoldToConfirm;
int main() {
    HoldToConfirm h;
    assert(!h.complete(3000000));
    h.update(true, 0);
    for (int64_t t = 100000; t < 3000000; t += 100000) {
        h.update(true, t);
        assert(!h.complete(t));
    }
    h.update(true, 3000000);
    assert(h.complete(3000000));
    h.update(false, 3000001);
    assert(!h.active());
    h.update(true, 4000000);
    for (int64_t t = 4100000; t <= 6900000; t += 100000)
        h.update(true, t);
    h.update(false, 6950000);
    h.update(true, 7000000);
    assert(!h.complete(7000000)); // Separate presses cannot accumulate time.
    h.update(true, 10000000);
    assert(!h.complete(10000000)); // Lost input stream.
    h.update(true, 9000000);
    assert(!h.complete(9000000)); // Clock reversal.
    std::puts("3-second hold: early press, exact deadline, release, interrupted input, clock "
              "reversal: PASS");
}
