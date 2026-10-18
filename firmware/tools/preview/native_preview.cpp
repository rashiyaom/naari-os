// Host-only device mocks. This executable links the real UiManager and graphics
// implementations. Cellular, audio, timers and sensors never access hardware.
#include "drivers/audio_es7210_es8311/audio_manager.h"
#include "drivers/axp2101/axp2101.h"
#include "drivers/display_co5300/display_co5300.h"
#include "drivers/imu_qmi8658/imu_qmi8658.h"
#include "drivers/rtc_pcf85063/rtc_pcf85063.h"
#include "drivers/touch_ft3168/touch_ft3168.h"
#include "network/phone_bridge.h"
#include "storage/memo_store.h"
#include "storage/preferences.h"
#include "ui/ui_manager.h"
#include <algorithm>
#include <cassert>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

int64_t previewTime = 12000000;
static bool previewPhoneConnected = true, previewPhoneReady = true;
int64_t esp_timer_get_time() { return previewTime; }

namespace SheShield {
DisplayCO5300 &DisplayCO5300::instance() {
    static DisplayCO5300 d;
    return d;
}
DisplayCO5300::DisplayCO5300()
    : m_initialized(false), m_brightness(85), m_spi(nullptr), m_framebuffer(nullptr),
      m_dmaChunkBuf(nullptr) {}
esp_err_t DisplayCO5300::init() {
    m_framebuffer = static_cast<uint16_t *>(std::calloc(LCD_H_RES * LCD_V_RES, sizeof(uint16_t)));
    m_initialized = true;
    return m_framebuffer ? ESP_OK : ESP_ERR_NO_MEM;
}
void DisplayCO5300::present() {}
void DisplayCO5300::setBrightness(uint8_t p) { m_brightness = p; }

AXP2101::AXP2101() : m_initialized(true) {}
AXP2101 &AXP2101::instance() {
    static AXP2101 d;
    return d;
}
esp_err_t AXP2101::getBatteryStatus(BatteryStatus &b) {
    b = {4.12f, 95, false, true};
    return ESP_OK;
}
RtcPCF85063::RtcPCF85063() {}
RtcPCF85063 &RtcPCF85063::instance() {
    static RtcPCF85063 d;
    return d;
}
void RtcPCF85063::getTimeString(char *p, size_t n) { std::snprintf(p, n, "14:24"); }
void RtcPCF85063::getDateString(char *p, size_t n) { std::snprintf(p, n, "MON, 12 OCT"); }
ImuQMI8658::ImuQMI8658() : m_initialized(true) {}
ImuQMI8658 &ImuQMI8658::instance() {
    static ImuQMI8658 d;
    return d;
}
esp_err_t ImuQMI8658::read(ImuData &d) {
    d = {0.02f, 0.01f, 0.99f, 0.5f, 0.1f, 0.2f, 26, false, false, false};
    return ESP_OK;
}
TouchFT3168::TouchFT3168() {}
TouchFT3168 &TouchFT3168::instance() {
    static TouchFT3168 d;
    return d;
}
bool TouchFT3168::read(TouchPoint &) { return false; }
AudioManager::AudioManager()
    : m_i2sReady(true), m_micReady(true), m_soundPlaying(false), m_memoSampleCount(0),
      m_recordingMemo(false), m_playingVoiceMemo(false),
      m_currentSoundType(AlarmSoundType::APPLE_CHIME), m_volumePercent(75) {}
AudioManager &AudioManager::instance() {
    static AudioManager d;
    return d;
}
void AudioManager::playSound(AlarmSoundType s) {
    m_currentSoundType = s;
    m_soundPlaying = true;
}
bool AudioManager::setSelectedAlarmSound(AlarmSoundType s) {
    m_selectedSoundType = s;
    return true;
}
void AudioManager::playSirenAlarm(bool enabled) {
    if (enabled) playSound(getSelectedAlarmSound()); else stopSound();
}
ImuQMI8658::FallTelemetry ImuQMI8658::fallTelemetry() const {
    return {1.01f, 2.0f, 0, 0, 100, 0, m_testMode};
}
void AudioManager::stopSound() { m_soundPlaying = false; }
void AudioManager::setVolume(uint8_t n) { m_volumePercent = n; }
bool AudioManager::startVoiceMemoRecording() {
    m_recordingMemo = true;
    m_memoSampleCount = 32000;
    m_inputPeak = 9000;
    return true;
}
void AudioManager::stopVoiceMemoRecording() { m_recordingMemo = false; }
bool AudioManager::playVoiceMemo() {
    m_playingVoiceMemo = true;
    return true;
}
void AudioManager::stopVoiceMemoPlayback() { m_playingVoiceMemo = false; }
size_t AudioManager::getRecordedDurationSec() const { return m_memoSampleCount / 16000; }
MemoStore &MemoStore::instance() {
    static MemoStore store;
    return store;
}
static bool previewSavedMemos = false;
static bool previewDeletionFails = false;
static std::vector<std::string> previewDeletedMemos;
std::vector<StoredMemo> MemoStore::catalog() const {
    if (!previewSavedMemos) return {};
    std::vector<StoredMemo> records;
    for (int i = 0; i < 6; ++i) {
        StoredMemo memo;
        memo.id = std::string(31, 'a') + char('a' + i);
        if (std::find(previewDeletedMemos.begin(), previewDeletedMemos.end(), memo.id) != previewDeletedMemos.end()) continue;
        memo.bytes = 32000 * (i + 1);
        memo.timestampMs = 1792267200000LL + i * 60000;
        memo.synced = i % 2;
        records.push_back(memo);
    }
    return records;
}
bool MemoStore::remove(const std::string &id) {
    if (previewDeletionFails) return false;
    auto memos = catalog();
    if (std::none_of(memos.begin(), memos.end(), [&](const StoredMemo &memo) { return memo.id == id; })) return false;
    previewDeletedMemos.push_back(id);
    return true;
}
void AudioManager::clearRecordedMemo() {
    stopVoiceMemoPlayback(); m_memoSampleCount = 0;
}
bool AudioManager::playStoredMemo(const std::string &id) {
    auto records = MemoStore::instance().catalog();
    auto m = std::find_if(records.begin(), records.end(), [&](const StoredMemo &m) { return m.id == id; });
    if (m == records.end()) return false;
    m_memoSampleCount = m->bytes / 2;
    m_playingVoiceMemo = true;
    return true;
}
std::string MemoStore::error() const { return ""; }
uint8_t readPreference(const char *, uint8_t fallback) { return fallback; }
bool writePreference(const char *, uint8_t) { return true; }
bool ImuQMI8658::setFallDetectionEnabled(bool enabled) {
    m_fallEnabled = enabled;
    return true;
}
PhoneBridge &PhoneBridge::instance() {
    static PhoneBridge p;
    return p;
}
PhoneLinkStatus PhoneBridge::status() const {
    PhoneLinkStatus s;
    s.connected = previewPhoneConnected;
    s.ready = previewPhoneReady;
    s.smsAllowed = s.callsAllowed = s.locationAllowed = s.ready;
    return s;
}
uint32_t PhoneBridge::request(const char *, const char *, const char *) { return 1; }
uint32_t PhoneBridge::lastRequestId() const { return 1; }
PhoneResult PhoneBridge::result(uint32_t id) const {
    PhoneResult r;
    r.id = id;
    r.state = PhoneRequestState::Queued;
    std::snprintf(r.detail, sizeof(r.detail), "Waiting for Android phone");
    return r;
}
std::string PhoneBridge::guardianName(unsigned i) const { return i ? "Sister" : "Mom"; }
std::string PhoneBridge::guardianPhone(unsigned i) const {
    return i ? "+919820011223" : "+919876543210";
}
void PhoneBridge::openPairing() {}
bool PhoneBridge::isPairing() const { return false; }
uint32_t PhoneBridge::passkey() const { return 123456; }
void PhoneBridge::forgetPhone() {}
SIM800L::SIM800L() {}
SimStatusReport SIM800L::getStatus() {
    SimStatusReport r = {};
    r.csq = 99;
    std::snprintf(r.statusString, sizeof(r.statusString), "A7672S registered (preview)");
    return r;
}

SIM800L &SIM800L::instance() {
    static SIM800L d;
    return d;
}
bool SIM800L::requestVoiceCall(const char *) { return true; }
uint32_t SIM800L::queueRequest(const char *, const char *, const char *) { return 1; }
bool SIM800L::requestHangUp() { return true; }
bool SIM800L::usesDirectCellular() const { return true; }
uint32_t SIM800L::lastRequestId() const { return 1; }
PhoneResult SIM800L::result(uint32_t id) const { return PhoneBridge::instance().result(id); }
bool SIM800L::requestSendLocationSms(const char *, const char *, uint8_t) { return true; }
bool SIM800L::requestPowerDown() { return true; }
StateMachine::StateMachine()
    : m_currentState(SystemState::SAFE), m_gracePeriodActive(true), m_graceRemainingSec(30),
      m_emergencyAlertDispatched(false) {}
StateMachine &StateMachine::instance() {
    static StateMachine d;
    return d;
}
void StateMachine::sendEvent(StateEvent e, uint32_t, float) {
    if (e == StateEvent::EVT_ENTER_WATCH_MODE) {
        m_currentState = SystemState::SAFETY_WATCH;
        UiManager::instance().updateState(m_currentState);
    }
    if (e == StateEvent::EVT_MANUAL_SOS_PRESS) {
        m_currentState = SystemState::EMERGENCY;
        UiManager::instance().updateState(m_currentState);
    }
    if (e == StateEvent::EVT_CANCEL_ALARM || e == StateEvent::EVT_ENTER_SAFE_MODE) {
        m_currentState = SystemState::SAFE;
        UiManager::instance().updateState(m_currentState);
    }
}
uint32_t StateMachine::getWatchActiveDurationSec() const { return 154; }
} // namespace SheShield

