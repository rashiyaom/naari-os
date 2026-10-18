#pragma once
#include "../sim800l/sim800l.h"
#include "modem_protocol.h"
#include <atomic>
namespace SheShield {
class A7672S {
  public:
    static A7672S &instance();
    esp_err_t init();
    uint32_t request(const char *type, const char *phone = "", const char *message = "");
    PhoneResult result(uint32_t id) const;
    uint32_t lastRequestId() const;
    SimStatusReport status() const;
    void registerStatusCallback(SimStatusCallback callback);

  private:
    A7672S() = default;
    struct Request {
        uint32_t id;
        int64_t createdMs;
        char type[20], phone[32], message[256];
    };
    static void taskEntry(void *self);
    void run();
    void finish(uint32_t id, PhoneRequestState state, const char *detail);
    void publish();
    void poll(A76xx::Protocol &modem);
    void handle(const Request &request, A76xx::Protocol &modem);
    QueueHandle_t queue = nullptr;
    SemaphoreHandle_t mutex = nullptr;
    TaskHandle_t task = nullptr;
    SimStatusReport report{};
    PhoneResult results[32]{};
    SimStatusCallback callback;
    uint32_t sequence = 0;
    std::atomic<bool> initialized{false};
    int64_t lastFixMs = 0;
};
} // namespace SheShield
