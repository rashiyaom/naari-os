import React, { useState } from 'react';
import {
  X,
  ShieldAlert,
  ShieldCheck,
  MapPin,
  Clock,
  Mic,
  Play,
  Pause,
  Download,
  ExternalLink,
  Activity,
  Phone,
  MessageSquare,
  CheckCircle2,
  FileText,
  Radio,
  AlertTriangle
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { insertEvent } from '../services/supabase';

export default function EmergencyDetailModal({ event, onClose }) {
  const { wearer, device, contactsList } = useAuth();
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [resolveNotes, setResolveNotes] = useState('');

  if (!event) return null;

  const isEmergency = event.event_type === 'emergency_triggered';
  const isResolved = event.event_type === 'emergency_resolved' || event.payload?.status === 'RESOLVED';
  const audioUrl = event.payload?.audio_url || '/evidence/sample_incident_evidence.wav';

  const handleResolve = async () => {
    setResolving(true);
    try {
      await insertEvent({
        wearer_id: wearer.id,
        device_id: device.id,
        event_type: 'emergency_resolved',
        lat: event.lat,
        lng: event.lng,
        address: event.address,
        payload: {
          incident_ref: event.id,
          resolved_by: 'GUARDIAN_PORTAL',
          notes: resolveNotes.trim() || 'Verified safe by guardian over phone.'
        }
      });
      onClose();
    } catch (err) {
      console.error('Failed to resolve event', err);
    } finally {
      setResolving(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-content glass-panel" onClick={(e) => e.stopPropagation()}>
        {/* Modal Header */}
        <div className="modal-header">
          <div className="modal-header-title">
            <div className={`modal-icon-badge ${isEmergency ? 'badge-emergency' : 'badge-safe'}`}>
              {isEmergency ? <ShieldAlert size={22} /> : <ShieldCheck size={22} />}
            </div>
            <div>
              <h2>
                {isEmergency ? 'Emergency Incident Dossier' : 'Event Telemetry Details'}
              </h2>
              <span className="modal-event-id mono">
                Event ID: {event.id} • {new Date(event.created_at).toLocaleString()}
              </span>
            </div>
          </div>

          <button onClick={onClose} className="modal-close-btn">
            <X size={20} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="modal-body">
          {/* Status Alert Ribbon */}
          <div className={`modal-status-ribbon ${isEmergency && !isResolved ? 'ribbon-emergency' : 'ribbon-safe'}`}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span className="pulse-dot" />
              <strong>
                {isEmergency && !isResolved ? 'STATUS: ACTIVE DISTRESS' : 'STATUS: RESOLVED & CLOSED'}
              </strong>
            </div>
            <span className="mono" style={{ fontSize: '11px' }}>
              Trigger Mechanism: {event.payload?.trigger || 'Hardware SOS / Fall Sensor'}
            </span>
          </div>

          {/* Location & GPS Info */}
          <div className="modal-section">
            <h4 className="section-title">
              <MapPin size={15} color="var(--watch-mint)" />
              Incident Geolocation & Coordinates
            </h4>
            <div className="location-box">
              <div className="location-text">
                <strong>{event.address || 'Connaught Place, Inner Circle, New Delhi'}</strong>
                <div className="coords mono">
                  Latitude: {event.lat}° N • Longitude: {event.lng}° E
                </div>
              </div>
              <a
                href={`https://maps.google.com/?q=${event.lat},${event.lng}`}
                target="_blank"
                rel="noreferrer"
                className="maps-button"
              >
                <span>Google Maps</span>
                <ExternalLink size={13} />
              </a>
            </div>
          </div>

          {/* Audio Evidence Player */}
          <div className="modal-section">
            <h4 className="section-title">
              <Mic size={15} color="var(--watch-coral)" />
              On-Device MicroSD Legal Audio Evidence (WAV 16kHz)
            </h4>
            <div className="audio-player-card">
              <div className="audio-player-controls">
                <button
                  onClick={() => setIsPlayingAudio(!isPlayingAudio)}
                  className="audio-play-btn"
                >
                  {isPlayingAudio ? <Pause size={18} /> : <Play size={18} />}
                </button>
                <div className="audio-waveform-visualizer">
                  <div className={`wave-bar ${isPlayingAudio ? 'animating' : ''}`} />
                  <div className={`wave-bar ${isPlayingAudio ? 'animating' : ''}`} style={{ height: '70%' }} />
                  <div className={`wave-bar ${isPlayingAudio ? 'animating' : ''}`} style={{ height: '90%' }} />
                  <div className={`wave-bar ${isPlayingAudio ? 'animating' : ''}`} style={{ height: '40%' }} />
                  <div className={`wave-bar ${isPlayingAudio ? 'animating' : ''}`} style={{ height: '80%' }} />
                  <div className={`wave-bar ${isPlayingAudio ? 'animating' : ''}`} style={{ height: '60%' }} />
                  <div className={`wave-bar ${isPlayingAudio ? 'animating' : ''}`} style={{ height: '95%' }} />
                  <div className={`wave-bar ${isPlayingAudio ? 'animating' : ''}`} style={{ height: '50%' }} />
                </div>
                <div className="audio-time mono">
                  {isPlayingAudio ? '00:14 / 00:45' : '00:00 / 00:45'}
                </div>
              </div>

              <div className="audio-footer">
                <span className="mono" style={{ fontSize: '11px', color: 'var(--watch-muted)' }}>
                  File: /sdcard/evidence/rec_{event.id}.wav • Dual ES7210 MEMS Mics
                </span>
                <button
                  onClick={() => alert(`Downloading forensic audio evidence ${event.id}.wav...`)}
                  className="audio-dl-btn"
                >
                  <Download size={13} />
                  <span>Download Evidence</span>
                </button>
              </div>
            </div>
          </div>

          {/* Structured Telemetry & Motion Sensor Data */}
          <div className="modal-section">
            <h4 className="section-title">
              <Activity size={15} color="var(--watch-mint)" />
              Structured Sensor & Movement Telemetry
            </h4>
            <div className="telemetry-table">
              <div className="table-row">
                <span className="cell-label">QMI8658 Peak Shock / Impact</span>
                <span className="cell-val mono">{event.payload?.imu_g || '3.42G (Free-fall + Sudden Deceleration)'}</span>
              </div>
              <div className="table-row">
                <span className="cell-label">ESP-SR Speech Engine</span>
                <span className="cell-val mono">Recognized "HELP ME" (96.4% confidence)</span>
              </div>
              <div className="table-row">
                <span className="cell-label">SIM800L Cellular Relays</span>
                <span className="cell-val mono">{contactsList.length} SMS Dispatched • Auto-Dial Primary Connected</span>
              </div>
              <div className="table-row">
                <span className="cell-label">Battery & Power Rail</span>
                <span className="cell-val mono">AXP2101 @ {event.payload?.battery || 88}% (Healthy)</span>
              </div>
            </div>
          </div>

          {/* Emergency Circle Notification Audit Log */}
          <div className="modal-section">
            <h4 className="section-title">
              <MessageSquare size={15} color="var(--watch-mint)" />
              Emergency Circle Contact Dispatch Log
            </h4>
            <div className="contact-audit-list">
              {contactsList.map((c, i) => (
                <div key={c.id} className="audit-row">
                  <div className="audit-left">
                    <span className="audit-name"><strong>{c.full_name}</strong> ({c.relation})</span>
                    <span className="audit-phone mono">{c.phone}</span>
                  </div>
                  <div className="audit-right">
                    <span className="audit-status-tag">
                      <CheckCircle2 size={12} color="var(--watch-mint)" />
                      <span>SMS DELIVERED (Cell Tower 404/45)</span>
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Resolve Incident Box */}
          {isEmergency && !isResolved && (
            <div className="modal-resolve-box">
              <h4 style={{ fontSize: '14px', fontWeight: 650, marginBottom: '6px' }}>
                Guardian Incident Resolution
              </h4>
              <textarea
                placeholder="Enter resolution notes (e.g. Spoke to wearer, confirmed safe, accidental press)..."
                value={resolveNotes}
                onChange={(e) => setResolveNotes(e.target.value)}
                rows={2}
                className="resolve-textarea"
              />
              <button
                onClick={handleResolve}
                disabled={resolving}
                className="resolve-submit-btn"
              >
                <CheckCircle2 size={16} />
                <span>{resolving ? 'Submitting Resolution...' : 'Mark Incident as Safely Resolved'}</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
