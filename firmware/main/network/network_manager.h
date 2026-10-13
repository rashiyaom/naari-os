#pragma once

#include <string>
#include <esp_err.h>

namespace SheShield {

class NetworkManager {
public:
    static NetworkManager& instance();

    esp_err_t init();
    bool connectWifi(const std::string& ssid, const std::string& password);
    bool isWifiConnected() const { return m_wifiConnected; }

    void startBleSosBeacon(const std::string& deviceId, const std::string& alertStatus);
    void stopBleSosBeacon();

    bool sendHttpAlert(const std::string& endpoint, const std::string& payload);

private:
    NetworkManager();
    ~NetworkManager() = default;

    bool m_initialized;
    bool m_wifiConnected;
    bool m_bleBeaconActive;
};

} // namespace SheShield