using namespace SheShield;
static void save(const std::string &name) {
    FILE *f = std::fopen((name + ".ppm").c_str(), "wb");
    assert(f);
    std::fprintf(f, "P6\n410 502\n255\n");
    auto p = DisplayCO5300::instance().previewFramebuffer();
    for (int i = 0; i < 410 * 502; ++i) {
        uint16_t c = (p[i] >> 8) | (p[i] << 8);
        uint8_t rgb[] = {uint8_t(((c >> 11) & 31) * 255 / 31), uint8_t(((c >> 5) & 63) * 255 / 63),
                         uint8_t((c & 31) * 255 / 31)};
        std::fwrite(rgb, 1, 3, f);
    }
    std::fclose(f);
}
static void snapshot(const char *name, WatchScreenId screen) {
    auto start = std::chrono::steady_clock::now();
    UiManager::instance().showScreen(screen);
    save(name);
    auto ms =
        std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count();
    std::printf("%-20s %.1f ms (host incl. export)\n", name, ms);
}

int main(int argc, char **argv) {
    auto &d = DisplayCO5300::instance();
    auto &ui = UiManager::instance();
    assert(d.init() == ESP_OK);
    assert(ui.init() == ESP_OK);
    // Regression: blending respects RGB565 wire byte order and clipped drawing
    // cannot touch memory beyond the original 410x502 framebuffer (ASan/UBSan).
    d.clear(0);
    d.blendPixel(0, 0, 0xffff, 8);
    uint16_t raw = d.previewFramebuffer()[0], pixel = (raw >> 8) | (raw << 8);
    assert(((pixel >> 11) & 31) == 17);
    assert(((pixel >> 5) & 63) == 34);
    assert((pixel & 31) == 17);
    d.fillSmoothRoundRect(-22, -18, 90, 60, 20, 0x1234);
    d.fillSmoothCircle(410, 502, 30, 0xabcd);
    d.drawText(390, 490, "A long clipping check", 0xffff, UiAssets::Font::Body, false, 18);
    assert(d.measureText("14:24", UiAssets::Font::Clock) < 340);
    assert(d.measureText("Emergency SOS", UiAssets::Font::Label) <= 156);
    assert(d.measureText("Phone & Dialer", UiAssets::Font::Label) <= 156);
    assert(d.measureText("Control Center", UiAssets::Font::Label) <= 156);
    SimStatusReport report = {};
    report.csq = 24;
    report.regStatus = NetworkRegStatus::REGISTERED_HOME;
    std::snprintf(report.statusString, sizeof(report.statusString), "Ready");
    ui.updateSimStatus(report);
    // An optional action sequence creates a framebuffer for the browser. All
    // actions travel through the firmware's existing touch / crown dispatcher.
    if (argc > 1) {
        for (int i = 1; i < argc; ++i) {
            const char *action = argv[i];
            if (!std::strcmp(action, "crown"))
                ui.handleCrownButtonPress();
            else if (!std::strcmp(action, "left"))
                ui.handleSwipeLeft();
            else if (!std::strcmp(action, "right"))
                ui.handleSwipeRight();
            else if (!std::strcmp(action, "up"))
                ui.handleSwipeUp();
            else if (!std::strcmp(action, "down"))
                ui.handleSwipeDown();
            else if (!std::strcmp(action, "hold")) {
                ui.startHoldCancellation();
                ui.updateHoldCancellation(65);
            } else if (!std::strncmp(action, "screen:", 7))
                ui.showScreen(static_cast<WatchScreenId>(std::atoi(action + 7)));
            else {
                int x, y;
                if (std::sscanf(action, "%d,%d", &x, &y) == 2)
                    ui.handleTouchTap(x, y);
            }
            previewTime += 350000;
            ui.tick();
        }
        ui.showScreen(ui.getCurrentScreen());
        save("frame");
        return 0;
    }
    snapshot("fall-detection", WatchScreenId::SCREEN_FALL_DETECTION);
    snapshot("phone-link", WatchScreenId::SCREEN_PHONE_LINK);
    ui.showScreen(WatchScreenId::SCREEN_WALLPAPERS);
    for (int i = 0; i < 5; ++i) {
        ui.handleTouchTap(100, 70 + i * 72);
        ui.handleTouchTap(100, 451);
        save("wallpaper-" + std::to_string(i));
        ui.showScreen(WatchScreenId::SCREEN_WALLPAPERS);
    }
    ui.handleTouchTap(100, 70);
    ui.handleTouchTap(100, 451);
    previewTime += 3000000;
    ui.tick();
    snapshot("home", WatchScreenId::SCREEN_SAFE);
    // Connectivity changes must redraw even when the RTC minute does not change.
    auto iconPixels = [&]() {
        std::string bytes;
        auto pixels = d.previewFramebuffer();
        for (int y = 19; y < 41; ++y)
            for (int x = 194; x < 216; ++x) {
                uint16_t pixel = pixels[y * 410 + x];
                bytes.append(reinterpret_cast<const char *>(&pixel), sizeof(pixel));
            }
        return bytes;
    };
    auto readyIcon = iconPixels();
    previewPhoneReady = false;
    previewTime += 1100000;
    ui.tick();
    auto bleOnlyIcon = iconPixels();
    assert(readyIcon != bleOnlyIcon);
    save("home-bluetooth-paired");
    previewPhoneConnected = false;
    previewTime += 1100000;
    ui.tick();
    auto offlineIcon = iconPixels();
    assert(offlineIcon != bleOnlyIcon && offlineIcon != readyIcon);
    save("home-bluetooth-offline");
    previewPhoneConnected = previewPhoneReady = true;
    previewTime += 1100000;
    ui.tick();
    assert(iconPixels() == readyIcon);
    snapshot("apps-1", WatchScreenId::SCREEN_LAUNCHPAD);
    ui.handleTouchTap(340, 448);
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_LAUNCHPAD);
    save("apps-2");
    ui.handleTouchTap(340, 448);
    save("apps-3");
    ui.handleTouchTap(340, 448);
    save("apps-4");
    ui.handleTouchTap(60, 448);
    ui.handleTouchTap(110, 300);
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_CONTROL_CENTER);
    snapshot("contacts", WatchScreenId::SCREEN_CONTACTS);
    ui.handleTouchTap(250, 80);
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_CALLING);
    snapshot("calling", WatchScreenId::SCREEN_CALLING);
    ui.showScreen(WatchScreenId::SCREEN_CONTACTS);
    ui.handleTouchTap(334, 80);
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_SMS_SENT);
    snapshot("message", WatchScreenId::SCREEN_SMS_SENT);
    snapshot("keypad", WatchScreenId::SCREEN_DIALER);
    ui.handleTouchTap(90, 149);
    ui.handleTouchTap(200, 149);
    ui.handleTouchTap(310, 149);
    save("keypad-number");
    snapshot("sounds", WatchScreenId::SCREEN_SOUND_STUDIO);
    ui.handleTouchTap(205, 338);
    snapshot("sounds-playing", WatchScreenId::SCREEN_SOUND_STUDIO);
    AudioManager::instance().stopSound();
    snapshot("controls", WatchScreenId::SCREEN_CONTROL_CENTER);
    snapshot("motion", WatchScreenId::SCREEN_TELEMETRY);
    snapshot("system", WatchScreenId::SCREEN_SYSTEM);
    snapshot("guard", WatchScreenId::SCREEN_WATCH);
    snapshot("sos", WatchScreenId::SCREEN_EMERGENCY);
    ui.handleTouchTap(205, 400);
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_CANCEL);
    ui.finishHoldCancellation(); // Direct and percentage-based bypasses must fail.
    ui.updateHoldCancellation(100);
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_CANCEL);
    previewTime += 10000000;
    ui.handleCrownButtonPress();
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_CANCEL);
    previewTime += 2000000;
    ui.handleSideButtonPress();
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_CANCEL);
    previewTime += 2000000;
    ui.handleSideButtonLongPress();
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_CANCEL);
    ui.handleTouchDrag(205, 350, true);
    for (int i = 0; i < 29; ++i) {
        previewTime += 100000;
        ui.handleTouchDrag(205, 350, true);
        assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_CANCEL);
    }
    ui.handleTouchDrag(20, 350, true); // Leaving the target resets the deadline.
    ui.handleTouchDrag(205, 350, true);
    for (int i = 0; i < 29; ++i) {
        previewTime += 100000; ui.handleTouchDrag(205, 350, true);
    }
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_CANCEL);
    previewTime += 100000; ui.handleTouchDrag(205, 350, true);
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_SAFE);
    snapshot("cancel", WatchScreenId::SCREEN_CANCEL);
    ui.startHoldCancellation();
    for (int i = 0; i < 19; ++i) {
        previewTime += 100000; ui.handleTouchDrag(205, 350, true);
    }
    save("cancel-holding");
    // Preview sounds cannot overwrite the stored SOS choice.
    for (int i = 0; i < 5; ++i) {
        ui.showScreen(WatchScreenId::SCREEN_SOUND_STUDIO);
        ui.handleTouchTap(205, 80 + i * 50);
        auto expected = static_cast<AlarmSoundType>(i);
        assert(AudioManager::instance().getSelectedAlarmSound() == expected);
        AudioManager::instance().playSound(AlarmSoundType::POLICE_WAIL);
        AudioManager::instance().playSirenAlarm(true);
        assert(AudioManager::instance().getCurrentSound() == expected);
    }
    AudioManager::instance().stopSound();
    snapshot("recorder", WatchScreenId::SCREEN_RECORDER);
    ui.handleTouchTap(205, 190);
    previewTime += 13000000;
    ui.tick();
    save("recording");
    ui.handleTouchTap(205, 350);
    previewTime += 5000000;
    ui.showScreen(WatchScreenId::SCREEN_RECORDER);
    save("memo-saved");
    ui.handleTouchTap(110, 340);
    ui.tick();
    save("memo-playing");
    previewSavedMemos = true;
    ui.showScreen(WatchScreenId::SCREEN_SAFE);
    ui.showScreen(WatchScreenId::SCREEN_RECORDER);
    save("memo-library");
    ui.handleTouchTap(320, 392); // Page two.
    save("memo-library-page-2");
    ui.handleTouchTap(205, 100); // Open an existing persisted recording.
    save("memo-detail");
    ui.handleTouchTap(205, 336);
    assert(AudioManager::instance().isPlayingVoiceMemo());
    assert(AudioManager::instance().getRecordedSamples() == 48000); // Correct ID, not latest RAM.
    save("memo-stored-playing");
    ui.handleTouchTap(205, 400); // Delete only opens confirmation and stops playback.
    assert(!AudioManager::instance().isPlayingVoiceMemo());
    assert(MemoStore::instance().catalog().size() == 6);
    save("memo-delete-confirm");
    ui.handleTouchTap(205, 400); // A second tap at the old position keeps the recording.
    assert(MemoStore::instance().catalog().size() == 6);
    ui.handleTouchTap(205, 336); // Playback still works after cancel.
    assert(AudioManager::instance().isPlayingVoiceMemo());
    ui.handleTouchTap(205, 336);
    assert(!AudioManager::instance().isPlayingVoiceMemo());
    ui.handleTouchTap(205, 450); // Back to the library.
    ui.handleTouchTap(205, 450); // New recording, without deleting older ones.
    assert(AudioManager::instance().isRecordingVoiceMemo());
    assert(MemoStore::instance().catalog().size() == 6);
    ui.handleTouchTap(205, 350);
    ui.handleTouchTap(205, 409); // Saved memos shortcut from the capture view.
    ui.handleTouchTap(205, 100);
    ui.handleTouchTap(205, 400);
    previewDeletionFails = true;
    ui.handleTouchTap(205, 336);
    assert(MemoStore::instance().catalog().size() == 6); // Storage failure preserves the entry.
    previewDeletionFails = false;
    ui.handleTouchTap(205, 400);
    ui.handleTouchTap(205, 336);
    assert(MemoStore::instance().catalog().size() == 5);
    assert(AudioManager::instance().getRecordedSamples() == 0);
    save("memo-deleted");
    while (!MemoStore::instance().catalog().empty()) {
        auto before = MemoStore::instance().catalog().size();
        ui.handleTouchTap(205, 100);
        ui.handleTouchTap(205, 400);
        if (before == 5) save("memo-delete-synced");
        ui.handleTouchTap(205, 336);
        assert(MemoStore::instance().catalog().size() + 1 == before);
    }
    save("memo-library-empty");
    ui.handleTouchTap(205, 450);
    assert(AudioManager::instance().isRecordingVoiceMemo());
    ui.showScreen(WatchScreenId::SCREEN_SAFE);
    previewSavedMemos = false;
    snapshot("faces", WatchScreenId::SCREEN_WALLPAPERS);
    for (int i = 0; i < 5; ++i) {
        ui.showScreen(WatchScreenId::SCREEN_WALLPAPERS);
        ui.handleTouchTap(200, 88 + i * 72);
        ui.handleTouchTap(205, 450);
        previewTime += 5000000;
        ui.showScreen(WatchScreenId::SCREEN_SAFE);
        save("face-" + std::to_string(i));
    }
    snapshot("torch", WatchScreenId::SCREEN_FLASHLIGHT);
    ui.handleTouchTap(205, 200);
    save("torch-warm");
    ui.handleTouchTap(205, 200);
    save("torch-strobe");
    auto red = d.previewFramebuffer()[250 * 410 + 205];
    previewTime += 100000; ui.tick();
    auto blue = d.previewFramebuffer()[250 * 410 + 205];
    assert(red != blue);
    previewTime += 100000; ui.tick();
    assert(d.previewFramebuffer()[250 * 410 + 205] == red);
    std::puts("RGB565 blending, bounds, glyph widths, drawer paging, keypad, contacts, SOS "
              "cancellation: PASS");
}
