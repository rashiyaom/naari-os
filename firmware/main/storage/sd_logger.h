#pragma once

#include <cstdint>
#include <string>
#include <esp_err.h>
#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

namespace SheShield {

// Standard 44-byte RIFF WAV Header
#pragma pack(push, 1)
struct WavHeader {
    char riffHeader[4];    // "RIFF"
    uint32_t wavSize;      // Total size - 8
    char waveHeader[4];    // "WAVE"
    char fmtHeader[4];     // "fmt "
    uint32_t fmtChunkSize; // 16 for PCM
    uint16_t audioFormat;  // 1 = PCM
    uint16_t numChannels;  // 1 = Mono, 2 = Stereo
    uint32_t sampleRate;   // e.g. 16000 Hz
    uint32_t byteRate;     // sampleRate * numChannels * bitsPerSample / 8
    uint16_t sampleAlignment; // numChannels * bitsPerSample / 8
    uint16_t bitDepth;     // 16-bit
    char dataHeader[4];    // "data"
    uint32_t dataBytes;    // PCM payload bytes
};
#pragma pack(pop)

class SdLogger {
public:
    static SdLogger& instance();

    esp_err_t init();
    bool isMounted() const { return m_mounted; }

    // Blackbox & Cellular Comms Audit Logging
    bool logIncidentEvent(const std::string& eventName, const std::string& details);

    // Forensic WAV Audio Evidence Capture
    bool startAudioEvidenceRecording(const std::string& incidentId, uint32_t sampleRate = 16000, uint16_t channels = 1);
    bool appendAudioChunk(const void* pcmData, size_t len);
    bool closeAudioEvidenceRecording();

    bool isRecordingEvidence() const { return m_recordingEvidence; }
    const std::string& getCurrentEvidenceFile() const { return m_currentAudioFile; }

private:
    SdLogger();
    ~SdLogger();
    SdLogger(const SdLogger&) = delete;
    SdLogger& operator=(const SdLogger&) = delete;

    bool m_mounted;
    bool m_recordingEvidence;
    std::string m_currentAudioFile;
    FILE* m_audioFileHandle;
    uint32_t m_totalPcmBytesWritten;
    WavHeader m_currentWavHeader;
    SemaphoreHandle_t m_fileMutex;
};

} // namespace SheShield
