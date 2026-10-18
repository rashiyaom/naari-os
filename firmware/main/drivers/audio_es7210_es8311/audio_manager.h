#pragma once

#include "../../board_pins.h"
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <esp_err.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <string>

namespace SheShield {

enum class AlarmSoundType {
    POLICE_WAIL = 0, // 600Hz - 1500Hz classic emergency wail
    PIERCING_PULSE,  // 2500Hz high-decibel deterrent pulse
    AMBULANCE_HILO,  // 960Hz / 770Hz European two-tone
    CHIRP_RADAR,     // 800Hz - 2400Hz fast chirp sweep
    APPLE_CHIME,     // 1760Hz gentle decay bell chime
    COUNT
};

class AudioManager {
  public:
    static AudioManager &instance();

    esp_err_t init();
    void playSound(AlarmSoundType soundType);
    void playSirenAlarm(bool enable);
    bool setSelectedAlarmSound(AlarmSoundType type);
    AlarmSoundType getSelectedAlarmSound() const { return m_selectedSoundType; }
    void stopSound();
    bool isPlaying() const { return m_soundPlaying || m_playingVoiceMemo; }
    AlarmSoundType getCurrentSound() const { return m_currentSoundType; }
    const char *getSoundName(AlarmSoundType type) const;

    void setVolume(uint8_t volumePercent); // 0 - 100
    uint8_t getVolume() const { return m_volumePercent; }

    // Hardware Microphone Capture API
    esp_err_t startMicRecording(uint32_t sampleRate = 16000);
    esp_err_t readMicSamples(void *buffer, size_t bufferSize, size_t *bytesRead);
    esp_err_t stopMicRecording();

    // Voice Memo Audio Recording & True Speaker Playback API
    bool startVoiceMemoRecording();
    void stopVoiceMemoRecording();
    bool playVoiceMemo();
    bool playStoredMemo(const std::string &id);
    bool isLoadingStoredMemo() const { return m_loadingStoredMemo; }
    bool storedMemoLoadFailed() const { return m_storedMemoFailed; }
    void clearStoredMemoError() { m_storedMemoFailed = false; }
    void stopVoiceMemoPlayback();
    void clearRecordedMemo();
    bool isPlayingVoiceMemo() const { return m_playingVoiceMemo; }
    bool isRecordingVoiceMemo() const { return m_recordingMemo; }
    size_t getRecordedDurationSec() const;
    size_t getRecordedSamples() const { return m_memoSampleCount; }
    uint16_t getInputPeak() const { return m_inputPeak; }
    bool isReady() const { return m_i2sReady && m_micReady; }
    void logMemoDiagnostics();

  private:
    AudioManager();
    ~AudioManager() = default;
    AudioManager(const AudioManager &) = delete;
    AudioManager &operator=(const AudioManager &) = delete;

    esp_err_t initI2S();
    esp_err_t initES8311();
    esp_err_t initES7210();
    static void audioTask(void *pvParameters);

    bool m_initialized;
    bool m_i2sReady;
    bool m_micReady;
    std::atomic<uint16_t> m_inputPeak{0};
    bool m_recording;
    std::atomic<bool> m_soundPlaying;
    std::atomic<bool> m_recordingMemo;
    std::atomic<bool> m_playingVoiceMemo;
    std::atomic<bool> m_loadingStoredMemo{false}, m_storedMemoFailed{false};
    std::atomic<uint32_t> m_playbackGeneration{0};
    std::atomic<size_t> m_memoSampleCount;
    size_t m_memoPlayCursor;
    std::atomic<AlarmSoundType> m_currentSoundType;
    std::atomic<AlarmSoundType> m_selectedSoundType{AlarmSoundType::POLICE_WAIL};
    std::atomic<uint8_t> m_volumePercent;
    TaskHandle_t m_taskHandle;
};

} // namespace SheShield
