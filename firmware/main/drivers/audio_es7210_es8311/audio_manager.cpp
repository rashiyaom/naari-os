#include "audio_manager.h"
#include "../../board_pins.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <driver/gpio.h>
#include <driver/i2c.h>
#include <driver/i2s_std.h>
#include <esp_check.h>
#include <esp_codec_dev.h>
#include <esp_codec_dev_defaults.h>
#include <esp_err.h>
#include <esp_heap_caps.h>
#include <esp_log.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

static const char *TAG = "Audio_Subsystem";

namespace SheShield {

static i2s_chan_handle_t s_txHandle = nullptr;
static i2s_chan_handle_t s_rxHandle = nullptr;
static SemaphoreHandle_t s_i2sRxMutex = nullptr;
static SemaphoreHandle_t s_audioMutex = nullptr;
static esp_codec_dev_handle_t s_speaker = nullptr, s_microphone = nullptr;
static const audio_codec_data_if_t *s_dataIf = nullptr;
struct AudioLock {
    AudioLock() { xSemaphoreTakeRecursive(s_audioMutex, portMAX_DELAY); }
    ~AudioLock() { xSemaphoreGiveRecursive(s_audioMutex); }
};

// 10 seconds of 16kHz 16-bit mono audio = 160,000 samples = 320 KB
static constexpr size_t kMaxMemoSamples = 16000 * 10;
static size_t s_memoCapacitySamples = 0;
static int16_t *s_memoPcmBuffer = nullptr;

AudioManager &AudioManager::instance() {
    static AudioManager s_instance;
    return s_instance;
}

AudioManager::AudioManager()
    : m_initialized(false), m_i2sReady(false), m_micReady(false), m_recording(false),
      m_soundPlaying(false), m_recordingMemo(false), m_playingVoiceMemo(false),
      m_memoSampleCount(0), m_memoPlayCursor(0), m_currentSoundType(AlarmSoundType::POLICE_WAIL),
      m_volumePercent(85), m_taskHandle(nullptr) {}

esp_err_t AudioManager::init() {
    ESP_LOGI(TAG,
             "Initializing Audio Subsystem (ES7210 Mic ADC + ES8311 DAC + Power "
             "Amp GPIO %d)...",
             PA_PIN_ENABLE);

    // 0. Create FreeRTOS Mutex to prevent concurrent I2S RX reads
    if (!s_i2sRxMutex) {
        s_i2sRxMutex = xSemaphoreCreateMutex();
    }

    s_audioMutex = xSemaphoreCreateRecursiveMutex();
    if (!s_i2sRxMutex || !s_audioMutex)
        return ESP_ERR_NO_MEM;

    // 1. Configure Speaker Power Amplifier Pin (GPIO 46)
    gpio_config_t pa_conf = {
        .pin_bit_mask = (1ULL << PA_PIN_ENABLE),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_ENABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&pa_conf);
    gpio_set_level(PA_PIN_ENABLE, 0); // Muted by default

    // 2. Allocate Buffer for Voice Memo (320 KB in PSRAM or 64 KB fallback in
    // internal RAM)
    s_memoPcmBuffer = static_cast<int16_t *>(
        heap_caps_malloc(kMaxMemoSamples * sizeof(int16_t), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    if (s_memoPcmBuffer) {
        s_memoCapacitySamples = kMaxMemoSamples;
    } else {
        // Fallback to internal RAM if PSRAM unavailable (4 seconds = 64,000
        // samples)
        s_memoCapacitySamples = 16000 * 4;
        s_memoPcmBuffer = static_cast<int16_t *>(
            heap_caps_malloc(s_memoCapacitySamples * sizeof(int16_t), MALLOC_CAP_INTERNAL));
    }

    if (s_memoPcmBuffer) {
        std::memset(s_memoPcmBuffer, 0, s_memoCapacitySamples * sizeof(int16_t));
        ESP_LOGI(TAG, "Voice Memo RAM buffer allocated (%zu samples).", s_memoCapacitySamples);
    }

    // I2S must supply a stable MCLK before either codec is opened.
    esp_err_t err = initI2S();
    if (err != ESP_OK)
        return err;
    audio_codec_i2s_cfg_t dataCfg = {};
    dataCfg.port = I2S_NUM;
    dataCfg.tx_handle = s_txHandle;
    dataCfg.rx_handle = s_rxHandle;
    s_dataIf = audio_codec_new_i2s_data(&dataCfg);
    if (!s_dataIf)
        return ESP_ERR_NO_MEM;
    esp_err_t outErr = initES8311();
    esp_err_t inErr = initES7210();
    m_i2sReady = outErr == ESP_OK;
    m_micReady = inErr == ESP_OK;
    if (xTaskCreatePinnedToCore(audioTask, "audio_pcm", 6144, this, 5, &m_taskHandle, 1) != pdPASS)
        return ESP_ERR_NO_MEM;
    m_initialized = true;
    ESP_LOGI(TAG, "Audio ready: speaker=%d microphone=%d memo=%zu samples", m_i2sReady, m_micReady,
             s_memoCapacitySamples);
    return outErr != ESP_OK ? outErr : inErr;
}

esp_err_t AudioManager::initES8311() {
    audio_codec_i2c_cfg_t bus = {};
    bus.port = I2C_MASTER_NUM;
    bus.addr = I2C_ADDR_ES8311 << 1; // codec library expects an 8-bit address
    auto ctrl = audio_codec_new_i2c_ctrl(&bus);
    es8311_codec_cfg_t cfg = {};
    cfg.ctrl_if = ctrl;
    cfg.gpio_if = audio_codec_new_gpio();
    cfg.codec_mode = ESP_CODEC_DEV_WORK_MODE_DAC;
    cfg.pa_pin = -1; // Audio task controls GPIO46 to avoid truncating the last DMA block.
    cfg.use_mclk = true;
    cfg.mclk_div = 256;
    auto chip = es8311_codec_new(&cfg);
    if (!chip)
        return ESP_FAIL;
    esp_codec_dev_cfg_t dev = {};
    dev.dev_type = ESP_CODEC_DEV_TYPE_OUT;
    dev.codec_if = chip;
    dev.data_if = s_dataIf;
    s_speaker = esp_codec_dev_new(&dev);
    esp_codec_dev_sample_info_t fs = {};
    fs.sample_rate = 16000;
    fs.bits_per_sample = 16;
    fs.channel = 2;
    if (!s_speaker || esp_codec_dev_open(s_speaker, &fs) != ESP_CODEC_DEV_OK)
        return ESP_FAIL;
    return esp_codec_dev_set_out_vol(s_speaker, m_volumePercent) == ESP_CODEC_DEV_OK ? ESP_OK
                                                                                     : ESP_FAIL;
}

esp_err_t AudioManager::initES7210() {
    audio_codec_i2c_cfg_t bus = {};
    bus.port = I2C_MASTER_NUM;
    bus.addr = I2C_ADDR_ES7210 << 1;
    auto ctrl = audio_codec_new_i2c_ctrl(&bus);
    es7210_codec_cfg_t cfg = {};
    cfg.ctrl_if = ctrl;
    cfg.mic_selected = ES7120_SEL_MIC1 | ES7120_SEL_MIC2;
    cfg.mclk_div = 256;
    auto chip = es7210_codec_new(&cfg);
    if (!chip)
        return ESP_FAIL;
    esp_codec_dev_cfg_t dev = {};
    dev.dev_type = ESP_CODEC_DEV_TYPE_IN;
    dev.codec_if = chip;
    dev.data_if = s_dataIf;
    s_microphone = esp_codec_dev_new(&dev);
    esp_codec_dev_sample_info_t fs = {};
    fs.sample_rate = 16000;
    fs.bits_per_sample = 16;
    fs.channel = 2;
    if (!s_microphone || esp_codec_dev_open(s_microphone, &fs) != ESP_CODEC_DEV_OK)
        return ESP_FAIL;
    return esp_codec_dev_set_in_gain(s_microphone, 24.0f) == ESP_CODEC_DEV_OK ? ESP_OK : ESP_FAIL;
}

esp_err_t AudioManager::initI2S() {
    i2s_chan_config_t channel = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM, I2S_ROLE_MASTER);
    channel.dma_desc_num = 6;
    channel.dma_frame_num = 256;
    ESP_RETURN_ON_ERROR(i2s_new_channel(&channel, &s_txHandle, &s_rxHandle), TAG, "I2S allocation");
    i2s_std_config_t cfg = {};
    cfg.clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(16000);
    // Both codecs use Philips I2S (one-bit delay), not left-justified MSB.
    cfg.slot_cfg =
        I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_STEREO);
    cfg.gpio_cfg.mclk = I2S_PIN_MCLK;
    cfg.gpio_cfg.bclk = I2S_PIN_BCLK;
    cfg.gpio_cfg.ws = I2S_PIN_WS;
    cfg.gpio_cfg.dout = I2S_PIN_DOUT;
    cfg.gpio_cfg.din = I2S_PIN_DIN;
    ESP_RETURN_ON_ERROR(i2s_channel_init_std_mode(s_txHandle, &cfg), TAG, "I2S TX init");
    // Duplex TX/RX have the same clock and slot configuration per ESP-IDF.
    ESP_RETURN_ON_ERROR(i2s_channel_init_std_mode(s_rxHandle, &cfg), TAG, "I2S RX init");
    ESP_RETURN_ON_ERROR(i2s_channel_enable(s_txHandle), TAG, "I2S TX enable");
    return i2s_channel_enable(s_rxHandle);
}

const char *AudioManager::getSoundName(AlarmSoundType type) const {
    switch (type) {
    case AlarmSoundType::POLICE_WAIL:
        return "Police Siren";
    case AlarmSoundType::PIERCING_PULSE:
        return "105dB Pulse";
    case AlarmSoundType::AMBULANCE_HILO:
        return "Hi-Lo Horn";
    case AlarmSoundType::CHIRP_RADAR:
        return "Chirp Radar";
    case AlarmSoundType::APPLE_CHIME:
        return "Apple Chime";
    default:
        return "Siren";
    }
}

void AudioManager::playSound(AlarmSoundType soundType) {
    AudioLock lock;
    if (!m_i2sReady)
        return;
    m_recordingMemo = false;
    m_playingVoiceMemo = false;
    m_currentSoundType = soundType;
    m_soundPlaying = true;
    gpio_set_level(PA_PIN_ENABLE, 1);
    ESP_LOGI(TAG, "Playing Alarm Sound: %s", getSoundName(soundType));
}

void AudioManager::playSirenAlarm(bool enable) {
    if (enable)
        playSound(AlarmSoundType::POLICE_WAIL);
    else
        stopSound();
}

void AudioManager::stopSound() {
    AudioLock lock;
    m_soundPlaying = false;
    m_playingVoiceMemo = false;
    gpio_set_level(PA_PIN_ENABLE, 0); // Mute speaker to eliminate noise
    ESP_LOGI(TAG, "Audio output stopped.");
}

void AudioManager::setVolume(uint8_t volumePercent) {
    AudioLock lock;
    if (volumePercent > 100)
        volumePercent = 100;
    m_volumePercent = volumePercent;
    if (s_speaker)
        esp_codec_dev_set_out_vol(s_speaker, volumePercent);
}

// -------------------------------------------------------------
// Voice Memo Real Microphone Recording & True Speaker Playback
// -------------------------------------------------------------
bool AudioManager::startVoiceMemoRecording() {
    AudioLock lock;
    if (!m_micReady || !s_memoPcmBuffer)
        return false;
    stopSound();
    m_memoSampleCount = 0;
    m_memoPlayCursor = 0;
    m_recordingMemo = true;
    m_inputPeak = 0;
    ESP_LOGI(TAG, "Starting microphone capture into RAM buffer...");
    return true;
}

void AudioManager::stopVoiceMemoRecording() {
    AudioLock lock;
    m_recordingMemo = false;
    ESP_LOGI(TAG, "Voice memo recording stopped. Recorded %zu samples (%.2f s).",
             m_memoSampleCount.load(), static_cast<float>(m_memoSampleCount) / 16000.0f);
}

bool AudioManager::playVoiceMemo() {
    AudioLock lock;
    if (!m_i2sReady || !s_memoPcmBuffer || m_memoSampleCount == 0 || m_recordingMemo)
        return false;
    stopSound();
    m_memoPlayCursor = 0;
    m_playingVoiceMemo = true;
    gpio_set_level(PA_PIN_ENABLE, 1); // Enable speaker amp
    ESP_LOGI(TAG, "Playing back recorded voice memo on speaker (%zu samples)...",
             m_memoSampleCount.load());
    return true;
}

void AudioManager::stopVoiceMemoPlayback() {
    AudioLock lock;
    m_playingVoiceMemo = false;
    gpio_set_level(PA_PIN_ENABLE, 0);
}

size_t AudioManager::getRecordedDurationSec() const {
    size_t s = m_memoSampleCount / 16000;
    return s;
}

void AudioManager::logMemoDiagnostics() {
    AudioLock lock;
    const size_t n = m_memoSampleCount;
    if (!n || !s_memoPcmBuffer) {
        ESP_LOGE(TAG, "PCM diagnostics: no samples");
        return;
    }
    int lo = 32767, hi = -32768;
    double sum = 0, squares = 0;
    for (size_t i = 0; i < n; ++i) {
        int v = s_memoPcmBuffer[i];
        lo = std::min(lo, v);
        hi = std::max(hi, v);
        sum += v;
        squares += static_cast<double>(v) * v;
    }
    double mean = sum / n;
    double rms = std::sqrt(std::max(0.0, squares / n - mean * mean));
    ESP_LOGI(TAG, "PCM diagnostics: samples=%zu min=%d max=%d AC_RMS=%.1f", n, lo, hi, rms);
}

esp_err_t AudioManager::startMicRecording(uint32_t sampleRate) {
    if (sampleRate != 16000 || !m_micReady)
        return ESP_ERR_INVALID_STATE;
    m_recording = true;
    return ESP_OK;
}

esp_err_t AudioManager::readMicSamples(void *buffer, size_t bufferSize, size_t *bytesRead) {
    if (bytesRead)
        *bytesRead = 0;
    if (!s_rxHandle || !m_micReady || m_recordingMemo) {
        return ESP_ERR_INVALID_STATE;
    }
    if (s_i2sRxMutex && xSemaphoreTake(s_i2sRxMutex, pdMS_TO_TICKS(25)) == pdTRUE) {
        esp_err_t err =
            i2s_channel_read(s_rxHandle, buffer, bufferSize, bytesRead, pdMS_TO_TICKS(25));
        xSemaphoreGive(s_i2sRxMutex);
        return err;
    }
    return ESP_ERR_TIMEOUT;
}

esp_err_t AudioManager::stopMicRecording() {
    m_recording = false;
    return ESP_OK;
}

// -------------------------------------------------------------
// Real-Time Audio Task (Handles Voice Playback & Siren Synthesis)
// -------------------------------------------------------------
void AudioManager::audioTask(void *pvParameters) {
    auto *self = static_cast<AudioManager *>(pvParameters);
    const int sampleRate = 16000;
    const int chunkSize = 256;
    int16_t buffer[chunkSize * 2]; // Stereo L+R

    uint32_t totalSamples = 0;
    float phase = 0.0f;

    while (true) {
        vTaskDelay(pdMS_TO_TICKS(1));
        AudioLock lock;
        // ── 1. Voice Memo Recording: Capture samples from I2S RX into RAM buffer
        if (self->m_recordingMemo && s_rxHandle && s_memoPcmBuffer) {
            int16_t rxBuf[chunkSize * 2];
            size_t bytesRead = 0;
            esp_err_t err = ESP_FAIL;
            if (s_i2sRxMutex && xSemaphoreTake(s_i2sRxMutex, pdMS_TO_TICKS(25)) == pdTRUE) {
                err = i2s_channel_read(s_rxHandle, rxBuf, sizeof(rxBuf), &bytesRead,
                                       pdMS_TO_TICKS(25));
                xSemaphoreGive(s_i2sRxMutex);
            }
            if (err == ESP_OK && bytesRead > 0) {
                size_t samplesRead = bytesRead / sizeof(int16_t) / 2; // Stereo pairs
                uint16_t peak = 0;
                for (size_t i = 0; i < samplesRead; i++) {
                    if (self->m_memoSampleCount < s_memoCapacitySamples) {
                        int16_t left = rxBuf[i * 2];
                        int16_t right = rxBuf[i * 2 + 1];
                        // Select the channel with strongest mic signal
                        // Choose one microphone for the entire frame to preserve waveform
                        // phase.
                        int32_t sample = left;
                        (void)right;
                        sample = sample * 2; // +6dB boost
                        if (sample > 32767)
                            sample = 32767;
                        if (sample < -32768)
                            sample = -32768;
                        peak = std::max(peak, static_cast<uint16_t>(std::abs(sample)));
                        s_memoPcmBuffer[self->m_memoSampleCount++] = static_cast<int16_t>(sample);
                    }
                }
                self->m_inputPeak = peak;
                if (self->m_memoSampleCount >= s_memoCapacitySamples)
                    self->m_recordingMemo = false;
            } else {
                vTaskDelay(pdMS_TO_TICKS(15));
            }
            continue;
        }

        // ── 2. Voice Memo Playback: Send recorded audio directly to speaker
        if (self->m_playingVoiceMemo && self->m_i2sReady && s_txHandle) {
            if (self->m_memoSampleCount > 0 && self->m_memoPlayCursor < self->m_memoSampleCount) {
                size_t remaining = self->m_memoSampleCount - self->m_memoPlayCursor;
                size_t toPlay = std::min(static_cast<size_t>(chunkSize), remaining);

                for (size_t i = 0; i < toPlay; i++) {
                    int16_t sample = s_memoPcmBuffer[self->m_memoPlayCursor + i];
                    // Apply volume scale
                    int32_t scaled = sample; // ES8311 sets output volume; avoid attenuating twice.
                    buffer[i * 2] = static_cast<int16_t>(scaled);
                    buffer[i * 2 + 1] = static_cast<int16_t>(scaled);
                }
                for (size_t i = toPlay; i < static_cast<size_t>(chunkSize); i++) {
                    buffer[i * 2] = 0;
                    buffer[i * 2 + 1] = 0;
                }
                size_t written = 0;
                esp_err_t err = i2s_channel_write(s_txHandle, buffer, sizeof(buffer), &written,
                                                  pdMS_TO_TICKS(80));
                if (err == ESP_OK && written == sizeof(buffer))
                    self->m_memoPlayCursor += toPlay;
                else {
                    self->m_playingVoiceMemo = false;
                    ESP_LOGE(TAG, "Speaker DMA write failed");
                }
            } else {
                // Playback reached end of recorded buffer
                self->m_playingVoiceMemo = false;
                vTaskDelay(pdMS_TO_TICKS(110)); // drain queued DMA before turning off the amplifier
                gpio_set_level(PA_PIN_ENABLE, 0);
                ESP_LOGI(TAG, "Voice memo playback completed.");
            }
            continue;
        }

        // ── 3. Alarm Sound Synthesizer (When siren triggered)
        if (!self->m_soundPlaying || !self->m_i2sReady || s_txHandle == nullptr) {
            vTaskDelay(pdMS_TO_TICKS(40));
            totalSamples = 0;
            phase = 0.0f;
            continue;
        }

        float amp = 28000.0f; // Hardware DAC applies the selected volume.
        AlarmSoundType type = self->m_currentSoundType;

        for (int i = 0; i < chunkSize; i++) {
            float t = static_cast<float>(totalSamples + i) / static_cast<float>(sampleRate);
            float freq = 1000.0f;
            float sampleVal = 0.0f;

            switch (type) {
            case AlarmSoundType::POLICE_WAIL: {
                float cycle = std::fmod(t, 1.5f) / 1.5f;
                freq = 600.0f + 1000.0f * (0.5f * (1.0f - std::cos(cycle * 2.0f * M_PI)));
                phase += (2.0f * M_PI * freq) / sampleRate;
                if (phase >= 2.0f * M_PI)
                    phase -= 2.0f * M_PI;
                sampleVal = std::sin(phase) * amp;
                break;
            }
            case AlarmSoundType::PIERCING_PULSE: {
                freq = 2400.0f;
                phase += (2.0f * M_PI * freq) / sampleRate;
                if (phase >= 2.0f * M_PI)
                    phase -= 2.0f * M_PI;
                bool pulseOn = (std::fmod(t, 0.125f) < 0.075f);
                sampleVal = pulseOn ? ((phase < M_PI ? 1.0f : -1.0f) * amp) : 0.0f;
                break;
            }
            case AlarmSoundType::AMBULANCE_HILO: {
                freq = (std::fmod(t, 0.8f) < 0.4f) ? 960.0f : 770.0f;
                phase += (2.0f * M_PI * freq) / sampleRate;
                if (phase >= 2.0f * M_PI)
                    phase -= 2.0f * M_PI;
                sampleVal = (std::sin(phase) + 0.3f * std::sin(2.0f * phase)) * (amp * 0.8f);
                break;
            }
            case AlarmSoundType::CHIRP_RADAR: {
                float sweep = std::fmod(t, 0.25f) / 0.25f;
                freq = 800.0f + 1600.0f * (sweep * sweep);
                phase += (2.0f * M_PI * freq) / sampleRate;
                if (phase >= 2.0f * M_PI)
                    phase -= 2.0f * M_PI;
                sampleVal = std::sin(phase) * amp;
                break;
            }
            case AlarmSoundType::APPLE_CHIME: {
                float subT = std::fmod(t, 1.2f);
                freq = 1760.0f;
                phase += (2.0f * M_PI * freq) / sampleRate;
                if (phase >= 2.0f * M_PI)
                    phase -= 2.0f * M_PI;
                float decay = std::exp(-5.0f * subT);
                sampleVal = std::sin(phase) * (amp * decay);
                break;
            }
            default:
                sampleVal = 0.0f;
                break;
            }

            int16_t pcm = static_cast<int16_t>(sampleVal);
            buffer[i * 2] = pcm;
            buffer[i * 2 + 1] = pcm;
        }

        totalSamples += chunkSize;
        size_t written = 0;
        i2s_channel_write(s_txHandle, buffer, sizeof(buffer), &written, pdMS_TO_TICKS(50));
    }
}

} // namespace SheShield
