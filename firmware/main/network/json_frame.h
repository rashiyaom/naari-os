#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
namespace SheShield {
// Application-level fragmentation for ATT MTU 23..517. A fragment is
// 0x7e, transfer-id, offset-low, offset-high, final(0/1), UTF-8 bytes.
struct JsonFrame {
    uint8_t transfer = 0;
    std::string pending;
    int accept(const uint8_t *bytes, size_t n, std::string &complete) {
        complete.clear();
        if (!n || n > 480)
            return -1;
        if (bytes[0] != 0x7e) {
            complete.assign(reinterpret_cast<const char *>(bytes), n);
            return 1;
        }
        if (n < 6 || bytes[4] > 1)
            return -1;
        size_t offset = bytes[2] | (size_t(bytes[3]) << 8);
        if (offset == 0) {
            pending.clear();
            transfer = bytes[1];
        }
        if (transfer != bytes[1] || offset != pending.size() || offset + n - 5 > 480) {
            pending.clear();
            return -1;
        }
        pending.append(reinterpret_cast<const char *>(bytes + 5), n - 5);
        if (!bytes[4])
            return 0;
        complete.swap(pending);
        pending.clear();
        return 1;
    }
};
} // namespace SheShield
