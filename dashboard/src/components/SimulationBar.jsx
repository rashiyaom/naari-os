import React, { useState } from 'react';
import {
  Zap,
  Activity,
  Mic,
  ShieldAlert,
  Shield,
  ShieldCheck,
  MapPin,
  ChevronDown,
  ChevronUp,
  Radio
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { insertEvent } from '../services/supabase';

export default function SimulationBar() {
  const { wearer, device } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [loadingAction, setLoadingAction] = useState(null);

  const simulate = async (actionType, customPayload = {}) => {
    setLoadingAction(actionType);
    try {
      const baseLat = device?.last_lat || 28.6139;
      const baseLng = device?.last_lng || 77.2090;

      // Small jitter for realistic movement simulation
      const newLat = Number((baseLat + (Math.random() - 0.5) * 0.003).toFixed(5));
      const newLng = Number((baseLng + (Math.random() - 0.5) * 0.003).toFixed(5));

      let event_type = 'location_update';
      let payload = { ...customPayload };
      let address = device?.address || 'Connaught Place, Inner Circle, New Delhi';

      if (actionType === 'FALL_IMPACT') {
        event_type = 'emergency_triggered';
        payload = {
          trigger: 'QMI8658_HIGH_G_FALL',
          imu_g: '3.65G Shock Impact',
          audio_url: '/evidence/evidence_fall.wav',
          battery: device?.battery_level || 88,
          notes: 'Fall detected after free-fall condition followed by inactivity.'
        };
        address = 'Barakhamba Road Metro, New Delhi';
      } else if (actionType === 'VOICE_KEYWORD') {
        event_type = 'emergency_triggered';
        payload = {
          trigger: 'ESP_SR_VOICE_KEYWORD',
          keyword: 'HELP ME',
          confidence: 0.97,
          audio_url: '/evidence/evidence_voice.wav',
          battery: device?.battery_level || 88,
          notes: 'Voice distress keyword recognized by offline neural speech model.'
        };
        address = 'Janpath Lane, New Delhi';
      } else if (actionType === 'MANUAL_SOS') {
        event_type = 'emergency_triggered';
        payload = {
          trigger: 'PHYSICAL_BUTTON_HOLD',
          audio_url: '/evidence/evidence_manual.wav',
          battery: device?.battery_level || 88,
          notes: 'Dedicated hardware SOS button held for >3 seconds.'
        };
      } else if (actionType === 'START_WATCH') {
        event_type = 'watch_started';
        payload = {
          trigger: 'USER_WATCH_MODE',
          mode: 'Night Walk Vigilant Guard',
          battery: device?.battery_level || 88
        };
      } else if (actionType === 'GPS_PING') {
        event_type = 'location_update';
        payload = {
          speed_kmh: Number((3 + Math.random() * 2).toFixed(1)),
          battery: device?.battery_level || 88,
          gsm_csq: 27
        };
      } else if (actionType === 'SAFE_CLEAR') {
        event_type = 'watch_cancelled';
        payload = {
          reason: 'USER_CONFIRMED_SAFE'
        };
      }

      await insertEvent({
        wearer_id: wearer.id,
        device_id: device.id,
        event_type,
        lat: newLat,
        lng: newLng,
        address,
        payload
      });
    } catch (e) {
      console.error('Simulation error:', e);
    } finally {
      setLoadingAction(null);
    }
  };

  return (
    <div className="simulation-bar-container">
      <div className="simulation-bar glass-panel">
        <div className="sim-bar-header" onClick={() => setCollapsed(!collapsed)}>
          <div className="sim-title">
            <Zap size={16} color="var(--watch-mint)" />
            <span>Naari OS — Demo Event Simulator</span>
          </div>
          <button className="collapse-btn">
            {collapsed ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
          </button>
        </div>

        {!collapsed && (
          <div className="sim-actions-grid" style={{ gridTemplateColumns: 'repeat(7, 1fr)' }}>
            <button
              onClick={() => simulate('FALL_IMPACT')}
              disabled={loadingAction !== null}
              className="sim-btn sim-emergency"
            >
              <Activity size={14} />
              <span>Simulate Fall</span>
            </button>

            <button
              onClick={() => simulate('VOICE_KEYWORD')}
              disabled={loadingAction !== null}
              className="sim-btn sim-voice"
            >
              <Mic size={14} />
              <span>Voice "Help Me"</span>
            </button>

            <button
              onClick={() => simulate('MANUAL_SOS')}
              disabled={loadingAction !== null}
              className="sim-btn sim-button"
            >
              <ShieldAlert size={14} />
              <span>SOS Button</span>
            </button>

            <button
              onClick={() => {
                // Simulate shake by dispatching a custom event that the WatchSimulator listens to
                window.dispatchEvent(new CustomEvent('naari_demo_shake'));
                simulate('MANUAL_SOS');
              }}
              disabled={loadingAction !== null}
              className="sim-btn"
              style={{ background: 'rgba(255,59,92,0.15)', borderColor: 'rgba(255,59,92,0.5)', color: '#ff7a8a' }}
            >
              <Radio size={14} />
              <span>📳 Shake SOS</span>
            </button>

            <button
              onClick={() => simulate('START_WATCH')}
              disabled={loadingAction !== null}
              className="sim-btn sim-watch"
            >
              <Shield size={14} />
              <span>Start Watch</span>
            </button>

            <button
              onClick={() => simulate('GPS_PING')}
              disabled={loadingAction !== null}
              className="sim-btn sim-ping"
            >
              <MapPin size={14} />
              <span>Cell Ping</span>
            </button>

            <button
              onClick={() => simulate('SAFE_CLEAR')}
              disabled={loadingAction !== null}
              className="sim-btn sim-safe"
            >
              <ShieldCheck size={14} />
              <span>Mark Safe</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
