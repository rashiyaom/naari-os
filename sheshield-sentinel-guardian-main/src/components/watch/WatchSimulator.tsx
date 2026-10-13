import React, { useState, useEffect, useRef, useCallback } from 'react';
import { SCREENS, WALLPAPERS, DRAWER_APPS, type WatchScreenType } from './watch-constants';

export { SCREENS };

// ─── Web Audio Synthesizer for Sound Studio, Voice Memo & Phone Ringing ─────
class WebAudioPlayer {
  private ctx: AudioContext | null = null;
  private osc: OscillatorNode | null = null;
  private gain: GainNode | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;
  public currentSound: string | null = null;

  init() {
    if (!this.ctx && typeof window !== 'undefined') {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioCtx) this.ctx = new AudioCtx();
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  play(type: string, volume: number = 0.5) {
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
      this.osc.type = 'sine';
      this.osc.frequency.setValueAtTime(440, now);
      let ringStep = 0;
      this.timer = setInterval(() => {
        if (!this.ctx || !this.gain || !this.osc) return;
        ringStep = (ringStep + 1) % 40;
        const isRinging = ringStep < 18;
        if (isRinging) {
          this.osc.frequency.setValueAtTime(ringStep % 2 === 0 ? 440 : 480, this.ctx.currentTime);
          this.gain.gain.setValueAtTime(volume * 0.35, this.ctx.currentTime);
        } else {
          this.gain.gain.setValueAtTime(0.0001, this.ctx.currentTime);
        }
      }, 100);
    } else if (type === 'police') {
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
      this.osc.type = 'square';
      this.osc.frequency.setValueAtTime(2200, now);
      let on = true;
      this.timer = setInterval(() => {
        if (!this.ctx || !this.gain) return;
        on = !on;
        this.gain.gain.setValueAtTime(on ? volume * 0.4 : 0.001, this.ctx.currentTime);
      }, 120);
    } else if (type === 'ambulance') {
      this.osc.type = 'sine';
      let hi = true;
      this.osc.frequency.setValueAtTime(960, now);
      this.timer = setInterval(() => {
        if (!this.ctx || !this.osc) return;
        hi = !hi;
        this.osc.frequency.setValueAtTime(hi ? 960 : 770, this.ctx.currentTime);
      }, 400);
    } else if (type === 'chirp') {
      this.osc.type = 'triangle';
      this.osc.frequency.setValueAtTime(1400, now);
    } else if (type === 'memo_playback') {
      // Warm recorded voice-like harmonic playback
      this.osc.type = 'triangle';
      this.osc.frequency.setValueAtTime(320, now);
      let step = 0;
      this.timer = setInterval(() => {
        if (!this.ctx || !this.osc) return;
        step++;
        const pitch = 300 + Math.sin(step * 0.5) * 80 + Math.cos(step * 1.2) * 50;
        this.osc.frequency.setValueAtTime(pitch, this.ctx.currentTime);
      }, 80);
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
      try { this.osc.stop(); } catch { /* ignore */ }
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



interface WatchSimulatorProps {
  targetScreen?: string | undefined;
  targetWallpaper?: string | undefined;
  onStateChange?: ((state: 'SAFE' | 'WATCH' | 'EMERGENCY') => void) | undefined;
  onScreenChange?: ((screen: string) => void) | undefined;
}

export default function WatchSimulator({
  targetScreen,
  targetWallpaper,
  onStateChange,
  onScreenChange
}: WatchSimulatorProps) {
  // Navigation & State
  const [currentScreen, setCurrentScreen] = useState<WatchScreenType>(SCREENS.SAFE);
  const [, setPrevScreen] = useState<WatchScreenType>(SCREENS.SAFE);
  const [cancelFromScreen, setCancelFromScreen] = useState<WatchScreenType>(SCREENS.SAFE);

  // App Drawer Pagination (3 pages × 4 apps)
  const [drawerPage, setDrawerPage] = useState(0);

  // Wallpaper state
  const [wallpaperIdx, setWallpaperIdx] = useState(1);

  useEffect(() => {
    if (targetScreen && Object.values(SCREENS).includes(targetScreen as WatchScreenType)) {
      setCurrentScreen(targetScreen as WatchScreenType);
    }
  }, [targetScreen]);

  useEffect(() => {
    if (targetWallpaper) {
      const idx = WALLPAPERS.findIndex(w => w.id === targetWallpaper);
      if (idx !== -1) setWallpaperIdx(idx);
    }
  }, [targetWallpaper]);

  // Time & Status
  const [time, setTime] = useState(new Date());
  const [battery] = useState(94);

  // Control Center toggles
  const [cellularActive, setCellularActive] = useState(true);
  const [wifiActive, setWifiActive] = useState(true);
  const [silentActive, setSilentActive] = useState(false);

  // Emergency 30s Countdown
  const [countdown, setCountdown] = useState(30);
  const [isEmergencyActive, setIsEmergencyActive] = useState(false);
  const countdownTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Hold to Cancel Progress
  const [holdProgress, setHoldProgress] = useState(0);
  const isHoldingRef = useRef(false);
  const holdStartTimeRef = useRef(0);

  // Calling & SMS Overlays
  const [callingInfo, setCallingInfo] = useState({ name: 'Emergency 112', number: '112', elapsed: 0 });
  const [smsInfo] = useState({ name: '3 Guardians', number: '+91 98765 43210' });
  const callTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Flashlight mode (0=White, 1=Amber, 2=Strobe)
  const [flashlightMode, setFlashlightMode] = useState(0);
  const [strobeOn, setStrobeOn] = useState(false);

  // Sound Studio State
  const [activeSound, setActiveSound] = useState<string | null>(null);
  const [volume, setVolume] = useState(75);

  // Voice Memo Recorder State (Real Web Audio Capture & Playback)
  const [isRecordingMemo, setIsRecordingMemo] = useState(false);
  const [hasRecordedMemo, setHasRecordedMemo] = useState(false);
  const [isPlayingMemo, setIsPlayingMemo] = useState(false);
  const [memoDuration, setMemoDuration] = useState(0);
  const [memoElapsed, setMemoElapsed] = useState(0);
  const memoTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordedChunksRef = useRef<Blob[]>([]);
  const recordedAudioUrlRef = useRef<string | null>(null);
  const audioPlayerRef = useRef<HTMLAudioElement | null>(null);

  // Dialer
  const [dialedDigits, setDialedDigits] = useState('');

  // Toast
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const toastTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = useCallback((msg: string) => {
    setToastMsg(msg);
    if (toastTimeoutRef.current) clearTimeout(toastTimeoutRef.current);
    toastTimeoutRef.current = setTimeout(() => setToastMsg(null), 2200);
  }, []);

  const navigateTo = useCallback((newScreen: WatchScreenType) => {
    setPrevScreen(currentScreen);
    setCurrentScreen(newScreen);
    onScreenChange?.(newScreen);
  }, [currentScreen, onScreenChange]);

  // Tick clock every second
  useEffect(() => {
    const t = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(t);
  }, []);

  // Flashlight Strobe Timer
  useEffect(() => {
    if (currentScreen === SCREENS.FLASHLIGHT && flashlightMode === 2) {
      const strobe = setInterval(() => setStrobeOn(prev => !prev), 100);
      return () => clearInterval(strobe);
    }
    return undefined;
  }, [currentScreen, flashlightMode]);

  // Handle Calling Screen Phone Ring Sound & Elapsed Timer
  useEffect(() => {
    if (currentScreen === SCREENS.CALLING) {
      audioSynth.play('phone_ring', volume / 100);
      callTimerRef.current = setInterval(() => {
        setCallingInfo(prev => ({ ...prev, elapsed: prev.elapsed + 1 }));
      }, 1000);
    } else {
      if (audioSynth.currentSound === 'phone_ring') {
        audioSynth.stop();
      }
      if (callTimerRef.current) {
        clearInterval(callTimerRef.current);
        callTimerRef.current = null;
      }
    }
    return () => {
      if (callTimerRef.current) clearInterval(callTimerRef.current);
    };
  }, [currentScreen, volume]);

  // Emergency countdown logic (30 seconds grace period)
  const triggerEmergency = useCallback(() => {
    setIsEmergencyActive(true);
    setCountdown(30);
    navigateTo(SCREENS.EMERGENCY);
    onStateChange?.('EMERGENCY');
    audioSynth.play('police', volume / 100);
    showToast('🚨 30s Emergency SOS countdown started!');
  }, [navigateTo, onStateChange, volume, showToast]);

  // Ensure police siren plays continuously while on Emergency screen
  useEffect(() => {
    if (currentScreen === SCREENS.EMERGENCY) {
      if (audioSynth.currentSound !== 'police') {
        audioSynth.play('police', volume / 100);
      }
    }
  }, [currentScreen, volume]);

  useEffect(() => {
    if (isEmergencyActive && currentScreen === SCREENS.EMERGENCY) {
      if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
      countdownTimerRef.current = setInterval(() => {
        setCountdown(prev => {
          if (prev <= 1) {
            clearInterval(countdownTimerRef.current!);
            audioSynth.stop();
            setCallingInfo({ name: 'Emergency 112', number: '112', elapsed: 0 });
            setCurrentScreen(SCREENS.CALLING);
            showToast('Grace window elapsed. Dialing Emergency 112...');
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
    }
    return () => {
      if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
    };
  }, [isEmergencyActive, currentScreen, showToast]);

  // Voice Memo recording timer
  useEffect(() => {
    if (isRecordingMemo) {
      memoTimerRef.current = setInterval(() => {
        setMemoElapsed(prev => prev + 1);
      }, 1000);
    } else {
      if (memoTimerRef.current) {
        clearInterval(memoTimerRef.current);
        memoTimerRef.current = null;
      }
    }
    return () => {
      if (memoTimerRef.current) clearInterval(memoTimerRef.current);
    };
  }, [isRecordingMemo]);

  const startRecordingMemo = async () => {
    setIsRecordingMemo(true);
    setMemoElapsed(0);
    recordedChunksRef.current = [];
    if (audioPlayerRef.current) {
      audioPlayerRef.current.pause();
      audioPlayerRef.current = null;
    }
    audioSynth.stop();

    try {
      if (typeof navigator !== 'undefined' && navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        let mimeType = '';
        if (typeof MediaRecorder !== 'undefined') {
          if (MediaRecorder.isTypeSupported('audio/webm;codecs=opus')) {
            mimeType = 'audio/webm;codecs=opus';
          } else if (MediaRecorder.isTypeSupported('audio/webm')) {
            mimeType = 'audio/webm';
          } else if (MediaRecorder.isTypeSupported('audio/mp4')) {
            mimeType = 'audio/mp4';
          }
        }
        const mr = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
        mediaRecorderRef.current = mr;
        mr.ondataavailable = (e) => {
          if (e.data && e.data.size > 0) {
            recordedChunksRef.current.push(e.data);
          }
        };
        mr.onstop = () => {
          if (recordedChunksRef.current.length > 0) {
            const blobType = mr.mimeType || 'audio/webm';
            const blob = new Blob(recordedChunksRef.current, { type: blobType });
            if (recordedAudioUrlRef.current) URL.revokeObjectURL(recordedAudioUrlRef.current);
            recordedAudioUrlRef.current = URL.createObjectURL(blob);
          }
          stream.getTracks().forEach(t => t.stop());
        };
        mr.start(100);
        showToast('● Recording voice from microphone...');
      } else {
        showToast('● Recording voice memo...');
      }
    } catch (err) {
      console.warn('Microphone permission or hardware unavailable:', err);
      showToast('● Mic recording (Simulated memo)');
    }
  };

  const stopRecordingMemo = () => {
    setIsRecordingMemo(false);
    setHasRecordedMemo(true);
    setMemoDuration(memoElapsed > 0 ? memoElapsed : 1);
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      try {
        mediaRecorderRef.current.stop();
      } catch { /* ignore */ }
    }
    showToast('Voice memo saved to storage!');
  };

  const playRecordedMemo = () => {
    if (isPlayingMemo) {
      setIsPlayingMemo(false);
      if (audioPlayerRef.current) {
        audioPlayerRef.current.pause();
        audioPlayerRef.current.currentTime = 0;
      }
      audioSynth.stop();
    } else {
      setIsPlayingMemo(true);
      if (recordedAudioUrlRef.current) {
        const audio = new Audio(recordedAudioUrlRef.current);
        audio.volume = Math.max(0.1, volume / 100);
        audioPlayerRef.current = audio;
        audio.onended = () => {
          setIsPlayingMemo(false);
        };
        audio.onerror = () => {
          setIsPlayingMemo(false);
        };
        audio.play().catch(e => {
          console.warn('Audio play error, falling back:', e);
          setIsPlayingMemo(false);
        });
      } else {
        // Fallback gentle pleasant bell chime (NOT harsh trash synth)
        audioSynth.play('bell', volume / 100);
        setTimeout(() => {
          setIsPlayingMemo(false);
          audioSynth.stop();
        }, (memoDuration || 3) * 1000);
      }
    }
  };

  // Hold to cancel logic
  const handleHoldStart = () => {
    isHoldingRef.current = true;
    holdStartTimeRef.current = Date.now();
    const interval = setInterval(() => {
      if (!isHoldingRef.current) {
        clearInterval(interval);
        return;
      }
      const elapsed = Date.now() - holdStartTimeRef.current;
      const progress = Math.min(100, (elapsed / 2000) * 100);
      setHoldProgress(progress);
      if (progress >= 100) {
        clearInterval(interval);
        isHoldingRef.current = false;
        setHoldProgress(0);
        setIsEmergencyActive(false);
        audioSynth.stop();
        showToast("SOS Disarmed: You're marked Safe");
        onStateChange?.('SAFE');
        navigateTo(SCREENS.SAFE);
      }
    }, 30);
  };

  const handleHoldEnd = () => {
    isHoldingRef.current = false;
    setHoldProgress(0);
  };

  // Hardware buttons
  const handleCrownClick = () => {
    if (isEmergencyActive || currentScreen === SCREENS.EMERGENCY || currentScreen === SCREENS.CANCEL) {
      setIsEmergencyActive(false);
      audioSynth.stop();
      onStateChange?.('SAFE');
      showToast('🛡️ SOS Disarmed: You are safe');
      navigateTo(SCREENS.SAFE);
      return;
    }
    if (currentScreen === SCREENS.FLASHLIGHT) {
      setFlashlightMode(0);
      navigateTo(SCREENS.SAFE);
      return;
    }
    if (currentScreen === SCREENS.SAFE) {
      navigateTo(SCREENS.LAUNCHPAD);
    } else {
      navigateTo(SCREENS.SAFE);
    }
  };

  const handleSideButtonClick = () => {
    if (isEmergencyActive || currentScreen === SCREENS.EMERGENCY || currentScreen === SCREENS.CANCEL) {
      setIsEmergencyActive(false);
      audioSynth.stop();
      onStateChange?.('SAFE');
      showToast('🛡️ SOS Disarmed: You are safe');
      navigateTo(SCREENS.SAFE);
      return;
    }
    if (currentScreen === SCREENS.FLASHLIGHT) {
      setFlashlightMode(0);
      navigateTo(SCREENS.SAFE);
      return;
    }
    if (currentScreen === SCREENS.CONTROL_CENTER) {
      navigateTo(SCREENS.SAFE);
    } else {
      navigateTo(SCREENS.CONTROL_CENTER);
    }
  };

  const currentWp = WALLPAPERS[wallpaperIdx] ?? WALLPAPERS[0]!;

  const getScreenBackgroundStyle = (): React.CSSProperties => {
    const base = {
      backgroundSize: 'cover',
      backgroundPosition: 'center',
      backgroundRepeat: 'no-repeat',
    };

    if (currentScreen === SCREENS.FLASHLIGHT) {
      const bg = flashlightMode === 0 ? '#ffffff' : flashlightMode === 1 ? '#ff9f0a' : strobeOn ? '#ff3b30' : '#000000';
      return {
        ...base,
        backgroundColor: bg,
        backgroundImage: 'none',
      };
    }
    if (currentScreen !== SCREENS.SAFE) {
      return {
        ...base,
        backgroundColor: '#000000',
        backgroundImage: 'none',
      };
    }
    if (currentWp.type === 'image') {
      return {
        ...base,
        backgroundColor: '#000000',
        backgroundImage: `linear-gradient(180deg, rgba(0, 0, 0, 0.35) 0%, rgba(0, 0, 0, 0.10) 40%, rgba(0, 0, 0, 0.65) 100%), url(${currentWp.value})`,
      };
    }
    if (currentWp.type === 'gradient') {
      return {
        ...base,
        backgroundColor: '#000000',
        backgroundImage: currentWp.value,
      };
    }
    return {
      ...base,
      backgroundColor: currentWp.value,
      backgroundImage: 'none',
    };
  };

  const timeHours = time.getHours().toString().padStart(2, '0');
  const timeMinutes = time.getMinutes().toString().padStart(2, '0');
  const dateStr = time.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });

  // Get current 4 apps for the paginated drawer
  const current4Apps = DRAWER_APPS.slice(drawerPage * 4, drawerPage * 4 + 4);

  return (
    <div className="ws-super-wrapper">
      {toastMsg && <div className="ws-floating-toast">{toastMsg}</div>}

      {/* Titanium Hardware Chassis */}
      <div className={`ws-watch-chassis ${isEmergencyActive ? 'emergency-glow' : ''}`}>
        
        {/* Hardware Crown Button (Top Right) */}
        <button
          className="ws-hw-crown"
          onClick={handleCrownClick}
          title="Digital Crown: Click for Apps Drawer / Watch Face"
        >
          <span className="ws-crown-core" />
        </button>

        {/* Hardware Side Button (Bottom Right) */}
        <button
          className="ws-hw-side-btn"
          onClick={handleSideButtonClick}
          onContextMenu={(e) => {
            e.preventDefault();
            triggerEmergency();
          }}
          title="Side Button: Click for Control Center • Long Click for SOS"
        />

        {/* Hardware Left Speaker Grille & Mic */}
        <div className="ws-hw-mic" />
        <div className="ws-hw-speaker-slots">
          <span /><span /><span />
        </div>

        {/* AMOLED Screen Viewport (410×502 aspect ratio) */}
        <div
          className="ws-screen-viewport ws-amoled-display"
          style={getScreenBackgroundStyle()}
        >
          {/* Glass Glare Overlay */}
          <div className="ws-display-glare" />

          {/* ══════════ SCREEN 1: SAFE WATCH FACE ══════════ */}
          {currentScreen === SCREENS.SAFE && (
            <div className="ws-ui-screen ws-screen ws-screen-safe-face ws-screen-safe" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>
              {/* Top Status Bar with high readability */}
              <div className="ws-top-status-bar" onClick={(e) => { e.stopPropagation(); navigateTo(SCREENS.CONTROL_CENTER); }}>
                <div className="ws-status-left">
                  <div className="ws-carrier-bars">
                    <span className="bar active" />
                    <span className="bar active" />
                    <span className="bar active" />
                    <span className="bar active" />
                  </div>
                  <span className="ws-status-carrier">Jio 4G</span>
                </div>
                <div className="ws-status-right">
                  <span className="ws-battery-val">{battery}%</span>
                  <div className="ws-battery-pill">
                    <span className="ws-battery-fill" style={{ width: `${battery}%` }} />
                  </div>
                </div>
              </div>

              {/* Wallpaper Chip (Tap to open Wallpapers app) */}
              <button
                className="ws-wallpaper-cycler"
                onClick={(e) => { e.stopPropagation(); navigateTo(SCREENS.WALLPAPERS); }}
                title="Tap to select 5 AMOLED wallpapers"
              >
                <span className="ws-wp-dot" style={{ background: currentWp.thumb }} />
                <span>{currentWp.name}</span>
              </button>

              {/* Watch Typography Clock */}
              <div className="ws-clock-hero">
                <div className="ws-clock-time">{timeHours}:{timeMinutes}</div>
                <div className="ws-clock-date">{dateStr}</div>
              </div>

              {/* Activity & Guardian Rings */}
              <div className="ws-activity-rings-container">
                <svg className="ws-activity-rings-svg" viewBox="0 0 100 100">
                  <circle cx="50" cy="50" r="42" stroke="rgba(255, 69, 58, 0.2)" strokeWidth="6" fill="none" />
                  <circle cx="50" cy="50" r="42" stroke="#ff453a" strokeWidth="6" fill="none" strokeDasharray="264" strokeDashoffset="40" strokeLinecap="round" />
                  <circle cx="50" cy="50" r="33" stroke="rgba(48, 209, 88, 0.2)" strokeWidth="6" fill="none" />
                  <circle cx="50" cy="50" r="33" stroke="#30d158" strokeWidth="6" fill="none" strokeDasharray="207" strokeDashoffset="30" strokeLinecap="round" />
                  <circle cx="50" cy="50" r="24" stroke="rgba(10, 132, 255, 0.2)" strokeWidth="6" fill="none" />
                  <circle cx="50" cy="50" r="24" stroke="#0a84ff" strokeWidth="6" fill="none" strokeDasharray="150" strokeDashoffset="25" strokeLinecap="round" />
                </svg>
                <div className="ws-center-ring-info">
                  <span className="ws-ring-val">72</span>
                  <span className="ws-ring-unit">BPM</span>
                </div>
              </div>

              {/* Bottom Quick-Action Dock */}
              <div className="ws-safe-dock">
                <button
                  className="ws-dock-btn sos"
                  onClick={(e) => { e.stopPropagation(); triggerEmergency(); }}
                  title="Emergency SOS"
                >
                  <img src="/icons/icon_sos.png" alt="SOS" className="ws-dock-custom-img" />
                </button>
                <button
                  className="ws-dock-btn guard"
                  onClick={(e) => {
                    e.stopPropagation();
                    onStateChange?.('WATCH');
                    navigateTo(SCREENS.WATCH);
                  }}
                  title="Night Walk Guard"
                >
                  <img src="/icons/icon_guard.png" alt="Guard" className="ws-dock-custom-img" />
                </button>
                <button
                  className="ws-dock-btn phone"
                  onClick={(e) => { e.stopPropagation(); navigateTo(SCREENS.DIALER); }}
                  title="Phone & Contacts"
                >
                  <img src="/icons/icon_phone.png" alt="Phone" className="ws-dock-custom-img" />
                </button>
              </div>

              <div className="ws-tap-apps-hint">TAP SCREEN FOR APPS</div>
            </div>
          )}

          {/* ══════════ SCREEN 2: 4-APP STACKED DRAWER (PAGINATED) ══════════ */}
          {currentScreen === SCREENS.LAUNCHPAD && (
            <div className="ws-ui-screen ws-screen ws-screen-slide-drawer">
              {/* Header */}
              <div className="ws-drawer-header ws-slide-drawer-header">
                <button
                  className="ws-drawer-home-btn"
                  onClick={() => navigateTo(SCREENS.SAFE)}
                >
                  &lt; HOME
                </button>
                <span className="ws-drawer-title ws-slide-drawer-title">APPS</span>
                <span className="ws-drawer-page-indicator">{drawerPage + 1} / 3</span>
              </div>

              {/* 4 Square Cards (2x2 Grid) for Current Page */}
              <div className="ws-drawer-grid-4">
                {current4Apps.map((app) => (
                  <button
                    key={app.id}
                    className="ws-drawer-card-square"
                    onClick={() => {
                      if (app.id === 'sos') triggerEmergency();
                      else {
                        if (app.id === 'guard') onStateChange?.('WATCH');
                        navigateTo(app.screen as WatchScreenType);
                      }
                    }}
                  >
                    {app.badge && (
                      <span
                        className="ws-drawer-square-badge"
                        style={{
                          color: app.badgeCol,
                          borderColor: `${app.badgeCol}44`,
                          background: `${app.badgeCol}22`,
                        }}
                      >
                        {app.badge}
                      </span>
                    )}
                    <div
                      className="ws-drawer-square-icon-wrap"
                      style={{
                        background: `radial-gradient(circle at center, ${app.badgeCol}35 0%, ${app.badgeCol}12 100%)`,
                        borderColor: `${app.badgeCol}44`
                      }}
                    >
                      <img src={app.iconSrc} alt={app.name} className="ws-drawer-custom-icon" />
                    </div>
                    <span className="ws-drawer-square-name">{app.name}</span>
                    <span className="ws-drawer-square-sub">{app.sub}</span>
                  </button>
                ))}
              </div>

              {/* Bottom Pagination Bar: PREV, 3 Dots, NEXT */}
              <div className="ws-drawer-pagination-bar">
                <button
                  className={`ws-page-nav-btn ${drawerPage === 0 ? 'disabled' : ''}`}
                  disabled={drawerPage === 0}
                  onClick={() => setDrawerPage(prev => Math.max(0, prev - 1))}
                >
                  &lt; PREV
                </button>

                <div className="ws-drawer-dots">
                  {[0, 1, 2].map((p) => (
                    <span
                      key={p}
                      className={`ws-drawer-dot ${p === drawerPage ? 'active-pill' : ''}`}
                      onClick={() => setDrawerPage(p)}
                    />
                  ))}
                </div>

                <button
                  className={`ws-page-nav-btn ${drawerPage === 2 ? 'disabled' : ''}`}
                  disabled={drawerPage === 2}
                  onClick={() => setDrawerPage(prev => Math.min(2, prev + 1))}
                >
                  NEXT &gt;
                </button>
              </div>
            </div>
          )}

          {/* ══════════ SCREEN 3: CONTROL CENTER ══════════ */}
          {currentScreen === SCREENS.CONTROL_CENTER && (
            <div className="ws-ui-screen ws-screen ws-screen-cc-mature">
              <div className="ws-cc-header">
                <div className="ws-cc-status-group">
                  <div className="ws-carrier-bars">
                    <span className="bar active" /><span className="bar active" /><span className="bar active" /><span className="bar active" />
                  </div>
                  <span className="ws-cc-carrier">Jio 4G LTE</span>
                  <span className="ws-cc-dot" />
                </div>
                <div className="ws-cc-batt-group">
                  <span className="ws-cc-batt-num">{battery}%</span>
                  <div className="ws-battery-pill mini"><span className="ws-battery-fill" style={{ width: `${battery}%` }} /></div>
                  <button className="ws-cc-done-btn" onClick={() => navigateTo(SCREENS.SAFE)}>DONE</button>
                </div>
              </div>

              <div className="ws-cc-cards-grid">
                <button
                  className={`ws-cc-card ${cellularActive ? 'active' : ''}`}
                  onClick={() => { setCellularActive(!cellularActive); showToast(cellularActive ? 'Cellular Radio Disabled' : 'Cellular Jio 4G Connected'); }}
                >
                  <div className="ws-cc-card-icon cellular">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M2 20h.01"/><path d="M7 20v-4"/><path d="M12 20v-8"/><path d="M17 20V4"/>
                    </svg>
                  </div>
                  <div className="ws-cc-card-texts">
                    <span className="title">Cellular</span>
                    <span className="sub">{cellularActive ? 'Connected' : 'Off'}</span>
                  </div>
                </button>

                <button
                  className={`ws-cc-card ${wifiActive ? 'active' : ''}`}
                  onClick={() => { setWifiActive(!wifiActive); showToast(wifiActive ? 'Wi-Fi Disconnected' : 'Wi-Fi 2.4GHz Connected'); }}
                >
                  <div className="ws-cc-card-icon wifi">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M5 12.55a11 11 0 0 1 14.08 0"/><path d="M1.42 9a16 16 0 0 1 21.16 0"/><path d="M8.53 16.11a6 6 0 0 1 6.95 0"/><line x1="12" y1="20" x2="12.01" y2="20"/>
                    </svg>
                  </div>
                  <div className="ws-cc-card-texts">
                    <span className="title">Wi-Fi</span>
                    <span className="sub">{wifiActive ? 'Connected' : 'Off'}</span>
                  </div>
                </button>

                <button
                  className={`ws-cc-card ${silentActive ? 'active' : ''}`}
                  onClick={() => { setSilentActive(!silentActive); showToast(silentActive ? 'Silent Mode Disabled' : 'Silent: Vibrate Only'); }}
                >
                  <div className="ws-cc-card-icon silent">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>
                    </svg>
                  </div>
                  <div className="ws-cc-card-texts">
                    <span className="title">Silent</span>
                    <span className="sub">{silentActive ? 'On' : 'Off'}</span>
                  </div>
                </button>

                <button
                  className="ws-cc-card"
                  onClick={() => navigateTo(SCREENS.FLASHLIGHT)}
                >
                  <div className="ws-cc-card-icon torch">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                      <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                    </svg>
                  </div>
                  <div className="ws-cc-card-texts">
                    <span className="title">Torch</span>
                    <span className="sub">Full Screen</span>
                  </div>
                </button>

                <button
                  className={`ws-cc-card ${activeSound ? 'alert-active' : ''}`}
                  onClick={() => {
                    if (activeSound) {
                      audioSynth.stop();
                      setActiveSound(null);
                      showToast('Siren stopped');
                    } else {
                      audioSynth.play('police', volume / 100);
                      setActiveSound('police');
                      showToast('🔊 105dB Siren sounding!');
                    }
                  }}
                >
                  <div className="ws-cc-card-icon siren">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                      <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
                    </svg>
                  </div>
                  <div className="ws-cc-card-texts">
                    <span className="title">Siren</span>
                    <span className="sub">{activeSound ? 'ACTIVE' : '105dB Loud'}</span>
                  </div>
                </button>

                <button
                  className="ws-cc-card"
                  onClick={() => navigateTo(SCREENS.WATCH)}
                >
                  <div className="ws-cc-card-icon gps">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                    </svg>
                  </div>
                  <div className="ws-cc-card-texts">
                    <span className="title">GPS Guard</span>
                    <span className="sub">3D Fix 12 Sats</span>
                  </div>
                </button>
              </div>

              <div className="ws-cc-volume-bar">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/>
                </svg>
                <div
                  className="ws-cc-vol-track"
                  onClick={(e) => {
                    const rect = e.currentTarget.getBoundingClientRect();
                    const clickX = e.clientX - rect.left;
                    const newVol = Math.round(Math.max(10, Math.min(100, (clickX / rect.width) * 100)));
                    setVolume(newVol);
                    showToast(`Volume: ${newVol}%`);
                  }}
                >
                  <div className="ws-cc-vol-fill" style={{ width: `${volume}%` }} />
                </div>
                <span className="ws-cc-vol-val">{volume}%</span>
              </div>

              <button className="ws-cc-sos-strip" onClick={triggerEmergency}>
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
                <span>EMERGENCY SOS</span>
              </button>
            </div>
          )}

          {/* ══════════ SCREEN 4: 30-SECOND EMERGENCY SOS ══════════ */}
          {currentScreen === SCREENS.EMERGENCY && (
            <div className="ws-ui-screen ws-screen ws-screen-emergency-alert">
              <div className="ws-emer-top-header">
                <span className="ws-emer-badge">CRITICAL ALERT</span>
                <span className="ws-emer-sub">CELLULAR 4G ARMED</span>
              </div>

              <div className="ws-countdown-arc-wrap">
                <svg className="ws-arc-svg" viewBox="0 0 120 120">
                  <circle cx="60" cy="60" r="50" stroke="rgba(255, 69, 58, 0.2)" strokeWidth="8" fill="none" />
                  <circle
                    cx="60"
                    cy="60"
                    r="50"
                    stroke="#ff3b30"
                    strokeWidth="8"
                    fill="none"
                    strokeDasharray="314"
                    strokeDashoffset={314 - (314 * countdown) / 30}
                    strokeLinecap="round"
                    style={{ transition: 'stroke-dashoffset 1s linear' }}
                  />
                </svg>
                <div className="ws-arc-inner-content">
                  <span className="ws-count-num">{countdown}</span>
                  <span className="ws-count-unit">SEC</span>
                </div>
              </div>

              <div className="ws-emer-status-text">
                Live GPS + Audio Stream to 112 & Guardians in {countdown}s
              </div>

              <button
                className="ws-cancel-slider-btn ws-disarm-immediate-btn"
                style={{
                  background: 'rgba(48, 209, 88, 0.22)',
                  border: '1.5px solid #30d158',
                  color: '#ffffff',
                  fontWeight: 700,
                  fontSize: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '6px',
                  cursor: 'pointer',
                  padding: '12px 16px',
                  borderRadius: '24px',
                  width: '100%',
                  marginTop: '10px'
                }}
                onClick={() => {
                  setIsEmergencyActive(false);
                  audioSynth.stop();
                  onStateChange?.('SAFE');
                  showToast('🛡️ SOS Disarmed: You are safe');
                  navigateTo(SCREENS.SAFE);
                }}
              >
                <span>🛡️ TAP TO DISARM · I AM SAFE</span>
              </button>
            </div>
          )}

          {/* ══════════ SCREEN 5: HOLD TO CANCEL SOS ══════════ */}
          {currentScreen === SCREENS.CANCEL && (
            <div className="ws-ui-screen ws-screen ws-screen-cancel-hold">
              <div className="ws-cancel-header">DISARM ALARM</div>
              <p className="ws-cancel-hint">Continuous touch for 2 seconds to verify your safety</p>

              <div className="ws-hold-ring-container">
                <svg className="ws-hold-svg" viewBox="0 0 140 140">
                  <circle cx="70" cy="70" r="60" stroke="rgba(48, 209, 88, 0.2)" strokeWidth="10" fill="none" />
                  <circle
                    cx="70"
                    cy="70"
                    r="60"
                    stroke="#30d158"
                    strokeWidth="10"
                    fill="none"
                    strokeDasharray="377"
                    strokeDashoffset={377 - (377 * holdProgress) / 100}
                    strokeLinecap="round"
                  />
                </svg>
                <button
                  className={`ws-hold-touch-target ${holdProgress > 0 ? 'pressing' : ''}`}
                  onMouseDown={handleHoldStart}
                  onMouseUp={handleHoldEnd}
                  onMouseLeave={handleHoldEnd}
                  onTouchStart={handleHoldStart}
                  onTouchEnd={handleHoldEnd}
                >
                  <span className="ws-hold-pct">{Math.round(holdProgress)}%</span>
                  <span className="ws-hold-label">HOLD</span>
                </button>
              </div>

              <button className="ws-nav-back-link" onClick={() => navigateTo(cancelFromScreen)}>
                Back to Alert
              </button>
            </div>
          )}

          {/* ══════════ SCREEN 6: CALLING OVERLAY ══════════ */}
          {currentScreen === SCREENS.CALLING && (
            <div className="ws-ui-screen ws-screen ws-screen-calling">
              <div className="ws-calling-pulse-circle">
                <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="#30d158" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                </svg>
              </div>

              <span className="ws-calling-status">OUTGOING CALL...</span>
              <span className="ws-calling-name">{callingInfo.name}</span>
              <span className="ws-calling-num">{callingInfo.number}</span>
              
              <div className="ws-calling-ring-indicator">
                <span className="ws-ring-wave" />
                <span>Ringing audio cadence (440Hz/480Hz)</span>
              </div>

              <span className="ws-calling-timer">00:{callingInfo.elapsed.toString().padStart(2, '0')}</span>

              <button
                className="ws-end-call-btn"
                onClick={() => {
                  audioSynth.stop();
                  showToast('Call Ended');
                  navigateTo(SCREENS.SAFE);
                }}
              >
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#ffffff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
                <span>END CALL</span>
              </button>
            </div>
          )}

          {/* ══════════ SCREEN 7: SMS SENT OVERLAY ══════════ */}
          {currentScreen === SCREENS.SMS_SENT && (
            <div className="ws-ui-screen ws-screen ws-screen-sms-sent">
              <div className="ws-sms-check-circle">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="#30d158" strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </div>
              <span className="ws-sms-sent-title">SMS Dispatched</span>
              <span className="ws-sms-sent-recipient">{smsInfo.name}</span>
              <p className="ws-sms-sent-body">
                "SOS! Emergency alert triggered. Wearer coordinates: 19.0760° N, 72.8777° E. Battery 94%."
              </p>
              <button className="ws-sms-done-btn" onClick={() => navigateTo(SCREENS.SAFE)}>
                Done
              </button>
            </div>
          )}

          {/* ══════════ SCREEN 8: SOUND STUDIO ══════════ */}
          {currentScreen === SCREENS.SOUND_STUDIO && (
            <div className="ws-ui-screen ws-screen ws-screen-sound-studio">
              <div className="ws-sound-header">
                <button className="ws-drawer-home-btn" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>&lt; APPS</button>
                <span className="title">Sound Studio</span>
                <span style={{ width: 48 }} />
              </div>

              <div className="ws-sound-list">
                {[
                  { id: 'police', name: 'Police Siren', sub: '600-1500Hz wail sweep' },
                  { id: 'pulse', name: '105dB Pulse', sub: '2200Hz high alert' },
                  { id: 'ambulance', name: 'Ambulance Hi-Lo', sub: '960/770Hz dual tone' },
                  { id: 'chirp', name: 'Chirp Radar', sub: '1400Hz frequency blast' },
                  { id: 'bell', name: 'Bell Chime', sub: '1760Hz harmonic pulse' },
                ].map(s => (
                  <button
                    key={s.id}
                    className={`ws-sound-card ${activeSound === s.id ? 'active' : ''}`}
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
                      <div className="ws-sound-name">{s.name}</div>
                      <div className="ws-sound-sub">{s.sub}</div>
                    </div>
                    <span className="ws-sound-state">{activeSound === s.id ? 'STOP' : 'PLAY'}</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* ══════════ SCREEN 9: FLASHLIGHT (100% FULL-SCREEN - ZERO TEXT) ══════════ */}
          {currentScreen === SCREENS.FLASHLIGHT && (
            <div
              className="ws-ui-screen ws-screen ws-screen-flashlight-pure"
              style={{
                background:
                  flashlightMode === 0
                    ? '#ffffff'
                    : flashlightMode === 1
                    ? '#ff9f0a'
                    : strobeOn
                    ? '#ff3b30'
                    : '#000000',
                cursor: 'pointer',
                position: 'relative'
              }}
              onClick={() => setFlashlightMode(prev => (prev + 1) % 3)}
              title="Tap screen to cycle White -> Amber -> Red Strobe. Click < EXIT or Crown to close."
            >
              <button
                className="ws-flashlight-exit-btn"
                style={{
                  position: 'absolute',
                  top: '12px',
                  left: '12px',
                  padding: '6px 14px',
                  borderRadius: '16px',
                  background: 'rgba(0,0,0,0.65)',
                  color: '#ffffff',
                  fontSize: '11px',
                  fontWeight: 700,
                  letterSpacing: '0.05em',
                  border: '1px solid rgba(255,255,255,0.25)',
                  cursor: 'pointer',
                  zIndex: 20
                }}
                onClick={(e) => {
                  e.stopPropagation();
                  setFlashlightMode(0);
                  navigateTo(SCREENS.LAUNCHPAD);
                }}
              >
                &lt; EXIT
              </button>
            </div>
          )}

          {/* ══════════ SCREEN 10: VOICE RECORDER (MIC MEMO) ══════════ */}
          {currentScreen === SCREENS.RECORDER && (
            <div className="ws-ui-screen ws-screen ws-screen-recorder">
              <div className="ws-recorder-header">
                <button className="ws-drawer-home-btn" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>&lt; APPS</button>
                <span className="title">Voice Memo</span>
                <span style={{ width: 48 }} />
              </div>

              {isRecordingMemo ? (
                <div className="ws-recorder-body recording">
                  <div className="ws-recorder-live-badge">
                    <span className="ws-rec-dot" />
                    <span>RECORDING LIVE</span>
                  </div>

                  <div className="ws-rec-timer">
                    00:{memoElapsed.toString().padStart(2, '0')}
                  </div>
                  <div className="ws-rec-mic-sub">ES7210 16kHz Blackbox Mic</div>

                  {/* Animated bouncing soundwave bars */}
                  <div className="ws-rec-soundwaves">
                    {[16, 28, 44, 20, 36, 48, 30, 42, 24, 38, 18, 32].map((h, i) => (
                      <span
                        key={i}
                        className="ws-soundwave-bar"
                        style={{
                          height: `${h}px`,
                          animationDelay: `${i * 0.08}s`
                        }}
                      />
                    ))}
                  </div>

                  <button className="ws-rec-stop-btn" onClick={stopRecordingMemo}>
                    ■ STOP RECORDING
                  </button>
                </div>
              ) : hasRecordedMemo ? (
                <div className="ws-recorder-body saved">
                  <div className="ws-recorder-saved-badge">
                    <span>✓ MEMO SAVED</span>
                  </div>

                  <div className="ws-rec-timer saved">
                    00:{memoDuration.toString().padStart(2, '0')}
                  </div>
                  <div className="ws-rec-mic-sub">
                    {isPlayingMemo ? 'Playing back on watch speaker...' : 'Audio Note Stored in RAM'}
                  </div>

                  {/* Waveform preview bars */}
                  <div className="ws-rec-soundwaves static">
                    {[14, 26, 38, 18, 32, 42, 28, 36, 22, 34, 16, 28].map((h, i) => (
                      <span
                        key={i}
                        className={`ws-soundwave-bar ${isPlayingMemo ? 'playing' : ''}`}
                        style={{ height: `${h}px` }}
                      />
                    ))}
                  </div>

                  <div className="ws-rec-actions">
                    <button
                      className={`ws-rec-play-btn ${isPlayingMemo ? 'active' : ''}`}
                      onClick={playRecordedMemo}
                    >
                      {isPlayingMemo ? '|| PAUSE' : '▶ PLAY MEMO'}
                    </button>
                    <button className="ws-rec-new-btn" onClick={startRecordingMemo}>
                      ● NEW
                    </button>
                  </div>

                  <div className="ws-rec-note">Audio plays through watch speaker</div>
                </div>
              ) : (
                <div className="ws-recorder-body idle">
                  <button className="ws-rec-big-circle" onClick={startRecordingMemo}>
                    <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="#ffffff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/>
                      <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                      <line x1="12" y1="19" x2="12" y2="22"/>
                    </svg>
                  </button>

                  <div className="ws-rec-idle-title">TAP TO RECORD</div>
                  <div className="ws-rec-idle-sub">Captures audio note via mic</div>
                  <div className="ws-rec-idle-desc">Emergency blackbox voice evidence</div>
                </div>
              )}
            </div>
          )}

          {/* ══════════ SCREEN 11: WALLPAPERS SELECTOR ══════════ */}
          {currentScreen === SCREENS.WALLPAPERS && (
            <div className="ws-ui-screen ws-screen ws-screen-wallpapers">
              <div className="ws-wp-header">
                <button className="ws-drawer-home-btn" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>&lt; APPS</button>
                <span className="title">Wallpapers</span>
                <span style={{ width: 48 }} />
              </div>

              <div className="ws-wp-cards-list">
                {WALLPAPERS.map((wp, idx) => (
                  <button
                    key={wp.id}
                    className={`ws-wp-card ${wallpaperIdx === idx ? 'selected' : ''}`}
                    onClick={() => {
                      setWallpaperIdx(idx);
                      showToast(`Selected: ${wp.name}`);
                    }}
                  >
                    <div className="ws-wp-swatch" style={{ background: wp.thumb }} />
                    <div className="ws-wp-card-text">
                      <div className="name">{wp.name}</div>
                      <div className="sub">{wp.sub}</div>
                    </div>
                    {wallpaperIdx === idx && <span className="ws-wp-check">✓</span>}
                  </button>
                ))}
              </div>

              <button
                className="ws-wp-apply-btn"
                onClick={() => {
                  showToast('Wallpaper Applied!');
                  navigateTo(SCREENS.SAFE);
                }}
              >
                APPLY TO WATCH FACE
              </button>
            </div>
          )}

          {/* ══════════ SCREEN 12: NIGHT WALK GUARD ══════════ */}
          {currentScreen === SCREENS.WATCH && (
            <div className="ws-ui-screen ws-screen ws-screen-guard">
              <div className="ws-guard-top">
                <span className="title">Night Walk Guard</span>
                <span className="badge">GPS ACTIVE</span>
              </div>
              <div className="ws-guard-pulse-center">
                <div className="ws-pulse-sonar" />
                <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="#ffd60a" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
              </div>
              <div className="ws-guard-meta">
                <div className="meta-row"><span>Heading</span><strong>042° NE</strong></div>
                <div className="meta-row"><span>Satellites</span><strong>12 Fixed</strong></div>
                <div className="meta-row"><span>Ping Interval</span><strong>30s Auto</strong></div>
              </div>
              <button className="ws-guard-end-btn" onClick={() => {
                onStateChange?.('SAFE');
                navigateTo(SCREENS.SAFE);
              }}>
                End Guard Session
              </button>
            </div>
          )}

          {/* ══════════ SCREEN 13: CONTACTS DIRECTORY ══════════ */}
          {currentScreen === SCREENS.CONTACTS && (
            <div className="ws-ui-screen ws-screen ws-screen-contacts">
              <div className="ws-contacts-header">
                <button className="ws-drawer-home-btn" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>&lt; APPS</button>
                <span>Guardians</span>
                <button onClick={() => navigateTo(SCREENS.SAFE)}>DONE</button>
              </div>
              <div className="ws-contacts-list">
                {[
                  { name: 'Mom (Primary)', num: '+91 98765 43210', initial: 'M', col: '#ff453a' },
                  { name: 'Dad', num: '+91 98200 11223', initial: 'D', col: '#0a84ff' },
                  { name: 'Sister (Riya)', num: '+91 98333 44556', initial: 'R', col: '#30d158' },
                  { name: 'Emergency Services', num: '112', initial: '112', col: '#ffd60a' },
                ].map((c, i) => (
                  <div key={i} className="ws-contact-row-card">
                    <span className="avatar" style={{ background: c.col }}>{c.initial}</span>
                    <div className="info">
                      <span className="name">{c.name}</span>
                      <span className="num">{c.num}</span>
                    </div>
                    <button
                      className="call-btn"
                      onClick={() => {
                        setCallingInfo({ name: c.name, number: c.num, elapsed: 0 });
                        navigateTo(SCREENS.CALLING);
                      }}
                    >
                      CALL
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ══════════ SCREEN 14: PHONE DIALER KEYPAD ══════════ */}
          {currentScreen === SCREENS.DIALER && (
            <div className="ws-ui-screen ws-screen ws-screen-dialer">
              <div className="ws-dialer-display">
                <span className="digits">{dialedDigits || 'Enter number'}</span>
                {dialedDigits && (
                  <button className="del" onClick={() => setDialedDigits(prev => prev.slice(0, -1))}>
                    ⌫
                  </button>
                )}
              </div>

              <div className="ws-dialer-grid">
                {['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'].map(d => (
                  <button key={d} className="key-btn" onClick={() => setDialedDigits(prev => prev + d)}>
                    {d}
                  </button>
                ))}
              </div>

              <div className="ws-dialer-bottom">
                <button
                  className="call-out-btn"
                  onClick={() => {
                    const targetNum = dialedDigits || '112';
                    setCallingInfo({ name: 'Cellular Call', number: targetNum, elapsed: 0 });
                    navigateTo(SCREENS.CALLING);
                  }}
                >
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                  </svg>
                </button>
                <button className="exit-btn" onClick={() => navigateTo(SCREENS.SAFE)}>
                  Back
                </button>
              </div>
            </div>
          )}

          {/* ══════════ SCREEN 15: SYSTEM HEALTH (SIMPLIFIED & FRIENDLY) ══════════ */}
          {currentScreen === SCREENS.SYSTEM && (
            <div className="ws-ui-screen ws-screen ws-screen-system">
              <div className="ws-sys-header">
                <button className="ws-drawer-home-btn" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>&lt; APPS</button>
                <span>System Health</span>
                <button onClick={() => navigateTo(SCREENS.SAFE)}>DONE</button>
              </div>

              {/* Status Banner */}
              <div className="ws-sys-status-banner">
                <span className="ws-sys-shield-ico">🛡️</span>
                <div>
                  <div className="status-title">ALL SYSTEMS NORMAL</div>
                  <div className="status-sub">4 Sensors Active · GPS Ready</div>
                </div>
              </div>

              <div className="ws-sys-scrollable">
                <div className="ws-sys-card-friendly">
                  <div className="card-top">
                    <span className="card-name">Battery Power</span>
                    <span className="badge mint">94% Healthy</span>
                  </div>
                  <div className="card-val">Good for 36 hrs · 4.18V · AXP2101</div>
                </div>

                <div className="ws-sys-card-friendly">
                  <div className="card-top">
                    <span className="card-name">Cellular 4G LTE</span>
                    <span className="badge blue">Jio 4G Connected</span>
                  </div>
                  <div className="card-val">Signal CSQ 26 · 112 Auto-Dial Armed</div>
                </div>

                <div className="ws-sys-card-friendly">
                  <div className="card-top">
                    <span className="card-name">Sensors & Motion</span>
                    <span className="badge cyan">Armed</span>
                  </div>
                  <div className="card-val">12 Satellites Locked · Fall Guard ON</div>
                </div>

                <div
                  className="ws-sys-card-friendly clickable"
                  onClick={() => {
                    audioSynth.play('police', volume / 100);
                    showToast('Testing 105dB Siren Speaker...');
                    setTimeout(() => audioSynth.stop(), 2000);
                  }}
                >
                  <div className="card-top">
                    <span className="card-name">105dB Siren</span>
                    <span className="badge amber">Ready (Tap to Test)</span>
                  </div>
                  <div className="card-val">ES8311 I2S Audio Amp Ready</div>
                </div>
              </div>
            </div>
          )}

          {/* ══════════ SCREEN 16: TELEMETRY & COMPASS ══════════ */}
          {currentScreen === SCREENS.TELEMETRY && (
            <div className="ws-ui-screen ws-screen ws-screen-telemetry">
              <div className="ws-tele-header">
                <button className="ws-drawer-home-btn" onClick={() => navigateTo(SCREENS.LAUNCHPAD)}>&lt; APPS</button>
                <span>Compass & Heading</span>
                <button onClick={() => navigateTo(SCREENS.SAFE)}>DONE</button>
              </div>
              <div className="ws-compass-rose">
                <svg viewBox="0 0 100 100" className="ws-compass-svg">
                  <circle cx="50" cy="50" r="45" stroke="rgba(255,255,255,0.15)" strokeWidth="2" fill="none" />
                  <line x1="50" y1="10" x2="50" y2="20" stroke="#ff453a" strokeWidth="3" />
                  <line x1="50" y1="90" x2="50" y2="80" stroke="#ffffff" strokeWidth="2" />
                  <line x1="10" y1="50" x2="20" y2="50" stroke="#ffffff" strokeWidth="2" />
                  <line x1="90" y1="50" x2="80" y2="50" stroke="#ffffff" strokeWidth="2" />
                  <polygon points="50,15 45,50 55,50" fill="#ff453a" />
                  <polygon points="50,85 45,50 55,50" fill="rgba(255,255,255,0.4)" />
                </svg>
                <div className="ws-heading-text">042° NE</div>
                <div className="ws-altitude-text">Altitude: 142m • Tilt: +2.4°</div>
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
}
