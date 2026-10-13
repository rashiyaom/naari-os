import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { insertEvent } from '../services/supabase';

// ─── Screen Identifiers (1:1 with firmware WatchScreenId) ───────────────────
export const SCREENS = {
  SAFE:           'safe',
  LAUNCHPAD:      'launchpad',
  SOUND_STUDIO:   'sound_studio',
  CONTROL_CENTER: 'control_center',
  FLASHLIGHT:     'flashlight',
  TELEMETRY:      'telemetry',
  SYSTEM:         'system',
  WATCH:          'watch',
  EMERGENCY:      'emergency',
  CONTACTS:       'contacts',
  DIALER:         'dialer',
  CANCEL:         'cancel',
  CALLING:        'calling',
  SMS_SENT:       'sms_sent',
};

// ─── Web Audio Synthesizer for Sound Studio & Phone Ringing ─────────────────
class WebAudioPlayer {
  constructor() {
    this.ctx = null;
    this.osc = null;
    this.gain = null;
    this.timer = null;
    this.currentSound = null;
  }

  init() {
    if (!this.ctx && typeof window !== 'undefined') {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) this.ctx = new AudioCtx();
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  play(type, volume = 0.5) {
    this.stop();
    this.init();
    if (!this.ctx) return;

    this.currentSound = type;
    const now = this.ctx.currentTime;
    this.osc = this.ctx.createOscillator();
    this.gain = this.ctx.createGain();
    this.gain.gain.setValueAtTime(volume * 0.4, now);

    this.osc.connect(this.gain);
    this.gain.connect(this.ctx.destination);

    if (type === 'phone_ring') {
      // Realistic international telephone ringback cadence (440Hz/480Hz dual cadence)
      this.osc.type = 'sine';
      this.osc.frequency.setValueAtTime(440, now);
      let ringStep = 0;
      this.timer = setInterval(() => {
        if (!this.ctx || !this.gain || !this.osc) return;
        ringStep = (ringStep + 1) % 40;
        // Ring for 1.8s (steps 0-17), pause for 2.2s (steps 18-39)
        const isRinging = ringStep < 18;
        if (isRinging) {
          this.osc.frequency.setValueAtTime(ringStep % 2 === 0 ? 440 : 480, this.ctx.currentTime);
          this.gain.gain.setValueAtTime(volume * 0.35, this.ctx.currentTime);
        } else {
          this.gain.gain.setValueAtTime(0.0001, this.ctx.currentTime);
        }
      }, 100);
    } else if (type === 'police') {
      // 600 - 1500 Hz wail sweep
      this.osc.type = 'sawtooth';
      this.osc.frequency.setValueAtTime(600, now);
      let step = 0;
      this.timer = setInterval(() => {
        if (!this.ctx || !this.osc) return;
        step = (step + 1) % 40;
        const freq = 600 + Math.sin(step * 0.16) * 700;
        this.osc.frequency.setValueAtTime(freq, this.ctx.currentTime);
      }, 50);
    } else if (type === 'pulse') {
      // 2400 Hz high pulse
      this.osc.type = 'square';
      this.osc.frequency.setValueAtTime(2200, now);
      let on = true;
      this.timer = setInterval(() => {
        if (!this.ctx || !this.gain) return;
        on = !on;
        this.gain.gain.setValueAtTime(on ? volume * 0.4 : 0.001, this.ctx.currentTime);
      }, 120);
    } else if (type === 'ambulance') {
      // 960 / 770 Hz hi-lo
      this.osc.type = 'sine';
      let hi = true;
      this.osc.frequency.setValueAtTime(960, now);
      this.timer = setInterval(() => {
        if (!this.ctx || !this.osc) return;
        hi = !hi;
        this.osc.frequency.setValueAtTime(hi ? 960 : 770, this.ctx.currentTime);
      }, 400);
    } else if (type === 'chirp') {
      // 800-2400 Hz chirp
      this.osc.type = 'triangle';
      this.osc.frequency.setValueAtTime(1400, now);
    } else {
      // Bell chime
      this.osc.type = 'sine';
      this.osc.frequency.setValueAtTime(1760, now);
      this.gain.gain.exponentialRampToValueAtTime(0.001, now + 1.2);
    }

    this.osc.start();
  }

  stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.osc) {
      try { this.osc.stop(); } catch (e) {}
      this.osc.disconnect();
      this.osc = null;
    }
    if (this.gain) {
      this.gain.disconnect();
      this.gain = null;
    }
    this.currentSound = null;
  }
}

const audioSynth = new WebAudioPlayer();

// ─── 5 AMOLED Watch Face Wallpapers ──────────────────────────────────────────
export const WALLPAPERS = [
  { id: 'oled_black',  name: 'OLED Pitch Black',  type: 'color',    value: '#000000', thumb: '#000000' },
  { id: 'topographic', name: 'Ultra Topographic', type: 'image',    value: '/wallpapers/topo.jpg', thumb: '#0f291e' },
  { id: 'aurora',      name: 'Cosmic Aurora',     type: 'image',    value: '/wallpapers/aurora.jpg', thumb: '#160d38' },
  { id: 'sunset',      name: 'Sunset Gradient',   type: 'image',    value: '/wallpapers/sunset.jpg', thumb: '#2e1430' },
  { id: 'cyberpunk',   name: 'Cyber Matrix',      type: 'gradient', value: 'radial-gradient(ellipse at 50% 30%, #0d2822 0%, #04090b 60%, #000000 100%)', thumb: '#081c17' }
];

