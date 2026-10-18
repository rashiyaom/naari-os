#pragma once
#include "../safety/hold_to_confirm.h"

#include "../drivers/sim800l/sim800l.h"
#include "../state_machine/state_machine.h"
#include <cstdint>
#include <atomic>
#include <esp_err.h>
#include <string>
#include <vector>

namespace SheShield {

// ─── Screen Identifiers ────────────────────────────────────────────────
enum class WatchScreenId {
    SCREEN_SAFE,           // Main watch face: clock, rings, dock
    SCREEN_LAUNCHPAD,      // App drawer: four pages, up to four apps per page
    SCREEN_SOUND_STUDIO,   // Alarm soundboard
    SCREEN_CONTROL_CENTER, // Quick settings tiles
    SCREEN_FLASHLIGHT,     // Multi-mode torch
    SCREEN_TELEMETRY,      // IMU + horizon + compass
    SCREEN_SYSTEM,         // Hardware diagnostics
    SCREEN_WATCH,          // Night walk guard session
    SCREEN_EMERGENCY,      // SOS alert active
    SCREEN_CONTACTS,       // Trusted contact directory
    SCREEN_DIALER,         // Phone keypad
    SCREEN_CANCEL,         // Hold-to-disarm slider
    SCREEN_CALLING,        // Outgoing call confirmation overlay
    SCREEN_SMS_SENT,       // SMS dispatched confirmation overlay
    SCREEN_RECORDER,       // Voice memo recorder & speaker playback
    SCREEN_WALLPAPERS,     // AMOLED watch face wallpaper selector
    SCREEN_FALL_DETECTION,
    SCREEN_PHONE_LINK,
};

// ─── Flashlight Modes ──────────────────────────────────────────────────
enum class FlashlightMode { WHITE_100 = 0, AMBER_WARM, RED_STROBE };

// ─── UiManager: Central display + touch controller ─────────────────────
class UiManager {
  public:
    static UiManager &instance();

    struct SyncSettings {
        int wallpaper, brightness, volume, battery, screen;
        bool fall;
    };
    SyncSettings syncSettings();
    bool applySetting(const char *operation, int value);
    esp_err_t init();
    void showScreen(WatchScreenId screenId);
    void updateState(SystemState state);
    void updateSimStatus(const SimStatusReport &simReport);
    void tick(); // Called every 15ms from uiTask

    // Physical button interface
    void handleSideButtonPress();        // AXP2101 Power / Side Button short click
                                         // (Control Center toggle)
    void handleSideButtonLongPress();    // AXP2101 / BOOT Long Press (Emergency SOS
                                         // trigger)
    void handleCrownButtonPress();       // Digital Crown (BOOT) single click (Home /
                                         // Launcher toggle)
    void handleCrownButtonDoublePress(); // Digital Crown (BOOT) double click
                                         // (Quick app switch)

    // Touch events (dispatched by tick)
    void handleTouchTap(int x, int y);
    void handleTouchDrag(int x, int y, bool pressed);
    void handleSwipeLeft();
    void handleSwipeRight();
    void handleSwipeUp();
    void handleSwipeDown();

    // Toast notification (auto-clears)
    void showToast(const char *message, int durationMs = 2200);
    void requestRefresh() { m_refreshRequested.store(true); }
    void requestScreen(WatchScreenId screen) { m_requestedScreen.store(static_cast<int>(screen)); }

    // Hold-to-cancel SOS flow
    void startHoldCancellation();
    void updateHoldCancellation(float progressPct);
    void finishHoldCancellation();
    void cancelHoldCancellation();

    // Calling / SMS overlay helpers (public so contacts can trigger)
    void showCallingScreen(const char *name, const char *number, uint32_t requestId = 0);
    void showSmsSentScreen(const char *name, const char *number, uint32_t requestId = 0);

    WatchScreenId getCurrentScreen() const { return m_currentScreen; }

  private:
    UiManager();
    ~UiManager() = default;
    UiManager(const UiManager &) = delete;
    UiManager &operator=(const UiManager &) = delete;

