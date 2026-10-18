#include <driver/gpio.h>
#include <driver/i2c.h>
#include <esp_err.h>
#include <esp_log.h>
#include <esp_timer.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/task.h>
#include <inttypes.h>
#include <math.h>
#include <nvs_flash.h>

#include "board_pins.h"
#include "drivers/audio_es7210_es8311/audio_manager.h"
#include "drivers/axp2101/axp2101.h"
#include "drivers/display_co5300/display_co5300.h"
#include "drivers/imu_qmi8658/imu_qmi8658.h"
#include "drivers/sim800l/sim800l.h"
#include "drivers/touch_ft3168/touch_ft3168.h"
#include "network/network_manager.h"
#include "state_machine/state_machine.h"
#include "storage/memo_store.h"
#include "storage/sd_logger.h"
#include "ui/ui_manager.h"
#include "voice/voice_trigger.h"

static const char *TAG = "SheShield_Main";

using namespace SheShield;

#include "drivers/rtc_pcf85063/rtc_pcf85063.h"

// -------------------------------------------------------------
// Hardware Button Multi-Press / Long-Press Subsystem
// -------------------------------------------------------------
static void buttonCadenceTask(void *pvParameters) {
    int64_t pressDownTime = 0;
    int clickCount = 0;
    int64_t lastReleaseTime = 0;
    int64_t lastPwrLongTrigger = 0;
    int64_t lastPwrShortTrigger = 0;

    bool prevGpio0State = false; // false = released (HIGH), true = pressed (LOW)
    int gpio0StableCount = 0;
    bool debouncedGpio0State = false;

    ESP_LOGI(TAG, "Hardware Button Cadence & Power Key Task started (20ms Polling Mode).");

    while (true) {
        int64_t nowMs = esp_timer_get_time() / 1000;

        // 1. Poll AXP2101 PMIC Physical Side Power Button (PEK / PWRON)
        bool pwrShort = false;
        bool pwrLong = false;
        if (AXP2101::instance().pollPowerKey(pwrShort, pwrLong)) {
            if (pwrLong && (nowMs - lastPwrLongTrigger > 2500)) {
                lastPwrLongTrigger = nowMs;
                ESP_LOGW(TAG, "AXP2101 Power Key Long Press -> Emergency SOS / Disarm");
                UiManager::instance().handleSideButtonLongPress();
            } else if (pwrShort && (nowMs - lastPwrShortTrigger > 500)) {
                lastPwrShortTrigger = nowMs;
                ESP_LOGI(TAG, "AXP2101 Power Key Short Press -> Control Center / Wake");
                UiManager::instance().handleSideButtonPress();
            }
        }

        // 2. Poll Digital Crown / BOOT Button (GPIO 0, Active LOW with Internal
        // Pullup)
        bool rawPressed = (gpio_get_level(BUTTON_PIN_SOS) == 0);
        if (rawPressed == prevGpio0State) {
            gpio0StableCount++;
        } else {
            gpio0StableCount = 0;
            prevGpio0State = rawPressed;
        }

        // Debounce: Require 2 consecutive 20ms matching samples (40ms noise filter)
        if (gpio0StableCount >= 2) {
            if (rawPressed != debouncedGpio0State) {
                debouncedGpio0State = rawPressed;
                if (debouncedGpio0State) { // Falling Edge: Button Pressed
                    pressDownTime = nowMs;
                } else { // Rising Edge: Button Released
                    if (pressDownTime > 0) {
                        int64_t duration = nowMs - pressDownTime;
                        pressDownTime = 0;

                        if (duration >= 2000) {
                            ESP_LOGW(TAG, "Hardware Crown/BOOT Long Press (%lld ms)", duration);
                            clickCount = 0;
                            UiManager::instance().handleSideButtonLongPress();
                        } else if (duration >= 40) { // Filter contact bounce spikes < 40ms
                            clickCount++;
                            lastReleaseTime = nowMs;
                        }
                    }
                }
            }
        }

        // Evaluate click cadence after 350ms quiet window
        if (clickCount > 0 && !debouncedGpio0State && (nowMs - lastReleaseTime) > 350) {
            if (clickCount >= 3) {
                ESP_LOGW(TAG, "Crown/BOOT Triple-Press (%d clicks) -> Emergency SOS", clickCount);
                UiManager::instance().handleSideButtonLongPress();
            } else if (clickCount == 2) {
                ESP_LOGI(TAG, "Crown/BOOT Double-Press -> Quick Switch");
                UiManager::instance().handleCrownButtonDoublePress();
            } else { // clickCount == 1
                ESP_LOGI(TAG, "Crown/BOOT Single Click -> Home / Launcher Toggle");
                UiManager::instance().handleCrownButtonPress();
            }
            clickCount = 0;
        }

        // Always yield to lower priority tasks (Core 1 UI and State Machine)
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}

// -------------------------------------------------------------
// Sensor Monitoring Task (QMI8658 IMU ~250Hz Sampling)
// -------------------------------------------------------------
static void imuMonitorTask(void *pvParameters) {
    ImuData data;
    TickType_t sampleWake = xTaskGetTickCount();
    while (true) {
        if (ImuQMI8658::instance().read(data) == ESP_OK &&
            ImuQMI8658::instance().checkFallCondition(data)) {
            float g = ImuQMI8658::instance().lastFallImpactG();
            ESP_LOGW(TAG, "Fall sequence detected; starting cancellable SOS window");
            StateMachine::instance().sendEvent(StateEvent::EVT_FALL_DETECTED, 0, g);
        }
        vTaskDelayUntil(&sampleWake, pdMS_TO_TICKS(4)); // Match 250Hz sensor ODR, including read time.
    }
}

// -------------------------------------------------------------
// UI Tick Task (15ms Loop)
// -------------------------------------------------------------
static void uiTask(void *pvParameters) {
    while (true) {
        UiManager::instance().tick();
        vTaskDelay(pdMS_TO_TICKS(15));
    }
}

#include "drivers/i2c_bus.h"

#ifdef NAARI_HARDWARE_SELFTEST
#include "storage/preferences.h"
static void localHardwareSelftest(void *) {
    vTaskDelay(pdMS_TO_TICKS(6000));
    auto &ui = UiManager::instance();
    auto &audio = AudioManager::instance();
    ESP_LOGI(TAG, "LOCAL SELFTEST: no phone requests or emergency events");
    uint8_t originalFace = readPreference("wallpaper", 0);
    for (int i = 0; i < 5; ++i) {
        ui.showScreen(WatchScreenId::SCREEN_WALLPAPERS);
        ui.handleTouchTap(120, 70 + i * 72);
        ui.handleTouchTap(205, 450);
        ESP_LOGI(TAG, "LOCAL SELFTEST: face %d saved=%d", i, readPreference("wallpaper", 0));
        vTaskDelay(pdMS_TO_TICKS(500));
    }
    ui.showScreen(WatchScreenId::SCREEN_WALLPAPERS);
    ui.handleTouchTap(120, 70 + originalFace * 72);
    ui.handleTouchTap(205, 450);
    ui.showScreen(WatchScreenId::SCREEN_RECORDER);
    ui.handleTouchTap(205, 197);
    vTaskDelay(pdMS_TO_TICKS(3000));
    ui.handleTouchTap(205, 355);
    audio.logMemoDiagnostics();
    ui.handleTouchTap(110, 345);
    vTaskDelay(pdMS_TO_TICKS(5000));
    ESP_LOGI(TAG, "LOCAL SELFTEST: audioReady=%d captured=%zu playbackComplete=%d", audio.isReady(),
             audio.getRecordedSamples(), !audio.isPlayingVoiceMemo());
    ImuData data = {};
    if (ImuQMI8658::instance().read(data) == ESP_OK)
        ESP_LOGI(TAG, "LOCAL SELFTEST: accel=%.3f,%.3f,%.3f gyro=%.2f,%.2f,%.2f", data.ax, data.ay,
                 data.az, data.gx, data.gy, data.gz);
    ui.showScreen(WatchScreenId::SCREEN_SAFE);
    ESP_LOGI(TAG, "LOCAL SELFTEST COMPLETE");
    vTaskDelete(nullptr);
}
#endif

extern "C" void app_main(void) {
    ESP_LOGI(TAG, "===============================================================");
    ESP_LOGI(TAG, "   SheShield Sentinel - Wearable Safety OS v1.0.0             ");
    ESP_LOGI(TAG, "   Board: Waveshare ESP32-S3-Touch-AMOLED-2.06 + A7672S LTE + BLE ");
    ESP_LOGI(TAG, "===============================================================");

    // 1. Initialize Non-Volatile Storage (NVS)
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    // 2. Initialize Hardware Buses & Drivers
    ESP_ERROR_CHECK(i2c_bus_init());
    AXP2101::instance().init();
    vTaskDelay(pdMS_TO_TICKS(100)); // Allow PMIC power rails to stabilize
    DisplayCO5300::instance().init();
    TouchFT3168::instance().init();
    ImuQMI8658::instance().init();
    SdLogger::instance().init();
    bool memoReady = MemoStore::instance().init();
    ESP_LOGI(TAG, "Persistent memos: ready=%d count=%u error=%s", memoReady,
             unsigned(MemoStore::instance().catalog().size()),
             MemoStore::instance().error().c_str());
    AudioManager::instance().init();
    esp_err_t modemError = SIM800L::instance().init();
    if (modemError != ESP_OK)
        ESP_LOGE(TAG, "Cellular transport initialization failed: %s", esp_err_to_name(modemError));
    NetworkManager::instance().init();
    VoiceTrigger::instance().init();
    RtcPCF85063::instance().init();
    UiManager::instance().init();

    // 3. Configure Hardware Button GPIO (Polling Mode, GPIO 0)
    gpio_config_t btn_conf = {
        .pin_bit_mask = (1ULL << BUTTON_PIN_SOS),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE // Interrupts disabled to prevent ipc0
                                       // stack overflow
    };
    gpio_config(&btn_conf);

    // 4. Initialize State Machine
    StateMachine::instance().init();

    // 5. Connect State Machine to UI Observer
    StateMachine::instance().registerStateCallback([](SystemState oldState, SystemState newState) {
        UiManager::instance().updateState(newState);
    });

    // 6. Connect the selected cellular transport to the UI observer
    SIM800L::instance().registerStatusCallback(
        [](const SimStatusReport &report) { UiManager::instance().updateSimStatus(report); });

    // 7. Start ESP-SR Offline Voice Recognition in Background
    VoiceTrigger::instance().startListening([](const char *phrase, float confidence) {
        ESP_LOGW(TAG, "ESP-SR Wake Word Trigger: \"%s\" (Confidence: %.2f)", phrase, confidence);
        StateMachine::instance().sendEvent(StateEvent::EVT_VOICE_TRIGGERED);
    });

    // 8. Spawn Dedicated FreeRTOS Tasks
    xTaskCreatePinnedToCore(buttonCadenceTask, "btn_task", 3072, nullptr, 6, nullptr, 1);
    xTaskCreatePinnedToCore(imuMonitorTask, "imu_task", 4096, nullptr, 5, nullptr, 1);
    xTaskCreatePinnedToCore(uiTask, "ui_task", 4096, nullptr, 4, nullptr, 1);

    ESP_LOGI(TAG, "Startup stack free minimum: %u bytes", unsigned(uxTaskGetStackHighWaterMark(nullptr)));
    ESP_LOGI(TAG, "SheShield Sentinel System fully running and guarding in [SAFE] mode.");
#ifdef NAARI_HARDWARE_SELFTEST
    xTaskCreate(localHardwareSelftest, "local_selftest", 4096, nullptr, 2, nullptr);
#else
    ESP_LOGI(TAG, "Production firmware: automatic hardware checks disabled.");
#endif
}
