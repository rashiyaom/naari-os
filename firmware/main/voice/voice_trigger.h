#pragma once

#include <cstdint>
#include <functional>
#include <esp_err.h>

namespace SheShield {

using VoiceTriggerCallback = std::function<void(const char* phrase, float confidence)>;

class VoiceTrigger {
public:
    static VoiceTrigger& instance();

    esp_err_t init();
    void startListening(VoiceTriggerCallback cb);
    void stopListening();
    bool isListening() const { return m_listening; }

private:
    VoiceTrigger();
    ~VoiceTrigger() = default;

    static void voiceTaskEntry(void* param);
    void processAudioFeed();

    bool m_initialized;
    bool m_listening;
    VoiceTriggerCallback m_callback;
};

} // namespace SheShield
