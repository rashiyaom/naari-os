#include "a7672s.h"
#include <algorithm>
#include <cstdio>
#include <cstring>
#include <driver/uart.h>
#include <esp_log.h>
#include <esp_random.h>
#include <esp_system.h>
#include <esp_timer.h>
#include <sdkconfig.h>
#include <utility>
namespace SheShield {
static const char *TAG = "A7672S";
static constexpr uart_port_t kUart = UART_NUM_1;
static int64_t nowMs() { return esp_timer_get_time() / 1000; }
struct ModemLock {
    SemaphoreHandle_t mutex;
    explicit ModemLock(SemaphoreHandle_t m) : mutex(m) {
        if (mutex)
            xSemaphoreTake(mutex, portMAX_DELAY);
    }
    ~ModemLock() {
        if (mutex)
            xSemaphoreGive(mutex);
    }
};
class UartIo : public A76xx::Io {
  public:
    bool write(const char *p, size_t n) override {
        return uart_write_bytes(kUart, p, n) == static_cast<int>(n);
    }
    int readByte(uint32_t timeout) override {
        uint8_t c;
        return uart_read_bytes(kUart, &c, 1, std::max<TickType_t>(1, pdMS_TO_TICKS(timeout))) == 1
                   ? c
                   : -1;
    }
    int64_t nowMs() const override { return SheShield::nowMs(); }
};
A7672S &A7672S::instance() {
    static A7672S modem;
    return modem;
}
esp_err_t A7672S::init() {
#ifndef CONFIG_NAARI_A7672S_ENABLED
    return ESP_ERR_NOT_SUPPORTED;
#else
    if (initialized)
        return ESP_OK;
    // GPIO43/44 are UART0 console pins by default. The modem build uses USB console only.
#if CONFIG_ESP_CONSOLE_UART
    ESP_LOGE(TAG, "Modem requires USB console; UART console conflicts with modem wiring");
    return ESP_ERR_INVALID_STATE;
#endif
    if (CONFIG_NAARI_MODEM_TX_GPIO == CONFIG_NAARI_MODEM_RX_GPIO)
        return ESP_ERR_INVALID_ARG;
    const int reserved[] = {0,  1,  2,  3,  4,  5,  6,  7,  8,  9,  11, 12, 13,
                            14, 15, 16, 17, 19, 20, 21, 26, 27, 28, 29, 30, 31,
                            32, 33, 34, 35, 36, 37, 38, 40, 41, 42, 45, 46};
    for (int pin : reserved)
        if (CONFIG_NAARI_MODEM_TX_GPIO == pin || CONFIG_NAARI_MODEM_RX_GPIO == pin)
            return ESP_ERR_INVALID_ARG;
    mutex = xSemaphoreCreateMutex();
    queue = xQueueCreate(8, sizeof(Request));
    if (!mutex || !queue) {
        if (mutex)
            vSemaphoreDelete(mutex);
        if (queue)
            vQueueDelete(queue);
        mutex = nullptr;
        queue = nullptr;
        return ESP_ERR_NO_MEM;
    }
    uart_config_t config = {};
    config.baud_rate = CONFIG_NAARI_MODEM_BAUD;
    config.data_bits = UART_DATA_8_BITS;
    config.parity = UART_PARITY_DISABLE;
    config.stop_bits = UART_STOP_BITS_1;
    config.flow_ctrl = UART_HW_FLOWCTRL_DISABLE;
    config.source_clk = UART_SCLK_DEFAULT;
    esp_err_t err = uart_param_config(kUart, &config);
    if (err == ESP_OK)
        err = uart_set_pin(kUart, CONFIG_NAARI_MODEM_TX_GPIO, CONFIG_NAARI_MODEM_RX_GPIO,
                           UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE);
    if (err == ESP_OK)
        err = uart_driver_install(kUart, 4096, 0, 0, nullptr, 0);
    if (err != ESP_OK) {
        vSemaphoreDelete(mutex);
        vQueueDelete(queue);
        mutex = nullptr;
        queue = nullptr;
        return err;
    }
    report.csq = 99;
    report.regStatus = NetworkRegStatus::NOT_REGISTERED;
    report.powerState = SimPowerState::POWERING_ON;
    std::snprintf(report.statusString, sizeof(report.statusString), "Waiting for A7672S");
    sequence = (esp_random() & 0x7fffffff) + 1;
    initialized = true;
    if (xTaskCreate(taskEntry, "a7672s", 8192, this, 3, &task) != pdPASS) {
        initialized = false;
        uart_driver_delete(kUart);
        vSemaphoreDelete(mutex);
        vQueueDelete(queue);
        mutex = nullptr;
        queue = nullptr;
        return ESP_ERR_NO_MEM;
    }
    ESP_LOGI(TAG, "Direct modem UART: TX=%d RX=%d baud=%d; no power/reset GPIO driven",
             CONFIG_NAARI_MODEM_TX_GPIO, CONFIG_NAARI_MODEM_RX_GPIO, CONFIG_NAARI_MODEM_BAUD);
    return ESP_OK;
#endif
}
uint32_t A7672S::request(const char *type, const char *phone, const char *message) {
    if (!initialized || !type || !phone || !message || std::strlen(type) >= 20 ||
        std::strlen(message) >= 256)
        return 0;
    bool location = !std::strcmp(type, "location"), hangup = !std::strcmp(type, "hangup"),
         call = !std::strcmp(type, "call"), sms = !std::strcmp(type, "sms"),
         locatedSms = !std::strcmp(type, "location_sms");
    if (!location && !hangup && !call && !sms && !locatedSms)
        return 0;
    if ((call || sms || locatedSms) && !A76xx::validPhone(phone))
        return 0;
    if (sms || locatedSms) {
        std::vector<A76xx::SmsPart> parts;
        if (!A76xx::encodeSms(phone, message, 0, parts))
            return 0;
    }
    ModemLock lock(mutex);
    bool registered = report.regStatus == NetworkRegStatus::REGISTERED_HOME ||
                      report.regStatus == NetworkRegStatus::REGISTERED_ROAMING;
    if (report.powerState != SimPowerState::READY_IDLE || (!location && !hangup && !registered))
        return 0;
    Request r = {};
    sequence = (sequence + 1) & 0x7fffffffu;
    if (!sequence)
        sequence = 1;
    r.id = sequence;
    r.createdMs = nowMs();
    std::snprintf(r.type, sizeof(r.type), "%s", type);
    std::snprintf(r.phone, sizeof(r.phone), "%s", phone);
    std::snprintf(r.message, sizeof(r.message), "%s", message);
    BaseType_t sent = hangup ? xQueueSendToFront(queue, &r, 0) : xQueueSend(queue, &r, 0);
    if (sent != pdTRUE)
        return 0;
    auto &result = results[r.id % 32];
    result = {};
    result.id = r.id;
    result.state = PhoneRequestState::Queued;
    std::snprintf(result.detail, sizeof(result.detail), "Queued for LTE modem");
    return r.id;
}
PhoneResult A7672S::result(uint32_t id) const {
    ModemLock lock(mutex);
    return results[id % 32].id == id ? results[id % 32] : PhoneResult{};
}
uint32_t A7672S::lastRequestId() const {
    ModemLock lock(mutex);
    return sequence;
}
SimStatusReport A7672S::status() const {
    ModemLock lock(mutex);
    auto s = report;
    if (nowMs() - lastFixMs > 120000)
        s.location.valid = false;
    return s;
}
void A7672S::registerStatusCallback(SimStatusCallback cb) {
    ModemLock lock(mutex);
    callback = std::move(cb);
}
void A7672S::finish(uint32_t id, PhoneRequestState state, const char *detail) {
    ModemLock lock(mutex);
    auto &r = results[id % 32];
    r = {};
    r.id = id;
    r.state = state;
    std::snprintf(r.detail, sizeof(r.detail), "%s", detail);
}
void A7672S::publish() {
    SimStatusCallback cb;
    {
        ModemLock lock(mutex);
        cb = callback;
    }
    if (cb)
        cb(status());
}
void A7672S::taskEntry(void *self) { static_cast<A7672S *>(self)->run(); }
void A7672S::poll(A76xx::Protocol &modem) {
    auto pin = modem.command("AT+CPIN?", 3000, "+CPIN:");
    bool ready = false;
    for (auto &line : pin.lines)
        if (line == "+CPIN: READY" && pin.reply == A76xx::Reply::Ok)
            ready = true;
    int reg = ready ? modem.registration() : -1;
    int csq = modem.needsSync() ? 99 : modem.signal();
    {
        ModemLock lock(mutex);
        report.lastHeartbeatMs = nowMs();
        report.csq = csq;
        report.rssiDbm = csq == 99 ? 0 : -113 + 2 * csq;
        report.gprsAttached = false;
        report.regStatus = ready ? (reg >= 0 && reg <= 5 ? static_cast<NetworkRegStatus>(reg)
                                                         : NetworkRegStatus::UNKNOWN)
                                 : NetworkRegStatus::NO_SIM_CARD;
        const char *text = !ready                   ? "SIM missing, locked or unavailable"
                           : (reg == 1 || reg == 5) ? "LTE modem registered"
                           : reg == 3               ? "Network registration denied"
                                                    : "Searching for cellular network";
        std::snprintf(report.statusString, sizeof(report.statusString), "%s", text);
        std::snprintf(report.operatorName, sizeof(report.operatorName), "A7672S cellular");
    }
    A76xx::Fix fix;
    if (!modem.needsSync() && modem.location(fix)) {
        ModemLock lock(mutex);
        report.location = {};
        report.location.valid = true;
        report.location.latitude = fix.latitude;
        report.location.longitude = fix.longitude;
        std::snprintf(report.location.timestamp, sizeof(report.location.timestamp), "%s",
                      fix.utc.c_str());
        lastFixMs = nowMs();
    }
    publish();
}
void A7672S::handle(const Request &r, A76xx::Protocol &modem) {
    if (nowMs() - r.createdMs > 30000) {
        finish(r.id, PhoneRequestState::Failed, "Request expired; no action taken");
        return;
    }
    finish(r.id, PhoneRequestState::Received, "Processing on LTE modem");
    if (!std::strcmp(r.type, "hangup")) {
        bool ok = modem.hangup();
        finish(r.id, ok ? PhoneRequestState::CallStarted : PhoneRequestState::Failed,
               ok ? "Hang-up accepted" : "Hang-up failed");
        return;
    }
    if (!std::strcmp(r.type, "location") || !std::strcmp(r.type, "location_sms")) {
        A76xx::Fix fix;
        if (modem.location(fix)) {
            ModemLock lock(mutex);
            report.location = {};
            report.location.valid = true;
            report.location.latitude = fix.latitude;
            report.location.longitude = fix.longitude;
            std::snprintf(report.location.timestamp, sizeof(report.location.timestamp), "%s",
                          fix.utc.c_str());
            lastFixMs = nowMs();
        }
        if (!std::strcmp(r.type, "location")) {
            finish(r.id,
                   status().location.valid ? PhoneRequestState::Located : PhoneRequestState::Failed,
                   status().location.valid ? "GNSS coordinates available"
                                           : "No GNSS fix; try outdoors");
            return;
        }
    }
    if (modem.needsSync()) {
        finish(r.id, PhoneRequestState::Failed, "UART response lost; no automatic retry");
        return;
    }
    int reg = modem.registration();
    if (reg != 1 && reg != 5) {
        finish(r.id, PhoneRequestState::Failed, "Modem is not registered");
        return;
    }
    if (!std::strcmp(r.type, "call")) {
        auto reply = modem.dial(r.phone);
        finish(r.id,
               reply == A76xx::Reply::Ok ? PhoneRequestState::CallStarted
                                         : PhoneRequestState::Failed,
               reply == A76xx::Reply::Ok        ? "Dial command accepted; not proof of answer"
               : reply == A76xx::Reply::Timeout ? "Call outcome unknown; do not retry blindly"
                                                : "Call rejected by modem");
        return;
    }
    std::string text = r.message;
    if (!std::strcmp(r.type, "location_sms")) {
        auto s = status();
        char coordinates[144];
        if (s.location.valid)
            std::snprintf(coordinates, sizeof(coordinates),
                          "\nGNSS (cached <=120s): https://maps.google.com/?q=%.6f,%.6f",
                          s.location.latitude, s.location.longitude);
        else
            std::snprintf(coordinates, sizeof(coordinates),
                          "\nLocation unavailable (no GNSS fix).");
        text += coordinates;
    }
    {
        ModemLock lock(mutex);
        report.lastSmsStatus = SmsDeliveryStatus::SMS_IN_PROGRESS;
    }
    auto outcome = modem.sendSms(r.phone, text, static_cast<uint8_t>(r.id));
    bool accepted = outcome.state == A76xx::SendState::Accepted;
    char detail[72];
    if (accepted)
        std::snprintf(detail, sizeof(detail), "%u part(s) accepted; delivery unconfirmed",
                      outcome.accepted);
    else if (outcome.state == A76xx::SendState::Rejected)
        std::snprintf(detail, sizeof(detail), "SMS rejected before submission");
    else
        std::snprintf(detail, sizeof(detail), "%u/%u parts accepted; outcome uncertain, no retry",
                      outcome.accepted, outcome.total);
    {
        ModemLock lock(mutex);
        report.lastSmsStatus =
            accepted ? SmsDeliveryStatus::SMS_SENT : SmsDeliveryStatus::SMS_FAILED_MODEM_ERROR;
        if (accepted)
            ++report.smsSentCount;
        else
            ++report.smsFailedCount;
    }
    finish(r.id, accepted ? PhoneRequestState::Sent : PhoneRequestState::Failed, detail);
    publish();
}
void A7672S::run() {
    UartIo uart;
    A76xx::Protocol modem(uart);
    bool ready = false;
    int64_t lastPoll = 0, lastProbe = -10000;
    while (true) {
        if (!ready || modem.needsSync()) {
            if (nowMs() - lastProbe < 10000) {
                vTaskDelay(pdMS_TO_TICKS(100));
                continue;
            }
            lastProbe = nowMs();
            ready = modem.initialize();
            {
                ModemLock lock(mutex);
                report.powerState = ready ? SimPowerState::READY_IDLE : SimPowerState::ERROR_STATE;
                if (!ready) {
                    report.regStatus = NetworkRegStatus::NOT_REGISTERED;
                    report.csq = 99;
                    std::snprintf(report.statusString, sizeof(report.statusString),
                                  "Check A7672S power / UART");
                }
            }
            if (!ready) {
                publish();
                continue;
            }
            lastPoll = 0;
        }
        if (nowMs() - lastPoll >= 10000 || lastPoll == 0) {
            poll(modem);
            lastPoll = nowMs();
        }
        Request r;
        if (xQueueReceive(queue, &r, pdMS_TO_TICKS(100)) == pdTRUE) {
            if (modem.needsSync())
                finish(r.id, PhoneRequestState::Failed,
                       "Modem resynchronizing; action not repeated");
            else
                handle(r, modem);
        }
    }
}
} // namespace SheShield
