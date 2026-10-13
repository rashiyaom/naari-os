// ─────────────────────────────────────────────────────────────────────────────
//  Naari OS · UiManager v7.0  –  Smooth native AMOLED interface
//
//  Display: 410 × 502  (CO5300 AMOLED, portrait, RGB565)
// ─────────────────────────────────────────────────────────────────────────────
#include "ui_manager.h"
#include "../network/phone_bridge.h"
#include "../storage/preferences.h"
#include "wallpaper_assets.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <esp_heap_caps.h>
#include <esp_log.h>
#include <esp_system.h>
#include <esp_timer.h>
#ifndef NAARI_UI_PREVIEW
#include <freertos/semphr.h>
#endif
#include "../drivers/audio_es7210_es8311/audio_manager.h"
#include "../drivers/axp2101/axp2101.h"
#include "../drivers/display_co5300/display_co5300.h"
#include "../drivers/imu_qmi8658/imu_qmi8658.h"
#include "../drivers/rtc_pcf85063/rtc_pcf85063.h"
#include "../drivers/touch_ft3168/touch_ft3168.h"

static const char *TAG = "NAARI_UI";

namespace SheShield {
#ifndef NAARI_UI_PREVIEW
static SemaphoreHandle_t s_uiMutex;
struct UiLock {
    UiLock() {
        if (!s_uiMutex)
            s_uiMutex = xSemaphoreCreateRecursiveMutex();
        xSemaphoreTakeRecursive(s_uiMutex, portMAX_DELAY);
    }
    ~UiLock() { xSemaphoreGiveRecursive(s_uiMutex); }
};
#else
struct UiLock {};
#endif
static constexpr int kDrawerPageCount = 4;
static void drawWallpaper(int index, int x = 0, int y = 0, int w = 410, int h = 502) {
    auto &display = DisplayCO5300::instance();
    if (index < 1 || index > 4) {
        display.fillRect(x, y, w, h, 0);
        return;
    }
    auto data = WallpaperAssets::pixels[index];
    auto palette = WallpaperAssets::palettes[index];
    for (int dy = 0; dy < h; ++dy)
        for (int dx = 0; dx < w; ++dx) {
            int pixel = (dy * 502 / h) * 410 + (dx * 410 / w);
            uint8_t byte = data[pixel / 2];
            uint8_t color = (pixel & 1) ? byte & 15 : byte >> 4;
            display.drawPixel(x + dx, y + dy, palette[color]);
        }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DESIGN TOKENS  (Apple Watch-inspired OLED palette, RGB565)
// ─────────────────────────────────────────────────────────────────────────────
using Font = UiAssets::Font;
using Symbol = UiAssets::Symbol;
static constexpr uint16_t rgb(unsigned r, unsigned g, unsigned b) {
    return ((r & 248) << 8) | ((g & 252) << 3) | (b >> 3);
}
static uint16_t mix(uint16_t a, uint16_t b, unsigned weight) {
    unsigned r = (((a >> 11) & 31) * (100 - weight) + ((b >> 11) & 31) * weight) / 100;
    unsigned g = (((a >> 5) & 63) * (100 - weight) + ((b >> 5) & 63) * weight) / 100;
    unsigned bl = ((a & 31) * (100 - weight) + (b & 31) * weight) / 100;
    return (r << 11) | (g << 5) | bl;
}
static constexpr uint16_t kBlack = 0;
static constexpr uint16_t kInk = rgb(8, 8, 10);
static constexpr uint16_t kSurface = rgb(16, 16, 16);
static constexpr uint16_t kCard = rgb(24, 24, 24);
static constexpr uint16_t kCardHi = rgb(40, 40, 41);
static constexpr uint16_t kSep = rgb(48, 48, 49);
static constexpr uint16_t kTitanium = rgb(76, 76, 81);
static constexpr uint16_t kGlassLine = rgb(48, 48, 52);
static constexpr uint16_t kWhite = 0xffff;
static constexpr uint16_t kTxtSoft = rgb(220, 220, 225);
static constexpr uint16_t kTxtMuted = rgb(160, 160, 168);
static constexpr uint16_t kTxtDim = rgb(114, 114, 122);
static constexpr uint16_t kMint = rgb(66, 220, 153);
static constexpr uint16_t kMintDim = rgb(17, 47, 33);
static constexpr uint16_t kCoral = rgb(255, 82, 101);
static constexpr uint16_t kCoralDark = rgb(55, 17, 25);
static constexpr uint16_t kCyan = rgb(91, 211, 240);
static constexpr uint16_t kCyanDim = rgb(15, 39, 47);
static constexpr uint16_t kAmber = rgb(255, 196, 86);
static constexpr uint16_t kAmberDim = rgb(49, 37, 16);
static constexpr uint16_t kBlue = rgb(81, 151, 255);
static constexpr uint16_t kBlueDim = rgb(18, 34, 58);
static constexpr uint16_t kPurple = rgb(179, 143, 255);
static constexpr uint16_t kPurpleDim = rgb(35, 25, 52);
static constexpr uint16_t kOrange = rgb(255, 149, 65);

// Grace-window length shown in UI
static constexpr uint32_t kGraceWindowSec = 30;
// Calling overlay auto-dismiss
static constexpr int64_t kCallingOverlayUs = 30000000LL;
// SMS overlay auto-dismiss

// Side-button minimum re-press interval
static constexpr int64_t kBtnDebounceUs = 250000LL;

// ─────────────────────────────────────────────────────────────────────────────
//  INLINE DRAWING WRAPPERS
// ─────────────────────────────────────────────────────────────────────────────
static inline DisplayCO5300 &D() { return DisplayCO5300::instance(); }

void UiManager::drawHLine(int x, int y, int w, uint16_t col) { D().drawHLine(x, y, w, col); }
void UiManager::drawVLine(int x, int y, int h, uint16_t col) { D().drawVLine(x, y, h, col); }
void UiManager::fillRect(int x, int y, int w, int h, uint16_t col) {
    D().fillRect(x, y, w, h, col);
}
void UiManager::fillRR(int x, int y, int w, int h, int r, uint16_t col) {
    D().fillRoundedRect(x, y, w, h, r, col);
}
void UiManager::drawRR(int x, int y, int w, int h, int r, uint16_t col) {
    D().drawRoundedRect(x, y, w, h, r, col);
}
void UiManager::fillCirc(int cx, int cy, int r, uint16_t col) { D().fillCircle(cx, cy, r, col); }
void UiManager::drawCirc(int cx, int cy, int r, uint16_t col) { D().drawCircle(cx, cy, r, col); }
void UiManager::drawStr(int cx, int cy, const char *s, uint16_t col, uint8_t sc, bool c) {
    D().drawString(cx, cy, s, col, sc, c);
}
void UiManager::drawStrBold(int x, int y, const char *s, uint16_t col, uint8_t scale,
                            bool centered) {
    Font font = scale >= 4   ? Font::Timer
                : scale == 3 ? Font::Display
                : scale == 2 ? Font::Title
                             : Font::Label;
    D().drawText(x, y, s, col, font, centered);
}
void UiManager::drawDivider(int y) { D().drawHLine(24, y, 362, kSep); }

// Consistent screen back-navigation header
void UiManager::drawNavBack(int y) {
    D().drawSymbol(35, y + 19, 18, Symbol::Left, kTxtSoft);
    D().drawText(50, y + 7, "Back", kTxtSoft, Font::Body);
}

void UiManager::drawPremiumBackdrop(uint16_t accent) {
    (void)accent;
    D().clear(kBlack);
}

void UiManager::drawPremiumHeader(const char *title, uint16_t accent, bool showBack) {
    (void)accent;
    if (showBack)
        drawNavBack(12);
    D().drawText(showBack ? 270 : 205, 18, title, kWhite, Font::Title, true, showBack ? 224 : 350);
}

void UiManager::drawPremiumCard(int x, int y, int w, int h, int radius, uint16_t accent,
                                bool active) {
    fillRR(x, y, w, h, radius, active ? mix(kCard, accent, 14) : kCard);
}

void UiManager::drawIconTile(int cx, int cy, int size, uint16_t accent, int iconType) {
    int x = cx - size / 2, y = cy - size / 2, r = size / 3;
    // Row-by-row gradient inside the same antialiased rounded silhouette.
    for (int row = 0; row < size; ++row) {
        float dy = row < r ? r - row - 0.5f : row >= size - r ? row - (size - r) + 0.5f : 0;
        float inset = dy > 0 ? r - std::sqrt(r * r - dy * dy) : 0;
        int edge = int(std::ceil(inset));
        // Ordered subpixel quantization avoids stripes on a 16-bit panel.
        static constexpr uint8_t bayer[16] = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        float lift = 1.08f - 0.23f * row / (size - 1);
        float red = std::min(31.0f, ((accent >> 11) & 31) * lift);
        float green = std::min(63.0f, ((accent >> 5) & 63) * lift);
        float blue = std::min(31.0f, (accent & 31) * lift);
        int first = std::max(0, edge - 1), last = std::min(size, size - edge + 1);
        for (int col = first; col < last; ++col) {
            float threshold = (bayer[(row % 4) * 4 + col % 4] + 0.5f) / 16.0f;
            uint16_t color = (int(red + threshold) << 11) | (int(green + threshold) << 5) |
                             int(blue + threshold);
            if (col >= edge && col < size - edge)
                D().drawPixel(x + col, y + row, color);
            else
                D().blendPixel(x + col, y + row, color,
                               uint8_t(std::clamp((edge - inset) * 15.0f, 0.0f, 15.0f)));
        }
    }
    Symbol sym = Symbol::Grid;
    switch (iconType) {
    case 1:
        sym = Symbol::Phone;
        break;
    case 2:
        sym = Symbol::Person;
        break;
    case 3:
        sym = Symbol::ShieldCheck;
        break;
    case 4:
        sym = Symbol::Music;
        break;
    case 5:
        sym = Symbol::Torch;
        break;
    case 6:
        sym = Symbol::Compass;
        break;
    case 7:
        sym = Symbol::Alert;
        break;
    case 8:
        sym = Symbol::Sliders;
        break;
    case 9:
        sym = Symbol::Gear;
        break;
    case 11:
        sym = Symbol::ShieldCheck;
        break;
    case 12:
        sym = Symbol::Mic;
        break;
    case 13:
        sym = Symbol::Wallpaper;
        break;
    }
    D().drawSymbol(cx, cy, size * 3 / 5, sym, kWhite);
}

void UiManager::drawAction(int x, int y, int w, int h, const char *title, uint16_t bg,
                           uint16_t fg) {
    fillRR(x, y, w, h, h / 2, bg);
    D().drawText(x + w / 2, y + (h - 22) / 2, title, fg, Font::Label, true, w - 16);
}
void UiManager::drawFooter(const char *title, int y) {
    drawAction(24, y, 362, 44, title, kCard, kTxtSoft);
}

// ─────────────────────────────────────────────────────────────────────────────
//  PIXEL-DRAWN SF-SYMBOL ICONS
// ─────────────────────────────────────────────────────────────────────────────

void UiManager::drawIconPhone(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Phone, col);
}

void UiManager::drawIconShield(int cx, int cy, int sz, uint16_t col, bool filled) {
    D().drawSymbol(cx, cy, sz, filled ? Symbol::ShieldCheck : Symbol::Shield, col);
}

void UiManager::drawIconAlert(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Alert, col);
}

void UiManager::drawIconMusic(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Music, col);
}

void UiManager::drawIconGrid(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Grid, col);
}

void UiManager::drawIconBattery(int cx, int cy, int pct, uint16_t col) {
    int w = 24, h = 12;
    int x = cx - w / 2, y = cy - h / 2;
    drawRR(x, y, w, h, 3, col);
    drawVLine(x + w, y + 3, h - 6, col);
    int fillW = ((w - 4) * pct) / 100;
    if (fillW > 0)
        fillRR(x + 2, y + 2, fillW, h - 4, 1, col);
}

