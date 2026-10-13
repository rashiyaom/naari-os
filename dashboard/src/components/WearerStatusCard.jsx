import React, { useState, useEffect } from 'react';
import {
  Shield,
  ShieldAlert,
  ShieldCheck,
  BatteryCharging,
  Signal,
  MapPin,
  Clock,
  Volume2,
  VolumeX,
  Phone,
  CheckCircle2,
  AlertTriangle,
  Radio,
  Cpu,
  Mic,
  Activity,
  HardDrive
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { insertEvent } from '../services/supabase';

export default function WearerStatusCard({ onSelectEvent }) {
  const { wearer, device, contactsList, events } = useAuth();
  const [sirenMuted, setSirenMuted] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);

  const currentState = device?.current_state || 'safe';

  // Timer for watch active state
  useEffect(() => {
    if (currentState !== 'watch') {
      setElapsedSec(0);
      return;
    }
    const timer = setInterval(() => setElapsedSec((s) => s + 1), 1000);
    return () => clearInterval(timer);
  }, [currentState]);

  // Find latest active emergency event if in emergency state
  const latestEmergency = events.find(e => e.event_type === 'emergency_triggered');

  const formatElapsed = (sec) => {
    const mins = Math.floor(sec / 60);
    const s = sec % 60;
    return `${String(mins).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  const handleResolveEmergency = async () => {
    setResolving(true);
    try {
      await insertEvent({
        wearer_id: wearer.id,
        device_id: device.id,
        event_type: 'emergency_resolved',
        lat: device.last_lat,
        lng: device.last_lng,
        address: device.address,
        payload: {
          resolved_by: 'GUARDIAN_PORTAL',
          notes: 'Marked safe and resolved by trusted contact after voice verification.'
        }
      });
    } catch (err) {
      console.error('Failed to resolve alert', err);
    } finally {
      setResolving(false);
    }
  };

  const handleCancelWatch = async () => {
    try {
      await insertEvent({
        wearer_id: wearer.id,
        device_id: device.id,
        event_type: 'watch_cancelled',
        lat: device.last_lat,
        lng: device.last_lng,
        address: device.address,
        payload: {
          reason: 'GUARDIAN_CONFIRMED_SAFE'
        }
      });
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="status-card-wrapper">
      {/* 1. STATE-DEPENDENT DYNAMIC HERO BANNER */}
      {currentState === 'emergency' && (
        <div className="emergency-hero-banner emergency-pulsing glass-panel">
          <div className="banner-left">
            <div className="emergency-alarm-icon">
              <ShieldAlert size={32} color="#ffffff" />
            </div>
            <div>
              <div className="emergency-badge-row">
                <span className="emergency-tag-pill">CRITICAL EMERGENCY</span>
                <span className="emergency-trigger-label">
                  Trigger: {latestEmergency?.payload?.trigger || 'IMU Fall Impact / Voice Trigger'}
                </span>
              </div>
              <h2 className="emergency-headline">
                SOS Alert Active for {wearer?.full_name}
              </h2>
              <p className="emergency-subline">
                Continuous SIM800L SMS alert broadcast sent • 110dB wearable siren active • Audio recording to MicroSD
              </p>
            </div>
          </div>

          <div className="banner-actions">
            <button
              onClick={() => setSirenMuted(!sirenMuted)}
              className="action-btn-secondary"
            >
              {sirenMuted ? <VolumeX size={16} /> : <Volume2 size={16} />}
              <span>{sirenMuted ? 'Unmute Portal Siren' : 'Mute Portal Siren'}</span>
            </button>

            {latestEmergency && (
              <button
                onClick={() => onSelectEvent(latestEmergency)}
                className="action-btn-inspect"
              >
                Inspect Evidence & Audio
              </button>
            )}

            <button
              onClick={handleResolveEmergency}
              disabled={resolving}
              className="action-btn-resolve"
            >
              <CheckCircle2 size={16} />
              <span>{resolving ? 'Resolving...' : 'Confirm Safe & Resolve'}</span>
            </button>
          </div>
        </div>
      )}

      {currentState === 'watch' && (
        <div className="watch-hero-banner glass-panel">
          <div className="banner-left">
            <div className="watch-orbit-ring">
              <Shield size={24} color="var(--watch-mint)" />
            </div>
            <div>
              <div className="watch-badge-row">
                <span className="watch-tag-pill">SAFETY WATCH ACTIVE</span>
                <span className="live-telemetry-tag">CELL LOCATION ACTIVE</span>
              </div>
              <h2 className="watch-headline">
                {wearer?.full_name} is in Vigilant Safety Watch Mode
              </h2>
              <p className="watch-subline">
                Active duration: <strong className="mono">{formatElapsed(elapsedSec)}</strong> • High-rate IMU & offline voice keywords armed
              </p>
            </div>
          </div>

          <div className="banner-actions">
            <button
              onClick={handleCancelWatch}
              className="action-btn-calm"
            >
              <CheckCircle2 size={16} />
              <span>Acknowledge All Safe</span>
            </button>
          </div>
        </div>
      )}

      {currentState === 'safe' && (
        <div className="safe-hero-banner glass-panel">
          <div className="banner-left">
            <div className="safe-shield-icon">
              <ShieldCheck size={26} color="var(--watch-mint)" />
            </div>
            <div>
              <div className="safe-badge-row">
                <span className="safe-tag-pill">ALL IS WELL</span>
                <span className="safe-clock-tag">
                  Last updated: {new Date(device?.last_heartbeat || Date.now()).toLocaleTimeString()}
                </span>
              </div>
              <h2 className="safe-headline">
                {wearer?.full_name} is in Safe Everyday Mode
              </h2>
              <p className="safe-subline">
                Battery at {device?.battery_level || 88}% • GSM Cellular signal healthy ({device?.gsm_signal || 26}/31 CSQ)
              </p>
            </div>
          </div>
        </div>
      )}

      {/* 2. WEARER PROFILE & HARDWARE TELEMETRY GRID */}
      <div className="telemetry-grid">
        {/* Wearer Profile Card */}
        <div className="glass-panel profile-card">
          <div className="profile-header">
            <img
              src={wearer?.avatar_url || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200'}
              alt={wearer?.full_name}
              className="wearer-avatar"
            />
            <div>
              <h3 className="wearer-name">{wearer?.full_name}</h3>
              <div className="wearer-phone">
                <Phone size={13} />
                <span>{wearer?.phone}</span>
              </div>
              <div className="wearer-invite-pill mono">
                Pair Code: {wearer?.invite_code}
              </div>
            </div>
          </div>

          {wearer?.emergency_notes && (
            <div className="emergency-notes-box">
              <span className="notes-label">Emergency Medical & Routine Info:</span>
              <p>{wearer?.emergency_notes}</p>
            </div>
          )}

          <div className="profile-contacts-summary">
            <span className="summary-title">Configured Emergency Circle ({contactsList.length})</span>
            <div className="contacts-mini-list">
              {contactsList.map((c) => (
                <div key={c.id} className="contact-mini-row">
                  <span className="contact-name">{c.full_name} ({c.relation})</span>
                  <span className={`contact-badge ${c.is_primary ? 'badge-primary' : ''}`}>
                    {c.is_primary ? 'Primary Auto-Dial' : 'SMS Alert'}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Device & Hardware Sensor Health */}
        <div className="glass-panel hardware-card">
          <div className="card-section-title">
            <Cpu size={16} color="var(--watch-mint)" />
            <span>Hardware & Sensor Subsystems</span>
            <span className="device-code-badge mono">{device?.device_code || 'ESP32-S3'}</span>
          </div>

          <div className="hardware-metrics-list">
            <div className="metric-row">
              <div className="metric-label">
                <BatteryCharging size={16} color="var(--watch-mint)" />
                <span>AXP2101 PMIC Battery</span>
              </div>
              <div className="metric-val">
                <span className="val-main">{device?.battery_level || 88}%</span>
                <span className="val-sub">4.12V LiPo</span>
              </div>
            </div>

            <div className="metric-row">
              <div className="metric-label">
                <Signal size={16} color="var(--watch-soft)" />
                <span>SIM800L GSM / GPRS</span>
              </div>
              <div className="metric-val">
                <span className="val-main">{device?.gsm_signal || 26} / 31 CSQ</span>
                <span className="val-sub">Tower 404/45 Ready</span>
              </div>
            </div>

            <div className="metric-row">
              <div className="metric-label">
                <Activity size={16} color="var(--watch-mint)" />
                <span>QMI8658 6-Axis IMU</span>
              </div>
              <div className="metric-val">
                <span className="val-status status-active">100Hz Fall Guard</span>
                <span className="val-sub">Threshold: 3.2G</span>
              </div>
            </div>

            <div className="metric-row">
              <div className="metric-label">
                <Mic size={16} color="var(--watch-mint)" />
                <span>ESP-SR Voice Keyword</span>
              </div>
              <div className="metric-val">
                <span className="val-status status-active">"Help Me" Armed</span>
                <span className="val-sub">Dual ES7210 Mics</span>
              </div>
            </div>

            <div className="metric-row">
              <div className="metric-label">
                <HardDrive size={16} color="var(--watch-mint)" />
                <span>MicroSD FATFS Storage</span>
              </div>
              <div className="metric-val">
                <span className="val-status status-active">Blackbox Ready</span>
                <span className="val-sub">/sdcard/evidence/</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
