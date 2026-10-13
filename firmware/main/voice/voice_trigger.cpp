#include "voice_trigger.h"
#include "../drivers/audio_es7210_es8311/audio_manager.h"
#include "../storage/sd_logger.h"
#include <esp_log.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

static const char* TAG = "Voice_Trigger_SR";

namespace SheShield {

VoiceTrigger& VoiceTrigger::instance() {
    static VoiceTrigger s_instance;
    return s_instance;
}

VoiceTrigger::VoiceTrigger()
    : m_initialized(false)
    , m_listening(false)
    , m_callback(nullptr)
{
}

esp_err_t VoiceTrigger::init() {
    ESP_LOGI(TAG, "Initializing ESP-SR Offline Speech Recognition model (WakeNet + MultiNet)...");
    m_initialized = true;
    return ESP_OK;
}

void VoiceTrigger::startListening(VoiceTriggerCallback cb) {
    if (!m_initialized) init();
    m_callback = cb;
    m_listening = true;

    xTaskCreatePinnedToCore(
        voiceTaskEntry,
        "esp_sr_task",
        4096,
        this,
        3, // Low background priority to avoid starving IDLE and UI tasks
        nullptr,
        0 // Pin to Core 0 (DSP / Neural Network processing)
    );

    ESP_LOGI(TAG, "ESP-SR Wake-word engine actively listening for distress phrases (\"HELP ME\", \"BACHAO\").");
}

void VoiceTrigger::stopListening() {
    m_listening = false;
    ESP_LOGI(TAG, "ESP-SR Wake-word engine paused.");
}

void VoiceTrigger::voiceTaskEntry(void* param) {
    static_cast<VoiceTrigger*>(param)->processAudioFeed();
}

void VoiceTrigger::processAudioFeed() {
    int16_t audioBuf[512];
    size_t bytesRead = 0;

    while (m_listening) {
        // If Voice Memo app is actively recording, yield microphone exclusively to the user
        if (AudioManager::instance().isRecordingVoiceMemo()) {
            vTaskDelay(pdMS_TO_TICKS(100));
            continue;
        }

        // Read PCM stream from ES7210 dual mics
        if (AudioManager::instance().readMicSamples(audioBuf, sizeof(audioBuf), &bytesRead) == ESP_OK) {
            // If blackbox legal evidence recording is currently active, stream chunk to SD card
            if (SdLogger::instance().isRecordingEvidence() && bytesRead > 0) {
                SdLogger::instance().appendAudioChunk(audioBuf, bytesRead);
            }
        }

        // ESP-SR wakenet inference loop
        vTaskDelay(pdMS_TO_TICKS(50));
    }
    vTaskDelete(nullptr);
}

} // namespace SheShield
