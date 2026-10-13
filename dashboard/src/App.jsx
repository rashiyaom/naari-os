import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginView from './components/LoginView';
import Navbar from './components/Navbar';
import WearerStatusCard from './components/WearerStatusCard';
import LiveMap from './components/LiveMap';
import AlertHistoryLog from './components/AlertHistoryLog';
import EmergencyDetailModal from './components/EmergencyDetailModal';
import SimulationBar from './components/SimulationBar';
import DatabaseConfigModal from './components/DatabaseConfigModal';
import WatchSimulator from './components/WatchSimulator';
import WatchShowcase from './components/WatchShowcase';

function MainDashboard({ onOpenDbConfig, onSwitchToWatch }) {
  const [selectedEvent, setSelectedEvent] = useState(null);

  return (
    <div className="dashboard-container">
      {/* Top Navigation */}
      <Navbar onOpenDbConfig={onOpenDbConfig} onSwitchToWatch={onSwitchToWatch} />

      {/* Main layout: watch simulator left | dashboard right */}
      <main className="dashboard-main-content">
        <div className="dashboard-split-layout">

          {/* ── Left: Apple Watch Simulator column ── */}
          <aside className="watch-simulator-col">
            <div className="watch-col-label">
              <span className="watch-col-badge">NAARI OS WATCH</span>
              <span className="watch-col-sub">Live Device Simulator</span>
            </div>
            <WatchSimulator />
          </aside>

          {/* ── Right: Dashboard content ── */}
          <div className="dashboard-content-col">
            {/* Dynamic State & Telemetry Hero Card */}
            <WearerStatusCard onSelectEvent={(evt) => setSelectedEvent(evt)} />

            {/* 2-Column Grid: Live Map & Alert History Log */}
            <div className="dashboard-grid">
              <div className="grid-col-map">
                <LiveMap />
              </div>
              <div className="grid-col-history">
                <AlertHistoryLog onSelectEvent={(evt) => setSelectedEvent(evt)} />
              </div>
            </div>
          </div>

        </div>
      </main>

      {/* Floating Hardware Event Simulator Bar */}
      <SimulationBar />

      {/* Emergency Detail Dossier Modal */}
      {selectedEvent && (
        <EmergencyDetailModal
          event={selectedEvent}
          onClose={() => setSelectedEvent(null)}
        />
      )}
    </div>
  );
}

function AppContent() {
  const { wearer, loading } = useAuth();
  const [viewMode, setViewMode] = useState('watch'); // 'watch' | 'dashboard'
  const [showDbModal, setShowDbModal] = useState(false);

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="loading-spinner" />
        <p>Connecting to Naari OS Guardian Network...</p>
      </div>
    );
  }

  // 1:1 Watch OS Display Showcase
  if (viewMode === 'watch') {
    return (
      <>
        <WatchShowcase onSwitchToDashboard={() => setViewMode('dashboard')} />
        {showDbModal && (
          <DatabaseConfigModal onClose={() => setShowDbModal(false)} />
        )}
      </>
    );
  }

  // Guardian Portal Dashboard
  return (
    <>
      {!wearer ? (
        <div>
          {/* Top Quick Banner to jump to Watch OS */}
          <div style={{
            background: 'rgba(0, 232, 168, 0.1)',
            borderBottom: '1px solid rgba(0, 232, 168, 0.25)',
            padding: '10px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            fontSize: '12px',
          }}>
            <span style={{ color: '#00e8a8', fontWeight: 600 }}>
              ⌚ Looking for the Apple Watch OS display simulation?
            </span>
            <button
              onClick={() => setViewMode('watch')}
              style={{
                background: '#00e8a8',
                color: '#000',
                border: 'none',
                fontWeight: 750,
                fontSize: '11px',
                padding: '5px 12px',
                borderRadius: '8px',
                cursor: 'pointer',
              }}
            >
              Open Watch OS 1:1 Display →
            </button>
          </div>
          <LoginView onOpenDbConfig={() => setShowDbModal(true)} />
        </div>
      ) : (
        <MainDashboard
          onOpenDbConfig={() => setShowDbModal(true)}
          onSwitchToWatch={() => setViewMode('watch')}
        />
      )}

      {showDbModal && (
        <DatabaseConfigModal onClose={() => setShowDbModal(false)} />
      )}
    </>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

