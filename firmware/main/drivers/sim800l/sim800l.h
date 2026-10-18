#pragma once

#include "../../network/phone_bridge.h"
#include "board_pins.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include <atomic>
#include <cstdint>
#include <esp_err.h>
#include <functional>
#include <string>

namespace SheShield {

// Cellular Network Registration Status
enum class NetworkRegStatus {
    NOT_REGISTERED = 0,
    REGISTERED_HOME = 1,
    SEARCHING = 2,
    REGISTRATION_DENIED = 3,
    UNKNOWN = 4,
    REGISTERED_ROAMING = 5,
    NO_SIM_CARD = 6
};

// Modem / phone transport readiness
enum class SimPowerState { POWERED_OFF, POWERING_ON, READY_IDLE, LOW_POWER_SLEEP, ERROR_STATE };

// SMS Transmission Status
enum class SmsDeliveryStatus {
    SMS_IDLE,
    SMS_IN_PROGRESS,
    SMS_SENT,
    SMS_DELIVERED_SUCCESS = SMS_SENT, // legacy value; network acceptance is not delivery
    SMS_FAILED_NO_NETWORK,
    SMS_FAILED_TIMEOUT,
    SMS_FAILED_MODEM_ERROR
};

// Validated modem GNSS or paired-phone coordinates; never fabricated cell location
struct GsmLocation {
    bool valid;
    double latitude;
    double longitude;
    char timestamp[32]; // "YYYY/MM/DD,HH:MM:SS"
    int errorCode;
};

// Overall Telemetry & Status Report
struct SimStatusReport {
    SimPowerState powerState;
    NetworkRegStatus regStatus;
    bool gprsAttached;
    uint8_t csq;     // 0-31 (99 = unknown)
    int16_t rssiDbm; // Computed dBm: -113 + (csq * 2)
    GsmLocation location;
    SmsDeliveryStatus lastSmsStatus;
    char operatorName[32];
    char statusString[64]; // Human-readable string for UI ("No Signal", "Searching...", "Ready")
    uint32_t smsSentCount;
    uint32_t smsFailedCount;
    int64_t lastHeartbeatMs;
};

// Asynchronous Request Types sent to SIM800L task
enum class SimRequestType {
    REQ_POWER_UP_AND_REGISTER,
    REQ_POWER_DOWN,
    REQ_FETCH_LOCATION,
    REQ_SEND_SMS,
    REQ_SEND_LOCATION_SMS,
    REQ_VOICE_CALL,
    REQ_QUERY_STATUS
};

struct SimRequest {
    SimRequestType type;
    char phoneNumber[32];
    char message[256];
    uint8_t retryCount;
    uint8_t maxRetries;
    int64_t requestTimeMs;
};

// Status update callback for State Machine & UI
using SimStatusCallback = std::function<void(const SimStatusReport &report)>;

class SIM800L {
  public:
    static SIM800L &instance();

    esp_err_t init();

    // Asynchronous Request APIs (Thread-safe, non-blocking via FreeRTOS queue)
    bool requestPowerUpAndRegister();
    bool requestPowerDown();
    bool requestLocationFetch();
    bool requestSendSms(const char *phoneNumber, const char *message, uint8_t maxRetries = 3);
    bool requestSendLocationSms(const char *phoneNumber, const char *messagePrefix,
                                uint8_t maxRetries = 3);
    bool requestVoiceCall(const char *phoneNumber);

    uint32_t queueRequest(const char *type, const char *phone = "", const char *message = "");
    bool requestHangUp();
    bool usesDirectCellular() const;
    bool isCellularRequest(uint32_t id) const { return (id & 0x80000000u) != 0; }
    uint32_t lastRequestId() const;
    PhoneResult result(uint32_t id) const;

    // Synchronous status inspection
    SimStatusReport getStatus();
    bool isRegistered() const;
    bool isReady() const;
    uint8_t getSignalCsq() const;
    GsmLocation getLastLocation() const;

    // Status Listener Registration
    void registerStatusCallback(SimStatusCallback cb);

  private:
    SIM800L();
    ~SIM800L() = default;
    SIM800L(const SIM800L &) = delete;
    SIM800L &operator=(const SIM800L &) = delete;

    bool m_initialized = false;
    std::atomic<uint32_t> m_lastQueuedId{0};
};

} // namespace SheShield