void UiManager::drawIconSignal(int cx, int cy, int csq, uint16_t col) {
    int bars = (csq > 25) ? 4 : (csq > 18) ? 3 : (csq > 10) ? 2 : (csq > 0) ? 1 : 0;
    int x = cx - 10, y = cy + 6;
    for (int b = 0; b < 4; b++) {
        int bh = (b + 1) * 3 + 2;
        uint16_t c = (b < bars) ? col : kSep;
        fillRect(x + b * 5, y - bh, 3, bh, c);
    }
}

void UiManager::drawIconWifi(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Wifi, col);
}

void UiManager::drawIconVolume(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Volume, col);
}

void UiManager::drawIconPerson(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Person, col);
}

void UiManager::drawIconFlash(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Torch, col);
}

void UiManager::drawIconCompass(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Compass, col);
}

void UiManager::drawIconCall(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Phone, col);
}

void UiManager::drawIconMessage(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Message, col);
}

void UiManager::drawIconEndCall(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::EndCall, col);
}

void UiManager::drawIconKeypad(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Keypad, col);
}

void UiManager::drawIconSliders(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Sliders, col);
}

void UiManager::drawIconGear(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Gear, col);
}

void UiManager::drawIconClock(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Clock, col);
}

// Microphone icon (Voice Recorder)
void UiManager::drawIconMicrophone(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Mic, col);
}

// Wallpaper / Photo icon
void UiManager::drawIconWallpaper(int cx, int cy, int sz, uint16_t col) {
    D().drawSymbol(cx, cy, sz, Symbol::Wallpaper, col);
}

// ─────────────────────────────────────────────────────────────────────────────
//  SINGLETON & CONSTRUCTOR
// ─────────────────────────────────────────────────────────────────────────────
UiManager &UiManager::instance() {
    static UiManager s;
    return s;
}

UiManager::UiManager()
    : m_initialized(false), m_currentScreen(WatchScreenId::SCREEN_SAFE),
      m_prevScreen(WatchScreenId::SCREEN_SAFE), m_cancelFromScreen(WatchScreenId::SCREEN_SAFE),
      m_systemState(SystemState::SAFE), m_sirenTestActive(false),
      m_flashlightMode(FlashlightMode::WHITE_100), m_strobeState(false), m_batteryPercent(94),
      m_timeStr("10:00"), m_dateStr("SAT, 04 OCT"), m_dialedNumber(""), m_toastExpiryUs(0),
      m_emergencyCountdownSec(kGraceWindowSec), m_lastGraceTickUs(0), m_holdProgress(0.0f),
      m_cancelConfirmed(false), m_holdingCancel(false), m_holdStartTimeUs(0), m_overlayStartUs(0),
      m_isTouched(false), m_touchStartX(0), m_touchStartY(0), m_lastTouchX(0), m_lastTouchY(0),
      m_touchStartTimeUs(0), m_lastSideBtnPressUs(0), m_lastCrownBtnPressUs(0),
      m_drawerScrollY(0.0f), m_drawerVelY(0.0f), m_launcherPanX(0.0f), m_launcherPanY(0.0f),
      m_launcherVelX(0.0f), m_launcherVelY(0.0f), m_lastDragX(0), m_lastDragY(0),
      m_isDraggingLauncher(false), m_lastDragTimeUs(0), m_drawerPage(0), m_wallpaperIdx(0),
      m_isRecordingMemo(false), m_hasRecordedMemo(false), m_isPlayingMemo(false),
      m_memoDurationSec(0), m_recordStartTimeUs(0), m_playbackStartTimeUs(0), m_safeTouchDownUs(0),
      m_animFrame(0), m_lastRenderTimeUs(0), m_lastSecondTickUs(0) {
    m_toastMessage[0] = '\0';
    m_overlayName[0] = '\0';
    m_overlayNumber[0] = '\0';
    std::memset(&m_lastSimStatus, 0, sizeof(m_lastSimStatus));
    m_lastSimStatus.csq = 99;
    std::snprintf(m_lastSimStatus.statusString, sizeof(m_lastSimStatus.statusString), "Ready");
}

// ─────────────────────────────────────────────────────────────────────────────
//  INIT
// ─────────────────────────────────────────────────────────────────────────────
esp_err_t UiManager::init() {
    UiLock uiLock;
    ESP_LOGI(TAG, "Naari OS v8 - audio, watch faces, Android BLE, fall detection");
    m_initialized = true;

    char tBuf[16], dBuf[32];
    RtcPCF85063::instance().getTimeString(tBuf, sizeof(tBuf));
    RtcPCF85063::instance().getDateString(dBuf, sizeof(dBuf));
    m_timeStr = tBuf;
    m_dateStr = dBuf;
    m_lastSecondTickUs = esp_timer_get_time();
    m_lastSimStatus = SIM800L::instance().getStatus();

    m_wallpaperIdx = std::min<int>(4, readPreference("wallpaper", 0));
    m_wallpaperSelection = m_wallpaperIdx;
    showScreen(WatchScreenId::SCREEN_SAFE);
    return ESP_OK;
}

// ─────────────────────────────────────────────────────────────────────────────
//  SHOW SCREEN (central dispatcher)
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::showScreen(WatchScreenId id) {
    UiLock uiLock;
    int64_t renderStartUs = esp_timer_get_time();
    m_prevScreen = m_currentScreen;
    // Stop recorder resources on every exit path (crown, swipe, state change).
    if (m_currentScreen == WatchScreenId::SCREEN_RECORDER && id != m_currentScreen) {
        if (m_isRecordingMemo)
            AudioManager::instance().stopVoiceMemoRecording();
        if (m_isPlayingMemo)
            AudioManager::instance().stopVoiceMemoPlayback();
        m_isRecordingMemo = m_isPlayingMemo = false;
        m_hasRecordedMemo = AudioManager::instance().getRecordedSamples() > 0;
        m_memoDurationSec = AudioManager::instance().getRecordedDurationSec();
    }
    if (m_currentScreen == WatchScreenId::SCREEN_FALL_DETECTION && id != m_currentScreen)
        m_fallDemoStartUs = 0;
    m_currentScreen = id;
    m_holdProgress = 0.0f;
    m_cancelConfirmed = false;
    m_holdingCancel = false;

    switch (id) {
    case WatchScreenId::SCREEN_SAFE:
        renderSafeScreen();
        break;
    case WatchScreenId::SCREEN_LAUNCHPAD:
        renderLaunchpadScreen();
        break;
    case WatchScreenId::SCREEN_SOUND_STUDIO:
        renderSoundStudioScreen();
        break;
    case WatchScreenId::SCREEN_CONTROL_CENTER:
        renderControlCenterScreen();
        break;
    case WatchScreenId::SCREEN_FLASHLIGHT:
        renderFlashlightScreen();
        break;
    case WatchScreenId::SCREEN_TELEMETRY:
        renderTelemetryScreen();
        break;
    case WatchScreenId::SCREEN_SYSTEM:
        renderSystemScreen();
        break;
    case WatchScreenId::SCREEN_WATCH:
        renderWatchScreen();
        break;
    case WatchScreenId::SCREEN_EMERGENCY:
        m_emergencyCountdownSec = kGraceWindowSec;
        m_lastGraceTickUs = esp_timer_get_time();
        renderEmergencyScreen();
        break;
    case WatchScreenId::SCREEN_CONTACTS:
        renderContactsScreen();
        break;
    case WatchScreenId::SCREEN_DIALER:
        renderDialerScreen();
        break;
    case WatchScreenId::SCREEN_CANCEL:
        renderCancelScreen();
        break;
    case WatchScreenId::SCREEN_CALLING:
        renderCallingScreen();
        break;
    case WatchScreenId::SCREEN_SMS_SENT:
        renderSmsSentScreen();
        break;
    case WatchScreenId::SCREEN_RECORDER:
        renderRecorderScreen();
        break;
    case WatchScreenId::SCREEN_FALL_DETECTION:
        renderFallDetectionScreen();
        break;
    case WatchScreenId::SCREEN_PHONE_LINK:
        renderPhoneLinkScreen();
        break;
    case WatchScreenId::SCREEN_WALLPAPERS:
        renderWallpapersScreen();
        break;
    }
    ESP_LOGI(TAG, "Rendered screen %d in %lld ms", static_cast<int>(id),
             static_cast<long long>((esp_timer_get_time() - renderStartUs) / 1000));
}

// ─────────────────────────────────────────────────────────────────────────────
//  STATE / SIM OBSERVERS
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::updateState(SystemState state) {
    UiLock uiLock;
    m_systemState = state;
    switch (state) {
    case SystemState::SAFE:
        showScreen(WatchScreenId::SCREEN_SAFE);
        break;
    case SystemState::SAFETY_WATCH:
        showScreen(WatchScreenId::SCREEN_WATCH);
        break;
    case SystemState::EMERGENCY:
        showScreen(WatchScreenId::SCREEN_EMERGENCY);
        break;
    }
}

void UiManager::updateSimStatus(const SimStatusReport &r) {
    UiLock uiLock;
    m_lastSimStatus = r;
}

// ─────────────────────────────────────────────────────────────────────────────
//  TOAST
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::showToast(const char *msg, int durationMs) {
    if (!msg)
        return;
    std::strncpy(m_toastMessage, msg, sizeof(m_toastMessage) - 1);
    m_toastExpiryUs = esp_timer_get_time() + (int64_t)durationMs * 1000;
}

// ─────────────────────────────────────────────────────────────────────────────
//  CALLING / SMS OVERLAYS
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::showCallingScreen(const char *name, const char *number) {
    std::strncpy(m_overlayName, name ? name : "Contact", sizeof(m_overlayName) - 1);
    std::strncpy(m_overlayNumber, number ? number : "", sizeof(m_overlayNumber) - 1);
    m_overlayRequestId = PhoneBridge::instance().lastRequestId();
    m_overlayStartUs = esp_timer_get_time();
    m_cancelFromScreen = m_currentScreen;
    showScreen(WatchScreenId::SCREEN_CALLING);
}

void UiManager::showSmsSentScreen(const char *name, const char *number) {
    std::strncpy(m_overlayName, name ? name : "Contact", sizeof(m_overlayName) - 1);
    std::strncpy(m_overlayNumber, number ? number : "", sizeof(m_overlayNumber) - 1);
    m_overlayRequestId = PhoneBridge::instance().lastRequestId();
    m_overlayStartUs = esp_timer_get_time();
    m_cancelFromScreen = m_currentScreen;
    showScreen(WatchScreenId::SCREEN_SMS_SENT);
}

// ─────────────────────────────────────────────────────────────────────────────
//  PHYSICAL BUTTON INTERFACE
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleCrownButtonPress() {
    UiLock uiLock;
    int64_t now = esp_timer_get_time();
    if (now - m_lastCrownBtnPressUs < 350000LL)
        return;
    m_lastCrownBtnPressUs = now;

    ESP_LOGI(TAG, "Digital Crown click – screen %d", (int)m_currentScreen);

    if (m_currentScreen == WatchScreenId::SCREEN_FLASHLIGHT) {
        m_flashlightMode = FlashlightMode::WHITE_100;
        D().setBrightness(85);
        showScreen(WatchScreenId::SCREEN_SAFE);
        return;
    }

    if (m_currentScreen == WatchScreenId::SCREEN_EMERGENCY ||
        m_currentScreen == WatchScreenId::SCREEN_CANCEL ||
        m_systemState == SystemState::EMERGENCY) {
        ESP_LOGI(TAG, "Crown click in EMERGENCY -> Disarm SOS");
        finishHoldCancellation();
        return;
    }

    switch (m_currentScreen) {
    case WatchScreenId::SCREEN_SAFE:
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        break;
    case WatchScreenId::SCREEN_LAUNCHPAD:
    case WatchScreenId::SCREEN_SOUND_STUDIO:
    case WatchScreenId::SCREEN_TELEMETRY:
    case WatchScreenId::SCREEN_SYSTEM:
    case WatchScreenId::SCREEN_CONTACTS:
    case WatchScreenId::SCREEN_DIALER:
    case WatchScreenId::SCREEN_CONTROL_CENTER:
    case WatchScreenId::SCREEN_WATCH:
    case WatchScreenId::SCREEN_RECORDER:
    case WatchScreenId::SCREEN_FALL_DETECTION:
    case WatchScreenId::SCREEN_PHONE_LINK:
    case WatchScreenId::SCREEN_WALLPAPERS:
        showScreen(WatchScreenId::SCREEN_SAFE);
        break;
    case WatchScreenId::SCREEN_CALLING:
        showScreen(m_cancelFromScreen);
        break;
    case WatchScreenId::SCREEN_SMS_SENT:
        showScreen(m_cancelFromScreen);
        break;
    default:
        showScreen(WatchScreenId::SCREEN_SAFE);
        break;
    }
}

