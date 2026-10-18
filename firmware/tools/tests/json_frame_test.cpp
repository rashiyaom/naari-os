#include "network/json_frame.h"
#include <cassert>
#include <iostream>
using namespace SheShield;
int main() {
    JsonFrame frame;
    std::string out;
    const std::string json =
        "{\"v\":1,\"op\":\"profile\",\"name0\":\"नारी\",\"phone0\":\"+12345\"}";
    for (size_t offset = 0; offset < json.size();) {
        size_t n = std::min<size_t>(15, json.size() - offset);
        std::string packet;
        packet += char(0x7e);
        packet += char(2);
        packet += char(offset);
        packet += char(offset >> 8);
        packet += char(offset + n == json.size());
        packet += json.substr(offset, n);
        int state =
            frame.accept(reinterpret_cast<const uint8_t *>(packet.data()), packet.size(), out);
        offset += n;
        assert(state == (offset == json.size() ? 1 : 0));
    }
    assert(out == json);
    uint8_t gap[] = {0x7e, 3, 9, 0, 1, 'x'};
    assert(frame.accept(gap, sizeof(gap), out) == -1);
    uint8_t invalid[] = {0x7e, 3, 0, 0, 2, 'x'};
    assert(frame.accept(invalid, sizeof(invalid), out) == -1);
    assert(frame.accept(reinterpret_cast<const uint8_t *>(json.data()), json.size(), out) == 1 &&
           out == json);
    std::string tooLong(481, 'x');
    assert(frame.accept(reinterpret_cast<const uint8_t *>(tooLong.data()), tooLong.size(), out) ==
           -1);
    std::cout
        << "BLE framing: MTU23, UTF-8 boundaries, invalid offset/flags, length bounds: PASS\n";
}
