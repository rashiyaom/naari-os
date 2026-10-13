#include "state_machine.h"
#include "../drivers/audio_es7210_es8311/audio_manager.h"
#include "../drivers/sim800l/sim800l.h"
#include "../network/network_manager.h"
#include "../network/phone_bridge.h"
#include "../storage/sd_logger.h"
#include <cstdio>
#include <cstring>
#include <esp_log.h>
#include <esp_timer.h>

static const char *TAG = "State_Machine";

namespace SheShield {

std::vector<EmergencyContact> StateMachine::getContacts() const {
    std::vector<EmergencyContact> contacts;
    for (unsigned i = 0; i < 2; ++i) {
        auto phone = PhoneBridge::instance().guardianPhone(i);
        if (!phone.empty())
            contacts.push_back(
                {PhoneBridge::instance().guardianName(i), phone, "Guardian", i == 0});
    }
    return contacts;
}
StateMachine &StateMachine::instance() {
    static StateMachine s_instance;
    return s_instance;
}

StateMachine::StateMachine()
    : m_currentState(SystemState::SAFE), m_eventQueue(nullptr), m_taskHandle(nullptr),
      m_stateCallback(nullptr), m_watchPeriodicTimer(nullptr), m_emergencyPeriodicTimer(nullptr),
      m_graceTimer(nullptr), m_wearerName("Naari wearer"), m_gracePeriodActive(false),
      m_graceRemainingSec(5), m_watchStartTimeMs(0), m_lastShockG(0.0f),
      m_emergencyAlertDispatched(false) {
    // Guardians come from the Android companion.
}

void StateMachine::init() {
    ESP_LOGI(TAG,
             "Initializing SheShield 3-Tier State Machine (SAFE / SAFETY_WATCH / EMERGENCY)...");

    m_eventQueue = xQueueCreate(24, sizeof(EventMessage));
    if (!m_eventQueue) {
        ESP_LOGE(TAG, "Failed to create state machine event queue!");
        return;
    }

    // 1. Create FreeRTOS Software Timers
    // Watch periodic location update timer (every 3 minutes = 180,000 ms)
    m_watchPeriodicTimer = xTimerCreate("watch_timer", pdMS_TO_TICKS(180000),
                                        pdTRUE, // Auto-reload
                                        this, watchTimerCallback);

    // Emergency high-rate periodic location update timer (every 60 seconds = 60,000 ms)
    m_emergencyPeriodicTimer = xTimerCreate("emerg_timer", pdMS_TO_TICKS(60000),
                                            pdTRUE, // Auto-reload
                                            this, emergencyTimerCallback);

    // Emergency grace period countdown timer (1-second ticks)
    m_graceTimer = xTimerCreate("grace_timer", pdMS_TO_TICKS(1000),
                                pdTRUE, // Auto-reload
                                this, graceTimerCallback);

    // 2. Spawn State Machine FreeRTOS Task (Priority 5 on Core 1)
    xTaskCreatePinnedToCore(taskEntry, "sm_task", 4096, this, 5, &m_taskHandle, 1);

    // Listen to SIM800L status updates
    SIM800L::instance().registerStatusCallback([this](const SimStatusReport &report) {
        this->sendEvent(StateEvent::EVT_SIM_STATUS_CHANGED,
                        static_cast<uint32_t>(report.regStatus));
    });

    ESP_LOGI(TAG, "State Machine active in [SAFE] state.");
}

void StateMachine::addEmergencyContact(const std::string &name, const std::string &phone,
                                       const std::string &relation, bool isPrimary) {
    if (isPrimary) {
        for (auto &c : m_contacts) {
            c.isPrimary = false;
        }
    }
    m_contacts.push_back({name, phone, relation, isPrimary});
}

void StateMachine::registerStateCallback(StateChangeCallback cb) { m_stateCallback = cb; }

void StateMachine::sendEvent(StateEvent event, uint32_t payload, float shockG) {
    if (!m_eventQueue)
        return;

    EventMessage msg;
    msg.event = event;
    msg.payload = payload;
    msg.shockG = shockG;
    msg.timestamp_ms = esp_timer_get_time() / 1000;

    if (xQueueSend(m_eventQueue, &msg, pdMS_TO_TICKS(50)) != pdTRUE) {
        ESP_LOGW(TAG, "Event queue full, dropped event: %d", static_cast<int>(event));
    }
}

void StateMachine::sendEventFromISR(StateEvent event, BaseType_t *higherPriorityTaskWoken) {
    if (!m_eventQueue)
        return;

    EventMessage msg;
    msg.event = event;
    msg.payload = 0;
    msg.shockG = 0.0f;
    msg.timestamp_ms = esp_timer_get_time() / 1000;

    xQueueSendFromISR(m_eventQueue, &msg, higherPriorityTaskWoken);
}

const char *StateMachine::getStateString() const {
    switch (m_currentState) {
    case SystemState::SAFE:
        return "SAFE";
    case SystemState::SAFETY_WATCH:
        return "SAFETY_WATCH";
    case SystemState::EMERGENCY:
        return "EMERGENCY";
    default:
        return "UNKNOWN";
    }
}

uint32_t StateMachine::getWatchActiveDurationSec() const {
    if (m_currentState != SystemState::SAFETY_WATCH || m_watchStartTimeMs == 0) {
        return 0;
    }
    return static_cast<uint32_t>((esp_timer_get_time() / 1000 - m_watchStartTimeMs) / 1000);
}

void StateMachine::taskEntry(void *param) { static_cast<StateMachine *>(param)->run(); }

void StateMachine::run() {
    EventMessage msg;
    while (true) {
        if (xQueueReceive(m_eventQueue, &msg, portMAX_DELAY) == pdTRUE) {
            switch (m_currentState) {
            case SystemState::SAFE:
                handleStateSafe(msg);
                break;
            case SystemState::SAFETY_WATCH:
                handleStateWatch(msg);
                break;
            case SystemState::EMERGENCY:
                handleStateEmergency(msg);
                break;
            }
        }
    }
}

void StateMachine::transitionTo(SystemState newState) {
    if (m_currentState == newState)
        return;

    SystemState oldState = m_currentState;
    m_currentState = newState;

    ESP_LOGW(TAG, "==========================================================");
    ESP_LOGW(TAG, "  >>> STATE TRANSITION: [%s] -> [%s] <<<",
             oldState == SystemState::SAFE
                 ? "SAFE"
                 : (oldState == SystemState::SAFETY_WATCH ? "SAFETY_WATCH" : "EMERGENCY"),
             newState == SystemState::SAFE
                 ? "SAFE"
                 : (newState == SystemState::SAFETY_WATCH ? "SAFETY_WATCH" : "EMERGENCY"));
    ESP_LOGW(TAG, "==========================================================");

    char logMsg[128];
    std::snprintf(logMsg, sizeof(logMsg), "STATE_CHANGE: %s -> %s",
                  oldState == SystemState::SAFE
                      ? "SAFE"
                      : (oldState == SystemState::SAFETY_WATCH ? "SAFETY_WATCH" : "EMERGENCY"),
                  newState == SystemState::SAFE
                      ? "SAFE"
                      : (newState == SystemState::SAFETY_WATCH ? "SAFETY_WATCH" : "EMERGENCY"));
    SdLogger::instance().logIncidentEvent("STATE_MACHINE", logMsg);

    if (m_stateCallback) {
        m_stateCallback(oldState, newState);
    }
}

// -------------------------------------------------------------
// State: SAFE
// -------------------------------------------------------------
void StateMachine::handleStateSafe(const EventMessage &msg) {
    switch (msg.event) {
    case StateEvent::EVT_ENTER_WATCH_MODE:
        ESP_LOGI(TAG, "[SAFE] User activated SAFETY_WATCH mode.");
        transitionTo(SystemState::SAFETY_WATCH);
        enterWatchState();
        break;

    case StateEvent::EVT_MANUAL_SOS_PRESS:
    case StateEvent::EVT_FALL_DETECTED:
    case StateEvent::EVT_VOICE_TRIGGERED:
        ESP_LOGW(TAG, "[SAFE] Direct trigger to EMERGENCY! (Event: %d, Shock: %.2f G)",
                 static_cast<int>(msg.event), msg.shockG);
        m_lastShockG = msg.shockG;
        transitionTo(SystemState::EMERGENCY);
        enterEmergencyState(msg.shockG);
        break;

    case StateEvent::EVT_SIM_STATUS_CHANGED:
        // Log cellular status change
        break;

    default:
        break;
    }
}

void StateMachine::enterSafeState() {
    // 1. Stop all timers
    if (m_watchPeriodicTimer)
        xTimerStop(m_watchPeriodicTimer, 0);
    if (m_emergencyPeriodicTimer)
        xTimerStop(m_emergencyPeriodicTimer, 0);
    if (m_graceTimer)
        xTimerStop(m_graceTimer, 0);

    m_gracePeriodActive = false;
    m_emergencyAlertDispatched = false;

    // 2. Put SIM800L modem to sleep to conserve battery
    SIM800L::instance().requestPowerDown();

    // 3. Stop siren & BLE beacon
    AudioManager::instance().playSirenAlarm(false);
    NetworkManager::instance().stopBleSosBeacon();
    SdLogger::instance().closeAudioEvidenceRecording();

    ESP_LOGI(TAG, "Entered SAFE state. Modem powered down.");
}

// -------------------------------------------------------------
// State: SAFETY_WATCH
// -------------------------------------------------------------
void StateMachine::handleStateWatch(const EventMessage &msg) {
    switch (msg.event) {
    case StateEvent::EVT_ENTER_SAFE_MODE:
    case StateEvent::EVT_CANCEL_ALARM:
        ESP_LOGI(TAG, "[SAFETY_WATCH] User confirmed safe / session finished.");
        cancelWatchSession();
        transitionTo(SystemState::SAFE);
        enterSafeState();
        break;

    case StateEvent::EVT_PERIODIC_WATCH_TICK: {
        ESP_LOGI(TAG, "[SAFETY_WATCH] Periodic location update timer fired.");
        // Send periodic location SMS to primary contact
        for (const auto &contact : getContacts()) {
            if (contact.isPrimary) {
                char prefix[128];
                std::snprintf(prefix, sizeof(prefix),
                              "[SAFETY WATCH UPDATE]\nWearer: %s\nStatus: Active Watch",
                              m_wearerName.c_str());
                SIM800L::instance().requestSendLocationSms(contact.phone.c_str(), prefix, 2);
                break;
            }
        }
        break;
    }

    case StateEvent::EVT_MANUAL_SOS_PRESS:
    case StateEvent::EVT_FALL_DETECTED:
    case StateEvent::EVT_VOICE_TRIGGERED:
        ESP_LOGW(TAG, "[SAFETY_WATCH] Escalating to EMERGENCY! (Event: %d, Shock: %.2f G)",
                 static_cast<int>(msg.event), msg.shockG);
        if (m_watchPeriodicTimer)
            xTimerStop(m_watchPeriodicTimer, 0);
        m_lastShockG = msg.shockG;
        transitionTo(SystemState::EMERGENCY);
        enterEmergencyState(msg.shockG);
        break;

    default:
        break;
    }
}

void StateMachine::enterWatchState() {
    m_watchStartTimeMs = esp_timer_get_time() / 1000;

    // 1. Power up SIM800L & register on cellular network
    SIM800L::instance().requestPowerUpAndRegister();

    // 2. Dispatch initial Safety Watch activation SMS to primary contact
    for (const auto &contact : getContacts()) {
        if (contact.isPrimary) {
            char prefix[160];
            std::snprintf(
                prefix, sizeof(prefix),
                "[SAFETY WATCH ACTIVE]\nWearer: %s has enabled Safety Watch (Night Walk Guard).",
                m_wearerName.c_str());
            SIM800L::instance().requestSendLocationSms(contact.phone.c_str(), prefix, 3);
            break;
        }
    }

    // 3. Start 3-minute periodic location SMS timer
    if (m_watchPeriodicTimer) {
        xTimerStart(m_watchPeriodicTimer, 0);
    }

    ESP_LOGI(TAG, "Entered SAFETY_WATCH state. Modem powering up & periodic timer running.");
}

void StateMachine::cancelWatchSession() {
    // Send cancellation / safe confirmation SMS
    for (const auto &contact : getContacts()) {
        if (contact.isPrimary) {
            char msg[160];
            std::snprintf(msg, sizeof(msg),
                          "[SAFETY WATCH ENDED]\nWearer %s has safely ended the Safety Watch "
                          "session. All is well.",
                          m_wearerName.c_str());
            SIM800L::instance().requestSendSms(contact.phone.c_str(), msg, 2);
            break;
        }
    }
}

// -------------------------------------------------------------
// State: EMERGENCY
// -------------------------------------------------------------
void StateMachine::handleStateEmergency(const EventMessage &msg) {
    switch (msg.event) {
    case StateEvent::EVT_CANCEL_ALARM:
        ESP_LOGI(TAG, "[EMERGENCY] User cancelled alarm / entered PIN.");
        cancelEmergencySession();
        transitionTo(SystemState::SAFE);
        enterSafeState();
        break;

    case StateEvent::EVT_GRACE_PERIOD_EXPIRED:
        ESP_LOGW(
            TAG,
            "[EMERGENCY] Grace confirmation window completed. Executing full SOS alert dispatch!");
        m_gracePeriodActive = false;
        m_graceRemainingSec = 0;
        dispatchEmergencyAlerts(m_lastShockG);
        break;

    case StateEvent::EVT_PERIODIC_EMERGENCY_TICK: {
        ESP_LOGI(TAG, "[EMERGENCY] Periodic 60s emergency location broadcast.");
        for (const auto &contact : getContacts()) {
            if (contact.isPrimary) {
                char prefix[128];
                std::snprintf(prefix, sizeof(prefix),
                              "[EMERGENCY LOCATION PING]\nWearer: %s\nSOS ACTIVE",
                              m_wearerName.c_str());
                SIM800L::instance().requestSendLocationSms(contact.phone.c_str(), prefix, 2);
                break;
            }
        }
        break;
    }

    default:
        break;
    }
}

void StateMachine::enterEmergencyState(float shockG) {
    m_lastShockG = shockG;
    m_gracePeriodActive = true;
    m_graceRemainingSec = 30; // Cancellable countdown before phone requests
    m_emergencyAlertDispatched = false;

    // 1. Immediately sound loud siren alarm on speaker
    AudioManager::instance().playSirenAlarm(true);

    // 2. Check Android phone connection
    SIM800L::instance().requestPowerUpAndRegister();

    // 3. Start Grace Timer (1-second tick)
    if (m_graceTimer) {
        xTimerStart(m_graceTimer, 0);
    }

    ESP_LOGW(TAG, "EMERGENCY grace confirmation window started (%" PRIu32 " seconds remaining)...",
             (uint32_t)m_graceRemainingSec);
}

void StateMachine::dispatchEmergencyAlerts(float shockG) {
    m_emergencyAlertDispatched = false;

    // 1. Trigger Loud High-Decibel Siren on ES8311 DAC
    AudioManager::instance().playSirenAlarm(true);

    // 2. Start MicroSD Blackbox Legal Audio Evidence Recording (WAV format)
    char incidentId[32];
    std::snprintf(incidentId, sizeof(incidentId), "INC_SOS_%lld", esp_timer_get_time() / 1000000);
    SdLogger::instance().startAudioEvidenceRecording(incidentId);

    // 3. Keep the authenticated phone service available during an SOS
    NetworkManager::instance().startBleSosBeacon("SHESHIELD_01", "CRITICAL_SOS");

    // 4. Send Structured Emergency SMS to ALL configured contacts
    for (const auto &contact : getContacts()) {
        char prefix[220];
        if (shockG > 0.5f) {
            std::snprintf(
                prefix, sizeof(prefix),
                "CRITICAL EMERGENCY ALERT!\nWearer: %s\nTrigger: Fall Shock (%.1fG Impact)",
                m_wearerName.c_str(), shockG);
        } else {
            std::snprintf(
                prefix, sizeof(prefix),
                "CRITICAL EMERGENCY ALERT!\nWearer: %s\nTrigger: SOS Button / Voice Keyword",
                m_wearerName.c_str());
        }

        if (SIM800L::instance().requestSendLocationSms(contact.phone.c_str(), prefix, 3))
            m_emergencyAlertDispatched = true;
    }

    // 5. Start 60-second high-rate emergency location timer
    if (m_emergencyPeriodicTimer) {
        xTimerStart(m_emergencyPeriodicTimer, 0);
    }

    ESP_LOGW(TAG, "Full Emergency SOS Protocol executed! Siren sounding, audio recording, phone "
                  "requests queued when connected.");
}

void StateMachine::cancelEmergencySession() {
    ESP_LOGI(TAG, "Cancelling Emergency State. Sending resolution SMS to contacts...");

    // Send Emergency Resolved SMS to contacts
    if (m_emergencyAlertDispatched) {
        for (const auto &contact : getContacts()) {
            char msg[180];
            std::snprintf(msg, sizeof(msg),
                          "[EMERGENCY RESOLVED]\nWearer %s has cancelled the distress alert and "
                          "confirmed safe.",
                          m_wearerName.c_str());
            SIM800L::instance().requestSendSms(contact.phone.c_str(), msg, 2);
        }
    }

    // Stop Siren, close audio evidence, stop emergency timer
    AudioManager::instance().playSirenAlarm(false);
    SdLogger::instance().closeAudioEvidenceRecording();
    NetworkManager::instance().stopBleSosBeacon();

    if (m_emergencyPeriodicTimer)
        xTimerStop(m_emergencyPeriodicTimer, 0);
    if (m_graceTimer)
        xTimerStop(m_graceTimer, 0);

    m_gracePeriodActive = false;
    m_emergencyAlertDispatched = false;
}

// -------------------------------------------------------------
// FreeRTOS Timer Callbacks
// -------------------------------------------------------------
void StateMachine::watchTimerCallback(TimerHandle_t xTimer) {
    StateMachine::instance().sendEvent(StateEvent::EVT_PERIODIC_WATCH_TICK);
}

void StateMachine::emergencyTimerCallback(TimerHandle_t xTimer) {
    StateMachine::instance().sendEvent(StateEvent::EVT_PERIODIC_EMERGENCY_TICK);
}

void StateMachine::graceTimerCallback(TimerHandle_t xTimer) {
    StateMachine &sm = StateMachine::instance();
    if (sm.m_gracePeriodActive) {
        if (sm.m_graceRemainingSec > 1) {
            sm.m_graceRemainingSec--;
        } else {
            xTimerStop(xTimer, 0);
            sm.sendEvent(StateEvent::EVT_GRACE_PERIOD_EXPIRED);
        }
    } else {
        xTimerStop(xTimer, 0);
    }
}

} // namespace SheShield