// ─── Simple Slide App Drawer Items (Custom Image Icons, No Emojis) ───────────
export const DRAWER_APPS = [
  {
    id: 'sos',
    name: 'Emergency SOS',
    sub: 'Instant 30s Grace & Dispatch',
    iconSrc: '/icons/icon_sos.png',
    screen: SCREENS.EMERGENCY,
    badge: 'PRIORITY',
    badgeCol: '#ff453a'
  },
  {
    id: 'phone',
    name: 'Phone & Dialer',
    sub: 'Cellular Outgoing Calls',
    iconSrc: '/icons/icon_phone.png',
    screen: SCREENS.DIALER,
    badge: 'READY',
    badgeCol: '#30d158'
  },
  {
    id: 'guard',
    name: 'Night Walk Guard',
    sub: 'GPS Beacon & Guardian Ping',
    iconSrc: '/icons/icon_guard.png',
    screen: SCREENS.WATCH,
    badge: 'ACTIVE',
    badgeCol: '#ffd60a'
  },
  {
    id: 'sounds',
    name: 'Sound Studio',
    sub: '105dB Police Siren & Pulse',
    iconSrc: '/icons/icon_sounds.png',
    screen: SCREENS.SOUND_STUDIO,
    badge: 'ARMED',
    badgeCol: '#ff9f0a'
  },
  {
    id: 'torch',
    name: 'Flashlight Torch',
    sub: '3 OLED Strobe Modes',
    iconSrc: '/icons/icon_torch.png',
    screen: SCREENS.FLASHLIGHT,
    badge: 'OLED',
    badgeCol: '#ffffff'
  },
  {
    id: 'contacts',
    name: 'Emergency Contacts',
    sub: '3 Pre-authorized Guardians',
    iconSrc: '/icons/icon_contacts.png',
    screen: SCREENS.CONTACTS,
    badge: 'SYNCED',
    badgeCol: '#0a84ff'
  },
  {
    id: 'compass',
    name: 'Compass & Heading',
    sub: 'Heading & Realtime Telemetry',
    iconSrc: '/icons/icon_compass.png',
    screen: SCREENS.TELEMETRY,
    badge: '028° NE',
    badgeCol: '#30b0c7'
  },
  {
    id: 'control',
    name: 'Control Center',
    sub: 'Hardware Radios & Toggles',
    iconSrc: '/icons/icon_control.png',
    screen: SCREENS.CONTROL_CENTER,
    badge: 'SYSTEM',
    badgeCol: '#8e8e93'
  },
  {
    id: 'system',
    name: 'Diagnostics',
    sub: 'Hardware Sensors & PMIC Battery',
    iconSrc: '/icons/icon_system.png',
    screen: SCREENS.SYSTEM,
    badge: 'ONLINE',
    badgeCol: '#636366'
  },
  {
    id: 'keypad',
    name: 'Direct Keypad',
    sub: 'Quick Standalone GSM Dialer',
    iconSrc: '/icons/icon_keypad.png',
    screen: SCREENS.DIALER,
    badge: '4G LTE',
    badgeCol: '#00e8a8'
  },
];

