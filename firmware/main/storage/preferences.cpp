#include "preferences.h"
#include <nvs.h>
namespace SheShield {
uint8_t readPreference(const char *key, uint8_t fallback) {
    nvs_handle_t h;
    uint8_t value = fallback;
    if (nvs_open("naari", NVS_READONLY, &h) == ESP_OK) {
        nvs_get_u8(h, key, &value);
        nvs_close(h);
    }
    return value;
}
bool writePreference(const char *key, uint8_t value) {
    nvs_handle_t h;
    if (nvs_open("naari", NVS_READWRITE, &h) != ESP_OK)
        return false;
    esp_err_t err = nvs_set_u8(h, key, value);
    if (err == ESP_OK)
        err = nvs_commit(h);
    nvs_close(h);
    return err == ESP_OK;
}
} // namespace SheShield
