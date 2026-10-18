#include "phone_bridge.h"
#include "json_frame.h"
#include "watch_sync.h"
#include <algorithm>
#include <cJSON.h>
#include <cmath>
#include <cstring>
#include <esp_log.h>
#include <esp_random.h>
#include <esp_timer.h>
#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>
#include <freertos/task.h>
#include <nvs.h>
extern "C" {
#include "host/ble_gatt.h"
#include "host/ble_hs.h"
#include "host/util/util.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
void ble_store_config_init(void);
}
namespace SheShield {
static const char *TAG = "Phone_BLE";
static SemaphoreHandle_t mutex;
struct PhoneLock {
    PhoneLock() { xSemaphoreTake(mutex, portMAX_DELAY); }
    ~PhoneLock() { xSemaphoreGive(mutex); }
};
static PhoneLinkStatus link;
static uint16_t connection = BLE_HS_CONN_HANDLE_NONE, requestHandle;
static uint8_t ownAddressType;
static bool subscribed = false, initialized = false;
static int64_t pairUntil = 0, lastNotify = 0, lastHeartbeat = 0;
static uint32_t pairingCode = 0, sequence = 0;
struct Pending {
    uint32_t id;
    int64_t created;
    std::string json;
    std::string type;
};
static Pending pending[16];
static unsigned head = 0, count = 0;
static PhoneResult results[32];
static std::string names[2] = {"Guardian 1", "Guardian 2"}, phones[2];
// BLE UUID byte order is little endian.
static const ble_uuid128_t serviceUuid = BLE_UUID128_INIT(
    0x02, 0x00, 0x12, 0xac, 0x42, 0x02, 0x06, 0xa5, 0xee, 0x11, 0x9e, 0xb1, 0xe0, 0xf2, 0x73, 0xd9);
static const ble_uuid128_t requestUuid = BLE_UUID128_INIT(
    0x02, 0x00, 0x12, 0xac, 0x42, 0x02, 0x06, 0xa5, 0xee, 0x11, 0x9e, 0xb1, 0xe1, 0xf2, 0x73, 0xd9);
static const ble_uuid128_t replyUuid = BLE_UUID128_INIT(
    0x02, 0x00, 0x12, 0xac, 0x42, 0x02, 0x06, 0xa5, 0xee, 0x11, 0x9e, 0xb1, 0xe2, 0xf2, 0x73, 0xd9);
static const char *stringValue(cJSON *o, const char *key) {
    auto v = cJSON_GetObjectItemCaseSensitive(o, key);
    return cJSON_IsString(v) ? v->valuestring : "";
}
static bool phoneValid(const char *phone) {
    size_t n = std::strlen(phone);
    if (n < 3 || n > 20)
        return false;
    for (size_t i = 0; i < n; ++i)
        if (!(phone[i] >= '0' && phone[i] <= '9') && !(i == 0 && phone[i] == '+'))
            return false;
    return true;
}
static void finish(uint32_t id, PhoneRequestState state, const char *detail) {
    auto &r = results[id % 32];
    r.id = id;
    r.state = state;
    std::snprintf(r.detail, sizeof(r.detail), "%s", detail);
}
static int access(uint16_t conn, uint16_t, ble_gatt_access_ctxt *ctx, void *) {
    ble_gap_conn_desc desc;
    if (ble_gap_conn_find(conn, &desc) || !desc.sec_state.encrypted ||
        !desc.sec_state.authenticated)
        return BLE_ATT_ERR_INSUFFICIENT_AUTHEN;
    if (ctx->op == BLE_GATT_ACCESS_OP_READ_CHR) {
        auto json = PhoneBridge::instance().pendingJson();
        return os_mbuf_append(ctx->om, json.data(), json.size()) == 0
                   ? 0
                   : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    uint16_t n = OS_MBUF_PKTLEN(ctx->om);
    if (!n || n > 480)
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    char buffer[481];
    if (os_mbuf_copydata(ctx->om, 0, n, buffer))
        return BLE_ATT_ERR_UNLIKELY;
    static JsonFrame frame;
    std::string json;
    int state = frame.accept(reinterpret_cast<uint8_t *>(buffer), n, json);
    if (state < 0)
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    if (!state)
        return 0;
    return PhoneBridge::instance().acceptReply(json.c_str()) ? 0 : BLE_ATT_ERR_UNLIKELY;
}
static ble_gatt_chr_def characteristics[3];
static ble_gatt_svc_def services[3];
static void advertise();
static int gapEvent(ble_gap_event *event, void *) {
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status == 0) {
            {
                PhoneLock l;
                connection = event->connect.conn_handle;
                link = {};
                subscribed = false;
            }
            ble_gap_security_initiate(event->connect.conn_handle);
        } else
            advertise();
        break;
    case BLE_GAP_EVENT_DISCONNECT: {
        WatchSync::disconnected();
        PhoneLock l;
        connection = BLE_HS_CONN_HANDLE_NONE;
        link = {};
        subscribed = false;
        for (unsigned i = 0; i < count; ++i)
            finish(pending[(head + i) % 16].id, PhoneRequestState::Failed,
                   "Phone disconnected; request not confirmed");
        count = 0;
    }
        advertise();
        break;
    case BLE_GAP_EVENT_ENC_CHANGE: {
        ble_gap_conn_desc d;
        if (!event->enc_change.status && !ble_gap_conn_find(event->enc_change.conn_handle, &d) &&
            d.sec_state.encrypted && d.sec_state.authenticated) {
            PhoneLock l;
            link.connected = true;
            pairUntil = 0;
            WatchSync::connected(event->enc_change.conn_handle);
            ESP_LOGI(TAG, "Authenticated Android link established");
        } else
            ble_gap_terminate(event->enc_change.conn_handle, BLE_ERR_AUTH_FAIL);
        break;
    }
    case BLE_GAP_EVENT_PASSKEY_ACTION: {
        if (!PhoneBridge::instance().isPairing()) {
            ble_gap_terminate(event->passkey.conn_handle, BLE_ERR_AUTH_FAIL);
            break;
        }
        ble_sm_io io = {};
        io.action = event->passkey.params.action;
        if (io.action != BLE_SM_IOACT_DISP) {
            ble_gap_terminate(event->passkey.conn_handle, BLE_ERR_AUTH_FAIL);
            break;
        }
        io.passkey = PhoneBridge::instance().passkey();
        ble_sm_inject_io(event->passkey.conn_handle, &io);
        break;
    }
    case BLE_GAP_EVENT_REPEAT_PAIRING:
        if (PhoneBridge::instance().isPairing()) {
            ble_gap_conn_desc d;
            if (!ble_gap_conn_find(event->repeat_pairing.conn_handle, &d))
                ble_store_util_delete_peer(&d.peer_id_addr);
            return BLE_GAP_REPEAT_PAIRING_RETRY;
        }
        return BLE_GAP_REPEAT_PAIRING_IGNORE;
    case BLE_GAP_EVENT_SUBSCRIBE:
        if (event->subscribe.attr_handle == requestHandle) {
            PhoneLock l;
            subscribed = event->subscribe.cur_notify;
        }
        break;
    case BLE_GAP_EVENT_ADV_COMPLETE:
        advertise();
        break;
    default:
        break;
    }
    return 0;
}
static void advertise() {
    ble_hs_adv_fields f = {};
    f.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    f.uuids128 = const_cast<ble_uuid128_t *>(&serviceUuid);
    f.num_uuids128 = 1;
    f.uuids128_is_complete = 1;
    if (ble_gap_adv_set_fields(&f))
        return;
    ble_hs_adv_fields scan = {};
    auto name = ble_svc_gap_device_name();
    scan.name = (uint8_t *)name;
    scan.name_len = std::strlen(name);
    scan.name_is_complete = 1;
    ble_gap_adv_rsp_set_fields(&scan);
    ble_gap_adv_params params = {};
    params.conn_mode = BLE_GAP_CONN_MODE_UND;
    params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    ble_gap_adv_start(ownAddressType, nullptr, BLE_HS_FOREVER, &params, gapEvent, nullptr);
}
static void synced() {
    if (!ble_hs_util_ensure_addr(0) && !ble_hs_id_infer_auto(0, &ownAddressType))
        advertise();
}
PhoneBridge &PhoneBridge::instance() {
    static PhoneBridge p;
    return p;
}
esp_err_t PhoneBridge::init() {
    if (initialized)
        return ESP_OK;
    mutex = xSemaphoreCreateMutex();
    if (!mutex)
        return ESP_ERR_NO_MEM;
    sequence = (esp_random() & 0x7fffffff) | 1;
    nvs_handle_t h;
    if (nvs_open("phone_profile", NVS_READONLY, &h) == ESP_OK) {
        for (unsigned i = 0; i < 2; ++i) {
            char key[12], b[48];
            size_t n = sizeof(b);
            std::snprintf(key, sizeof(key), "name%u", i);
            if (nvs_get_str(h, key, b, &n) == ESP_OK)
                names[i] = b;
            n = sizeof(b);
            std::snprintf(key, sizeof(key), "phone%u", i);
            if (nvs_get_str(h, key, b, &n) == ESP_OK)
                phones[i] = b;
        }
        nvs_close(h);
    }
    esp_err_t err = nimble_port_init();
    if (err != ESP_OK)
        return err;
    ble_hs_cfg.sync_cb = synced;
    ble_hs_cfg.store_status_cb = ble_store_util_status_rr;
    ble_hs_cfg.sm_io_cap = BLE_HS_IO_DISPLAY_ONLY;
    ble_hs_cfg.sm_bonding = 1;
    ble_hs_cfg.sm_mitm = 1;
    ble_hs_cfg.sm_sc = 1;
    ble_hs_cfg.sm_our_key_dist = BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;
    ble_hs_cfg.sm_their_key_dist = BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;
    ble_svc_gap_init();
    ble_svc_gatt_init();
    ble_svc_gap_device_name_set("Naari Watch");
    characteristics[0].uuid = &requestUuid.u;
    characteristics[0].access_cb = access;
    characteristics[0].flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY |
                               BLE_GATT_CHR_F_READ_ENC | BLE_GATT_CHR_F_READ_AUTHEN;
    characteristics[0].val_handle = &requestHandle;
    characteristics[1].uuid = &replyUuid.u;
    characteristics[1].access_cb = access;
    characteristics[1].flags =
        BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_ENC | BLE_GATT_CHR_F_WRITE_AUTHEN;
    services[0].type = BLE_GATT_SVC_TYPE_PRIMARY;
    services[0].uuid = &serviceUuid.u;
    services[0].characteristics = characteristics;
    if (!WatchSync::init())
        return ESP_ERR_NO_MEM;
    services[1] = *WatchSync::service();
    if (ble_gatts_count_cfg(services) || ble_gatts_add_svcs(services))
        return ESP_FAIL;
    ble_att_set_preferred_mtu(512);
    ble_store_config_init();
    initialized = true;
    nimble_port_freertos_init([](void *) {
        nimble_port_run();
        nimble_port_freertos_deinit();
    });
    xTaskCreate(
        [](void *) {
            while (true) {
                PhoneBridge::instance().tick();
                vTaskDelay(pdMS_TO_TICKS(500));
            }
        },
        "phone_requests", 4096, nullptr, 3, nullptr);
    ESP_LOGI(TAG, "Real BLE phone service advertising; open Phone Link to pair");
    return ESP_OK;
}
void PhoneBridge::openPairing() {
    if (!mutex)
        return;
    PhoneLock l;
    pairingCode = 100000 + esp_random() % 900000;
    pairUntil = esp_timer_get_time() + 60000000;
}
bool PhoneBridge::isPairing() const {
    if (!mutex)
        return false;
    PhoneLock l;
    return pairUntil > esp_timer_get_time();
}
uint32_t PhoneBridge::passkey() const {
    if (!mutex)
        return 0;
    PhoneLock l;
    return pairingCode;
}
void PhoneBridge::forgetPhone() {
    if (!mutex)
        return;
    uint16_t c;
    {
        PhoneLock l;
        c = connection;
        link = {};
    }
    if (c != BLE_HS_CONN_HANDLE_NONE)
        ble_gap_terminate(c, BLE_ERR_REM_USER_CONN_TERM);
    ble_store_clear();
}
PhoneLinkStatus PhoneBridge::status() const {
    if (!mutex)
        return {};
    PhoneLock l;
    auto s = link;
    if (esp_timer_get_time() / 1000 - s.locationTimeMs > 120000)
        s.locationValid = false;
    return s;
}
uint32_t PhoneBridge::request(const char *type, const char *phone, const char *message) {
    if (!mutex || !type || !phone || !message || std::strlen(message) > 256)
        return 0;
    if (std::strcmp(type, "location") && !phoneValid(phone))
        return 0;
    if (std::strcmp(type, "sms") && std::strcmp(type, "location_sms") &&
        std::strcmp(type, "call") && std::strcmp(type, "location"))
        return 0;
    PhoneLock l;
    if (!link.connected || !link.ready || count == 16)
        return 0;
    sequence = (sequence + 1) & 0x7fffffffu;
    if (!sequence) sequence = 1;
    uint32_t id = sequence;
    cJSON *o = cJSON_CreateObject();
    if (!o)
        return 0;
    cJSON_AddNumberToObject(o, "v", 1);
    cJSON_AddNumberToObject(o, "id", id);
    cJSON_AddStringToObject(o, "type", type);
    cJSON_AddStringToObject(o, "phone", phone);
    cJSON_AddStringToObject(o, "message", message);
    char *json = cJSON_PrintUnformatted(o);
    cJSON_Delete(o);
    if (!json)
        return 0;
    if (std::strlen(json) > 480) {
        cJSON_free(json);
        return 0;
    }
    pending[(head + count) % 16] = {id, esp_timer_get_time() / 1000, json, type};
    ++count;
    cJSON_free(json);
    finish(id, PhoneRequestState::Queued, "Waiting for Android phone");
    lastNotify = 0;
    return id;
}
PhoneResult PhoneBridge::result(uint32_t id) const {
    if (!mutex)
        return {};
    PhoneLock l;
    auto r = results[id % 32];
    return r.id == id ? r : PhoneResult{};
}
uint32_t PhoneBridge::lastRequestId() const {
    if (!mutex)
        return 0;
    PhoneLock l;
    return sequence;
}
std::string PhoneBridge::guardianName(unsigned i) const {
    if (!mutex || i > 1)
        return "Guardian";
    PhoneLock l;
    return names[i];
}
std::string PhoneBridge::guardianPhone(unsigned i) const {
    if (!mutex || i > 1)
        return "";
    PhoneLock l;
    return phones[i];
}
std::string PhoneBridge::pendingJson() const {
    if (!mutex)
        return "{}";
    PhoneLock l;
    return count ? pending[head].json : "{}";
}
bool PhoneBridge::acceptReply(const char *json) {
    if (!json || std::strlen(json) > 480)
        return false;
    cJSON *o = cJSON_Parse(json);
    if (!o)
        return false;
    auto v = cJSON_GetObjectItem(o, "v");
    if (!cJSON_IsObject(o) || !cJSON_IsNumber(v) || v->valuedouble != 1) {
        cJSON_Delete(o);
        return false;
    }
    PhoneLock l;
    const char *op = stringValue(o, "op");
    if (!std::strcmp(op, "hello")) {
        link.ready = true;
        lastHeartbeat = esp_timer_get_time() / 1000;
        link.smsAllowed = cJSON_IsTrue(cJSON_GetObjectItem(o, "sms"));
        link.callsAllowed = cJSON_IsTrue(cJSON_GetObjectItem(o, "calls"));
        link.locationAllowed = cJSON_IsTrue(cJSON_GetObjectItem(o, "location"));
    } else if (!std::strcmp(op, "profile")) {
        std::string nextNames[2], nextPhones[2];
        for (unsigned i = 0; i < 2; ++i) {
            char key[12];
            std::snprintf(key, sizeof(key), "phone%u", i);
            auto phone = stringValue(o, key);
            if (*phone && !phoneValid(phone)) {
                cJSON_Delete(o);
                return false;
            }
            nextPhones[i] = phone;
            std::snprintf(key, sizeof(key), "name%u", i);
            nextNames[i] = std::string(stringValue(o, key)).substr(0, 32);
            if (nextNames[i].empty())
                nextNames[i] = "Guardian";
        }
        nvs_handle_t h;
        esp_err_t err = nvs_open("phone_profile", NVS_READWRITE, &h);
        if (err != ESP_OK) {
            cJSON_Delete(o);
            return false;
        }
        for (unsigned i = 0; i < 2 && err == ESP_OK; ++i) {
            char key[12];
            std::snprintf(key, sizeof(key), "phone%u", i);
            err = nvs_set_str(h, key, nextPhones[i].c_str());
            std::snprintf(key, sizeof(key), "name%u", i);
            if (err == ESP_OK)
                err = nvs_set_str(h, key, nextNames[i].c_str());
        }
        if (err == ESP_OK)
            err = nvs_commit(h);
        nvs_close(h);
        if (err != ESP_OK) {
            cJSON_Delete(o);
            return false;
        }
        for (unsigned i = 0; i < 2; ++i) {
            phones[i] = nextPhones[i];
            names[i] = nextNames[i];
        }
    } else {
        auto item = cJSON_GetObjectItem(o, "id");
        if (!cJSON_IsNumber(item) || !std::isfinite(item->valuedouble) ||
            std::floor(item->valuedouble) != item->valuedouble || item->valuedouble < 1 ||
            item->valuedouble > UINT32_MAX) {
            cJSON_Delete(o);
            return false;
        }
        uint32_t id = static_cast<uint32_t>(item->valuedouble);
        // Only the current request (or a late delivery report for a known sent SMS)
        // can advance state.
        auto prior = results[id % 32];
        bool current = count && pending[head].id == id;
        const char *s = stringValue(o, "status");
        PhoneRequestState state = PhoneRequestState::Failed;
        if (!std::strcmp(s, "received"))
            state = PhoneRequestState::Received;
        else if (!std::strcmp(s, "sent"))
            state = PhoneRequestState::Sent;
        else if (!std::strcmp(s, "located"))
            state = PhoneRequestState::Located;
        else if (!std::strcmp(s, "delivered"))
            state = PhoneRequestState::Delivered;
        else if (!std::strcmp(s, "calling"))
            state = PhoneRequestState::CallStarted;
        else if (!std::strcmp(s, "needs_phone"))
            state = PhoneRequestState::NeedsPhone;
        else if (std::strcmp(s, "failed")) {
            cJSON_Delete(o);
            return false;
        }
        bool terminal = state != PhoneRequestState::Received;
        bool duplicate = prior.id == id && prior.state == state && terminal;
        bool delivery = prior.id == id && prior.state == PhoneRequestState::Sent &&
                        state == PhoneRequestState::Delivered;
        if (!current && !duplicate && !delivery) {
            cJSON_Delete(o);
            return false;
        }
        if (!current && duplicate) {
            cJSON_Delete(o);
            return true;
        }
        if (current) {
            const auto &kind = pending[head].type;
            if ((state == PhoneRequestState::Sent || state == PhoneRequestState::Delivered) &&
                kind != "sms" && kind != "location_sms") {
                cJSON_Delete(o);
                return false;
            }
            if (state == PhoneRequestState::Located && kind != "location") {
                cJSON_Delete(o);
                return false;
            }
            if (state == PhoneRequestState::CallStarted && kind != "call") {
                cJSON_Delete(o);
                return false;
            }
        }
        auto lat = cJSON_GetObjectItem(o, "lat"), lon = cJSON_GetObjectItem(o, "lon");
        bool locationValid = cJSON_IsNumber(lat) && cJSON_IsNumber(lon) &&
                             std::isfinite(lat->valuedouble) && std::isfinite(lon->valuedouble) &&
                             std::abs(lat->valuedouble) <= 90 && std::abs(lon->valuedouble) <= 180;
        if (state == PhoneRequestState::Located && !locationValid) {
            cJSON_Delete(o);
            return false;
        }
        finish(id, state, stringValue(o, "detail"));
        if (locationValid) {
            link.locationValid = true;
            link.latitude = lat->valuedouble;
            link.longitude = lon->valuedouble;
            link.locationTimeMs = esp_timer_get_time() / 1000;
        }
        if (current && state != PhoneRequestState::Received) {
            pending[head].json.clear();
            head = (head + 1) % 16;
            --count;
            lastNotify = 0;
        }
    }
    cJSON_Delete(o);
    return true;
}
void PhoneBridge::tick() {
    if (!mutex)
        return;
    uint16_t conn;
    uint32_t id = 0;
    int64_t now = esp_timer_get_time() / 1000;
    {
        PhoneLock l;
        conn = connection;
        if (link.ready && now - lastHeartbeat > 45000)
            link.ready = false;
        if (count && now - pending[head].created > (pending[head].type == "call" ? 30000 : 90000)) {
            finish(pending[head].id, PhoneRequestState::Failed,
                   "Phone request timed out; outcome unknown");
            pending[head].json.clear();
            head = (head + 1) % 16;
            --count;
        }
        if (link.connected && link.ready && subscribed && count && now - lastNotify > 1500) {
            id = pending[head].id;
            lastNotify = now;
        }
    }
    if (id && conn != BLE_HS_CONN_HANDLE_NONE) {
        auto b = ble_hs_mbuf_from_flat(&id, sizeof(id));
        if (b)
            ble_gatts_notify_custom(conn, requestHandle, b);
    }
}
} // namespace SheShield
