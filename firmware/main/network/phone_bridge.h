#pragma once
#include <cstdint>
#include <esp_err.h>
#include <string>
namespace SheShield {
enum class PhoneRequestState {
    Unknown,
    Queued,
    Received,
    Sent,
    Located,
    Delivered,
    CallStarted,
    NeedsPhone,
    Failed
};
struct PhoneResult {
    uint32_t id = 0;
    PhoneRequestState state = PhoneRequestState::Unknown;
    char detail[72] = {};
};
struct PhoneLinkStatus {
    bool connected = false;
    bool ready = false;
    bool smsAllowed = false;
    bool callsAllowed = false;
    bool locationAllowed = false;
    bool locationValid = false;
    double latitude = 0, longitude = 0;
    int64_t locationTimeMs = 0;
};
// UUIDs: service d973f2e0-b19e-11ee-a506-0242ac120002; requests e1; replies e2.
// Requests are read in full after a tiny notification, so MTU never truncates
// JSON.
class PhoneBridge {
  public:
    static PhoneBridge &instance();
    esp_err_t init();
    void openPairing();
    bool isPairing() const;
    uint32_t passkey() const;
    void forgetPhone();
    PhoneLinkStatus status() const;
    uint32_t request(const char *type, const char *phone = "", const char *message = "");
    PhoneResult result(uint32_t id) const;
    uint32_t lastRequestId() const;
    std::string guardianName(unsigned index) const;
    std::string guardianPhone(unsigned index) const;
    std::string pendingJson() const;
    bool acceptReply(const char *json);
    void tick();

  private:
    PhoneBridge() = default;
};
} // namespace SheShield
