// Legacy API compatibility; select the A7672S UART transport or the Android bridge.
#include "sim800l.h"
#include "../a7672s/a7672s.h"
#include <cstdio>
#include <sdkconfig.h>
namespace SheShield {
SIM800L &SIM800L::instance() {
    static SIM800L s;
    return s;
}
SIM800L::SIM800L() = default;
bool SIM800L::usesDirectCellular() const {
#ifdef CONFIG_NAARI_A7672S_ENABLED
    return true;
#else
    return false;
#endif
}
esp_err_t SIM800L::init() {
    auto error = usesDirectCellular() ? A7672S::instance().init() : ESP_OK;
    m_initialized = error == ESP_OK;
    return error;
}
bool SIM800L::requestPowerUpAndRegister() { return m_initialized; }
// SAFE mode does not interrupt in-flight messages or turn off a shared modem.
bool SIM800L::requestPowerDown() { return true; }
uint32_t SIM800L::queueRequest(const char *type, const char *phone, const char *message) {
    // Fail over only before the modem accepts work. Never replay an uncertain SMS/call.
    uint32_t id = usesDirectCellular() ? A7672S::instance().request(type, phone, message) : 0;
    if (id)
        id |= 0x80000000u;
    else
        id = PhoneBridge::instance().request(type, phone, message);
    if (id)
        m_lastQueuedId = id;
    return id;
}
bool SIM800L::requestLocationFetch() { return queueRequest("location") != 0; }
bool SIM800L::requestSendSms(const char *phone, const char *message, uint8_t) {
    return queueRequest("sms", phone, message) != 0;
}
bool SIM800L::requestSendLocationSms(const char *phone, const char *message, uint8_t) {
    return queueRequest("location_sms", phone, message) != 0;
}
bool SIM800L::requestVoiceCall(const char *phone) { return queueRequest("call", phone) != 0; }
bool SIM800L::requestHangUp() {
    return usesDirectCellular() && A7672S::instance().request("hangup") != 0;
}
uint32_t SIM800L::lastRequestId() const { return m_lastQueuedId; }
PhoneResult SIM800L::result(uint32_t id) const {
    auto r = isCellularRequest(id) ? A7672S::instance().result(id & 0x7fffffffu)
                                   : PhoneBridge::instance().result(id);
    if (r.id)
        r.id = id;
    return r;
}
SimStatusReport SIM800L::getStatus() {
    if (usesDirectCellular()) {
        auto cellular = A7672S::instance().status();
        if (cellular.regStatus == NetworkRegStatus::REGISTERED_HOME ||
            cellular.regStatus == NetworkRegStatus::REGISTERED_ROAMING ||
            !PhoneBridge::instance().status().ready)
            return cellular;
    }
    SimStatusReport s = {};
    auto link = PhoneBridge::instance().status();
    s.powerState = link.ready ? SimPowerState::READY_IDLE : SimPowerState::POWERED_OFF;
    s.regStatus = link.ready ? NetworkRegStatus::REGISTERED_HOME : NetworkRegStatus::NOT_REGISTERED;
    s.csq = 99;
    s.location.valid = link.locationValid;
    s.location.latitude = link.latitude;
    s.location.longitude = link.longitude;
    std::snprintf(s.operatorName, sizeof(s.operatorName), "Android BLE");
    std::snprintf(s.statusString, sizeof(s.statusString), "%s",
                  link.ready       ? "Phone connected"
                  : link.connected ? "Open companion app"
                                   : "Connect Android phone");
    auto r = result(lastRequestId());
    if (r.state == PhoneRequestState::Queued || r.state == PhoneRequestState::Received)
        s.lastSmsStatus = SmsDeliveryStatus::SMS_IN_PROGRESS;
    else if (r.state == PhoneRequestState::Sent || r.state == PhoneRequestState::Delivered)
        s.lastSmsStatus = SmsDeliveryStatus::SMS_SENT;
    else if (r.state == PhoneRequestState::Failed)
        s.lastSmsStatus = SmsDeliveryStatus::SMS_FAILED_MODEM_ERROR;
    return s;
}
bool SIM800L::isRegistered() const {
    auto s = const_cast<SIM800L *>(this)->getStatus();
    return s.regStatus == NetworkRegStatus::REGISTERED_HOME ||
           s.regStatus == NetworkRegStatus::REGISTERED_ROAMING;
}
bool SIM800L::isReady() const { return isRegistered(); }
uint8_t SIM800L::getSignalCsq() const { return const_cast<SIM800L *>(this)->getStatus().csq; }
GsmLocation SIM800L::getLastLocation() const {
    return const_cast<SIM800L *>(this)->getStatus().location;
}
void SIM800L::registerStatusCallback(SimStatusCallback cb) {
    if (usesDirectCellular())
        A7672S::instance().registerStatusCallback(cb);
}
} // namespace SheShield
