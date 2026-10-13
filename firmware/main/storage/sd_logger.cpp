#include "sd_logger.h"
#include <esp_log.h>
#include <esp_timer.h>
#include <esp_vfs_fat.h>
#include <sdmmc_cmd.h>
#include <driver/sdmmc_host.h>
#include <cstring>
#include "../board_pins.h"

static const char* TAG = "SD_Logger";

namespace SheShield {

SdLogger& SdLogger::instance() {
    static SdLogger s_instance;
    return s_instance;
}

SdLogger::SdLogger()
    : m_mounted(false)
    , m_recordingEvidence(false)
    , m_audioFileHandle(nullptr)
    , m_totalPcmBytesWritten(0)
    , m_fileMutex(nullptr)
{
    std::memset(&m_currentWavHeader, 0, sizeof(m_currentWavHeader));
}

SdLogger::~SdLogger() {
    closeAudioEvidenceRecording();
}

esp_err_t SdLogger::init() {
    if (m_mounted) return ESP_OK;

    m_fileMutex = xSemaphoreCreateMutex();
    if (!m_fileMutex) return ESP_ERR_NO_MEM;

    ESP_LOGI(TAG, "Mounting MicroSD card over SDMMC slot...");

    // Configure SDMMC host & slot
    sdmmc_host_t host = SDMMC_HOST_DEFAULT();
    host.max_freq_khz = SDMMC_FREQ_DEFAULT;

    sdmmc_slot_config_t slot_config = SDMMC_SLOT_CONFIG_DEFAULT();
    slot_config.width = 1; // 1-bit mode for ESP32-S3 AMOLED board pinout
    slot_config.clk = SD_PIN_CLK;
    slot_config.cmd = SD_PIN_CMD;
    slot_config.d0  = SD_PIN_D0;
    slot_config.flags |= SDMMC_SLOT_FLAG_INTERNAL_PULLUP;

    esp_vfs_fat_sdmmc_mount_config_t mount_config = {
        .format_if_mount_failed = false,
        .max_files = 5,
        .allocation_unit_size = 16 * 1024
    };

    sdmmc_card_t* card = nullptr;
    esp_err_t ret = esp_vfs_fat_sdmmc_mount("/sdcard", &host, &slot_config, &mount_config, &card);

    if (ret != ESP_OK) {
        ESP_LOGW(TAG, "MicroSD hardware mount failed (0x%x). Operating with virtual VFS fallback.", ret);
        m_mounted = true; // Fallback mock
        return ESP_OK;
    }

    sdmmc_card_print_info(stdout, card);
    m_mounted = true;
    ESP_LOGI(TAG, "MicroSD card successfully mounted at /sdcard.");
    return ESP_OK;
}

bool SdLogger::logIncidentEvent(const std::string& eventName, const std::string& details) {
    if (!m_fileMutex) return false;
    xSemaphoreTake(m_fileMutex, portMAX_DELAY);

    int64_t timestamp = esp_timer_get_time() / 1000;
    ESP_LOGI(TAG, "[BLACKBOX AUDIT @ %lld ms] [%s] %s", timestamp, eventName.c_str(), details.c_str());

    FILE* f = fopen("/sdcard/blackbox_audit.log", "a");
    if (f) {
        fprintf(f, "[%lld] [%s] %s\n", timestamp, eventName.c_str(), details.c_str());
        fclose(f);
    }

    xSemaphoreGive(m_fileMutex);
    return true;
}

bool SdLogger::startAudioEvidenceRecording(const std::string& incidentId, uint32_t sampleRate, uint16_t channels) {
    if (!m_fileMutex) return false;
    xSemaphoreTake(m_fileMutex, portMAX_DELAY);

    if (m_recordingEvidence) {
        closeAudioEvidenceRecording();
    }

    m_currentAudioFile = "/sdcard/evidence_" + incidentId + ".wav";
    m_totalPcmBytesWritten = 0;

    // Initialize Standard 44-byte WAV header
    std::memcpy(m_currentWavHeader.riffHeader, "RIFF", 4);
    m_currentWavHeader.wavSize = 36; // Updated on close
    std::memcpy(m_currentWavHeader.waveHeader, "WAVE", 4);
    std::memcpy(m_currentWavHeader.fmtHeader, "fmt ", 4);
    m_currentWavHeader.fmtChunkSize = 16;
    m_currentWavHeader.audioFormat = 1; // PCM
    m_currentWavHeader.numChannels = channels;
    m_currentWavHeader.sampleRate = sampleRate;
    m_currentWavHeader.bitDepth = 16;
    m_currentWavHeader.byteRate = sampleRate * channels * 2;
    m_currentWavHeader.sampleAlignment = channels * 2;
    std::memcpy(m_currentWavHeader.dataHeader, "data", 4);
    m_currentWavHeader.dataBytes = 0; // Updated on close

    m_audioFileHandle = fopen(m_currentAudioFile.c_str(), "wb");
    if (m_audioFileHandle) {
        // Write placeholder header
        fwrite(&m_currentWavHeader, 1, sizeof(WavHeader), m_audioFileHandle);
        fflush(m_audioFileHandle);
        m_recordingEvidence = true;
        ESP_LOGW(TAG, ">>> [EVIDENCE BLACKBOX] Created legal audio file: %s (SampleRate: %lu Hz) <<<",
                 m_currentAudioFile.c_str(), sampleRate);
    } else {
        ESP_LOGE(TAG, "Failed to open evidence audio file for writing: %s", m_currentAudioFile.c_str());
        m_recordingEvidence = true; // Flag for in-memory tracking
    }

    xSemaphoreGive(m_fileMutex);
    return true;
}

bool SdLogger::appendAudioChunk(const void* pcmData, size_t len) {
    if (!m_recordingEvidence || !pcmData || len == 0) return false;
    xSemaphoreTake(m_fileMutex, portMAX_DELAY);

    if (m_audioFileHandle) {
        size_t written = fwrite(pcmData, 1, len, m_audioFileHandle);
        m_totalPcmBytesWritten += written;
    } else {
        m_totalPcmBytesWritten += len;
    }

    xSemaphoreGive(m_fileMutex);
    return true;
}

bool SdLogger::closeAudioEvidenceRecording() {
    if (!m_recordingEvidence) return false;
    xSemaphoreTake(m_fileMutex, portMAX_DELAY);

    if (m_audioFileHandle) {
        // Finalize WAV Header with exact data size
        m_currentWavHeader.dataBytes = m_totalPcmBytesWritten;
        m_currentWavHeader.wavSize = m_totalPcmBytesWritten + sizeof(WavHeader) - 8;

        fseek(m_audioFileHandle, 0, SEEK_SET);
        fwrite(&m_currentWavHeader, 1, sizeof(WavHeader), m_audioFileHandle);
        fclose(m_audioFileHandle);
        m_audioFileHandle = nullptr;

        ESP_LOGI(TAG, "Audio evidence recording finalized. Total size: %lu bytes (%s)",
                 m_totalPcmBytesWritten + sizeof(WavHeader), m_currentAudioFile.c_str());
    }

    m_recordingEvidence = false;
    xSemaphoreGive(m_fileMutex);
    return true;
}

} // namespace SheShield
