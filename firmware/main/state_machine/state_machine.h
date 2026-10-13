#pragma once

#include "../drivers/sim800l/sim800l.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "freertos/timers.h"
#include <atomic>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace SheShield {

enum class SystemState {
    SAFE,         // Normal watch face, low power, SIM800L asleep, monitoring triggers
    SAFETY_WATCH, // Heightened alert mode, SIM800L on, SMS dispatch + periodic GPS pings
    EMERGENCY // Critical SOS: Confirmation window, siren, audio record, structured SMS, 60s pings
};

enum class StateEvent {
    EVT_NONE,
    EVT_ENTER_WATCH_MODE,        // User activated Safety Watch from UI or button
    EVT_ENTER_SAFE_MODE,         // Safe check-in confirmed, cancel session
    EVT_MANUAL_SOS_PRESS,        // Physical SOS button held or triple-press detected
    EVT_FALL_DETECTED,           // IMU detected high-G impact followed by stillness
    EVT_VOICE_TRIGGERED,         // ESP-SR detected "Help Me" / "Bachao" wake word
    EVT_GRACE_PERIOD_EXPIRED,    // Emergency confirmation countdown finished -> fire alert
    EVT_CANCEL_ALARM,            // User cancelled false alarm / confirmed safe via UI
    EVT_PERIODIC_WATCH_TICK,     // Timer fired for periodic Safety Watch location SMS
    EVT_PERIODIC_EMERGENCY_TICK, // Timer fired for high-rate emergency location SMS
    EVT_SIM_STATUS_CHANGED,      // SIM800L registration or CSQ changed
    EVT_BATTERY_LOW              // Low battery warning
};

struct EventMessage {
    StateEvent event;
    uint32_t payload;
    float shockG;
    int64_t timestamp_ms;
};

struct EmergencyContact {
    std::string name;
    std::string phone;
    std::string relation;
    bool isPrimary;
};

class StateMachine {
  public:
    static StateMachine &instance();

    void init();
    void sendEvent(StateEvent event, uint32_t payload = 0, float shockG = 0.0f);
    void sendEventFromISR(StateEvent event, BaseType_t *higherPriorityTaskWoken);

    SystemState getCurrentState() const { return m_currentState; }
    const char *getStateString() const;

    // Contact Management
    void addEmergencyContact(const std::string &name, const std::string &phone,
                             const std::string &relation, bool isPrimary = false);
    std::vector<EmergencyContact> getContacts() const;

    // State Change Observer Callback
    using StateChangeCallback = std::function<void(SystemState oldState, SystemState newState)>;
    void registerStateCallback(StateChangeCallback cb);

    // Emergency Grace Period & Status
    bool isEmergencyGraceActive() const { return m_gracePeriodActive; }
    uint32_t getGraceRemainingSec() const { return m_graceRemainingSec; }
    bool werePhoneAlertsQueued() const { return m_emergencyAlertDispatched; }
    uint32_t getWatchActiveDurationSec() const;

  private:
    StateMachine();
    ~StateMachine() = default;
    StateMachine(const StateMachine &) = delete;
    StateMachine &operator=(const StateMachine &) = delete;

    static void taskEntry(void *param);
    void run();

    // State Handlers
    void transitionTo(SystemState newState);
    void handleStateSafe(const EventMessage &msg);
    void handleStateWatch(const EventMessage &msg);
    void handleStateEmergency(const EventMessage &msg);

    // State Actions
    void enterSafeState();
    void enterWatchState();
    void enterEmergencyState(float shockG);
    void dispatchEmergencyAlerts(float shockG);
    void cancelWatchSession();
    void cancelEmergencySession();

    // Timers
    static void watchTimerCallback(TimerHandle_t xTimer);
    static void emergencyTimerCallback(TimerHandle_t xTimer);
    static void graceTimerCallback(TimerHandle_t xTimer);

    std::atomic<SystemState> m_currentState;
    QueueHandle_t m_eventQueue;
    TaskHandle_t m_taskHandle;
    StateChangeCallback m_stateCallback;

    TimerHandle_t m_watchPeriodicTimer;
    TimerHandle_t m_emergencyPeriodicTimer;
    TimerHandle_t m_graceTimer;

    std::vector<EmergencyContact> m_contacts;
    std::string m_wearerName;

    std::atomic<bool> m_gracePeriodActive;
    std::atomic<uint32_t> m_graceRemainingSec;
    int64_t m_watchStartTimeMs;
    float m_lastShockG;
    std::atomic<bool> m_emergencyAlertDispatched;
};

} // namespace SheShield
