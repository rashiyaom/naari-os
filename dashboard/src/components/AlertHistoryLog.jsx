import React, { useState } from 'react';
import {
  Activity,
  ShieldAlert,
  ShieldCheck,
  Shield,
  MapPin,
  Clock,
  Mic,
  ChevronRight,
  Filter,
  CheckCircle2,
  AlertTriangle,
  Radio,
  FileText
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function AlertHistoryLog({ onSelectEvent }) {
  const { events } = useAuth();
  const [filter, setFilter] = useState('ALL'); // 'ALL' | 'EMERGENCY' | 'WATCH' | 'LOCATION'

  const filteredEvents = events.filter((e) => {
    if (filter === 'EMERGENCY') return e.event_type.startsWith('emergency');
    if (filter === 'WATCH') return e.event_type.startsWith('watch');
    if (filter === 'LOCATION') return e.event_type === 'location_update';
    return true;
  });

  const getEventBadge = (type) => {
    switch (type) {
      case 'emergency_triggered':
        return {
          label: 'EMERGENCY DISTRESS',
          className: 'badge-emergency',
          icon: ShieldAlert
        };
      case 'emergency_resolved':
        return {
          label: 'EMERGENCY RESOLVED',
          className: 'badge-resolved',
          icon: CheckCircle2
        };
      case 'watch_started':
        return {
          label: 'SAFETY WATCH STARTED',
          className: 'badge-watch',
          icon: Shield
        };
      case 'watch_cancelled':
        return {
          label: 'WATCH CANCELLED / SAFE',
          className: 'badge-safe',
          icon: ShieldCheck
        };
      case 'location_update':
        return {
          label: 'LOCATION PING',
          className: 'badge-location',
          icon: MapPin
        };
      default:
        return {
          label: type.toUpperCase(),
          className: 'badge-default',
          icon: Activity
        };
    }
  };

  return (
    <div className="glass-panel history-card">
      <div className="history-header">
        <div className="header-left">
          <Activity size={18} color="var(--watch-mint)" />
          <div>
            <h3 className="history-title">Alert & Incident History Log</h3>
            <span className="history-subtitle">
              Auditable tamper-proof telemetry events from wearable & SIM800L
            </span>
          </div>
        </div>

        {/* Filter Pills */}
        <div className="history-filters">
          <button
            onClick={() => setFilter('ALL')}
            className={`filter-btn ${filter === 'ALL' ? 'active' : ''}`}
          >
            All ({events.length})
          </button>
          <button
            onClick={() => setFilter('EMERGENCY')}
            className={`filter-btn ${filter === 'EMERGENCY' ? 'active emergency-tab' : ''}`}
          >
            Emergencies ({events.filter(e => e.event_type.startsWith('emergency')).length})
          </button>
          <button
            onClick={() => setFilter('WATCH')}
            className={`filter-btn ${filter === 'WATCH' ? 'active' : ''}`}
          >
            Safety Watch
          </button>
          <button
            onClick={() => setFilter('LOCATION')}
            className={`filter-btn ${filter === 'LOCATION' ? 'active' : ''}`}
          >
            Location Pings
          </button>
        </div>
      </div>

      {/* Events Timeline List */}
      <div className="history-list-container">
        {filteredEvents.length === 0 ? (
          <div className="history-empty-state">
            <Clock size={24} color="var(--watch-muted)" />
            <p>No events found for this filter category.</p>
          </div>
        ) : (
          <div className="timeline-list">
            {filteredEvents.map((evt) => {
              const badge = getEventBadge(evt.event_type);
              const BadgeIcon = badge.icon;
              const isEmergency = evt.event_type === 'emergency_triggered';

              return (
                <div
                  key={evt.id}
                  onClick={() => onSelectEvent(evt)}
                  className={`timeline-item ${isEmergency ? 'timeline-emergency' : ''}`}
                >
                  <div className={`timeline-icon-box ${badge.className}`}>
                    <BadgeIcon size={16} />
                  </div>

                  <div className="timeline-content">
                    <div className="timeline-top-row">
                      <div className="event-type-badge-group">
                        <span className={`event-badge ${badge.className}`}>
                          {badge.label}
                        </span>
                        {evt.payload?.trigger && (
                          <span className="event-trigger-tag mono">
                            Trigger: {evt.payload.trigger}
                          </span>
                        )}
                      </div>
                      <span className="event-time mono">
                        {new Date(evt.created_at).toLocaleString([], {
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit'
                        })}
                      </span>
                    </div>

                    <div className="timeline-location-row">
                      <MapPin size={13} color="var(--watch-soft)" />
                      <span>{evt.address || `${evt.lat?.toFixed(4)}, ${evt.lng?.toFixed(4)}`}</span>
                    </div>

                    {/* Metadata chips */}
                    <div className="timeline-meta-row">
                      {evt.payload?.audio_url && (
                        <span className="meta-chip meta-audio">
                          <Mic size={11} /> MicroSD Audio Evidence Recorded
                        </span>
                      )}
                      {evt.payload?.battery && (
                        <span className="meta-chip">
                          Battery: {evt.payload.battery}%
                        </span>
                      )}
                      {evt.payload?.notes && (
                        <span className="meta-chip meta-notes">
                          {evt.payload.notes}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="timeline-arrow">
                    <ChevronRight size={16} />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
