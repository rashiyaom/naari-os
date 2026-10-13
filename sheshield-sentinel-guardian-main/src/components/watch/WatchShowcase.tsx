import { useState } from 'react';
import {
  Shield,
  Sparkles,
  Smartphone,
  Radio,
  Image as ImageIcon
} from 'lucide-react';
import WatchSimulator from './WatchSimulator';
import { SCREENS, WALLPAPERS } from './watch-constants';

interface WatchShowcaseProps {
  onStateChange?: (state: 'SAFE' | 'WATCH' | 'EMERGENCY') => void;
  compact?: boolean;
}

export function WatchShowcase({ onStateChange, compact = false }: WatchShowcaseProps) {
  const [selectedScreen, setSelectedScreen] = useState<string>(SCREENS.SAFE);
  const [selectedWallpaper, setSelectedWallpaper] = useState<string>('topographic');

  const screenButtons = [
    { id: SCREENS.SAFE, label: 'Watch Face (Wallpapers)', icon: '⌚' },
    { id: SCREENS.LAUNCHPAD, label: '4-App Stacks Drawer', icon: '☰' },
    { id: SCREENS.RECORDER, label: 'Voice Recorder (Memo)', icon: '🎙️' },
    { id: SCREENS.WALLPAPERS, label: 'Wallpapers Selector', icon: '🖼️' },
    { id: SCREENS.SYSTEM, label: 'System Health (Friendly)', icon: '💚' },
    { id: SCREENS.FLASHLIGHT, label: 'Flashlight (Full Screen)', icon: '🔦' },
    { id: SCREENS.CONTROL_CENTER, label: 'Control Center (watchOS 10)', icon: '⚙️' },
    { id: SCREENS.EMERGENCY, label: '30s SOS Countdown', icon: '🚨' },
    { id: SCREENS.CALLING, label: 'Calling (Ring Sound)', icon: '📞' },
    { id: SCREENS.SMS_SENT, label: 'SMS Sent Overlay', icon: '💬' },
    { id: SCREENS.SOUND_STUDIO, label: 'Sound Studio (Sirens)', icon: '🎵' },
    { id: SCREENS.WATCH, label: 'Night Walk Guard', icon: '🌙' },
    { id: SCREENS.CONTACTS, label: 'Guardians Directory', icon: '👥' },
    { id: SCREENS.DIALER, label: 'Phone Keypad', icon: '🔢' },
    { id: SCREENS.TELEMETRY, label: 'Compass & Heading', icon: '🧭' },
  ];

  return (
    <div className={`watch-showcase-container ${compact ? 'compact' : ''}`}>
      {/* Top Banner Navigation */}
      <header className="showcase-nav-bar glass-panel">
        <div className="showcase-brand">
          <div className="showcase-logo-badge">
            <Shield size={22} color="#00e8a8" strokeWidth={2.4} />
          </div>
          <div>
            <div className="showcase-title-row">
              <span className="brand-title">Naari <span style={{ color: '#00e8a8' }}>OS</span></span>
              <span className="watch-live-pill">LIVE 410×502 AMOLED DISPLAY</span>
            </div>
            <p className="showcase-sub">
              Apple Watch Fluid Interface • Simple Slide Drawer • Custom Sliced Icons • 5 Wallpapers
            </p>
          </div>
        </div>
      </header>

      {/* Main Showcase Stage */}
      <main className="showcase-stage">
        {/* Left Column: Screen Jumpers & Wallpaper Picker */}
        <aside className="showcase-screen-palette glass-panel">
          {/* Wallpaper Selection Box */}
          <div className="showcase-wp-selector">
            <div className="palette-header">
              <ImageIcon size={16} color="#00e8a8" />
              <span>Watch Face Wallpapers</span>
            </div>
            <p className="palette-sub">Tap to change AMOLED background:</p>

            <div className="wp-thumbnails-grid">
              {WALLPAPERS.map((wp) => (
                <button
                  key={wp.id}
                  type="button"
                  className={`wp-chip-btn ${selectedWallpaper === wp.id ? 'active' : ''}`}
                  onClick={() => {
                    setSelectedWallpaper(wp.id);
                    setSelectedScreen(SCREENS.SAFE);
                  }}
                  title={wp.name}
                >
                  <span className="wp-color-preview" style={{ background: wp.thumb }} />
                  <span className="wp-chip-name">{wp.name}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="palette-divider" />

          {/* Screen Jumpers */}
          <div className="palette-header">
            <Sparkles size={16} color="#00e8a8" />
            <span>Screen Jumpers</span>
          </div>
          <p className="palette-sub">1-Click Test All Watch Screens:</p>

          <div className="palette-grid">
            {screenButtons.map((btn) => (
              <button
                key={btn.id}
                type="button"
                className={`palette-btn ${selectedScreen === btn.id ? 'active' : ''}`}
                onClick={() => setSelectedScreen(btn.id)}
              >
                <span className="palette-icon">{btn.icon}</span>
                <span className="palette-label">{btn.label}</span>
              </button>
            ))}
          </div>

          <div className="showcase-tip-card">
            <strong>💡 Interaction Tips:</strong>
            <ul>
              <li><strong>Simple Slide Drawer:</strong> Click "Slide App Drawer" or tap the watch clock to see the clean vertical list with custom 3D icons.</li>
              <li><strong>Phone Ring Audio:</strong> Open Calling Overlay to hear real telephone ringback audio synthesis!</li>
              <li><strong>Mature Control Center:</strong> Tap top status island or click Control Center to see watchOS 10 frosted squircle cards.</li>
              <li><strong>Interactive Crown:</strong> Click the titanium crown on the watch to toggle Home/Apps.</li>
            </ul>
          </div>
        </aside>

        {/* Center: The Apple Watch Display Casing */}
        <section className="showcase-watch-stage">
          <WatchSimulator
            targetScreen={selectedScreen}
            targetWallpaper={selectedWallpaper}
            onStateChange={onStateChange}
          />
        </section>

        {/* Right Column: Firmware & Feature Checklist */}
        <aside className="showcase-specs-panel glass-panel">
          <div className="palette-header">
            <Smartphone size={16} color="#00e8a8" />
            <span>Firmware & OS Specs</span>
          </div>

          {/* Feature Checklist */}
          <div className="spec-checklist">
            <div className="spec-item solved">
              <span className="check-badge">✓</span>
              <div>
                <strong>Simple Slide App Drawer</strong>
                <p>Clean vertical list view with custom rendered 3D squircle icons, priority badges, and fluid scrolling (zero emojis!).</p>
              </div>
            </div>

            <div className="spec-item solved">
              <span className="check-badge">✓</span>
              <div>
                <strong>5 AMOLED Wallpapers</strong>
                <p>OLED Pitch Black, Ultra Topographic Contours, Cosmic Aurora, California Sunset Gradient, and Cyberpunk Matrix.</p>
              </div>
            </div>

            <div className="spec-item solved">
              <span className="check-badge">✓</span>
              <div>
                <strong>Mature watchOS 10 Control Center</strong>
                <p>High-density carrier status (Jio 4G LTE), CSQ bars, battery pill, 6 frosted glass squircle cards, and full-width Emergency SOS strip.</p>
              </div>
            </div>

            <div className="spec-item solved">
              <span className="check-badge">✓</span>
              <div>
                <strong>Cellular Call Ringing Audio</strong>
                <p>Realistic telephone ringback cadence synthesizer (440Hz/480Hz dual cadence) when dispatching emergency calls.</p>
              </div>
            </div>

            <div className="spec-item solved">
              <span className="check-badge">✓</span>
              <div>
                <strong>30s Glitch-Free Countdown & Buttons</strong>
                <p>Accurate RTC-synchronized radial ring depletion arc + isolated Digital Crown (GPIO 0) and Side Power Button (PEK).</p>
              </div>
            </div>
          </div>

          {/* Quick Hardware Simulator Buttons */}
          <div className="hw-sim-deck">
            <div className="palette-header" style={{ marginBottom: 6 }}>
              <Radio size={15} color="#ffd60a" />
              <span>Direct Audio & Trigger Emulation</span>
            </div>

            <div className="hw-sim-grid">
              <button
                type="button"
                className="hw-sim-btn call"
                onClick={() => setSelectedScreen(SCREENS.CALLING)}
              >
                📞 Ringing Call Test
              </button>
              <button
                type="button"
                className="hw-sim-btn sos"
                onClick={() => setSelectedScreen(SCREENS.EMERGENCY)}
              >
                🚨 30s SOS Countdown
              </button>
              <button
                type="button"
                className="hw-sim-btn sounds"
                onClick={() => setSelectedScreen(SCREENS.SOUND_STUDIO)}
              >
                🎵 105dB Sirens
              </button>
              <button
                type="button"
                className="hw-sim-btn sms"
                onClick={() => setSelectedScreen(SCREENS.SMS_SENT)}
              >
                💬 SMS Sent Screen
              </button>
            </div>
          </div>
        </aside>
      </main>
    </div>
  );
}
export default WatchShowcase;