    // ── Screen renderers ─────────────────────────────────────
    void renderSafeScreen();
    void renderLaunchpadScreen();
    void renderSoundStudioScreen();
    void renderControlCenterScreen();
    void renderFlashlightScreen();
    void renderTelemetryScreen();
    void renderSystemScreen();
    void renderWatchScreen();
    void renderEmergencyScreen();
    void renderContactsScreen();
    void renderDialerScreen();
    void renderCancelScreen();
    void renderCallingScreen();
    void renderSmsSentScreen();
    void renderRecorderScreen();
    void renderMemoLibrary();
    void renderStoredMemo();
    void handleMemoLibraryTap(int x, int y);
    void renderWallpapersScreen();
    void renderFallDetectionScreen();
    void renderPhoneLinkScreen();
    void renderToastOverlay();

    // ── Per-screen touch dispatch ─────────────────────────────
    void handleSafeTap(int x, int y);
    void handleLaunchpadTap(int x, int y);
    void handleSoundStudioTap(int x, int y);
    void handleControlCenterTap(int x, int y);
    void handleFlashlightTap(int x, int y);
    void handleTelemetryTap(int x, int y);
    void handleSystemTap(int x, int y);
    void handleContactsTap(int x, int y);
    void handleDialerTap(int x, int y);
    void handleWatchTap(int x, int y);
    void handleEmergencyTap(int x, int y);
    void handleCallingTap(int x, int y);
    void handleSmsSentTap(int x, int y);
    void handleRecorderTap(int x, int y);
    void handleWallpapersTap(int x, int y);
    void handleFallDetectionTap(int x, int y);
    void handlePhoneLinkTap(int x, int y);

    // ── Icon drawing helpers (all pixel-precise SF-symbol style) ─
    void drawIconPhone(int cx, int cy, int sz, uint16_t col);
    void drawIconShield(int cx, int cy, int sz, uint16_t col, bool filled);
    void drawIconAlert(int cx, int cy, int sz, uint16_t col);
    void drawIconMusic(int cx, int cy, int sz, uint16_t col);
    void drawIconGrid(int cx, int cy, int sz, uint16_t col);
    void drawIconBattery(int cx, int cy, int pct, uint16_t col);
    void drawIconSignal(int cx, int cy, int csq, uint16_t col);
    void drawIconWifi(int cx, int cy, int sz, uint16_t col);
    void drawIconVolume(int cx, int cy, int sz, uint16_t col);
    void drawIconPerson(int cx, int cy, int sz, uint16_t col);
    void drawIconFlash(int cx, int cy, int sz, uint16_t col);
    void drawIconCompass(int cx, int cy, int sz, uint16_t col);
    void drawIconCall(int cx, int cy, int sz, uint16_t col);
    void drawIconMessage(int cx, int cy, int sz, uint16_t col);
    void drawIconEndCall(int cx, int cy, int sz, uint16_t col);
    void drawIconKeypad(int cx, int cy, int sz, uint16_t col);
    void drawIconSliders(int cx, int cy, int sz, uint16_t col);
    void drawIconGear(int cx, int cy, int sz, uint16_t col);
    void drawIconClock(int cx, int cy, int sz, uint16_t col);
    void drawIconMicrophone(int cx, int cy, int sz, uint16_t col);
    void drawIconWallpaper(int cx, int cy, int sz, uint16_t col);
    void drawNavBack(int y); // Consistent back header pill
    void drawPremiumBackdrop(uint16_t accent);
    void drawPremiumHeader(const char *title, uint16_t accent, bool showBack = true);
    void drawPremiumCard(int x, int y, int w, int h, int radius, uint16_t accent,
                         bool active = false);
    void drawIconTile(int cx, int cy, int size, uint16_t accent, int iconType);
    void drawFooter(const char *title, int y = 432);
    void drawAction(int x, int y, int w, int h, const char *title, uint16_t background,
                    uint16_t foreground);

