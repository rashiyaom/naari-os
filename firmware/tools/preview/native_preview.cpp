// Host-only device mocks. This executable links the real UiManager and graphics
// implementations. Cellular, audio, timers and sensors never access hardware.
#include "drivers/audio_es7210_es8311/audio_manager.h"
#include "drivers/axp2101/axp2101.h"
#include "drivers/display_co5300/display_co5300.h"
#include "drivers/imu_qmi8658/imu_qmi8658.h"
#include "drivers/rtc_pcf85063/rtc_pcf85063.h"
#include "drivers/touch_ft3168/touch_ft3168.h"
#include "network/phone_bridge.h"
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
    s.connected = s.ready = s.smsAllowed = s.callsAllowed = s.locationAllowed = true;
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
    std::snprintf(r.statusString, sizeof(r.statusString), "Phone connected");
    return r;
}

SIM800L &SIM800L::instance() {
    static SIM800L d;
    return d;
}
bool SIM800L::requestVoiceCall(const char *) { return true; }
bool SIM800L::requestSendLocationSms(const char *, const char *, uint8_t) { return true; }
bool SIM800L::requestPowerDown() { return true; }
StateMachine::StateMachine()
    : m_currentState(SystemState::SAFE), m_gracePeriodActive(true), m_graceRemainingSec(30) {}
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
    assert(ui.getCurrentScreen() == WatchScreenId::SCREEN_SAFE);
    previewTime += 10000000;
    snapshot("cancel", WatchScreenId::SCREEN_CANCEL);
    ui.updateHoldCancellation(65);
    ui.tick();
    save("cancel-holding");
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
    std::puts("RGB565 blending, bounds, glyph widths, drawer paging, keypad, contacts, SOS "
              "cancellation: PASS");
}