void UiManager::handleCrownButtonDoublePress() {
    UiLock uiLock;
    int64_t now = esp_timer_get_time();
    if (now - m_lastCrownBtnPressUs < 350000LL)
        return;
    m_lastCrownBtnPressUs = now;

    if (m_prevScreen != m_currentScreen && m_prevScreen != WatchScreenId::SCREEN_EMERGENCY) {
        showScreen(m_prevScreen);
    } else {
        showScreen(WatchScreenId::SCREEN_SAFE);
    }
}

void UiManager::handleSideButtonPress() {
    UiLock uiLock;
    int64_t now = esp_timer_get_time();
    if (now - m_lastSideBtnPressUs < 400000LL)
        return;
    m_lastSideBtnPressUs = now;

    ESP_LOGI(TAG, "Side Button click – screen %d", (int)m_currentScreen);

    // If on Emergency or Cancel screen, side button click DISARMS SOS
    if (m_currentScreen == WatchScreenId::SCREEN_EMERGENCY ||
        m_currentScreen == WatchScreenId::SCREEN_CANCEL ||
        m_systemState == SystemState::EMERGENCY) {
        ESP_LOGI(TAG, "Side Button click in EMERGENCY -> Disarm SOS");
        finishHoldCancellation();
        return;
    }

    if (m_currentScreen == WatchScreenId::SCREEN_CALLING) {
        showScreen(m_cancelFromScreen);
        return;
    }

    if (m_currentScreen == WatchScreenId::SCREEN_FLASHLIGHT) {
        m_flashlightMode = FlashlightMode::WHITE_100;
        D().setBrightness(85);
        showScreen(WatchScreenId::SCREEN_SAFE);
        return;
    }

    if (m_currentScreen == WatchScreenId::SCREEN_CONTROL_CENTER) {
        showScreen(m_prevScreen == WatchScreenId::SCREEN_CONTROL_CENTER ? WatchScreenId::SCREEN_SAFE
                                                                        : m_prevScreen);
    } else {
        showScreen(WatchScreenId::SCREEN_CONTROL_CENTER);
    }
}

void UiManager::handleSideButtonLongPress() {
    UiLock uiLock;
    int64_t now = esp_timer_get_time();
    if (now - m_lastSideBtnPressUs < 1500000LL)
        return; // 1.5s debounce latch
    m_lastSideBtnPressUs = now;

    // Holding side button while in EMERGENCY disarms the alert!
    if (m_currentScreen == WatchScreenId::SCREEN_EMERGENCY ||
        m_currentScreen == WatchScreenId::SCREEN_CANCEL ||
        m_systemState == SystemState::EMERGENCY) {
        ESP_LOGI(TAG, "Side Button LONG PRESS while in EMERGENCY -> DISARM SOS!");
        finishHoldCancellation();
        return;
    }

    ESP_LOGW(TAG, "Side Button LONG PRESS (>2s) -> SOS Triggered!");
    showToast("EMERGENCY SOS!", 3000);
    showScreen(WatchScreenId::SCREEN_EMERGENCY);
    StateMachine::instance().sendEvent(StateEvent::EVT_MANUAL_SOS_PRESS);
}

// ─────────────────────────────────────────────────────────────────────────────
//  SWIPE GESTURES
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleSwipeLeft() {
    UiLock uiLock;
    switch (m_currentScreen) {
    case WatchScreenId::SCREEN_SAFE:
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        break;
    case WatchScreenId::SCREEN_LAUNCHPAD:
        if (m_drawerPage < kDrawerPageCount - 1) {
            m_drawerPage++;
            renderLaunchpadScreen();
        } else {
            showScreen(WatchScreenId::SCREEN_CONTACTS);
        }
        break;
    case WatchScreenId::SCREEN_CONTACTS:
        showScreen(WatchScreenId::SCREEN_DIALER);
        break;
    case WatchScreenId::SCREEN_FLASHLIGHT:
        m_flashlightMode = FlashlightMode::WHITE_100;
        D().setBrightness(85);
        showScreen(WatchScreenId::SCREEN_SAFE);
        break;
    default:
        break;
    }
}

void UiManager::handleSwipeRight() {
    UiLock uiLock;
    switch (m_currentScreen) {
    case WatchScreenId::SCREEN_DIALER:
        showScreen(WatchScreenId::SCREEN_CONTACTS);
        break;
    case WatchScreenId::SCREEN_CONTACTS:
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        break;
    case WatchScreenId::SCREEN_LAUNCHPAD:
        if (m_drawerPage > 0) {
            m_drawerPage--;
            renderLaunchpadScreen();
        } else {
            showScreen(WatchScreenId::SCREEN_SAFE);
        }
        break;
    case WatchScreenId::SCREEN_FLASHLIGHT:
        m_flashlightMode = FlashlightMode::WHITE_100;
        D().setBrightness(85);
        showScreen(WatchScreenId::SCREEN_SAFE);
        break;
    case WatchScreenId::SCREEN_RECORDER:
    case WatchScreenId::SCREEN_FALL_DETECTION:
    case WatchScreenId::SCREEN_PHONE_LINK:
    case WatchScreenId::SCREEN_WALLPAPERS:
    case WatchScreenId::SCREEN_SOUND_STUDIO:
    case WatchScreenId::SCREEN_TELEMETRY:
    case WatchScreenId::SCREEN_SYSTEM:
    case WatchScreenId::SCREEN_CONTROL_CENTER:
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        break;
    default:
        break;
    }
}

void UiManager::handleSwipeUp() {
    UiLock uiLock;
    if (m_currentScreen == WatchScreenId::SCREEN_SAFE)
        showScreen(WatchScreenId::SCREEN_CONTROL_CENTER);
}

