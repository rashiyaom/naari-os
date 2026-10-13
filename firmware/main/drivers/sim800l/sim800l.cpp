// Compatibility adapter: communications run through the paired Android phone.
#include "sim800l.h"
#include "../../network/phone_bridge.h"
#include <cstdio>
#include <cstring>
namespace SheShield {
SIM800L &SIM800L::instance() {
    static SIM800L s;
    return s;
}
SIM800L::SIM800L()
    : m_initialized(false), m_requestQueue(nullptr), m_taskHandle(nullptr), m_statusMutex(nullptr),
      m_status{}, m_consecutiveErrors(0), m_lastCsqCheckMs(0), m_lastRegCheckMs(0) {}
esp_err_t SIM800L::init() {
    m_initialized = true;
    return ESP_OK;
}
bool SIM800L::requestPowerUpAndRegister() { return PhoneBridge::instance().status().ready; }
bool SIM800L::requestPowerDown() { return true; }
bool SIM800L::requestLocationFetch() { return PhoneBridge::instance().request("location") != 0; }
bool SIM800L::requestSendSms(const char *phone, const char *message, uint8_t) {
    return PhoneBridge::instance().request("sms", phone, message) != 0;
}
bool SIM800L::requestSendLocationSms(const char *phone, const char *message, uint8_t) {
    return PhoneBridge::instance().request("location_sms", phone, message) != 0;
}
bool SIM800L::requestVoiceCall(const char *phone) {
    return PhoneBridge::instance().request("call", phone) != 0;
}
SimStatusReport SIM800L::getStatus() {
    SimStatusReport s = {};
    auto link = PhoneBridge::instance().status();
    s.powerState = link.ready ? SimPowerState::READY_IDLE : SimPowerState::POWERED_OFF;
    s.regStatus = link.ready ? NetworkRegStatus::REGISTERED_HOME : NetworkRegStatus::NOT_REGISTERED;
    s.gprsAttached = link.ready;
    s.csq = 99;
    s.location.valid = link.locationValid;
    s.location.latitude = link.latitude;
    s.location.longitude = link.longitude;
    std::snprintf(s.operatorName, sizeof(s.operatorName), "Android BLE");
    std::snprintf(s.statusString, sizeof(s.statusString), "%s",
                  link.ready       ? "Phone connected"
                  : link.connected ? "Open companion app"
                                   : "Connect Android phone");
    auto r = PhoneBridge::instance().result(PhoneBridge::instance().lastRequestId());
    if (r.state == PhoneRequestState::Queued || r.state == PhoneRequestState::Received)
        s.lastSmsStatus = SmsDeliveryStatus::SMS_IN_PROGRESS;
    else if (r.state == PhoneRequestState::Sent || r.state == PhoneRequestState::Delivered)
        s.lastSmsStatus = SmsDeliveryStatus::SMS_DELIVERED_SUCCESS;
    else if (r.state == PhoneRequestState::Failed)
        s.lastSmsStatus = SmsDeliveryStatus::SMS_FAILED_MODEM_ERROR;
    return s;
}
bool SIM800L::isRegistered() const { return PhoneBridge::instance().status().ready; }
bool SIM800L::isReady() const { return isRegistered(); }
uint8_t SIM800L::getSignalCsq() const { return 99; }
const GsmLocation &SIM800L::getLastLocation() const {
    static GsmLocation s;
    auto l = PhoneBridge::instance().status();
    s = {};
    s.valid = l.locationValid;
    s.latitude = l.latitude;
    s.longitude = l.longitude;
    return s;
}
void SIM800L::registerStatusCallback(SimStatusCallback cb) { m_callback = cb; }
} // namespace SheShield