    // ── Primitive drawing passthrough helpers (inline wrappers) ─
    void drawHLine(int x, int y, int w, uint16_t col);
    void drawVLine(int x, int y, int h, uint16_t col);
    void fillRect(int x, int y, int w, int h, uint16_t col);
    void fillRR(int x, int y, int w, int h, int r, uint16_t col);
    void drawRR(int x, int y, int w, int h, int r, uint16_t col);
    void fillCirc(int cx, int cy, int r, uint16_t col);
    void drawCirc(int cx, int cy, int r, uint16_t col);
    void drawStr(int cx, int cy, const char *s, uint16_t col, uint8_t scale, bool centred);
    void drawStrBold(int cx, int cy, const char *s, uint16_t col, uint8_t scale = 1,
                     bool centred = false);
    void drawDivider(int y);

    // ── State ────────────────────────────────────────────────
    bool m_initialized;
    WatchScreenId m_currentScreen;
    WatchScreenId m_prevScreen; // for Back navigation
    WatchScreenId m_cancelFromScreen;
    SystemState m_systemState;
    bool m_sirenTestActive;
    FlashlightMode m_flashlightMode;
    int64_t m_strobeStartUs = 0;

    uint8_t m_batteryPercent;
    uint8_t m_lastPhoneLinkState = 255; // last rendered: offline / BLE only / app ready
    std::string m_timeStr;
    std::string m_dateStr;

    // Dialer
    std::string m_dialedNumber;

    // Toast
    char m_toastMessage[80];
    int64_t m_toastExpiryUs;

    // Emergency / Cancel
    uint32_t m_emergencyCountdownSec; // counts from 10 → 0 (grace window)
    int64_t m_lastGraceTickUs;        // precise per-second timer
    HoldToConfirm m_cancelHold;
    float m_holdProgress;
    bool m_cancelConfirmed;
    bool m_holdingCancel;

    // Calling / SMS overlay
    char m_overlayName[64];
    char m_overlayNumber[32];
    int64_t m_overlayStartUs; // auto-dismiss after 5s (SMS)

    // Touch state
    bool m_isTouched;
    int m_touchStartX;
    int m_touchStartY;
    int m_lastTouchX;
    int m_lastTouchY;
    int64_t m_touchStartTimeUs;

    // Side-button / Crown debounce
    int64_t m_lastSideBtnPressUs;
    int64_t m_lastCrownBtnPressUs;

    // Apple Watch Simple Slide App Drawer (vertical list physics)
    float m_drawerScrollY;
    float m_drawerVelY;
    float m_launcherPanX;
    float m_launcherPanY;
    float m_launcherVelX;
    float m_launcherVelY;
    int m_lastDragX;
    int m_lastDragY;
    bool m_isDraggingLauncher;
    int64_t m_lastDragTimeUs;

    // Drawer 4-App Stacks Pagination
    int m_drawerPage;
    int m_wallpaperIdx;
    int m_wallpaperSelection = 0;
    int64_t m_fallDemoStartUs = 0;
    uint32_t m_overlayRequestId = 0;

    // Voice Memo Audio Recorder
    bool m_confirmMemoDelete = false;
    bool m_showMemoLibrary = false;
    unsigned m_memoLibraryPage = 0;
    std::string m_selectedMemoId;
    bool m_isRecordingMemo;
    bool m_hasRecordedMemo;
    bool m_isPlayingMemo;
    int m_memoDurationSec;
    int64_t m_recordStartTimeUs;
    int64_t m_playbackStartTimeUs;
    int64_t m_safeTouchDownUs;

    // Render / animation
    uint32_t m_animFrame;
    int64_t m_lastRenderTimeUs;
    int64_t m_lastSecondTickUs; // drives safe-screen clock + emergency countdown
    std::atomic<bool> m_refreshRequested{false};
    std::atomic<int> m_requestedScreen{-1};

    SimStatusReport m_lastSimStatus;
};

} // namespace SheShield
