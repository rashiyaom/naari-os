#include "watch_sync.h"
#include "../drivers/audio_es7210_es8311/audio_manager.h"
#include "../drivers/rtc_pcf85063/rtc_pcf85063.h"
#include "../storage/memo_store.h"
#include "../ui/ui_manager.h"
#include "json_frame.h"
#include "phone_bridge.h"
#include <cJSON.h>
#include <cmath>
#include <cstring>
#include <ctime>
#include <esp_random.h>
#include <esp_timer.h>
#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>
#include <freertos/task.h>
#include <sys/time.h>
extern "C" {
#include "host/ble_hs.h"
}
namespace SheShield {
namespace {
SemaphoreHandle_t lock;
QueueHandle_t commands;
uint16_t conn = BLE_HS_CONN_HANDLE_NONE, eventHandle;
uint32_t boot, seq = 0;
std::string response = "{}", selectedId;
size_t selectedOffset = 0;
int64_t findUntil = 0;
struct Command {
    uint16_t conn;
    char json[481];
};
struct Guard {
    Guard() { xSemaphoreTake(lock, portMAX_DELAY); }
    ~Guard() { xSemaphoreGive(lock); }
};
const ble_uuid128_t serviceUuid = BLE_UUID128_INIT(0x02, 0x00, 0x12, 0xac, 0x42, 0x02, 0x06, 0xa5,
                                                   0xee, 0x11, 0x9e, 0xb1, 0xf0, 0xf2, 0x73, 0xd9);
const ble_uuid128_t cmdUuid = BLE_UUID128_INIT(0x02, 0x00, 0x12, 0xac, 0x42, 0x02, 0x06, 0xa5, 0xee,
                                               0x11, 0x9e, 0xb1, 0xf1, 0xf2, 0x73, 0xd9);
const ble_uuid128_t eventUuid = BLE_UUID128_INIT(0x02, 0x00, 0x12, 0xac, 0x42, 0x02, 0x06, 0xa5,
                                                 0xee, 0x11, 0x9e, 0xb1, 0xf2, 0xf2, 0x73, 0xd9);
const ble_uuid128_t dataUuid = BLE_UUID128_INIT(0x02, 0x00, 0x12, 0xac, 0x42, 0x02, 0x06, 0xa5,
                                                0xee, 0x11, 0x9e, 0xb1, 0xf3, 0xf2, 0x73, 0xd9);
ble_gatt_chr_def chars[4];
ble_gatt_svc_def svc;
std::string stringify(cJSON *json) {
    char *s = cJSON_PrintUnformatted(json);
    std::string result = s ? s : "{}";
    cJSON_free(s);
    return result;
}
const char *str(cJSON *o, const char *key) {
    auto v = cJSON_GetObjectItemCaseSensitive(o, key);
    return cJSON_IsString(v) ? v->valuestring : "";
}
bool integer(cJSON *o, const char *key, int64_t &out, int64_t low, int64_t high) {
    auto v = cJSON_GetObjectItemCaseSensitive(o, key);
    if (!cJSON_IsNumber(v) || !std::isfinite(v->valuedouble) ||
        std::floor(v->valuedouble) != v->valuedouble || v->valuedouble < low ||
        v->valuedouble > high)
        return false;
    out = v->valuedouble;
    return true;
}
cJSON *status() {
    auto state = UiManager::instance().syncSettings();
    auto sm = StateMachine::instance().getCurrentState();
    auto o = cJSON_CreateObject();
    cJSON_AddNumberToObject(o, "boot_id", boot);
    cJSON_AddNumberToObject(o, "seq", ++seq);
    cJSON_AddNumberToObject(o, "battery", state.battery);
    cJSON_AddNumberToObject(o, "wallpaper", state.wallpaper);
    cJSON_AddNumberToObject(o, "brightness", state.brightness);
    cJSON_AddNumberToObject(o, "volume", state.volume);
    cJSON_AddBoolToObject(o, "fall_enabled", state.fall);
    cJSON_AddStringToObject(o, "mode",
                            sm == SystemState::SAFE        ? "SAFE"
                            : sm == SystemState::EMERGENCY ? "EMERGENCY"
                                                           : "WATCH");
    cJSON_AddNumberToObject(o, "screen", state.screen);
    cJSON_AddNumberToObject(o, "incident_id", StateMachine::instance().incidentId());
    cJSON_AddStringToObject(
        o, "trigger",
        StateMachine::instance().incidentTrigger() == StateEvent::EVT_FALL_DETECTED ? "FALL"
                                                                                    : "SOS");
    cJSON_AddNumberToObject(o, "countdown",
                            StateMachine::instance().isEmergencyGraceActive()
                                ? StateMachine::instance().getGraceRemainingSec()
                                : 0);
    cJSON_AddNumberToObject(o, "recording_count", MemoStore::instance().catalog().size());
    cJSON_AddBoolToObject(o, "recording", AudioManager::instance().isRecordingVoiceMemo());
    cJSON_AddBoolToObject(o, "saving", MemoStore::instance().busy());
    auto caps = cJSON_AddArrayToObject(o, "capabilities");
    for (const char *cap : {"wallpaper", "brightness", "volume", "fall_detection", "watch_mode",
                            "find_watch", "time_sync", "safe_check_in", "profile_read",
                            "audio_transfer"})
        cJSON_AddItemToArray(caps, cJSON_CreateString(cap));
    return o;
}
void publish(cJSON *o) {
    auto text = stringify(o);
    cJSON_Delete(o);
    if (text.size() > 480)
        return;
    uint16_t handle;
    {
        Guard g;
        response = text;
        handle = conn;
    }
    if (handle != BLE_HS_CONN_HANDLE_NONE) {
        uint32_t notice = seq;
        auto packet = ble_hs_mbuf_from_flat(&notice, 4);
        if (packet)
            ble_gatts_notify_custom(handle, eventHandle, packet);
    }
}
void respond(int id, bool ok, cJSON *applied = nullptr,
             const char *error = "Unsupported or invalid command") {
    auto o = cJSON_CreateObject();
    cJSON_AddNumberToObject(o, "v", 2);
    cJSON_AddNumberToObject(o, "cmd", id);
    cJSON_AddStringToObject(o, "status", ok ? "applied" : "rejected");
    if (applied)
        cJSON_AddItemToObject(o, "applied", applied);
    if (!ok)
        cJSON_AddStringToObject(o, "err", error);
    publish(o);
}
void execute(const Command &input) {
    {
        Guard g;
        if (conn != input.conn)
            return;
    }
    auto o = cJSON_Parse(input.json);
    if (!o)
        return;
    int64_t id = 0, version = 0;
    if (!integer(o, "v", version, 2, 2) || !integer(o, "cmd", id, 1, 2147483647)) {
        cJSON_Delete(o);
        return;
    }
    auto args = cJSON_GetObjectItemCaseSensitive(o, "args");
    std::string op = str(o, "op");
    int64_t value = 0;
    bool ok = false;
    cJSON *applied = nullptr;
    if (op == "get_status") {
        ok = true;
        applied = status();
    } else if (op == "get_profile") {
        applied = cJSON_CreateObject();
        for (unsigned i = 0; i < 2; ++i) {
            char key[12];
            snprintf(key, sizeof(key), "name%u", i);
            cJSON_AddStringToObject(applied, key, PhoneBridge::instance().guardianName(i).c_str());
            snprintf(key, sizeof(key), "phone%u", i);
            cJSON_AddStringToObject(applied, key, PhoneBridge::instance().guardianPhone(i).c_str());
        }
        ok = true;
    } else if (op == "set_wallpaper" || op == "set_brightness" || op == "set_volume") {
        if (integer(args, op == "set_wallpaper" ? "index" : "percent", value, 0,
                    op == "set_wallpaper" ? 4 : 100))
            ok = UiManager::instance().applySetting(op.c_str(), value);
        if (ok)
            applied = status();
    } else if (op == "set_fall_enabled") {
        auto v = cJSON_GetObjectItemCaseSensitive(args, "enabled");
        if (cJSON_IsBool(v))
            ok = UiManager::instance().applySetting(op.c_str(), cJSON_IsTrue(v));
        if (ok)
            applied = status();
    } else if (op == "set_mode") {
        auto sm = StateMachine::instance().getCurrentState();
        std::string mode = str(args, "mode");
        if (sm != SystemState::EMERGENCY && (mode == "SAFE" || mode == "WATCH")) {
            auto target = mode == "SAFE" ? SystemState::SAFE : SystemState::SAFETY_WATCH;
            if (sm != target)
                StateMachine::instance().sendEvent(mode == "SAFE"
                                                       ? StateEvent::EVT_ENTER_SAFE_MODE
                                                       : StateEvent::EVT_ENTER_WATCH_MODE);
            for (int i = 0; i < 40 && StateMachine::instance().getCurrentState() != target; ++i)
                vTaskDelay(pdMS_TO_TICKS(10));
            ok = StateMachine::instance().getCurrentState() == target;
            if (ok)
                applied = status();
        }
    } else if (op == "find_watch" &&
               StateMachine::instance().getCurrentState() != SystemState::EMERGENCY) {
        if (integer(args, "duration_s", value, 1, 60)) {
            AudioManager::instance().playSound(AlarmSoundType::APPLE_CHIME);
            findUntil = esp_timer_get_time() + value * 1000000;
            ok = true;
        }
    } else if (op == "sync_time") {
        int64_t zone;
        if (integer(args, "epoch_s", value, 1577836800LL, 4102444800LL) &&
            integer(args, "tz_offset_min", zone, -840, 840)) {
            timeval tv = {static_cast<time_t>(value), 0};
            settimeofday(&tv, nullptr);
            time_t local = value + zone * 60;
            tm dt;
            gmtime_r(&local, &dt);
            RtcDateTime rtc = {
                static_cast<uint16_t>(dt.tm_year + 1900), static_cast<uint8_t>(dt.tm_mon + 1),
                static_cast<uint8_t>(dt.tm_mday),         static_cast<uint8_t>(dt.tm_hour),
                static_cast<uint8_t>(dt.tm_min),          static_cast<uint8_t>(dt.tm_sec),
                static_cast<uint8_t>(dt.tm_wday)};
            ok = RtcPCF85063::instance().setDateTime(rtc) == ESP_OK;
        }
    } else if (op == "safe_check_in") {
        auto sm = StateMachine::instance().getCurrentState();
        // A remote check-in can finish Night Guard, but it can never dismiss
        // an active emergency. SOS dismissal remains a deliberate watch hold.
        if (sm != SystemState::EMERGENCY) {
            if (sm == SystemState::SAFETY_WATCH)
                StateMachine::instance().sendEvent(StateEvent::EVT_ENTER_SAFE_MODE);
            for (int i = 0; i < 40 && StateMachine::instance().getCurrentState() != SystemState::SAFE;
                 ++i)
                vTaskDelay(pdMS_TO_TICKS(10));
            ok = StateMachine::instance().getCurrentState() == SystemState::SAFE;
            if (ok) {
                applied = status();
                cJSON_AddBoolToObject(applied, "checked_in", true);
            }
        }
    } else if (op == "get_recording") {
        auto catalog = MemoStore::instance().catalog();
        if (integer(args, "index", value, 0, 1000) && size_t(value) < catalog.size()) {
            auto m = catalog[value];
            applied = cJSON_CreateObject();
            cJSON_AddStringToObject(applied, "id", m.id.c_str());
            cJSON_AddStringToObject(applied, "sha256", m.sha256.c_str());
            cJSON_AddNumberToObject(applied, "bytes", m.bytes);
            cJSON_AddNumberToObject(applied, "timestamp_ms", m.timestampMs);
            cJSON_AddBoolToObject(applied, "synced", m.synced);
            ok = true;
        }
    } else if (op == "recording_chunk") {
        std::string id = str(args, "id");
        if (integer(args, "offset", value, 0, 320000)) {
            auto data = MemoStore::instance().chunk(id, value);
            if (!data.empty()) {
                Guard g;
                selectedId = id;
                selectedOffset = value;
                ok = true;
            }
        }
    } else if (op == "recording_ack")
        ok = MemoStore::instance().acknowledge(str(args, "id"), str(args, "sha256"));
    respond(id, ok, applied);
    cJSON_Delete(o);
}
int access(uint16_t handle, uint16_t, ble_gatt_access_ctxt *context, void *arg) {
    ble_gap_conn_desc desc;
    if (ble_gap_conn_find(handle, &desc) || !desc.sec_state.encrypted ||
        !desc.sec_state.authenticated)
        return BLE_ATT_ERR_INSUFFICIENT_AUTHEN;
    if (context->op == BLE_GATT_ACCESS_OP_READ_CHR) {
        if (arg == reinterpret_cast<void *>(1)) {
            std::string id;
            size_t offset;
            {
                Guard g;
                id = selectedId;
                offset = selectedOffset;
            }
            auto data = MemoStore::instance().chunk(id, offset);
            return os_mbuf_append(context->om, data.data(), data.size())
                       ? BLE_ATT_ERR_INSUFFICIENT_RES
                       : 0;
        }
        Guard g;
        return os_mbuf_append(context->om, response.data(), response.size())
                   ? BLE_ATT_ERR_INSUFFICIENT_RES
                   : 0;
    }
    unsigned n = OS_MBUF_PKTLEN(context->om);
    if (!n || n > 480)
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    Command cmd = {};
    cmd.conn = handle;
    if (os_mbuf_copydata(context->om, 0, n, cmd.json))
        return BLE_ATT_ERR_UNLIKELY;
    static JsonFrame frame;
    std::string json;
    int state = frame.accept(reinterpret_cast<uint8_t *>(cmd.json), n, json);
    if (state < 0)
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    if (!state)
        return 0;
    std::memcpy(cmd.json, json.data(), json.size());
    cmd.json[json.size()] = 0;
    return xQueueSend(commands, &cmd, 0) == pdTRUE ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}
} // namespace
bool WatchSync::init() {
    lock = xSemaphoreCreateMutex();
    commands = xQueueCreate(4, sizeof(Command));
    if (!lock || !commands)
        return false;
    boot = esp_random() & 0x7fffffff;
    return xTaskCreate(
               [](void *) {
                   while (true) {
                       Command cmd;
                       if (xQueueReceive(commands, &cmd, pdMS_TO_TICKS(1000)) == pdTRUE)
                           execute(cmd);
                       if (findUntil && esp_timer_get_time() >= findUntil) {
                           findUntil = 0;
                           if (StateMachine::instance().getCurrentState() != SystemState::EMERGENCY)
                               AudioManager::instance().stopSound();
                       }
                   }
               },
               "watch_sync", 8192, nullptr, 3, nullptr) == pdPASS;
}
const ble_gatt_svc_def *WatchSync::service() {
    chars[0].uuid = &cmdUuid.u;
    chars[0].access_cb = access;
    chars[0].flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_ENC | BLE_GATT_CHR_F_WRITE_AUTHEN;
    chars[1].uuid = &eventUuid.u;
    chars[1].access_cb = access;
    chars[1].flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY | BLE_GATT_CHR_F_READ_ENC |
                     BLE_GATT_CHR_F_READ_AUTHEN;
    chars[1].val_handle = &eventHandle;
    chars[2].uuid = &dataUuid.u;
    chars[2].access_cb = access;
    chars[2].arg = reinterpret_cast<void *>(1);
    chars[2].flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_READ_ENC | BLE_GATT_CHR_F_READ_AUTHEN;
    svc.type = BLE_GATT_SVC_TYPE_PRIMARY;
    svc.uuid = &serviceUuid.u;
    svc.characteristics = chars;
    return &svc;
}
void WatchSync::connected(uint16_t handle) {
    Guard g;
    conn = handle;
    response = "{}";
    selectedId.clear();
}
void WatchSync::disconnected() {
    Guard g;
    conn = BLE_HS_CONN_HANDLE_NONE;
    xQueueReset(commands);
    response = "{}";
    selectedId.clear();
}
} // namespace SheShield
