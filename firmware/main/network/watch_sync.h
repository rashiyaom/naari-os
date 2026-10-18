#pragma once
struct ble_gatt_svc_def;
namespace SheShield {
class WatchSync {
  public:
    static bool init();
    static const ble_gatt_svc_def *service();
    static void connected(unsigned short handle);
    static void disconnected();
};
} // namespace SheShield