void UiManager::handleSwipeDown() {
    UiLock uiLock;
    if (m_currentScreen == WatchScreenId::SCREEN_CONTROL_CENTER)
        showScreen(WatchScreenId::SCREEN_SAFE);
    else if (m_currentScreen == WatchScreenId::SCREEN_LAUNCHPAD)
        showScreen(WatchScreenId::SCREEN_SAFE);
    else if (m_currentScreen == WatchScreenId::SCREEN_FLASHLIGHT) {
        m_flashlightMode = FlashlightMode::WHITE_100;
        D().setBrightness(85);
        showScreen(WatchScreenId::SCREEN_SAFE);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TOUCH TAP DISPATCHER
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleTouchTap(int x, int y) {
    UiLock uiLock;
    switch (m_currentScreen) {
    case WatchScreenId::SCREEN_SAFE:
        handleSafeTap(x, y);
        break;
    case WatchScreenId::SCREEN_LAUNCHPAD:
        handleLaunchpadTap(x, y);
        break;
    case WatchScreenId::SCREEN_SOUND_STUDIO:
        handleSoundStudioTap(x, y);
        break;
    case WatchScreenId::SCREEN_CONTROL_CENTER:
        handleControlCenterTap(x, y);
        break;
    case WatchScreenId::SCREEN_FLASHLIGHT:
        handleFlashlightTap(x, y);
        break;
    case WatchScreenId::SCREEN_TELEMETRY:
        handleTelemetryTap(x, y);
        break;
    case WatchScreenId::SCREEN_SYSTEM:
        handleSystemTap(x, y);
        break;
    case WatchScreenId::SCREEN_CONTACTS:
        handleContactsTap(x, y);
        break;
    case WatchScreenId::SCREEN_DIALER:
        handleDialerTap(x, y);
        break;
    case WatchScreenId::SCREEN_WATCH:
        handleWatchTap(x, y);
        break;
    case WatchScreenId::SCREEN_EMERGENCY:
        handleEmergencyTap(x, y);
        break;
    case WatchScreenId::SCREEN_CALLING:
        handleCallingTap(x, y);
        break;
    case WatchScreenId::SCREEN_SMS_SENT:
        handleSmsSentTap(x, y);
        break;
    case WatchScreenId::SCREEN_RECORDER:
        handleRecorderTap(x, y);
        break;
    case WatchScreenId::SCREEN_FALL_DETECTION:
        handleFallDetectionTap(x, y);
        break;
    case WatchScreenId::SCREEN_PHONE_LINK:
        handlePhoneLinkTap(x, y);
        break;
    case WatchScreenId::SCREEN_WALLPAPERS:
        handleWallpapersTap(x, y);
        break;
    case WatchScreenId::SCREEN_CANCEL:
        if (m_cancelConfirmed)
            showScreen(WatchScreenId::SCREEN_SAFE);
        break;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 1 : SAFE  (main watch face)
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleSafeTap(int x, int y) {
    if (y < 52) {
        showScreen(WatchScreenId::SCREEN_CONTROL_CENTER);
        return;
    }
    if (y < 185) {
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        return;
    }
    if (y < 370) {
        int dx = x - 205, dy = y - 276;
        if (dx * dx + dy * dy <= 92 * 92) {
            showToast("Guard mode activated", 2000);
            StateMachine::instance().sendEvent(StateEvent::EVT_ENTER_WATCH_MODE);
        }
        return;
    }
    if (y < 426) {
        if (x < 140)
            showScreen(WatchScreenId::SCREEN_SYSTEM);
        else if (x < 270)
            StateMachine::instance().sendEvent(StateEvent::EVT_ENTER_WATCH_MODE);
        else
            showScreen(WatchScreenId::SCREEN_TELEMETRY);
        return;
    }
    if (x < 96)
        showScreen(WatchScreenId::SCREEN_DIALER);
    else if (x < 168)
        showScreen(WatchScreenId::SCREEN_CONTACTS);
    else if (x < 242) {
        showScreen(WatchScreenId::SCREEN_EMERGENCY);
        StateMachine::instance().sendEvent(StateEvent::EVT_MANUAL_SOS_PRESS);
    } else if (x < 314)
        showScreen(WatchScreenId::SCREEN_SOUND_STUDIO);
    else
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
}

void UiManager::renderSafeScreen() {
    drawWallpaper(m_wallpaperIdx);
    D().drawText(34, 20, m_dateStr.c_str(), kTxtMuted, Font::Caption, false, 222);
    D().drawSymbol(292, 30, 18, Symbol::Phone,
                   PhoneBridge::instance().status().ready ? kMint : kTxtDim);
    char battery[12];
    std::snprintf(battery, sizeof(battery), "%d%%", m_batteryPercent);
    D().drawText(338, 17, battery, kTxtSoft, Font::Body, true, 56);
    drawIconBattery(378, 30, m_batteryPercent, kTxtSoft);

    D().drawText(205, 64, "N A A R I", kTxtMuted, Font::Caption, true);
    D().drawText(205, 77, m_timeStr.c_str(), kWhite, Font::Clock, true, 340);
    fillCirc(159, 181, 3, kMint);
    D().drawText(215, 168, "Protected", kMint, Font::Body, true);

    const int cx = 205, cy = 276;
    const float start = -1.5707963f, tau = 6.2831853f;
    D().drawSmoothArc(cx, cy, 80, 12, 0, tau, mix(kBlack, kCoral, 17));
    D().drawSmoothArc(cx, cy, 61, 12, 0, tau, mix(kBlack, kMint, 17));
    D().drawSmoothArc(cx, cy, 42, 12, 0, tau, mix(kBlack, kCyan, 17));
    D().drawSmoothArc(cx, cy, 80, 12, start, start + tau * 0.82f, kCoral);
    D().drawSmoothArc(cx, cy, 61, 12, start, start + tau * 0.72f, kMint);
    D().drawSmoothArc(cx, cy, 42, 12, start, start + tau * 0.64f, kCyan);
    drawIconShield(cx, cy, 32, kTxtSoft, true);

    fillRR(24, 378, 111, 38, 19, kCard);
    D().drawText(79, 387, battery, kTxtSoft, Font::Label, true);
    fillRR(149, 378, 112, 38, 19, kCard);
    D().drawText(205, 387, "Guard", kMint, Font::Label, true);
    fillRR(275, 378, 111, 38, 19, kCard);
    D().drawText(330, 387, m_lastSimStatus.location.valid ? "Located" : "Location", kCyan,
                 Font::Label, true);

    fillRR(24, 429, 362, 57, 28, kSurface);
    drawIconPhone(60, 457, 26, kMint);
    drawIconPerson(132, 457, 26, kBlue);
    fillCirc(205, 457, 23, kCoral);
    drawIconShield(205, 457, 26, kWhite, true);
    drawIconMusic(278, 457, 26, kPurple);
    drawIconGrid(350, 457, 26, kTxtSoft);
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 2 : LAUNCHPAD APP DRAWER (2x2 Grid of 4 Square App Cards)
// ─────────────────────────────────────────────────────────────────────────────
struct SlideDrawerApp {
    const char *name;
    const char *subtitle;
    uint16_t baseColor;
    WatchScreenId targetScreen;
    int iconType;
};

static const SlideDrawerApp kDrawerApps[] = {
    {"Emergency SOS", "Send an alert", kCoral, WatchScreenId::SCREEN_EMERGENCY, 7},
    {"Phone & Dialer", "Calls & keypad", rgb(41, 178, 93), WatchScreenId::SCREEN_DIALER, 1},
    {"Night Guard", "Walk with confidence", rgb(199, 139, 42), WatchScreenId::SCREEN_SAFE, 3},
    {"Voice Recorder", "Record a memo", rgb(202, 75, 93), WatchScreenId::SCREEN_RECORDER, 12},
    {"Sound Studio", "Alert tones", rgb(148, 92, 221), WatchScreenId::SCREEN_SOUND_STUDIO, 4},
    {"Flashlight", "Light your way", rgb(119, 131, 150), WatchScreenId::SCREEN_FLASHLIGHT, 5},
    {"Guardians", "Trusted contacts", rgb(60, 126, 223), WatchScreenId::SCREEN_CONTACTS, 2},
    {"System Health", "Watch diagnostics", rgb(62, 143, 125), WatchScreenId::SCREEN_SYSTEM, 9},
    {"Wallpapers", "Choose your face", rgb(101, 92, 205), WatchScreenId::SCREEN_WALLPAPERS, 13},
    {"Compass & IMU", "Motion & orientation", rgb(50, 146, 177), WatchScreenId::SCREEN_TELEMETRY,
     6},
    {"Control Center", "Quick controls", rgb(101, 111, 133), WatchScreenId::SCREEN_CONTROL_CENTER,
     8},
    {"Disarm PIN", "Cancel an alert", rgb(43, 145, 91), WatchScreenId::SCREEN_CANCEL, 11},
    {"Fall Detection", "Motion protection", kCoral, WatchScreenId::SCREEN_FALL_DETECTION, 7},
    {"Phone Link", "Connect Android", kBlue, WatchScreenId::SCREEN_PHONE_LINK, 1},
};

static constexpr int kNumDrawerApps = sizeof(kDrawerApps) / sizeof(kDrawerApps[0]);

void UiManager::handleLaunchpadTap(int x, int y) {
    // Header Left: "< HOME" pill
    if (y <= 50 && x <= 120) {
        showScreen(WatchScreenId::SCREEN_SAFE);
        return;
    }

    // Bottom Navigation Bar: "< PREV" (left) / "NEXT >" (right)
    if (y >= 418) {
        if (x <= 140 && m_drawerPage > 0) {
            m_drawerPage--;
            renderLaunchpadScreen();
            return;
        }
        if (x >= 270 && m_drawerPage < kDrawerPageCount - 1) {
            m_drawerPage++;
            renderLaunchpadScreen();
            return;
        }
    }

    // 2x2 Square Grid Area (y = 56 to 416)
    if (y >= 56 && y < 416) {
        int row = (y >= 234) ? 1 : 0;
        int col = (x >= 205) ? 1 : 0;
        int item = row * 2 + col;
        int idx = m_drawerPage * 4 + item;

        if (idx >= 0 && idx < kNumDrawerApps) {
            if (kDrawerApps[idx].targetScreen == WatchScreenId::SCREEN_EMERGENCY) {
                showToast("Emergency SOS Armed", 2000);
                showScreen(WatchScreenId::SCREEN_EMERGENCY);
                StateMachine::instance().sendEvent(StateEvent::EVT_MANUAL_SOS_PRESS);
            } else if (idx == 2) {
                StateMachine::instance().sendEvent(StateEvent::EVT_ENTER_WATCH_MODE);
                showScreen(WatchScreenId::SCREEN_WATCH);
            } else {
                showScreen(kDrawerApps[idx].targetScreen);
            }
        }
    }
}

void UiManager::renderLaunchpadScreen() {
    drawPremiumBackdrop(kMint);
    D().drawSymbol(34, 30, 18, Symbol::Left, kTxtSoft);
    D().drawText(48, 17, "Home", kTxtSoft, Font::Body);
    D().drawText(205, 14, "Apps", kWhite, Font::Title, true);
    char page[24];
    std::snprintf(page, sizeof(page), "%d / %d", m_drawerPage + 1, kDrawerPageCount);
    D().drawText(353, 19, page, kTxtMuted, Font::Caption, true);

    for (int item = 0; item < 4; ++item) {
        int index = m_drawerPage * 4 + item;
        if (index >= kNumDrawerApps)
            break;
        const auto &app = kDrawerApps[index];
        int x = 22 + (item % 2) * 194, y = 58 + (item / 2) * 180;
        drawPremiumCard(x, y, 172, 172, 28, app.baseColor);
        drawIconTile(x + 86, y + 55, 76, app.baseColor, app.iconType);
        D().drawText(x + 86, y + 110, app.name, kWhite, Font::Label, true, 156);
        D().drawText(x + 86, y + 138, app.subtitle, kTxtMuted, Font::Caption, true, 152);
    }
    // Existing pagination hitboxes and app order are unchanged.
    if (m_drawerPage > 0) {
        fillRR(22, 424, 100, 48, 24, kCard);
        D().drawSymbol(44, 448, 18, Symbol::Left, kTxtSoft);
        D().drawText(79, 437, "Prev", kTxtSoft, Font::Body, true);
    } else
        D().drawText(73, 437, "Prev", kTxtDim, Font::Body, true);
    if (m_drawerPage < kDrawerPageCount - 1) {
        fillRR(288, 424, 100, 48, 24, kCard);
        D().drawText(329, 437, "Next", kTxtSoft, Font::Body, true);
        D().drawSymbol(365, 448, 18, Symbol::Right, kTxtSoft);
    } else
        D().drawText(339, 437, "Next", kTxtDim, Font::Body, true);
    for (int i = 0; i < kDrawerPageCount; ++i) {
        if (i == m_drawerPage)
            fillRR(167 + i * 20, 445, 16, 6, 3, kWhite);
        else
            fillCirc(175 + i * 20, 448, 3, kSep);
    }
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 3 : SOUND STUDIO
// ─────────────────────────────────────────────────────────────────────────────
static const char *kSoundNames[5] = {"Police Siren", "105dB Pulse", "Ambulance Hi-Lo",
                                     "Chirp Radar", "Bell Chime"};
static const char *kSoundSub[5] = {"600-1600 Hz sweep", "2400 Hz burst", "960/770 Hz wave",
                                   "800-2400 Hz chirp", "1760 Hz chime"};

void UiManager::handleSoundStudioTap(int x, int y) {
    if (x <= 130 && y <= 52) {
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        return;
    }

    if (y >= 58 && y < 308) {
        int idx = (y - 58) / 50;
        if (idx < 0)
            idx = 0;
        if (idx >= 5)
            idx = 4;
        auto ns = static_cast<AlarmSoundType>(idx);
        bool playing = AudioManager::instance().isPlaying();
        bool same = (AudioManager::instance().getCurrentSound() == ns);
        if (same && playing)
            AudioManager::instance().stopSound();
        else if (playing) {
            AudioManager::instance().playSound(ns);
        } else {
            AudioManager::instance().playSound(ns);
        }
        renderSoundStudioScreen();
        return;
    }

    if (y >= 314 && y < 366) {
        if (AudioManager::instance().isPlaying())
            AudioManager::instance().stopSound();
        else
            AudioManager::instance().playSound(AudioManager::instance().getCurrentSound());
        renderSoundStudioScreen();
        return;
    }

    if (y >= 376 && y <= 424) {
        if (x >= 42 && x < 122)
            AudioManager::instance().setVolume(25);
        else if (x >= 122 && x < 202)
            AudioManager::instance().setVolume(50);
        else if (x >= 202 && x < 282)
            AudioManager::instance().setVolume(75);
        else if (x >= 282)
            AudioManager::instance().setVolume(100);
        renderSoundStudioScreen();
        return;
    }

    if (y >= 430)
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
}

void UiManager::renderSoundStudioScreen() {
    drawPremiumBackdrop(kPurple);
    drawPremiumHeader("Sounds", kPurple);
    auto cur = AudioManager::instance().getCurrentSound();
    bool playing = AudioManager::instance().isPlaying();
    for (int i = 0; i < 5; ++i) {
        int y = 58 + i * 50;
        bool selected = cur == static_cast<AlarmSoundType>(i);
        drawPremiumCard(24, y, 362, 45, 14, kPurple, selected);
        drawIconMusic(47, y + 23, 22, selected ? kPurple : kTxtDim);
        D().drawText(69, y + 3, kSoundNames[i], kWhite, Font::Label, false, 271);
        D().drawText(69, y + 24, kSoundSub[i], kTxtMuted, Font::Caption, false, 271);
        if (selected)
            D().drawSymbol(361, y + 23, 18, playing ? Symbol::Pause : Symbol::Check, kPurple);
    }
    drawAction(24, 314, 362, 50, playing ? "Stop sound" : "Play sound",
               playing ? kCoralDark : kPurpleDim, playing ? kCoral : kPurple);
    const int volumes[] = {25, 50, 75, 100};
    int current = AudioManager::instance().getVolume();
    // Segment geometry now matches its existing four tap zones exactly.
    for (int i = 0; i < 4; ++i) {
        char label[8];
        std::snprintf(label, sizeof(label), "%d%%", volumes[i]);
        drawAction(42 + i * 80, 376, 76, 42, label, current == volumes[i] ? kCardHi : kSurface,
                   current == volumes[i] ? kWhite : kTxtMuted);
    }
    drawFooter("Apps");
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 4 : FLASHLIGHT  (100% Full-Screen Pure Illumination - Zero Text)
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleFlashlightTap(int x, int y) {
    // Tapping top area (y < 70) exits flashlight cleanly
    if (y < 70) {
        m_flashlightMode = FlashlightMode::WHITE_100;
        D().setBrightness(85);
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        return;
    }
    // Tap anywhere cycles modes: White 100% -> Amber Warm -> Red Strobe -> White
    // 100%
    if (m_flashlightMode == FlashlightMode::WHITE_100) {
        m_flashlightMode = FlashlightMode::AMBER_WARM;
    } else if (m_flashlightMode == FlashlightMode::AMBER_WARM) {
        m_flashlightMode = FlashlightMode::RED_STROBE;
    } else {
        m_flashlightMode = FlashlightMode::WHITE_100;
    }
    renderFlashlightScreen();
}

void UiManager::renderFlashlightScreen() {
    uint16_t bg = m_flashlightMode == FlashlightMode::WHITE_100    ? kWhite
                  : m_flashlightMode == FlashlightMode::AMBER_WARM ? rgb(255, 199, 108)
                  : m_strobeState                                  ? kCoral
                                                                   : kBlack;
    D().setBrightness(100);
    D().clear(bg);
    fillRR(23, 14, 86, 34, 17, kCard);
    D().drawSymbol(40, 31, 18, Symbol::Left, kWhite);
    D().drawText(73, 20, "Exit", kWhite, Font::Body, true);
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 5 : TELEMETRY
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleTelemetryTap(int x, int y) { showScreen(WatchScreenId::SCREEN_LAUNCHPAD); }

void UiManager::renderTelemetryScreen() {
    drawPremiumBackdrop(kCyan);
    drawPremiumHeader("Motion", kCyan);
    ImuData imu = {};
    ImuQMI8658::instance().read(imu);
    const int cx = 205, cy = 154, r = 80;
    D().drawSmoothArc(cx, cy, r, 2, 0, 6.2831853f, kSep);
    for (int i = 0; i < 24; ++i) {
        float a = i * 6.2831853f / 24;
        int inner = i % 6 == 0 ? 65 : 73;
        D().drawLine(cx + int(inner * std::cos(a)), cy + int(inner * std::sin(a)),
                     cx + int(79 * std::cos(a)), cy + int(79 * std::sin(a)),
                     i % 6 == 0 ? kTxtMuted : kSep);
    }
    D().drawText(cx, 75, "N", kCyan, Font::Caption, true);
    D().drawText(cx, 213, "S", kTxtMuted, Font::Caption, true);
    D().drawText(cx - 68, 144, "W", kTxtMuted, Font::Caption, true);
    D().drawText(cx + 68, 144, "E", kTxtMuted, Font::Caption, true);
    D().drawSymbol(cx, cy, 44, Symbol::Compass, kCyan);
    int bx = cx + int(std::clamp(imu.ax * 35, -38.0f, 38.0f));
    int by = cy + int(std::clamp(-imu.ay * 35, -38.0f, 38.0f));
    fillCirc(bx, by, 5, kMint);
    float pitch = std::atan2(-imu.ax, std::sqrt(imu.ay * imu.ay + imu.az * imu.az)) * 57.2958f;
    float roll = std::atan2(imu.ay, imu.az) * 57.2958f;
    char text[60];
    std::snprintf(text, sizeof(text), "Pitch %+.1f°  ·  Roll %+.1f°", pitch, roll);
    D().drawText(205, 245, text, kTxtSoft, Font::Body, true, 360);
    drawPremiumCard(24, 281, 174, 112, 20, kCyan);
    drawPremiumCard(212, 281, 174, 112, 20, kCyan);
    D().drawText(41, 291, "Acceleration", kTxtMuted, Font::Caption);
    D().drawText(229, 291, "Rotation", kTxtMuted, Font::Caption);
    const float acc[] = {imu.ax, imu.ay, imu.az}, gyro[] = {imu.gx, imu.gy, imu.gz};
    const uint16_t colors[] = {kCoral, kMint, kCyan};
    for (int i = 0; i < 3; ++i) {
        std::snprintf(text, sizeof(text), "%c  %+.2f g", 'X' + i, acc[i]);
        D().drawText(41, 316 + i * 22, text, colors[i], Font::Body, false, 140);
        std::snprintf(text, sizeof(text), "%c  %+.0f°/s", 'X' + i, gyro[i]);
        D().drawText(229, 316 + i * 22, text, colors[i], Font::Body, false, 140);
    }
    D().drawText(205, 404, "Orientation from the motion sensor", kTxtDim, Font::Caption, true);
    drawFooter("Apps", 438);
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 6 : SYSTEM HEALTH  (Simplified, Friendly, High Readability)
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleSystemTap(int x, int y) {
    if (y <= 52 && x <= 130) {
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        return;
    }

    // Tap Siren card (y=342..412) to test speaker
    if (y >= 342 && y <= 412) {
        m_sirenTestActive = !m_sirenTestActive;
        if (m_sirenTestActive) {
            AudioManager::instance().playSound(AlarmSoundType::APPLE_CHIME);
            showToast("Playing speaker chime", 2000);
        } else {
            AudioManager::instance().stopSound();
        }
        renderSystemScreen();
        return;
    }

    if (y >= 426) {
        showScreen(WatchScreenId::SCREEN_SAFE);
    }
}

void UiManager::renderSystemScreen() {
    drawPremiumBackdrop(kMint);
    drawPremiumHeader("System", kMint);
    drawPremiumCard(24, 56, 362, 54, 18, kMint);
    drawIconShield(50, 83, 26, kMint, true);
    D().drawText(76, 61, "Watch status", kWhite, Font::Label);
    D().drawText(76, 84, "Hardware & connections", kTxtMuted, Font::Caption);
    BatteryStatus bat = {};
    bool batOk = AXP2101::instance().getBatteryStatus(bat) == ESP_OK;
    drawPremiumCard(24, 118, 362, 68, 20, kMint);
    drawIconBattery(52, 152, m_batteryPercent, kMint);
    char text[64];
    std::snprintf(text, sizeof(text), "Battery  %d%%", m_batteryPercent);
    D().drawText(82, 126, text, kWhite, Font::Label);
    if (batOk)
        std::snprintf(text, sizeof(text), "%.2f V · %s", bat.voltage,
                      bat.isCharging ? "Charging" : "On battery");
    else
        std::snprintf(text, sizeof(text), "Battery telemetry unavailable");
    D().drawText(82, 153, text, kTxtMuted, Font::Caption, false, 284);
    drawPremiumCard(24, 194, 362, 68, 20, kBlue);
    drawIconPhone(52, 228, 26, kBlue);
    D().drawText(82, 202, "Android phone", kWhite, Font::Label);
    D().drawText(82, 229, m_lastSimStatus.statusString, kTxtMuted, Font::Caption, false, 282);
    drawPremiumCard(24, 270, 362, 68, 20, kCyan);
    drawIconCompass(52, 304, 26, kCyan);
    ImuData imu = {};
    bool imuOk = ImuQMI8658::instance().read(imu) == ESP_OK;
    D().drawText(82, 278, "Motion sensor", kWhite, Font::Label);
    D().drawText(82, 305, imuOk ? "6-axis sensor available" : "Sensor unavailable", kTxtMuted,
                 Font::Caption);
    drawPremiumCard(24, 346, 362, 68, 20, kPurple, m_sirenTestActive);
    drawIconMusic(52, 380, 26, kPurple);
    D().drawText(82, 354, m_sirenTestActive ? "Sound test playing" : "Speaker test", kWhite,
                 Font::Label);
    D().drawText(82, 381, "Tap to play a chime", kTxtMuted, Font::Caption);
    drawFooter("Home", 432);
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 7 : CONTROL CENTER
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleControlCenterTap(int x, int y) {
    if (x >= 280 && y <= 50) {
        showScreen(WatchScreenId::SCREEN_SAFE);
        return;
    }

    // Row 1: Cellular / Wi-Fi
    if (y >= 52 && y < 120) {
        if (x >= 24 && x < 200) {
            showScreen(WatchScreenId::SCREEN_PHONE_LINK);
            return;
        } else if (x >= 210 && x < 386) {
            showToast("Use the phone network via BLE", 2000);
            return;
        }
    }

    // Row 2: Silent / Flashlight
    if (y >= 126 && y < 194) {
        if (x >= 24 && x < 200) {
            showToast("Silent Mode: Vibrate Only", 2000);
            return;
        } else if (x >= 210 && x < 386) {
            showScreen(WatchScreenId::SCREEN_FLASHLIGHT);
            return;
        }
    }

    // Row 3: Siren / GPS Guard
    if (y >= 200 && y < 268) {
        if (x >= 24 && x < 200) {
            m_sirenTestActive = !m_sirenTestActive;
            if (m_sirenTestActive)
                AudioManager::instance().playSound(AlarmSoundType::POLICE_WAIL);
            else
                AudioManager::instance().stopSound();
            renderControlCenterScreen();
            return;
        } else if (x >= 210 && x < 386) {
            showScreen(WatchScreenId::SCREEN_WATCH);
            return;
        }
    }

    // Row 4: Volume Bar
    if (y >= 274 && y < 320) {
        int curVol = AudioManager::instance().getVolume();
        int nextVol = (curVol >= 100) ? 25 : curVol + 25;
        AudioManager::instance().setVolume(nextVol);
        char vmsg[32];
        std::snprintf(vmsg, sizeof(vmsg), "Volume: %d%%", nextVol);
        showToast(vmsg, 1500);
        renderControlCenterScreen();
        return;
    }

    // Row 5: Emergency SOS strip
    if (y >= 328 && y < 386) {
        showToast("Emergency SOS Armed", 2000);
        showScreen(WatchScreenId::SCREEN_EMERGENCY);
        StateMachine::instance().sendEvent(StateEvent::EVT_MANUAL_SOS_PRESS);
        return;
    }

    // Row 6: Dismiss
    if (y >= 390)
        showScreen(WatchScreenId::SCREEN_SAFE);
}

void UiManager::renderControlCenterScreen() {
    drawPremiumBackdrop(kBlue);
    D().drawText(31, 16, "Controls", kWhite, Font::Title);
    char battery[12];
    std::snprintf(battery, sizeof(battery), "%d%%", m_batteryPercent);
    D().drawText(255, 20, battery, kTxtMuted, Font::Caption, true);
    drawAction(302, 12, 78, 32, "Done", kCard, kTxtSoft);
    struct Tile {
        const char *name;
        const char *detail;
        Symbol symbol;
        uint16_t accent;
    };
    const Tile tiles[] = {
        {"Phone Link", m_lastSimStatus.statusString, Symbol::Phone, kMint},
        {"Phone data", "Via companion", Symbol::Wifi, kBlue},
        {"Silent", "Sound controls", Symbol::Moon, kPurple},
        {"Torch", "Illuminate", Symbol::Torch, kAmber},
        {"Siren", m_sirenTestActive ? "Playing" : "Test sound", Symbol::Music, kCoral},
        {"Guard", "Safety session", Symbol::ShieldCheck, kMint},
    };
    for (int i = 0; i < 6; ++i) {
        int x = 24 + (i % 2) * 188, y = 52 + (i / 2) * 74;
        const auto &tile = tiles[i];
        drawPremiumCard(x, y, 174, 66, 20, tile.accent, i == 4 && m_sirenTestActive);
        D().drawSymbol(x + 27, y + 32, 26, tile.symbol, tile.accent);
        D().drawText(x + 50, y + 9, tile.name, kWhite, Font::Label, false, 112);
        D().drawText(x + 50, y + 36, tile.detail, kTxtMuted, Font::Caption, false, 112);
    }
    fillRR(24, 274, 362, 46, 20, kCard);
    drawIconVolume(48, 297, 22, kTxtSoft);
    int vol = AudioManager::instance().getVolume();
    int width = 225 * vol / 100;
    fillRR(75, 294, 225, 6, 3, kSep);
    if (width)
        fillRR(75, 294, width, 6, 3, kTxtSoft);
    char value[12];
    std::snprintf(value, sizeof(value), "%d%%", vol);
    D().drawText(346, 285, value, kTxtSoft, Font::Label, true, 60);
    fillRR(24, 328, 362, 56, 24, kCoralDark);
    drawIconAlert(53, 356, 26, kCoral);
    D().drawText(218, 344, "Emergency SOS", kCoral, Font::Label, true);
    drawAction(24, 394, 362, 40, "Close controls", kSurface, kTxtMuted);
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 8 : CONTACTS
// ─────────────────────────────────────────────────────────────────────────────
struct ContactRow {
    const char *name;
    const char *number;
    uint16_t avatarBg;
    bool hasSms;
    const char *callLabel;
};

static const ContactRow kContacts[4] = {
    {"Guardian 1", "Set in Android companion", 0x6000, true, "CALL"},
    {"Guardian 2", "Set in Android companion", kBlueDim, true, "CALL"},
    {"Police · 112", "Emergency (Global)", kCoralDark, false, "112"},
    {"Women Helpline", "1091  |  24/7", kMintDim, false, "1091"},
};
static const char *kContactPhones[4] = {"", "", "112", "1091"};

void UiManager::handleContactsTap(int x, int y) {
    if (y < 52 && x < 130) {
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        return;
    }

    for (int i = 0; i < 4; i++) {
        int cy = 54 + i * 88;
        if (y >= cy && y < cy + 80) {
            if (x >= 210 && x < 290) {
                auto phone = i < 2 ? PhoneBridge::instance().guardianPhone(i)
                                   : std::string(kContactPhones[i]);
                if (!SIM800L::instance().requestVoiceCall(phone.c_str())) {
                    showToast("Connect phone and set guardians", 2000);
                    renderContactsScreen();
                    return;
                }
                showCallingScreen(i < 2 ? PhoneBridge::instance().guardianName(i).c_str()
                                        : kContacts[i].name,
                                  phone.c_str());
                return;
            }
            if (kContacts[i].hasSms && x >= 295) {
                auto phone = PhoneBridge::instance().guardianPhone(i);
                if (!SIM800L::instance().requestSendLocationSms(
                        phone.c_str(), "[NAARI CHECK-IN] Wearer is safe.")) {
                    showToast("Connect phone and set guardians", 2000);
                    renderContactsScreen();
                    return;
                }
                showSmsSentScreen(PhoneBridge::instance().guardianName(i).c_str(), phone.c_str());
                return;
            }
        }
    }

    if (y >= 414)
        showScreen(WatchScreenId::SCREEN_SAFE);
}

void UiManager::renderContactsScreen() {
    drawPremiumBackdrop(kBlue);
    drawPremiumHeader("Guardians", kBlue);
    for (int i = 0; i < 4; ++i) {
        int y = 54 + i * 88;
        const auto &c = kContacts[i];
        drawPremiumCard(24, y, 362, 80, 20, kBlue);
        fillCirc(54, y + 26, 18,
                 i == 0   ? kPurpleDim
                 : i == 1 ? kBlueDim
                 : i == 2 ? kCoralDark
                          : kMintDim);
        char initial[2] = {c.name[0], 0};
        D().drawText(54, y + 14, initial,
                     i == 0   ? kPurple
                     : i == 1 ? kBlue
                     : i == 2 ? kCoral
                              : kMint,
                     Font::Label, true);
        std::string guardian = i < 2    ? PhoneBridge::instance().guardianName(i)
                               : i == 2 ? "Police"
                                        : "Helpline";
        const char *name = guardian.c_str();
        D().drawText(82, y + 12, name, kWhite, Font::Label, false, 120);
        // All details occupy their own baseline below the actions. No text is
        // allowed behind either of the established call / SMS touch targets.
        auto phone = i < 2 ? PhoneBridge::instance().guardianPhone(i) : std::string(c.number);
        if (phone.empty())
            phone = "Set in Android companion";
        D().drawText(40, y + 53, phone.c_str(), kTxtMuted, Font::Caption, false, 326);
        fillRR(213, y + 8, 74, 36, 18, kMintDim);
        if (c.hasSms) {
            drawIconPhone(250, y + 26, 22, kMint);
            fillRR(297, y + 8, 74, 36, 18, kBlueDim);
            drawIconMessage(334, y + 26, 22, kBlue);
        } else
            D().drawText(250, y + 15, c.callLabel, kMint, Font::Label, true, 62);
    }
    drawFooter("Home", 418);
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 9 : DIALER
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleDialerTap(int x, int y) {
    if (y < 52 && x < 130) {
        showScreen(WatchScreenId::SCREEN_SAFE);
        return;
    }

    if (y >= 52 && y <= 110 && x >= 290) {
        if (!m_dialedNumber.empty()) {
            m_dialedNumber.pop_back();
            renderDialerScreen();
        }
        return;
    }

    // 3×4 key grid
    int col = -1, row = -1;
    if (x >= 35 && x < 145)
        col = 0;
    else if (x >= 145 && x < 255)
        col = 1;
    else if (x >= 255 && x < 375)
        col = 2;
    if (y >= 115 && y < 185)
        row = 0;
    else if (y >= 185 && y < 255)
        row = 1;
    else if (y >= 255 && y < 325)
        row = 2;
    else if (y >= 325 && y < 395)
        row = 3;

    if (col >= 0 && row >= 0) {
        static const char keyMap[4][3] = {
            {'1', '2', '3'}, {'4', '5', '6'}, {'7', '8', '9'}, {'*', '0', '#'}};
        if (m_dialedNumber.length() < 15) {
            m_dialedNumber.push_back(keyMap[row][col]);
            renderDialerScreen();
        }
        return;
    }

    // Bottom action row
    if (y >= 400) {
        if (x < 145) {
            m_dialedNumber.clear();
            renderDialerScreen();
            return;
        }
        if (x >= 155 && x <= 255) {
            if (!m_dialedNumber.empty()) {
                if (!SIM800L::instance().requestVoiceCall(m_dialedNumber.c_str())) {
                    showToast("Connect Android phone first", 2000);
                    renderDialerScreen();
                    return;
                }
                showCallingScreen(m_dialedNumber.c_str(), m_dialedNumber.c_str());
            } else
                showToast("Enter a number first", 1500);
            return;
        }
        if (x >= 265) {
            if (!m_dialedNumber.empty()) {
                m_dialedNumber.pop_back();
                renderDialerScreen();
            }
        }
    }
}

void UiManager::renderDialerScreen() {
    drawPremiumBackdrop(kMint);
    drawPremiumHeader("Keypad", kMint);
    D().drawText(185, 64, m_dialedNumber.empty() ? "Enter a number" : m_dialedNumber.c_str(),
                 m_dialedNumber.empty() ? kTxtDim : kWhite,
                 m_dialedNumber.empty() ? Font::Body : Font::Title, true, 284);
    if (!m_dialedNumber.empty())
        D().drawSymbol(340, 84, 22, Symbol::Left, kTxtMuted);
    const char *keys[4][3] = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
    const char *letters[4][3] = {
        {"", "ABC", "DEF"}, {"GHI", "JKL", "MNO"}, {"PQRS", "TUV", "WXYZ"}, {"", "+", ""}};
    for (int row = 0; row < 4; ++row)
        for (int col = 0; col < 3; ++col) {
            int cx = 90 + col * 110, cy = 149 + row * 70;
            fillCirc(cx, cy, 31, kCard);
            D().drawText(cx, cy - 26, keys[row][col], kWhite, Font::Title, true);
            D().drawText(cx, cy + 6, letters[row][col], kTxtMuted, Font::Caption, true);
        }
    drawAction(24, 404, 100, 52, "Clear", kSurface, kTxtMuted);
    fillCirc(205, 430, 29, rgb(39, 170, 87));
    drawIconPhone(205, 430, 26, kWhite);
    drawAction(286, 404, 100, 52, "Delete", kSurface, kTxtMuted);
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 10 : SAFETY WATCH  (Night Walk Guard)
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleWatchTap(int x, int y) {
    if (y >= 328 && y < 386) {
        showToast("Session Ended: Safe", 2500);
        StateMachine::instance().sendEvent(StateEvent::EVT_ENTER_SAFE_MODE);
        showScreen(WatchScreenId::SCREEN_SAFE);
        return;
    }
    if (y >= 394) {
        showToast("Emergency SOS Armed", 2000);
        showScreen(WatchScreenId::SCREEN_EMERGENCY);
        StateMachine::instance().sendEvent(StateEvent::EVT_MANUAL_SOS_PRESS);
    }
}

void UiManager::renderWatchScreen() {
    drawPremiumBackdrop(kAmber);
    drawIconShield(48, 33, 26, kAmber, true);
    D().drawText(76, 19, "Night Guard", kWhite, Font::Title);
    D().drawText(205, 79, "You're on your way", kTxtSoft, Font::Body, true);
    uint32_t duration = StateMachine::instance().getWatchActiveDurationSec();
    char timer[24];
    std::snprintf(timer, sizeof(timer), "%02lu:%02lu", (unsigned long)(duration / 60),
                  (unsigned long)(duration % 60));
    D().drawText(205, 109, timer, kWhite, Font::Timer, true);
    D().drawText(205, 172, "Session duration", kTxtMuted, Font::Caption, true);
    struct Card {
        const char *label;
        const char *value;
        uint16_t color;
    };
    char location[40];
    if (m_lastSimStatus.location.valid)
        std::snprintf(location, sizeof(location), "%.2f, %.2f", m_lastSimStatus.location.latitude,
                      m_lastSimStatus.location.longitude);
    else
        std::snprintf(location, sizeof(location), "Awaiting location");
    auto guardian = PhoneBridge::instance().guardianPhone(0).empty()
                        ? std::string("Not configured")
                        : PhoneBridge::instance().guardianName(0);
    const Card cards[] = {{"Guardian", guardian.c_str(), kBlue},
                          {"Location", location, kCyan},
                          {"Check-ins", "Every 3 minutes", kAmber},
                          {"Siren", "Ready", kCoral}};
    for (int i = 0; i < 4; ++i) {
        int x = 24 + (i % 2) * 186, y = 202 + (i / 2) * 62;
        drawPremiumCard(x, y, 176, 56, 18, cards[i].color);
        D().drawText(x + 16, y + 5, cards[i].label, kTxtMuted, Font::Caption);
        D().drawText(x + 16, y + 27, cards[i].value, cards[i].color, Font::Body, false, 146);
    }
    drawAction(24, 328, 362, 56, "I'm safe · End session", kMintDim, kMint);
    drawAction(24, 394, 362, 54, "Emergency SOS", kCoralDark, kCoral);
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 11 : EMERGENCY SOS
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleEmergencyTap(int x, int y) {
    if (y >= 350) {
        ESP_LOGI(TAG, "Emergency screen Disarm button tapped -> DISARM SOS!");
        finishHoldCancellation();
    }
}

void UiManager::renderEmergencyScreen() {
    drawPremiumBackdrop(kCoral);
    bool grace = StateMachine::instance().isEmergencyGraceActive();
    uint32_t sec = grace ? StateMachine::instance().getGraceRemainingSec() : 0;
    m_emergencyCountdownSec = sec;
    D().drawText(205, 22, "Emergency SOS", kCoral, Font::Title, true);
    D().drawSmoothArc(205, 138, 59, 10, 0, 6.2831853f, kCoralDark);
    if (sec)
        D().drawSmoothArc(205, 138, 59, 10, -1.5707963f,
                          -1.5707963f + 6.2831853f * std::min(1.0f, float(sec) / kGraceWindowSec),
                          kCoral);
    char number[12];
    std::snprintf(number, sizeof(number), "%lu", (unsigned long)sec);
    D().drawText(205, 99, sec ? number : "00", kWhite, Font::Timer, true);
    D().drawText(205, 158, sec ? "seconds" : "Alert active", kTxtMuted, Font::Caption, true);
    struct Row {
        const char *label;
        Symbol icon;
        uint16_t color;
    };
    const Row rows[] = {{PhoneBridge::instance().status().ready ? "Android phone connected"
                                                                : "Android phone disconnected",
                         Symbol::Phone, kMint},
                        {grace ? "Alerts wait for countdown"
                         : StateMachine::instance().werePhoneAlertsQueued()
                             ? "Phone alerts requested"
                             : "Phone or guardians not ready",
                         Symbol::Alert, kCoral},
                        {"Deterrent siren", Symbol::Volume, kAmber},
                        {"Cancel if you are safe", Symbol::Shield, kCyan}};
    for (int i = 0; i < 4; ++i) {
        int y = 216 + i * 36;
        fillRR(24, y, 362, 32, 12, kSurface);
        D().drawSymbol(46, y + 16, 18, rows[i].icon, rows[i].color);
        D().drawText(65, y + 5, rows[i].label, kTxtSoft, Font::Body, false, 300);
    }
    drawAction(24, 370, 362, 64, "I'm safe · Disarm SOS", kMintDim, kMint);
    D().drawText(205, 453, "Crown or side button also disarms", kTxtDim, Font::Caption, true);
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 12 : HOLD-TO-CANCEL
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleTouchDrag(int x, int y, bool pressed) {
    if (m_currentScreen == WatchScreenId::SCREEN_CANCEL && !m_cancelConfirmed) {
        if (pressed && y >= 290) {
            if (!m_holdingCancel) {
                m_holdingCancel = true;
                m_holdStartTimeUs = esp_timer_get_time();
            } else {
                int64_t elapsedMs = (esp_timer_get_time() - m_holdStartTimeUs) / 1000;
                float pct = (float)elapsedMs / 2000.0f * 100.0f;
                if (pct > 100.0f)
                    pct = 100.0f;
                updateHoldCancellation(pct);
            }
        } else {
            if (m_holdingCancel)
                cancelHoldCancellation();
        }
    }
}

void UiManager::startHoldCancellation() {
    m_holdProgress = 0.0f;
    m_cancelConfirmed = false;
    m_holdingCancel = true;
    m_holdStartTimeUs = esp_timer_get_time();
}

void UiManager::updateHoldCancellation(float pct) {
    m_holdProgress = pct;
    if (pct >= 100.0f && !m_cancelConfirmed)
        finishHoldCancellation();
}

void UiManager::finishHoldCancellation() {
    m_cancelConfirmed = true;
    m_holdingCancel = false;
    m_holdProgress = 100.0f;
    showToast("SOS Disarmed: You're Safe", 2500);
    StateMachine::instance().sendEvent(StateEvent::EVT_CANCEL_ALARM);
    AudioManager::instance().stopSound();
    showScreen(WatchScreenId::SCREEN_SAFE);
}

void UiManager::cancelHoldCancellation() {
    m_holdingCancel = false;
    m_holdProgress = 0.0f;
}

void UiManager::renderCancelScreen() {
    drawPremiumBackdrop(kMint);
    D().drawText(205, 29, "Disarm SOS", kWhite, Font::Title, true);
    D().drawText(205, 66, "Hold below for 2 seconds", kTxtMuted, Font::Body, true);
    D().drawSmoothArc(205, 190, 67, 12, 0, 6.2831853f, kCard);
    if (m_holdProgress > 0)
        D().drawSmoothArc(205, 190, 67, 12, -1.5707963f,
                          -1.5707963f + 6.2831853f * m_holdProgress / 100, kMint);
    char value[12];
    std::snprintf(value, sizeof(value), "%.0f%%", m_holdProgress);
    D().drawText(205, 155, value, kMint, Font::Timer, true);
    fillRR(44, 278, 322, 6, 3, kCard);
    int width = int(322 * m_holdProgress / 100);
    if (width > 0)
        fillRR(44, 278, width, 6, 3, kMint);
    fillRR(44, 314, 322, 98, 32, m_holdingCancel ? kMintDim : kCard);
    D().drawText(205, 334,
                 m_cancelConfirmed ? "You're safe"
                 : m_holdingCancel ? "Keep holding"
                                   : "Press & hold",
                 m_holdingCancel ? kMint : kWhite, Font::Title, true);
    D().drawText(205, 374, m_holdingCancel ? "Release to reset" : "to cancel the alert", kTxtMuted,
                 Font::Body, true);
    D().drawText(205, 440, "Crown also cancels the SOS", kTxtDim, Font::Caption, true);
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 13 : CALLING OVERLAY
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleCallingTap(int x, int y) {
    if (y >= 370 && y < 456) {
        showScreen(m_cancelFromScreen);
    }
}

static const char *requestLabel(PhoneRequestState state, bool sms) {
    switch (state) {
    case PhoneRequestState::Located:
        return "Location received";
    case PhoneRequestState::Sent:
        return sms ? "SMS sent by phone" : "Request completed";
    case PhoneRequestState::Delivered:
        return "SMS delivered";
    case PhoneRequestState::CallStarted:
        return "Call started on phone";
    case PhoneRequestState::NeedsPhone:
        return "Continue on your phone";
    case PhoneRequestState::Failed:
        return "Phone request failed";
    case PhoneRequestState::Received:
        return "Phone is processing";
    default:
        return "Waiting for your phone";
    }
}
void UiManager::renderCallingScreen() {
    drawPremiumBackdrop(kMint);
    auto r = PhoneBridge::instance().result(m_overlayRequestId);
    D().drawText(205, 34, "Phone call", kMint, Font::Body, true);
    fillCirc(205, 162, 62, kCard);
    drawIconPhone(205, 159, 60, kTxtSoft);
    D().drawText(205, 249, m_overlayName, kWhite, Font::Title, true, 354);
    D().drawText(205, 288, m_overlayNumber, kTxtMuted, Font::Body, true, 354);
    D().drawText(205, 327, requestLabel(r.state, false), kTxtSoft, Font::Body, true, 362);
    drawAction(60, 389, 290, 58, "Back to watch", kCard, kWhite);
    D().drawText(205, 466, "Manage the call on your phone", kTxtMuted, Font::Caption, true);
    renderToastOverlay();
    D().present();
}
void UiManager::handleSmsSentTap(int, int) { showScreen(m_cancelFromScreen); }
void UiManager::renderSmsSentScreen() {
    drawPremiumBackdrop(kBlue);
    auto r = PhoneBridge::instance().result(m_overlayRequestId);
    fillCirc(205, 157, 63, kBlueDim);
    drawIconMessage(205, 157, 60, kBlue);
    bool success = r.state == PhoneRequestState::Sent || r.state == PhoneRequestState::Delivered;
    if (success) {
        fillCirc(253, 109, 19, kMint);
        D().drawSymbol(253, 109, 22, Symbol::Check, kBlack);
    }
    D().drawText(205, 245, requestLabel(r.state, true), kWhite, Font::Title, true, 360);
    D().drawText(205, 289, m_overlayName, kBlue, Font::Body, true, 354);
    D().drawText(205, 322, m_overlayNumber, kTxtMuted, Font::Body, true, 354);
    D().drawText(205, 369, r.detail, kTxtMuted, Font::Caption, true, 354);
    drawAction(60, 421, 290, 48, "Back", kCard, kTxtSoft);
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 15 : VOICE RECORDER APP  (Record & Playback Memo via Watch Speaker)
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::handleRecorderTap(int x, int y) {
    if (y <= 52 && x <= 130) {
        if (m_isRecordingMemo) {
            m_isRecordingMemo = false;
            AudioManager::instance().stopVoiceMemoRecording();
        }
        if (m_isPlayingMemo) {
            m_isPlayingMemo = false;
            AudioManager::instance().stopVoiceMemoPlayback();
        }
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        return;
    }

    // When idle (no memo recorded yet): tap big center button to start recording
    if (!m_isRecordingMemo && !m_hasRecordedMemo) {
        if (y >= 130 && y <= 320) {
            m_isRecordingMemo = AudioManager::instance().startVoiceMemoRecording();
            if (!m_isRecordingMemo)
                showToast("Microphone unavailable", 2000);
            m_recordStartTimeUs = esp_timer_get_time();
            renderRecorderScreen();
            return;
        }
    }

    // While recording: tap STOP button (y=320..400)
    if (m_isRecordingMemo) {
        if (y >= 320 && y <= 400) {
            m_isRecordingMemo = false;
            AudioManager::instance().stopVoiceMemoRecording();
            m_hasRecordedMemo = AudioManager::instance().getRecordedSamples() > 0;
            m_memoDurationSec = AudioManager::instance().getRecordedDurationSec();
            showToast(m_hasRecordedMemo ? "Memo ready to play" : "No microphone audio captured",
                      1800);
            renderRecorderScreen();
            return;
        }
    }

    // When memo is saved:
    if (m_hasRecordedMemo && !m_isRecordingMemo) {
        // Left Button: PLAY / STOP (x=24..198, y=300..380)
        if (x >= 24 && x <= 198 && y >= 300 && y <= 380) {
            if (m_isPlayingMemo) {
                m_isPlayingMemo = false;
                AudioManager::instance().stopVoiceMemoPlayback();
            } else {
                m_isPlayingMemo = AudioManager::instance().playVoiceMemo();
                if (!m_isPlayingMemo)
                    showToast("Playback unavailable", 2000);
                m_playbackStartTimeUs = esp_timer_get_time();
            }
            renderRecorderScreen();
            return;
        }

        // Right Button: RECORD NEW (x=212..386, y=300..380)
        if (x >= 212 && x <= 386 && y >= 300 && y <= 380) {
            if (m_isPlayingMemo) {
                m_isPlayingMemo = false;
                AudioManager::instance().stopVoiceMemoPlayback();
            }
            m_isRecordingMemo = AudioManager::instance().startVoiceMemoRecording();
            if (!m_isRecordingMemo)
                showToast("Microphone unavailable", 2000);
            m_recordStartTimeUs = esp_timer_get_time();
            renderRecorderScreen();
            return;
        }
    }

    if (y >= 430)
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
}

void UiManager::renderRecorderScreen() {
    drawPremiumBackdrop(kCoral);
    drawPremiumHeader("Voice Memos", kCoral);
    if (m_isRecordingMemo || m_hasRecordedMemo) {
        uint16_t accent = m_isRecordingMemo ? kCoral : kMint;
        D().drawText(205, 73,
                     m_isRecordingMemo ? "Recording"
                     : m_isPlayingMemo ? "Playing memo"
                                       : "Memo saved",
                     accent, Font::Body, true);
        int64_t sec = m_isRecordingMemo ? AudioManager::instance().getRecordedDurationSec()
                                        : m_memoDurationSec;
        char timer[24];
        std::snprintf(timer, sizeof(timer), "%02lu:%02lu", (unsigned long)(sec / 60),
                      (unsigned long)(sec % 60));
        D().drawText(205, 112, timer, kWhite, Font::Timer, true);
        D().drawText(205, 182,
                     m_isRecordingMemo ? "Speak into your watch · up to 10s" : "Your audio note",
                     kTxtMuted, Font::Caption, true);
        for (int i = 0; i < 28; ++i) {
            float phase =
                (i * 0.81f) + (m_isRecordingMemo || m_isPlayingMemo ? m_animFrame * 0.09f : 0);
            int level = m_isRecordingMemo
                            ? std::min<int>(50, AudioManager::instance().getInputPeak() / 300)
                            : 28;
            int h = 4 + int(std::abs(std::sin(phase)) * level);
            fillRR(43 + i * 12, 246 - h / 2, 5, h, 2, accent);
        }
        if (m_isRecordingMemo)
            drawAction(105, 326, 200, 64, "Stop recording", kCoralDark, kCoral);
        else {
            drawAction(24, 310, 174, 68, m_isPlayingMemo ? "Stop" : "Play memo", kMintDim, kMint);
            drawAction(212, 310, 174, 68, "New memo", kCard, kCoral);
            D().drawText(205, 397, "Listen through the watch speaker", kTxtMuted, Font::Caption,
                         true);
        }
    } else {
        fillCirc(205, 197, 66, kCard);
        drawIconMicrophone(205, 190, 60, kTxtSoft);
        fillCirc(205, 242, 8, kCoral);
        D().drawText(205, 289, "Tap to record", kWhite, Font::Title, true);
        D().drawText(205, 330, "Up to 10 seconds · saved until restart", kTxtMuted, Font::Body,
                     true, 350);
    }
    drawFooter("Apps");
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  SCREEN 16 : WALLPAPERS APP  (5 AMOLED Watch Face Themes)
// ─────────────────────────────────────────────────────────────────────────────
static const char *kWallpaperNames[5] = {"Obsidian", "Topographic", "Cosmic Aurora", "Sunset Glow",
                                         "Cyber Matrix"};

void UiManager::handleWallpapersTap(int x, int y) {
    if (y <= 52 && x <= 130) {
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        return;
    }

    // 5 Wallpaper Cards
    if (y >= 58 && y < 418) {
        int idx = (y - 58) / 72;
        if (idx >= 0 && idx < 5) {
            m_wallpaperSelection = idx;
            renderWallpapersScreen();
            return;
        }
    }

    // Apply button at bottom
    if (y >= 426) {
        if (!writePreference("wallpaper", m_wallpaperSelection)) {
            showToast("Could not save face", 2000);
            renderWallpapersScreen();
            return;
        }
        m_wallpaperIdx = m_wallpaperSelection;
        showToast("Watch face applied", 2000);
        showScreen(WatchScreenId::SCREEN_SAFE);
    }
}

void UiManager::renderWallpapersScreen() {
    drawPremiumBackdrop(kPurple);
    drawPremiumHeader("Watch Faces", kPurple);
    const char *subs[] = {"Pure black", "Contour lines", "Night sky", "Warm horizon",
                          "Minimal grid"};
    for (int i = 0; i < 5; ++i) {
        int y = 58 + i * 72;
        bool selected = m_wallpaperSelection == i;
        drawPremiumCard(24, y, 362, 64, 18, kPurple, selected);
        fillRR(38, y + 8, 46, 48, 12, kBlack);
        drawWallpaper(i, 41, y + 11, 40, 42);
        D().drawText(99, y + 7, kWallpaperNames[i], kWhite, Font::Label, false, 232);
        D().drawText(99, y + 35, subs[i], kTxtMuted, Font::Caption, false, 232);
        if (selected)
            D().drawSymbol(357, y + 32, 22, Symbol::Check, kPurple);
    }
    drawAction(24, 428, 362, 48, "Apply watch face", kPurpleDim, kPurple);
    renderToastOverlay();
    D().present();
}

void UiManager::handleFallDetectionTap(int, int y) {
    if (y < 52 || y >= 432) {
        m_fallDemoStartUs = 0;
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        return;
    }
    if (y >= 299 && y < 359) {
        auto &imu = ImuQMI8658::instance();
        if (!imu.setFallDetectionEnabled(!imu.isFallDetectionEnabled()))
            showToast("Motion sensor unavailable", 2000);
    } else if (y >= 370 && y < 421) {
        if (m_fallDemoStartUs) {
            m_fallDemoStartUs = 0;
            showToast("Demo cancelled · no alert sent", 2000);
        } else
            m_fallDemoStartUs = esp_timer_get_time();
    }
    renderFallDetectionScreen();
}
void UiManager::renderFallDetectionScreen() {
    drawPremiumBackdrop(kCoral);
    drawPremiumHeader("Fall Detection", kCoral);
    auto &imu = ImuQMI8658::instance();
    bool demo = m_fallDemoStartUs != 0;
    int left =
        demo ? std::max<int>(0, 30 - (esp_timer_get_time() - m_fallDemoStartUs) / 1000000) : 0;
    if (demo && left == 0) {
        m_fallDemoStartUs = 0;
        demo = false;
        showToast("Demo complete · no alert sent", 2000);
    }
    fillCirc(205, 125, 46, kCard);
    drawIconShield(205, 125, 50, imu.isFallDetectionEnabled() ? kMint : kTxtMuted, true);
    D().drawText(205, 191,
                 demo                           ? "Fall alert demo"
                 : imu.isFallDetectionEnabled() ? "Monitoring motion"
                                                : "Monitoring is off",
                 kWhite, Font::Title, true, 360);
    if (demo) {
        char t[16];
        std::snprintf(t, sizeof(t), "%02d", left);
        D().drawText(205, 226, t, kCoral, Font::Timer, true);
    } else {
        D().drawText(205, 235, "Fall + impact + stillness", kTxtMuted, Font::Body, true);
        D().drawText(205, 263, "30 seconds to cancel an SOS", kTxtMuted, Font::Caption, true);
    }
    drawAction(24, 303, 362, 54,
               imu.isFallDetectionEnabled() ? "Turn monitoring off" : "Turn monitoring on",
               imu.isFallDetectionEnabled() ? kMintDim : kCard,
               imu.isFallDetectionEnabled() ? kMint : kTxtSoft);
    drawAction(24, 373, 362, 48, demo ? "I'm OK · cancel demo" : "Try alert demo", kCard, kCoral);
    drawFooter("Apps");
    renderToastOverlay();
    D().present();
}
void UiManager::handlePhoneLinkTap(int, int y) {
    if (y < 52 || y >= 432) {
        showScreen(WatchScreenId::SCREEN_LAUNCHPAD);
        return;
    }
    auto &phone = PhoneBridge::instance();
    if (y >= 360 && y < 420) {
        if (phone.status().connected)
            phone.forgetPhone();
        phone.openPairing();
    } else if (y >= 295 && y < 345) {
        if (!phone.request("location"))
            showToast("Open the Android companion", 2000);
        else
            showToast("Location requested from phone", 2000);
    }
    renderPhoneLinkScreen();
}
void UiManager::renderPhoneLinkScreen() {
    drawPremiumBackdrop(kBlue);
    drawPremiumHeader("Phone Link", kBlue);
    auto &phone = PhoneBridge::instance();
    auto status = phone.status();
    D().drawSymbol(205, 101, 50, Symbol::Phone, status.ready ? kMint : kBlue);
    D().drawText(205, 143,
                 status.ready        ? "Android connected"
                 : phone.isPairing() ? "Enter code on phone"
                                     : "Connect your Android",
                 kWhite, Font::Title, true, 364);
    if (phone.isPairing()) {
        char code[16];
        std::snprintf(code, sizeof(code), "%06lu", (unsigned long)phone.passkey());
        D().drawText(205, 188, code, kBlue, Font::Display, true);
        D().drawText(205, 250, "Pairing is open for 60 seconds", kTxtMuted, Font::Caption, true);
    } else {
        const char *rows[] = {"Calls", "SMS", "Location"};
        bool allowed[] = {status.callsAllowed, status.smsAllowed, status.locationAllowed};
        for (int i = 0; i < 3; ++i) {
            int y = 191 + i * 28;
            D().drawText(40, y, rows[i], kTxtSoft, Font::Body);
            D().drawText(362, y, allowed[i] ? "Ready" : "Needs app", allowed[i] ? kMint : kTxtMuted,
                         Font::Caption, true, 94);
        }
    }
    drawAction(24, 296, 362, 48, "Get phone location", kCard, kTxtSoft);
    drawAction(24, 365, 362, 54, status.connected ? "Pair a different phone" : "Start pairing",
               kBlueDim, kBlue);
    drawFooter("Apps");
    renderToastOverlay();
    D().present();
}

// ─────────────────────────────────────────────────────────────────────────────
//  TOAST OVERLAY
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::renderToastOverlay() {
    if (!m_toastMessage[0])
        return;
    if (esp_timer_get_time() > m_toastExpiryUs) {
        m_toastMessage[0] = 0;
        return;
    }
    int w = std::min(366, D().measureText(m_toastMessage, Font::Body) + 44), x = (410 - w) / 2;
    fillRR(x, 55, w, 46, 23, kCardHi);
    D().drawText(205, 67, m_toastMessage, kWhite, Font::Body, true, w - 28);
}

// ─────────────────────────────────────────────────────────────────────────────
//  MAIN TICK
// ─────────────────────────────────────────────────────────────────────────────
void UiManager::tick() {
    UiLock uiLock;
    if (!m_initialized)
        return;

    m_animFrame++;
    int64_t now = esp_timer_get_time();

    // ── 1. Touch Input (FT3168)
    TouchPoint pt;
    bool hasTouch = TouchFT3168::instance().read(pt);

    if (hasTouch && pt.pressed) {
        if (!m_isTouched) {
            m_isTouched = true;
            m_touchStartX = pt.x;
            m_touchStartY = pt.y;
            m_lastTouchX = pt.x;
            m_lastTouchY = pt.y;
            m_touchStartTimeUs = now;

            if (m_currentScreen == WatchScreenId::SCREEN_SAFE) {
                m_safeTouchDownUs = now;
            }
        } else {
            m_lastTouchX = pt.x;
            m_lastTouchY = pt.y;
            handleTouchDrag(pt.x, pt.y, true);

            // Safe Screen Long-Press (> 1.5s) opens Wallpapers selector!
            if (m_currentScreen == WatchScreenId::SCREEN_SAFE && m_safeTouchDownUs > 0) {
                if ((now - m_safeTouchDownUs) > 1500000LL) {
                    m_safeTouchDownUs = 0;
                    showToast("Select Wallpaper", 1500);
                    showScreen(WatchScreenId::SCREEN_WALLPAPERS);
                }
            }
        }
    } else {
        if (m_isTouched) {
            m_isTouched = false;
            m_safeTouchDownUs = 0;
            int64_t durMs = (now - m_touchStartTimeUs) / 1000;
            int dx = m_lastTouchX - m_touchStartX;
            int dy = m_lastTouchY - m_touchStartY;
            handleTouchDrag(m_lastTouchX, m_lastTouchY, false);

            bool isSwipeH = (std::abs(dx) > 60 && std::abs(dx) > std::abs(dy) * 1.3f);
            bool isSwipeV = (std::abs(dy) > 60 && std::abs(dy) > std::abs(dx) * 1.3f);

            if (isSwipeH && dx < 0)
                handleSwipeLeft();
            else if (isSwipeH && dx > 0)
                handleSwipeRight();
            else if (isSwipeV && dy < 0)
                handleSwipeUp();
            else if (isSwipeV && dy > 0)
                handleSwipeDown();
            else if (durMs < 1200)
                handleTouchTap(m_touchStartX, m_touchStartY);
        }
    }

    // ── 2. Per-second tick (RTC, emergency countdown)
    if (now - m_lastSecondTickUs >= 1000000LL) {
        m_lastSecondTickUs = now;
        m_lastSimStatus = SIM800L::instance().getStatus();

        char tBuf[16], dBuf[32];
        RtcPCF85063::instance().getTimeString(tBuf, sizeof(tBuf));
        RtcPCF85063::instance().getDateString(dBuf, sizeof(dBuf));
        bool changed = (m_timeStr != tBuf);
        m_timeStr = tBuf;
        m_dateStr = dBuf;

        if (m_currentScreen == WatchScreenId::SCREEN_EMERGENCY) {
            uint32_t smSec = StateMachine::instance().getGraceRemainingSec();
            bool graceActive = StateMachine::instance().isEmergencyGraceActive();
            m_emergencyCountdownSec = graceActive ? smSec : 0;
            renderEmergencyScreen();
        }

        if (changed && m_currentScreen == WatchScreenId::SCREEN_SAFE) {
            renderSafeScreen();
        }
    }

    // ── 3. Battery poll (every ~5s)
    if (m_animFrame % 333 == 0) {
        BatteryStatus bat;
        if (AXP2101::instance().getBatteryStatus(bat) == ESP_OK)
            m_batteryPercent = bat.percentage;
    }

    // ── 4. Auto-dismiss overlays
    if (m_currentScreen == WatchScreenId::SCREEN_SMS_SENT) {
        if (now - m_overlayStartUs >= 90000000LL)
            showScreen(m_cancelFromScreen);
    }

    // ── 5. Dynamic screen refresh (100ms interval)
    if (now - m_lastRenderTimeUs >= 100000LL) {
        m_lastRenderTimeUs = now;

        switch (m_currentScreen) {
        case WatchScreenId::SCREEN_FALL_DETECTION:
            renderFallDetectionScreen();
            break;
        case WatchScreenId::SCREEN_PHONE_LINK:
            renderPhoneLinkScreen();
            break;
        case WatchScreenId::SCREEN_SMS_SENT:
            renderSmsSentScreen();
            break;
        case WatchScreenId::SCREEN_TELEMETRY:
            renderTelemetryScreen();
            break;
        case WatchScreenId::SCREEN_FLASHLIGHT:
            if (m_flashlightMode == FlashlightMode::RED_STROBE) {
                static int64_t s_lastStrobeUs = 0;
                if (now - s_lastStrobeUs >= 250000LL) {
                    s_lastStrobeUs = now;
                    m_strobeState = !m_strobeState;
                    renderFlashlightScreen();
                }
            }
            break;
        case WatchScreenId::SCREEN_WATCH:
            renderWatchScreen();
            break;
        case WatchScreenId::SCREEN_CANCEL:
            renderCancelScreen();
            break;
        case WatchScreenId::SCREEN_CALLING:
            renderCallingScreen();
            break;
        case WatchScreenId::SCREEN_RECORDER:
            if (m_isRecordingMemo) {
                if (!AudioManager::instance().isRecordingVoiceMemo()) {
                    m_isRecordingMemo = false;
                    m_hasRecordedMemo = AudioManager::instance().getRecordedSamples() > 0;
                    m_memoDurationSec = AudioManager::instance().getRecordedDurationSec();
                }
                renderRecorderScreen();
            } else if (m_isPlayingMemo) {
                if (!AudioManager::instance().isPlayingVoiceMemo()) {
                    m_isPlayingMemo = false;
                    AudioManager::instance().stopVoiceMemoPlayback();
                    renderRecorderScreen();
                } else {
                    renderRecorderScreen();
                }
            }
            break;
        default:
            if (m_toastMessage[0] != '\0' && now > m_toastExpiryUs) {
                m_toastMessage[0] = '\0';
                if (m_currentScreen == WatchScreenId::SCREEN_SAFE) {
                    renderSafeScreen();
                }
            }
            break;
        }
    }
}

} // namespace SheShield