export default function WatchSimulator({ targetScreen, targetWallpaper }) {
  const { wearer, device } = useAuth();

  // Navigation & State
  const [currentScreen, setCurrentScreen] = useState(SCREENS.SAFE);
  const [prevScreen, setPrevScreen] = useState(SCREENS.SAFE);
  const [cancelFromScreen, setCancelFromScreen] = useState(SCREENS.SAFE);

  // Wallpaper state
  const [wallpaperIdx, setWallpaperIdx] = useState(1); // default to Ultra Topographic

  useEffect(() => {
    if (targetScreen && Object.values(SCREENS).includes(targetScreen)) {
      setCurrentScreen(targetScreen);
    }
  }, [targetScreen]);

  useEffect(() => {
    if (targetWallpaper !== undefined && targetWallpaper !== null) {
      const idx = WALLPAPERS.findIndex(w => w.id === targetWallpaper);
      if (idx !== -1) setWallpaperIdx(idx);
    }
  }, [targetWallpaper]);

  // Time & Status
  const [time, setTime] = useState(new Date());
  const [battery, setBattery] = useState(94);
  const [csq, setCsq] = useState(26);

  // Control Center toggles
  const [cellularActive, setCellularActive] = useState(true);
  const [wifiActive, setWifiActive] = useState(true);
  const [silentActive, setSilentActive] = useState(false);

  // Simple Slide App Drawer Scroll State
  const [drawerScrollY, setDrawerScrollY] = useState(0);

  // Emergency 30s Countdown
  const [countdown, setCountdown] = useState(30);
  const [isEmergencyActive, setIsEmergencyActive] = useState(false);
  const countdownTimerRef = useRef(null);

  // Hold to Cancel Progress
  const [holdProgress, setHoldProgress] = useState(0);
  const isHoldingRef = useRef(false);
  const holdStartTimeRef = useRef(0);

  // Calling & SMS Overlays
  const [callingInfo, setCallingInfo] = useState({ name: 'Emergency 112', number: '112', elapsed: 0 });
  const [smsInfo, setSmsInfo] = useState({ name: '3 Guardians', number: '+91 98765 43210' });
  const callTimerRef = useRef(null);

  // Flashlight mode
  const [flashlightMode, setFlashlightMode] = useState(0); // 0=White, 1=Amber, 2=Strobe
  const [strobeOn, setStrobeOn] = useState(false);

  // Sound Studio State
  const [activeSound, setActiveSound] = useState(null);
  const [volume, setVolume] = useState(75);

  // Dialer State
  const [dialedNum, setDialedNum] = useState('');

  // Toast
  const [toast, setToast] = useState(null);

  const showToast = useCallback((msg) => {
    setToast(msg);
    setTimeout(() => setToast(null), 2500);
  }, []);

  // Update real-time clock
  useEffect(() => {
    const t = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(t);
  }, []);

  // Screen transition helper
  const navigateTo = useCallback((screenId) => {
    setPrevScreen(currentScreen);
    setCurrentScreen(screenId);
    setHoldProgress(0);
  }, [currentScreen]);

  // ── Emergency SOS 30-Second Countdown Lifecycle ─────────────────────────
  const startEmergencyProtocol = useCallback(() => {
    setIsEmergencyActive(true);
    setCountdown(30);
    setCancelFromScreen(currentScreen);
    navigateTo(SCREENS.EMERGENCY);
    audioSynth.play('police', volume / 100);

    if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);

    countdownTimerRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(countdownTimerRef.current);
          countdownTimerRef.current = null;
          // 30 seconds completed -> trigger Outgoing Calling Overlay!
          setCallingInfo({ name: 'Police Helpline 112', number: '112', elapsed: 0 });
          navigateTo(SCREENS.CALLING);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    // Also dispatch to Supabase backend
    insertEvent({
      wearer_id: wearer?.id || 'demo_wearer',
      device_id: device?.id || 'demo_watch',
      event_type: 'emergency_triggered',
      lat: 28.6139,
      lng: 77.2090,
      address: 'New Delhi (Live Watch GPS)',
      payload: { trigger: 'MANUAL_SOS_30S_COUNTDOWN', countdown: 30 }
    }).catch(() => {});
  }, [currentScreen, navigateTo, volume, wearer, device]);

  // Disarm Emergency SOS
  const disarmSOS = useCallback(() => {
    if (countdownTimerRef.current) {
      clearInterval(countdownTimerRef.current);
      countdownTimerRef.current = null;
    }
    audioSynth.stop();
    setIsEmergencyActive(false);
    setCountdown(30);
    showToast("SOS Disarmed: Safe");
    navigateTo(SCREENS.SAFE);

    insertEvent({
      wearer_id: wearer?.id || 'demo_wearer',
      device_id: device?.id || 'demo_watch',
      event_type: 'emergency_resolved',
      lat: 28.6139,
      lng: 77.2090,
      address: 'New Delhi (Live Watch GPS)',
      payload: { resolved_by: 'WATCH_DISARM_HOLD' }
    }).catch(() => {});
  }, [navigateTo, showToast, wearer, device]);

  // Outgoing call duration ticker
  useEffect(() => {
    if (currentScreen === SCREENS.CALLING) {
      callTimerRef.current = setInterval(() => {
        setCallingInfo((prev) => ({ ...prev, elapsed: prev.elapsed + 1 }));
      }, 1000);
    } else {
      if (callTimerRef.current) clearInterval(callTimerRef.current);
    }
    return () => {
      if (callTimerRef.current) clearInterval(callTimerRef.current);
    };
  }, [currentScreen]);

  // Flashlight red strobe effect
  useEffect(() => {
    if (currentScreen === SCREENS.FLASHLIGHT && flashlightMode === 2) {
      const st = setInterval(() => setStrobeOn((prev) => !prev), 150);
      return () => clearInterval(st);
    }
  }, [currentScreen, flashlightMode]);

  // ── Physical Controls (Digital Crown & Side Power Button) ────────────────
  const handleCrownClick = () => {
    if (currentScreen === SCREENS.SAFE) {
      // Crown click on Watch Face opens the Honeycomb SpringBoard!
      navigateTo(SCREENS.LAUNCHPAD);
    } else if (currentScreen === SCREENS.CANCEL) {
      // Crown click aborts cancel and returns to active emergency
      navigateTo(SCREENS.EMERGENCY);
    } else if (currentScreen === SCREENS.CALLING || currentScreen === SCREENS.SMS_SENT) {
      audioSynth.stop();
      navigateTo(cancelFromScreen || SCREENS.SAFE);
    } else {
      // Single crown click from any app/screen smoothly navigates home to Watch Face!
      audioSynth.stop();
      navigateTo(SCREENS.SAFE);
    }
  };

  const handleCrownDoubleClick = () => {
    // Quick app switch between current and previous screen
    if (prevScreen && prevScreen !== currentScreen) {
      navigateTo(prevScreen);
    } else {
      navigateTo(SCREENS.SAFE);
    }
  };

  const handleSideButtonClick = () => {
    if (currentScreen === SCREENS.CALLING) {
      audioSynth.stop();
      navigateTo(cancelFromScreen || SCREENS.SAFE);
      return;
    }
    if (currentScreen === SCREENS.CONTROL_CENTER) {
      navigateTo(prevScreen === SCREENS.CONTROL_CENTER ? SCREENS.SAFE : prevScreen);
    } else {
      navigateTo(SCREENS.CONTROL_CENTER);
    }
  };

  const handleSideButtonLongPress = () => {
    showToast("EMERGENCY SOS TRIGGERED!");
    startEmergencyProtocol();
  };

  // Side button hold detection
  const sideBtnPressTimer = useRef(null);
  const onSideBtnMouseDown = () => {
    sideBtnPressTimer.current = setTimeout(() => {
      handleSideButtonLongPress();
      sideBtnPressTimer.current = null;
    }, 1800);
  };
  const onSideBtnMouseUp = () => {
    if (sideBtnPressTimer.current) {
      clearTimeout(sideBtnPressTimer.current);
      sideBtnPressTimer.current = null;
      handleSideButtonClick();
    }
  };

  // Ring sound effect on outgoing call
  useEffect(() => {
    if (currentScreen === SCREENS.CALLING) {
      audioSynth.play('phone_ring', volume / 100);
    } else if (audioSynth.currentSound === 'phone_ring') {
      audioSynth.stop();
    }
  }, [currentScreen, volume]);

  // Hold-to-Disarm Mouse Tracking
  const onDisarmMouseDown = () => {
    isHoldingRef.current = true;
    holdStartTimeRef.current = Date.now();
  };
  const onDisarmMouseUp = () => {
    isHoldingRef.current = false;
    setHoldProgress(0);
  };

  useEffect(() => {
    if (currentScreen !== SCREENS.CANCEL) return;
    const interval = setInterval(() => {
      if (isHoldingRef.current) {
        const elapsed = Date.now() - holdStartTimeRef.current;
        const pct = Math.min(100, (elapsed / 2000) * 100);
        setHoldProgress(pct);
        if (pct >= 100) {
          isHoldingRef.current = false;
          disarmSOS();
        }
      }
    }, 40);
    return () => clearInterval(interval);
  }, [currentScreen, disarmSOS]);

  // Format call timer
  const formatTime = (s) => {
    const mins = String(Math.floor(s / 60)).padStart(2, '0');
    const secs = String(s % 60).padStart(2, '0');
    return `${mins}:${secs}`;
  };

  const currentWallpaper = WALLPAPERS[wallpaperIdx] || WALLPAPERS[0];

  return (
    <div className="ws-super-wrapper">
      {/* Toast Notification */}
      {toast && (
        <div className="ws-floating-toast">
          <span>{toast}</span>
        </div>
      )}

      {/* Main Apple Watch Chassis */}
      <div className="ws-watch-chassis">
        {/* Physical Digital Crown */}
        <div
          className="ws-hw-crown"
          onClick={handleCrownClick}
          onDoubleClick={handleCrownDoubleClick}
          title="Digital Crown: Click=Home/Apps | Double-Click=Switch"
        />

        {/* Physical Side Power Button */}
        <div
          className="ws-hw-side-btn"
          onMouseDown={onSideBtnMouseDown}
          onMouseUp={onSideBtnMouseUp}
          title="Side Button: Click=Control Center | Hold 2s=Emergency SOS"
        />

        {/* 410×502 High-Density AMOLED Screen Glass */}
        <div className="ws-screen-viewport">
          
          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 1: WATCH FACE (SAFE)
              ══════════════════════════════════════════════════════════════════ */}
          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 1: WATCH FACE (SAFE) WITH WALLPAPERS & CUSTOM ICONS
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.SAFE && (
            <div
              className="ws-ui-screen ws-screen-safe"
              style={{
                backgroundImage: currentWallpaper.type === 'image' ? `url(${currentWallpaper.value})` : undefined,
                background: currentWallpaper.type !== 'image' ? currentWallpaper.value : undefined,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
              }}
            >
              {/* Dynamic Status Island */}
              <div className="ws-status-island" onClick={() => navigateTo(SCREENS.CONTROL_CENTER)}>
                <span className="ws-status-date">
                  {time.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' }).toUpperCase()}
                </span>
                <div className="ws-status-icons">
                  <span className="ws-icon-signal">📶 4G</span>
                  <span className="ws-icon-batt">🔋 {battery}%</span>
                </div>
              </div>

              {/* Wallpaper Quick Switcher Chip */}
              <div
                className="ws-wallpaper-cycler"
                onClick={(e) => {
                  e.stopPropagation();
                  const next = (wallpaperIdx + 1) % WALLPAPERS.length;
                  setWallpaperIdx(next);
                  showToast(`Wallpaper: ${WALLPAPERS[next].name}`);
                }}
                title="Tap to cycle wallpapers"
              >
                <span className="ws-wp-dot" style={{ background: currentWallpaper.thumb }} />
                <span>{currentWallpaper.name}</span>
                <span className="ws-wp-cycle-icon">↻</span>
              </div>

              {/* Large Clock Display */}
              <div className="ws-clock-display" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>
                <span className="ws-clock-digits">
                  {String(time.getHours()).padStart(2, '0')}:{String(time.getMinutes()).padStart(2, '0')}
                </span>
                <span className="ws-brand-subtitle">NAARI OS · GUARDIAN</span>
              </div>

              {/* Concentric Apple Watch Activity Rings */}
              <div className="ws-rings-container" onClick={() => navigateTo(SCREENS.WATCH)}>
                <svg className="ws-rings-svg" viewBox="0 0 200 200">
                  {/* Background tracks */}
                  <circle cx="100" cy="100" r="75" stroke="#380000" strokeWidth="12" fill="none" opacity="0.5" />
                  <circle cx="100" cy="100" r="58" stroke="#00381e" strokeWidth="12" fill="none" opacity="0.5" />
                  <circle cx="100" cy="100" r="41" stroke="#002d38" strokeWidth="12" fill="none" opacity="0.5" />

                  {/* Active Rings */}
                  {/* Outer Move Ring (Coral 78%) */}
                  <circle
                    cx="100" cy="100" r="75"
                    stroke="#ff4020" strokeWidth="12" fill="none"
                    strokeDasharray="471.2"
                    strokeDashoffset={471.2 * (1 - 0.78)}
                    strokeLinecap="round"
                    transform="rotate(-90 100 100)"
                  />
                  {/* Middle Exercise Ring (Mint 92%) */}
                  <circle
                    cx="100" cy="100" r="58"
                    stroke="#00e8a8" strokeWidth="12" fill="none"
                    strokeDasharray="364.4"
                    strokeDashoffset={364.4 * (1 - 0.92)}
                    strokeLinecap="round"
                    transform="rotate(-90 100 100)"
                  />
                  {/* Inner Stand Ring (Cyan 65%) */}
                  <circle
                    cx="100" cy="100" r="41"
                    stroke="#00d5ff" strokeWidth="12" fill="none"
                    strokeDasharray="257.6"
                    strokeDashoffset={257.6 * (1 - 0.65)}
                    strokeLinecap="round"
                    transform="rotate(-90 100 100)"
                  />
                </svg>

                {/* Center Sentinel Shield Badge */}
                <div className="ws-rings-center-badge">
                  <span className="ws-shield-glyph">🛡</span>
                  <span className="ws-safe-tag">SAFE</span>
                </div>
              </div>

              {/* Bottom Floating Glass Dock (Custom Image Icons, No Emojis) */}
              <div className="ws-glass-dock">
                <button className="ws-dock-btn" onClick={() => navigateTo(SCREENS.DIALER)} title="Phone">
                  <img src="/icons/icon_phone.png" alt="Phone" className="ws-dock-icon-img" />
                </button>
                <button className="ws-dock-btn" onClick={() => navigateTo(SCREENS.CONTACTS)} title="Contacts">
                  <img src="/icons/icon_contacts.png" alt="Contacts" className="ws-dock-icon-img" />
                </button>
                <button className="ws-dock-btn ws-dock-sos" onClick={startEmergencyProtocol} title="SOS">
                  <img src="/icons/icon_sos.png" alt="SOS" className="ws-dock-icon-img" />
                </button>
                <button className="ws-dock-btn" onClick={() => navigateTo(SCREENS.SOUND_STUDIO)} title="Sounds">
                  <img src="/icons/icon_sounds.png" alt="Sounds" className="ws-dock-icon-img" />
                </button>
                <button className="ws-dock-btn" onClick={() => navigateTo(SCREENS.CONTROL_CENTER)} title="Control">
                  <img src="/icons/icon_control.png" alt="Control" className="ws-dock-icon-img" />
                </button>
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 2: SIMPLE SLIDE APP DRAWER (WATCHOS LIST VIEW)
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.LAUNCHPAD && (
            <div className="ws-ui-screen ws-screen-slide-drawer">
              {/* Drawer Header */}
              <div className="ws-drawer-header">
                <div className="ws-drawer-title-group">
                  <span className="ws-drawer-title">Applications</span>
                  <span className="ws-drawer-count">{DRAWER_APPS.length} APPS</span>
                </div>
                <button className="ws-done-pill" onClick={() => navigateTo(SCREENS.SAFE)}>
                  DONE
                </button>
              </div>

              {/* Scrollable Slide List */}
              <div className="ws-slide-drawer-list">
                {DRAWER_APPS.map((app) => (
                  <div
                    key={app.id}
                    className="ws-drawer-card"
                    onClick={() => {
                      if (app.id === 'sos') {
                        startEmergencyProtocol();
                      } else {
                        navigateTo(app.screen);
                      }
                    }}
                  >
                    {/* Custom Image Icon */}
                    <div className="ws-drawer-icon-wrap">
                      <img src={app.iconSrc} alt={app.name} className="ws-drawer-custom-icon" />
                    </div>

                    {/* App Info */}
                    <div className="ws-drawer-info">
                      <div className="ws-drawer-name-row">
                        <strong className="ws-drawer-name">{app.name}</strong>
                        <span
                          className="ws-drawer-badge"
                          style={{
                            color: app.badgeCol,
                            borderColor: `${app.badgeCol}44`,
                            background: `${app.badgeCol}15`,
                          }}
                        >
                          {app.badge}
                        </span>
                      </div>
                      <span className="ws-drawer-sub">{app.sub}</span>
                    </div>

                    <span className="ws-drawer-chevron">›</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 3: EMERGENCY SOS 30-SECOND COUNTDOWN
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.EMERGENCY && (
            <div className="ws-ui-screen ws-screen-sos">
              <div className="ws-sos-banner">
                <span>! EMERGENCY SOS ACTIVE</span>
              </div>

              {/* Synchronized 30-Second Dial with SVG Progress Arc */}
              <div className="ws-sos-dial-box">
                <svg className="ws-sos-svg" viewBox="0 0 120 120">
                  <circle cx="60" cy="60" r="50" stroke="#500000" strokeWidth="10" fill="none" />
                  <circle
                    cx="60" cy="60" r="50"
                    stroke="#ff4020" strokeWidth="10" fill="none"
                    strokeDasharray="314.15"
                    strokeDashoffset={314.15 * (1 - countdown / 30)}
                    strokeLinecap="round"
                    transform="rotate(-90 60 60)"
                  />
                </svg>
                <div className="ws-sos-dial-content">
                  <span className="ws-countdown-digits">{countdown}</span>
                  <span className="ws-countdown-unit">SEC</span>
                </div>
              </div>

              {/* Live Action Status Rows */}
              <div className="ws-sos-status-list">
                <div className="ws-sos-row">
                  <span className="ws-dot ws-dot-mint" />
                  <span>Cellular Modem: Connected</span>
                </div>
                <div className="ws-sos-row">
                  <span className="ws-dot ws-dot-mint" />
                  <span>Emergency 112: Auto-Dialing</span>
                </div>
                <div className="ws-sos-row">
                  <span className="ws-dot ws-dot-coral" />
                  <span>Deterrent Siren: 105 dB ON</span>
                </div>
                <div className="ws-sos-row">
                  <span className="ws-dot ws-dot-cyan" />
                  <span>Blackbox Mic: Recording Audio</span>
                </div>
              </div>

              {/* Hold to Disarm Button */}
              <button className="ws-disarm-trigger-btn" onClick={() => navigateTo(SCREENS.CANCEL)}>
                <span>HOLD 2s TO DISARM SOS</span>
                <span className="ws-sub-hint">Tap here or hold side button</span>
              </button>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 4: HOLD-TO-DISARM (CANCEL)
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.CANCEL && (
            <div className="ws-ui-screen ws-screen-cancel">
              <div className="ws-cancel-header">
                <h2>DISARM EMERGENCY</h2>
                <p>Hold button for 2 seconds</p>
              </div>

              <div className="ws-cancel-circle">
                <svg viewBox="0 0 100 100" className="ws-cancel-svg">
                  <circle cx="50" cy="50" r="42" stroke="#222" strokeWidth="8" fill="none" />
                  <circle
                    cx="50" cy="50" r="42"
                    stroke="#00e8a8" strokeWidth="8" fill="none"
                    strokeDasharray="263.8"
                    strokeDashoffset={263.8 * (1 - holdProgress / 100)}
                    strokeLinecap="round"
                    transform="rotate(-90 50 50)"
                  />
                </svg>
                <span className="ws-cancel-pct">{Math.round(holdProgress)}%</span>
              </div>

              <div
                className={`ws-hold-pad ${holdProgress > 0 ? 'active' : ''}`}
                onMouseDown={onDisarmMouseDown}
                onMouseUp={onDisarmMouseUp}
                onMouseLeave={onDisarmMouseUp}
              >
                <span>{holdProgress > 0 ? 'KEEP HOLDING...' : 'PRESS & HOLD'}</span>
                <span className="ws-hold-sub">to disarm alert</span>
              </div>

              <button className="ws-cancel-abort-btn" onClick={() => navigateTo(SCREENS.EMERGENCY)}>
                Crown: Return to SOS
              </button>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 5: OUTGOING CALLING OVERLAY (WITH PHONE RING SOUND)
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.CALLING && (
            <div className="ws-ui-screen ws-screen-calling">
              <div className="ws-calling-glow-pill">
                <span>OUTGOING EMERGENCY CALL</span>
              </div>

              {/* Pulsing Avatar with Custom Phone Icon */}
              <div className="ws-calling-avatar-box">
                <div className="ws-calling-pulse-ring" />
                <div className="ws-calling-avatar">
                  <img src="/icons/icon_phone.png" alt="Call" className="ws-calling-icon-img" />
                </div>
              </div>

              <h2 className="ws-caller-name">{callingInfo.name}</h2>
              <p className="ws-caller-num">{callingInfo.number}</p>
              
              {/* Ringing Sound Indicator */}
              <div className="ws-calling-ring-indicator">
                <span className="ws-ring-pulse-dot" />
                <span className="ws-ring-text">Ringing...</span>
                <div className="ws-audio-ring-bars">
                  <span className="ws-audio-bar" />
                  <span className="ws-audio-bar" />
                  <span className="ws-audio-bar" />
                </div>
              </div>

              <div className="ws-call-duration">{formatTime(callingInfo.elapsed)}</div>

              {/* Control Tiles */}
              <div className="ws-call-tiles">
                <button className="ws-call-tile">MUTE</button>
                <button className="ws-call-tile">KEYPAD</button>
                <button className="ws-call-tile active">SPEAKER</button>
              </div>

              {/* Crimson End Call Button */}
              <button
                className="ws-end-call-btn"
                onClick={() => {
                  audioSynth.stop();
                  showToast("Call Ended");
                  navigateTo(cancelFromScreen || SCREENS.SAFE);
                }}
              >
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                  <path d="M4 15C7 15 9.5 13 10.5 11L9 8C9 8 11.5 5 15.5 5C19.5 5 22 8 22 8L20.5 11C21.5 13 24 15 27 15" stroke="#ffffff" strokeWidth="2.5" strokeLinecap="round" />
                  <path d="M6 18L18 6" stroke="#ffffff" strokeWidth="2.5" strokeLinecap="round" />
                </svg>
              </button>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 6: SMS DISPATCHED OVERLAY
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.SMS_SENT && (
            <div className="ws-ui-screen ws-screen-sms" onClick={() => navigateTo(cancelFromScreen || SCREENS.SAFE)}>
              <div className="ws-sms-bubble-icon">
                <svg width="30" height="30" viewBox="0 0 24 24" fill="none">
                  <path d="M21 11.5C21.0034 12.8199 20.6951 14.1219 20.1 15.3C19.3944 16.7118 18.3098 17.8992 16.9674 18.7293C15.6251 19.5594 14.0782 19.9994 12.5 20C11.1801 20.0035 9.87812 19.6951 8.7 19.1L3 21L4.9 15.3C4.30493 14.1219 3.99656 12.8199 4 11.5C4.00061 9.92179 4.44061 8.37488 5.27072 7.03258C6.10083 5.69028 7.28825 4.6056 8.7 3.90003C9.87812 3.30496 11.1801 2.99659 12.5 3.00003H13C15.0843 3.11502 17.053 3.99479 18.5291 5.47089C20.0052 6.94699 20.885 8.91568 21 11V11.5Z" fill="#00e8a8" fillOpacity="0.3" stroke="#00e8a8" strokeWidth="2" strokeLinejoin="round" />
                </svg>
                <div className="ws-sms-check">✓</div>
              </div>

              <h2 className="ws-sms-title">DISTRESS SMS SENT</h2>
              <p className="ws-sms-to">Delivered to</p>
              <h3 className="ws-sms-contact">{smsInfo.name}</h3>
              <p className="ws-sms-number">{smsInfo.number}</p>

              <div className="ws-sms-chip">
                <span>✓ Live GPS Coordinates Shared</span>
              </div>

              <span className="ws-sms-dismiss-hint">Tap anywhere to dismiss</span>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 7: REDESIGNED MATURE WATCHOS 10 CONTROL CENTER
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.CONTROL_CENTER && (
            <div className="ws-ui-screen ws-screen-control">
              {/* Header */}
              <div className="ws-screen-header">
                <span className="ws-cc-header-title">Control Center</span>
                <button className="ws-done-pill" onClick={() => navigateTo(SCREENS.SAFE)}>DONE</button>
              </div>

              {/* Status Header Bar: Carrier + Battery */}
              <div className="ws-cc-status-bar">
                <div className="ws-cc-carrier">
                  <svg width="14" height="11" viewBox="0 0 14 11" fill="none">
                    <rect x="0" y="8" width="2.2" height="3" rx="0.5" fill="#00e8a8" />
                    <rect x="3.5" y="5.5" width="2.2" height="5.5" rx="0.5" fill="#00e8a8" />
                    <rect x="7" y="3" width="2.2" height="8" rx="0.5" fill="#00e8a8" />
                    <rect x="10.5" y="0.5" width="2.2" height="10.5" rx="0.5" fill="#00e8a8" />
                  </svg>
                  <span>Jio 4G • LTE</span>
                </div>
                <div className="ws-cc-batt-tag">
                  <svg width="15" height="10" viewBox="0 0 15 10" fill="none">
                    <rect x="0.5" y="0.5" width="12" height="9" rx="2" stroke="#00e8a8" />
                    <rect x="2" y="2" width="9" height="6" rx="1" fill="#00e8a8" />
                    <path d="M13.5 3.5V6.5" stroke="#00e8a8" strokeLinecap="round" />
                  </svg>
                  <span>{battery}%</span>
                </div>
              </div>

              {/* 2-Column Grid of Mature Frosted Glass Squircles (No Emojis) */}
              <div className="ws-cc-grid">
                {/* 1. Cellular */}
                <div
                  className={`ws-cc-card ${cellularActive ? 'active-green' : ''}`}
                  onClick={() => setCellularActive(prev => !prev)}
                >
                  <div className="ws-cc-icon-circle">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                      <path d="M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                      <circle cx="12" cy="12" r="3" fill="currentColor" />
                      <path d="M12 7V3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                      <path d="M12 21V17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                    </svg>
                  </div>
                  <div className="ws-cc-meta">
                    <strong>Cellular</strong>
                    <small>{cellularActive ? 'Connected' : 'Off'}</small>
                  </div>
                </div>

                {/* 2. Wi-Fi */}
                <div
                  className={`ws-cc-card ${wifiActive ? 'active-blue' : ''}`}
                  onClick={() => setWifiActive(prev => !prev)}
                >
                  <div className="ws-cc-icon-circle">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                      <path d="M5 12.55C7.94 9.61 12.06 9.61 15 12.55" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                      <path d="M1.42 9C6.44 3.98 14.56 3.98 19.58 9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                      <circle cx="10" cy="17" r="1.5" fill="currentColor" />
                    </svg>
                  </div>
                  <div className="ws-cc-meta">
                    <strong>Wi-Fi</strong>
                    <small>{wifiActive ? 'Active' : 'Off'}</small>
                  </div>
                </div>

                {/* 3. Silent Mode */}
                <div
                  className={`ws-cc-card ${silentActive ? 'active-purple' : ''}`}
                  onClick={() => setSilentActive(prev => !prev)}
                >
                  <div className="ws-cc-icon-circle">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </div>
                  <div className="ws-cc-meta">
                    <strong>Silent</strong>
                    <small>{silentActive ? 'Armed' : 'Normal'}</small>
                  </div>
                </div>

                {/* 4. Flashlight Torch */}
                <div className="ws-cc-card" onClick={() => navigateTo(SCREENS.FLASHLIGHT)}>
                  <div className="ws-cc-icon-circle">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                      <path d="M18 6L14 2H10L6 6V9L9 12V21C9 21.55 9.45 22 10 22H14C14.55 22 15 21.55 15 21V12L18 9V6Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
                      <path d="M12 6V9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                    </svg>
                  </div>
                  <div className="ws-cc-meta">
                    <strong>Torch</strong>
                    <small>3 Modes</small>
                  </div>
                </div>

                {/* 5. Siren Quick Trigger */}
                <div
                  className={`ws-cc-card ${activeSound ? 'alerting-card' : ''}`}
                  onClick={() => {
                    if (activeSound) {
                      audioSynth.stop();
                      setActiveSound(null);
                    } else {
                      audioSynth.play('police', volume / 100);
                      setActiveSound('police');
                    }
                  }}
                >
                  <div className="ws-cc-icon-circle">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                      <path d="M11 5L6 9H2V15H6L11 19V5Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
                      <path d="M15.54 8.46C16.48 9.4 17 10.66 17 12C17 13.34 16.48 14.6 15.54 15.54" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                      <path d="M19.07 4.93C20.94 6.8 22 9.35 22 12C22 14.65 20.94 17.2 19.07 19.07" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                    </svg>
                  </div>
                  <div className="ws-cc-meta">
                    <strong>Siren</strong>
                    <small>{activeSound ? 'Playing' : 'Test 105dB'}</small>
                  </div>
                </div>

                {/* 6. GPS Location Fix */}
                <div className="ws-cc-card" onClick={() => navigateTo(SCREENS.TELEMETRY)}>
                  <div className="ws-cc-icon-circle">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                      <circle cx="12" cy="12" r="7" stroke="currentColor" strokeWidth="2" />
                      <circle cx="12" cy="12" r="2" fill="currentColor" />
                      <path d="M12 2V5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                      <path d="M12 19V22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                      <path d="M2 12H5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                      <path d="M19 12H22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                    </svg>
                  </div>
                  <div className="ws-cc-meta">
                    <strong>GPS</strong>
                    <small>3D Fix</small>
                  </div>
                </div>
              </div>

              {/* Volume Bar */}
              <div className="ws-vol-bar">
                {[25, 50, 75, 100].map((v) => (
                  <button
                    key={v}
                    className={`ws-vol-btn ${volume === v ? 'active' : ''}`}
                    onClick={() => setVolume(v)}
                  >
                    {v}%
                  </button>
                ))}
              </div>

              {/* Full-Width Crimson Emergency SOS Strip */}
              <button className="ws-cc-sos-strip" onClick={startEmergencyProtocol}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                  <path d="M12 2L2 6V13C2 19 6 23.5 11 24.5C16 23.5 20 19 20 13V6L11 2Z" fill="#ffffff" stroke="#ffffff" strokeWidth="1.5" />
                  <path d="M12 8V12" stroke="#ff3b30" strokeWidth="2" strokeLinecap="round" />
                  <circle cx="12" cy="16" r="1" fill="#ff3b30" />
                </svg>
                <span>EMERGENCY SOS</span>
              </button>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 8: SOUND STUDIO
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.SOUND_STUDIO && (
            <div className="ws-ui-screen ws-screen-sounds">
              <div className="ws-screen-header">
                <button className="ws-back-pill" onClick={() => navigateTo(SCREENS.SAFE)}>‹ BACK</button>
                <span>Sound Studio</span>
              </div>

              <div className="ws-sound-list">
                {[
                  { id: 'police', name: 'Police Siren', desc: '600–1600 Hz sweep' },
                  { id: 'pulse',  name: '105dB Pulse',  desc: '2400 Hz burst' },
                  { id: 'ambulance', name: 'Ambulance', desc: '960/770 Hz hi-lo' },
                  { id: 'chirp',  name: 'Chirp Radar',  desc: '800–2400 Hz chirp' },
                  { id: 'chime',  name: 'Bell Chime',   desc: '1760 Hz alert chime' },
                ].map((s) => (
                  <div
                    key={s.id}
                    className={`ws-sound-row ${activeSound === s.id ? 'active' : ''}`}
                    onClick={() => {
                      if (activeSound === s.id) {
                        audioSynth.stop();
                        setActiveSound(null);
                      } else {
                        audioSynth.play(s.id, volume / 100);
                        setActiveSound(s.id);
                      }
                    }}
                  >
                    <div>
                      <strong>{s.name}</strong>
                      <small>{s.desc}</small>
                    </div>
                    <span>{activeSound === s.id ? '⏹' : '▶'}</span>
                  </div>
                ))}
              </div>

              {/* Volume Bar */}
              <div className="ws-vol-bar">
                {[25, 50, 75, 100].map((v) => (
                  <button
                    key={v}
                    className={`ws-vol-btn ${volume === v ? 'active' : ''}`}
                    onClick={() => setVolume(v)}
                  >
                    {v}%
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 9: FLASHLIGHT
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.FLASHLIGHT && (
            <div
              className="ws-ui-screen ws-screen-torch"
              style={{
                background:
                  flashlightMode === 0 ? '#ffffff' :
                  flashlightMode === 1 ? '#ff9f0a' :
                  (strobeOn ? '#ff3b30' : '#111111'),
                color: flashlightMode === 0 ? '#000000' : '#ffffff',
              }}
              onClick={() => setFlashlightMode((prev) => (prev + 1) % 3)}
            >
              <div className="ws-torch-center">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none">
                  <path d="M18 6L14 2H10L6 6V9L9 12V21C9 21.55 9.45 22 10 22H14C14.55 22 15 21.55 15 21V12L18 9V6Z" fill="currentColor" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
                  <path d="M12 6V9" stroke="#000000" strokeWidth="2" strokeLinecap="round" />
                </svg>
                <strong>
                  {flashlightMode === 0 ? '100% WHITE' : flashlightMode === 1 ? 'WARM AMBER' : 'RED STROBE'}
                </strong>
                <small>Tap anywhere to cycle</small>
              </div>

              <button
                className="ws-torch-exit"
                onClick={(e) => {
                  e.stopPropagation();
                  navigateTo(SCREENS.SAFE);
                }}
              >
                ✕ Close
              </button>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 10: CONTACTS
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.CONTACTS && (
            <div className="ws-ui-screen ws-screen-contacts">
              <div className="ws-screen-header">
                <button className="ws-back-pill" onClick={() => navigateTo(SCREENS.SAFE)}>‹ BACK</button>
                <span>Contacts</span>
              </div>

              <div className="ws-contacts-list">
                {[
                  { name: 'Mom', phone: '+91 98765 43210', bg: '#00e8a8' },
                  { name: 'Sister', phone: '+91 98200 11223', bg: '#0a84ff' },
                  { name: 'Police 112', phone: '112', bg: '#ff453a' },
                  { name: 'Women Helpline', phone: '1091', bg: '#ffd60a' },
                ].map((c) => (
                  <div key={c.name} className="ws-contact-card">
                    <div className="ws-contact-avatar" style={{ background: c.bg }}>
                      {c.name.charAt(0)}
                    </div>
                    <div className="ws-contact-meta">
                      <strong>{c.name}</strong>
                      <small>{c.phone}</small>
                    </div>
                    <div className="ws-contact-actions">
                      <button
                        className="ws-call-action"
                        onClick={() => {
                          setCallingInfo({ name: c.name, number: c.phone, elapsed: 0 });
                          navigateTo(SCREENS.CALLING);
                        }}
                      >
                        CALL
                      </button>
                      <button
                        className="ws-sms-action"
                        onClick={() => {
                          setSmsInfo({ name: c.name, number: c.phone });
                          navigateTo(SCREENS.SMS_SENT);
                        }}
                      >
                        SMS
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 11: STANDALONE PHONE DIALER
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.DIALER && (
            <div className="ws-ui-screen ws-screen-dialer">
              <div className="ws-screen-header">
                <button className="ws-back-pill" onClick={() => navigateTo(SCREENS.SAFE)}>‹ BACK</button>
                <div className="ws-dial-display">{dialedNum || 'Dial Number'}</div>
              </div>

              <div className="ws-keypad-grid">
                {['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'].map((k) => (
                  <button
                    key={k}
                    className="ws-keypad-key"
                    onClick={() => setDialedNum((prev) => (prev.length < 15 ? prev + k : prev))}
                  >
                    {k}
                  </button>
                ))}
              </div>

              <div className="ws-dialer-bottom">
                <button className="ws-dial-aux" onClick={() => setDialedNum('')}>CLR</button>
                <button
                  className="ws-dial-call-btn"
                  onClick={() => {
                    if (dialedNum) {
                      setCallingInfo({ name: 'Cellular Call', number: dialedNum, elapsed: 0 });
                      navigateTo(SCREENS.CALLING);
                    }
                  }}
                >
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                    <path d="M22 16.92V19.92C22.0011 20.1986 21.9441 20.4742 21.8325 20.7294C21.7209 20.9846 21.5573 21.2137 21.3521 21.4019C21.1468 21.5902 20.9046 21.7334 20.6407 21.8224C20.3769 21.9114 20.0974 21.9442 19.82 21.9188C16.7428 21.5857 13.787 20.5342 11.19 18.85C8.77382 17.3147 6.72533 15.2662 5.19 12.85C3.49997 10.2412 2.44824 7.27099 2.12 4.18C2.09503 3.90353 2.12787 3.62489 2.21668 3.36181C2.30548 3.09873 2.44828 2.85711 2.63604 2.65239C2.8238 2.44766 3.05223 2.28456 3.30689 2.17316C3.56155 2.06176 3.83674 2.00455 4.115 2.005H7.115C7.5953 1.99988 8.06144 2.16668 8.42858 2.47477C8.79572 2.78287 9.03923 3.21203 9.115 3.685C9.25624 4.63004 9.48834 5.55835 9.807 6.453C9.94056 6.82424 9.96781 7.22731 9.88539 7.61339C9.80297 7.99946 9.61439 8.3516 9.342 8.627L8.062 9.907C9.48419 12.4087 11.5513 14.4758 14.053 15.898L15.333 14.618C15.6084 14.3456 15.9605 14.157 16.3466 14.0746C16.7327 13.9922 17.1358 14.0194 17.507 14.153C18.4016 14.4717 19.3299 14.7038 20.275 14.845C20.7533 14.9213 21.1866 15.1687 21.4957 15.5414C21.8047 15.9141 21.9691 16.3871 21.96 16.872L22 16.92Z" fill="#ffffff" />
                  </svg>
                </button>
                <button className="ws-dial-aux" onClick={() => setDialedNum((prev) => prev.slice(0, -1))}>DEL</button>
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 12: NIGHT WALK GUARD SESSION (WATCH)
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.WATCH && (
            <div className="ws-ui-screen ws-screen-guard">
              <div className="ws-guard-banner">
                <span>NIGHT WALK GUARD</span>
              </div>

              <div className="ws-guard-clock">
                <span>{String(time.getHours()).padStart(2, '0')}:{String(time.getMinutes()).padStart(2, '0')}</span>
                <small>SESSION ACTIVE</small>
              </div>

              <div className="ws-guard-cards">
                <div className="ws-guard-card">
                  <small>Guardian</small>
                  <strong>Mom</strong>
                  <span className="ws-card-mint">+91 98765</span>
                </div>
                <div className="ws-guard-card">
                  <small>GSM Location</small>
                  <strong>28.61° N</strong>
                  <span className="ws-card-mint">77.20° E</span>
                </div>
                <div className="ws-guard-card">
                  <small>SMS Pings</small>
                  <strong>Every 3 min</strong>
                </div>
                <div className="ws-guard-card">
                  <small>Siren</small>
                  <strong className="ws-card-coral">Armed</strong>
                </div>
              </div>

              <button
                className="ws-guard-safe-btn"
                onClick={() => {
                  showToast("Guard Session Ended");
                  navigateTo(SCREENS.SAFE);
                }}
              >
                I'M SAFE · END SESSION
              </button>

              <button className="ws-guard-sos-btn" onClick={startEmergencyProtocol}>
                ! TRIGGER EMERGENCY SOS
              </button>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 13: PREMIUM SYSTEM HEALTH
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.SYSTEM && (
            <div className="ws-ui-screen ws-screen-system">
              <div className="ws-premium-app-header">
                <button className="ws-back-pill" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>‹ APPS</button>
                <span>System Health</span>
                <i className="ws-header-status-dot" />
              </div>

              <div className="ws-system-hero">
                <div className="ws-system-shield">✓</div>
                <div>
                  <strong>Watch ready</strong>
                  <span>Core safety systems online</span>
                </div>
              </div>

              <div className="ws-system-stack">
                <div className="ws-system-row">
                  <span className="ws-system-row-icon battery">▰</span>
                  <div><strong>Battery</strong><small>AXP2101 power manager</small></div>
                  <b>{battery}%</b>
                </div>
                <div className="ws-system-row">
                  <span className="ws-system-row-icon cellular">▥</span>
                  <div><strong>Cellular</strong><small>Signal quality</small></div>
                  <b>{csq}/31</b>
                </div>
                <div className="ws-system-row">
                  <span className="ws-system-row-icon sensor">◇</span>
                  <div><strong>Motion sensing</strong><small>QMI8658 fall guard</small></div>
                  <b>ARMED</b>
                </div>
                <button
                  className="ws-system-row ws-system-test"
                  onClick={() => {
                    audioSynth.play('bell', volume / 100);
                    showToast('Speaker test complete');
                    setTimeout(() => audioSynth.stop(), 1200);
                  }}
                >
                  <span className="ws-system-row-icon speaker">◖</span>
                  <div><strong>Speaker test</strong><small>Tap to verify alert audio</small></div>
                  <b>TEST</b>
                </button>
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              SCREEN 14: COMPASS & MOTION
              ══════════════════════════════════════════════════════════════════ */}
          {currentScreen === SCREENS.TELEMETRY && (
            <div className="ws-ui-screen ws-screen-telemetry-premium">
              <div className="ws-premium-app-header">
                <button className="ws-back-pill" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>‹ APPS</button>
                <span>Compass</span>
                <i className="ws-header-status-dot cyan" />
              </div>

              <div className="ws-premium-compass">
                <svg viewBox="0 0 140 140" aria-label="Compass heading 42 degrees northeast">
                  <circle cx="70" cy="70" r="62" fill="rgba(255,255,255,.035)" stroke="rgba(255,255,255,.16)" strokeWidth="2" />
                  <circle cx="70" cy="70" r="51" fill="none" stroke="rgba(255,255,255,.08)" strokeWidth="1" />
                  <text x="70" y="20" textAnchor="middle" fill="#ff453a" fontSize="11" fontWeight="800">N</text>
                  <text x="70" y="128" textAnchor="middle" fill="rgba(255,255,255,.4)" fontSize="9">S</text>
                  <text x="17" y="74" textAnchor="middle" fill="rgba(255,255,255,.4)" fontSize="9">W</text>
                  <text x="123" y="74" textAnchor="middle" fill="rgba(255,255,255,.4)" fontSize="9">E</text>
                  <path d="M70 25 L62 73 L70 68 L78 73 Z" fill="#ff453a" />
                  <path d="M70 115 L62 67 L70 72 L78 67 Z" fill="rgba(255,255,255,.48)" />
                  <circle cx="70" cy="70" r="5" fill="#fff" />
                </svg>
                <strong>042° NE</strong>
                <span>Motion stable · Fall Guard armed</span>
              </div>

              <div className="ws-motion-grid">
                <div><small>PITCH</small><strong>+2.4°</strong></div>
                <div><small>ROLL</small><strong>-0.8°</strong></div>
                <div><small>IMPACT</small><strong>1.02G</strong></div>
              </div>
            </div>
          )}

        </div>
      </div>

      {/* Hardware Guidance Below Watch */}
      <div className="ws-footer-legend">
        <span onClick={handleCrownClick}>◉ Crown: Apps / Home</span>
        <span onClick={handleSideButtonClick}>⬤ Side: Control / SOS</span>
      </div>
    </div>
  );
}
