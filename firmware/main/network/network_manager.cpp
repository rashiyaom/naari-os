#include "network_manager.h"
#include "phone_bridge.h"
#include <esp_log.h>

static const char *TAG = "Network_Manager";

namespace SheShield {

NetworkManager &NetworkManager::instance() {
    static NetworkManager s_instance;
    return s_instance;
}

NetworkManager::NetworkManager()
    : m_initialized(false), m_wifiConnected(false), m_bleBeaconActive(false) {}

esp_err_t NetworkManager::init() {
    ESP_LOGI(TAG, "Initializing authenticated BLE phone bridge...");
    esp_err_t err = PhoneBridge::instance().init();
    m_initialized = err == ESP_OK;
    return err;
}

bool NetworkManager::connectWifi(const std::string &ssid, const std::string &password) {
    ESP_LOGI(TAG, "Connecting to Wi-Fi SSID: %s...", ssid.c_str());
    (void)password;
    return false; // Wi-Fi transport is not configured.
}

void NetworkManager::startBleSosBeacon(const std::string &deviceId,
                                       const std::string &alertStatus) {
    ESP_LOGW(TAG, "SOS state active [Dev: %s | Status: %s]; phone BLE service remains available",
             deviceId.c_str(), alertStatus.c_str());
    m_bleBeaconActive = true;
}

void NetworkManager::stopBleSosBeacon() {
    if (m_bleBeaconActive) {
        ESP_LOGI(TAG, "SOS state cleared; phone BLE service stays available.");
        m_bleBeaconActive = false;
    }
}

bool NetworkManager::sendHttpAlert(const std::string &endpoint, const std::string &payload) {
    ESP_LOGI(TAG, "Posting HTTP alert to %s: %s", endpoint.c_str(), payload.c_str());
    return false; // No HTTP transport is configured.
}

} // namespace SheShield
